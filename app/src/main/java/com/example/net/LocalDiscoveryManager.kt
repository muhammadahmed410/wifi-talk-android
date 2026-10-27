package com.example.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.Peer
import com.example.model.PeerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust Local-Network Peer Discovery.
 * Combines Android Network Service Discovery (DNS-SD/mDNS) with UDP Broadcast/Multicast fallback.
 * Works seamlessly across standard Wi-Fi routers and portable Wi-Fi Hotspots without internet.
 */
class LocalDiscoveryManager(
    private val context: Context,
    private val localDeviceId: String,
    private var localDisplayName: String,
    private val signalingPort: Int = 5355,
    private val audioPort: Int = 5356,
    private val onPeerDiscovered: (Peer) -> Unit,
    private val onPeerStatusChanged: (String, PeerStatus) -> Unit
) {
    private val tag = "LocalDiscoveryManager"

    companion object {
        const val NSD_SERVICE_TYPE = "_wifitalk._tcp."
        const val UDP_DISCOVERY_PORT = 5354
        const val BEACON_PREFIX = "WFTK_BCN:"
        const val HEARTBEAT_INTERVAL_MS = 4000L
        const val UNREACHABLE_TIMEOUT_MS = 12000L
        const val OFFLINE_TIMEOUT_MS = 25000L
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var udpSocket: DatagramSocket? = null
    private var isRunning = false
    private var isNsdRegistered = false
    private var registeredNsdServiceName: String? = null

    // Track active peers: deviceId -> Peer
    private val peerMap = ConcurrentHashMap<String, Peer>()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun updateDisplayName(newName: String) {
        localDisplayName = newName
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        multicastLock = NetworkUtils.createMulticastLock(context)?.apply {
            try { acquire() } catch (e: Exception) { Log.w(tag, "Failed to acquire multicast lock", e) }
        }

        startNsdRegistration()
        startNsdDiscovery()
        startUdpBroadcastDiscovery()
        startPeerHealthCheck()

        Log.i(tag, "LocalDiscoveryManager started for device $localDeviceId ($localDisplayName)")
    }

    private fun startNsdRegistration() {
        if (nsdManager == null) return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "WiFiTalk-$localDeviceId"
            serviceType = NSD_SERVICE_TYPE
            port = signalingPort
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                isNsdRegistered = true
                registeredNsdServiceName = info.serviceName
                Log.d(tag, "NSD service registered: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                isNsdRegistered = false
                Log.w(tag, "NSD registration failed: code $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                isNsdRegistered = false
                Log.d(tag, "NSD service unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "NSD unregistration failed: code $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register NSD service", e)
        }
    }

    private fun startNsdDiscovery() {
        if (nsdManager == null) return
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "NSD Discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("wifitalk", ignoreCase = true)) {
                    if (service.serviceName == registeredNsdServiceName) {
                        return // Ignore own service
                    }
                    resolveNsdService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "NSD service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "NSD Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(tag, "NSD start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(tag, "NSD stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error initiating NSD discovery", e)
        }
    }

    private fun resolveNsdService(service: NsdServiceInfo) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "NSD resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host ?: return
                val ip = host.hostAddress ?: return
                val port = serviceInfo.port
                val serviceName = serviceInfo.serviceName

                // Extract deviceId from name "WiFiTalk-<deviceId>"
                val deviceId = if (serviceName.startsWith("WiFiTalk-")) {
                    serviceName.removePrefix("WiFiTalk-")
                } else {
                    serviceName
                }

                if (deviceId != localDeviceId && !ip.startsWith("127.")) {
                    handlePeerBeacon(
                        deviceId = deviceId,
                        displayName = "WiFi Peer ${deviceId.take(4)}",
                        ip = ip,
                        signalingPort = port,
                        audioPort = audioPort
                    )
                }
            }
        }

        try {
            nsdManager?.resolveService(service, resolver)
        } catch (e: Exception) {
            Log.w(tag, "NSD resolve call exception", e)
        }
    }

    private fun startUdpBroadcastDiscovery() {
        // 1. UDP Receiver Loop
        scope.launch {
            try {
                val socket = DatagramSocket(UDP_DISCOVERY_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                udpSocket = socket

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && isRunning) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (text.startsWith(BEACON_PREFIX)) {
                            val jsonStr = text.removePrefix(BEACON_PREFIX)
                            val json = JSONObject(jsonStr)
                            val peerDeviceId = json.optString("deviceId")
                            val peerDisplayName = json.optString("displayName", "Unknown")
                            val peerSigPort = json.optInt("port", signalingPort)
                            val peerAudPort = json.optInt("audioPort", audioPort)
                            val senderIp = packet.address.hostAddress ?: ""

                            if (peerDeviceId.isNotEmpty() && peerDeviceId != localDeviceId && senderIp.isNotEmpty()) {
                                handlePeerBeacon(
                                    deviceId = peerDeviceId,
                                    displayName = peerDisplayName,
                                    ip = senderIp,
                                    signalingPort = peerSigPort,
                                    audioPort = peerAudPort
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "UDP discovery socket error", e)
            }
        }

        // 2. Periodic UDP Broadcast Sender Loop
        scope.launch {
            val sendSocket = DatagramSocket()
            sendSocket.broadcast = true

            while (isActive && isRunning) {
                try {
                    val beaconJson = JSONObject().apply {
                        put("deviceId", localDeviceId)
                        put("displayName", localDisplayName)
                        put("port", signalingPort)
                        put("audioPort", audioPort)
                        put("time", System.currentTimeMillis())
                    }
                    val payload = (BEACON_PREFIX + beaconJson.toString()).toByteArray(Charsets.UTF_8)
                    val broadcastIpStr = NetworkUtils.getBroadcastAddress()
                    val broadcastAddr = InetAddress.getByName(broadcastIpStr)

                    val packet = DatagramPacket(payload, payload.size, broadcastAddr, UDP_DISCOVERY_PORT)
                    sendSocket.send(packet)
                } catch (e: Exception) {
                    Log.d(tag, "Broadcast send error: ${e.message}")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
            try { sendSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handlePeerBeacon(
        deviceId: String,
        displayName: String,
        ip: String,
        signalingPort: Int,
        audioPort: Int
    ) {
        val now = System.currentTimeMillis()
        val existing = peerMap[deviceId]

        val updated = if (existing != null) {
            val wasOffline = existing.status != PeerStatus.ONLINE
            val peer = existing.copy(
                displayName = displayName.ifEmpty { existing.displayName },
                ipAddress = ip,
                signalingPort = signalingPort,
                audioPort = audioPort,
                lastSeenEpoch = now,
                status = PeerStatus.ONLINE
            )
            peerMap[deviceId] = peer
            if (wasOffline) {
                onPeerStatusChanged(deviceId, PeerStatus.ONLINE)
            }
            peer
        } else {
            val peer = Peer(
                deviceId = deviceId,
                displayName = displayName,
                ipAddress = ip,
                signalingPort = signalingPort,
                audioPort = audioPort,
                lastSeenEpoch = now,
                status = PeerStatus.ONLINE
            )
            peerMap[deviceId] = peer
            onPeerDiscovered(peer)
            peer
        }
        onPeerDiscovered(updated)
    }

    private fun startPeerHealthCheck() {
        scope.launch {
            while (isActive && isRunning) {
                delay(3000L)
                val now = System.currentTimeMillis()
                for ((id, peer) in peerMap) {
                    val elapsed = now - peer.lastSeenEpoch
                    when {
                        elapsed > OFFLINE_TIMEOUT_MS -> {
                            if (peer.status != PeerStatus.OFFLINE) {
                                val updated = peer.copy(status = PeerStatus.OFFLINE)
                                peerMap[id] = updated
                                onPeerStatusChanged(id, PeerStatus.OFFLINE)
                            }
                        }
                        elapsed > UNREACHABLE_TIMEOUT_MS -> {
                            if (peer.status == PeerStatus.ONLINE) {
                                val updated = peer.copy(status = PeerStatus.UNREACHABLE)
                                peerMap[id] = updated
                                onPeerStatusChanged(id, PeerStatus.UNREACHABLE)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getDiscoveredPeers(): List<Peer> = peerMap.values.toList()

    fun isNsdActive(): Boolean = isNsdRegistered

    fun stop() {
        isRunning = false
        try {
            if (registrationListener != null && nsdManager != null) {
                nsdManager.unregisterService(registrationListener)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering NSD", e)
        }

        try {
            if (discoveryListener != null && nsdManager != null) {
                nsdManager.stopServiceDiscovery(discoveryListener)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping NSD discovery", e)
        }

        try {
            udpSocket?.close()
        } catch (_: Exception) {}

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}

        peerMap.clear()
        Log.i(tag, "LocalDiscoveryManager stopped")
    }
}
