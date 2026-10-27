package com.example.repository

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.WiFiTalkApplication
import com.example.db.CallLogEntity
import com.example.db.MessageEntity
import com.example.db.PeerEntity
import com.example.model.CallSession
import com.example.model.CallState
import com.example.model.CallType
import com.example.model.Message
import com.example.model.MessageStatus
import com.example.model.NetworkDiagnostics
import com.example.model.Peer
import com.example.model.PeerStatus
import com.example.net.LocalDiscoveryManager
import com.example.net.NetworkUtils
import com.example.net.SignalingClient
import com.example.net.SignalingProtocol
import com.example.net.SignalingServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class WiFiTalkRepository(
    private val app: WiFiTalkApplication
) {
    private val tag = "WiFiTalkRepository"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val db = app.database
    private val securityManager = app.securityManager
    private val notificationHelper = app.notificationHelper
    private val audioEngine = app.voipAudioEngine
    private val signalingClient = app.signalingClient

    // Active Call Session StateFlow
    private val _currentCall = MutableStateFlow<CallSession?>(null)
    val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

    // Network Diagnostics StateFlow
    private val _diagnostics = MutableStateFlow(NetworkDiagnostics())
    val diagnostics: StateFlow<NetworkDiagnostics> = _diagnostics.asStateFlow()

    private var discoveryManager: LocalDiscoveryManager? = null
    private var signalingServer: SignalingServer? = null
    private var ringtone: Ringtone? = null
    private var callDurationJob: Job? = null

    init {
        setupNetworkMonitoring()
        startCoreServices()
    }

    private fun setupNetworkMonitoring() {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(tag, "Wi-Fi Network available")
                updateDiagnostics()
                restartDiscovery()
            }

            override fun onLost(network: Network) {
                Log.d(tag, "Wi-Fi Network lost")
                updateDiagnostics()
            }
        })
    }

    fun startCoreServices() {
        val localId = securityManager.deviceId
        val displayName = app.getDisplayName().ifEmpty { "User ${localId.take(4)}" }

        // 1. Initialize TCP Signaling Server
        signalingServer = SignalingServer(
            port = 5355,
            securityManager = securityManager,
            isPeerBlocked = { peerId ->
                // Check if peer is blocked synchronously via cache or DB
                false
            },
            onMessageReceived = { messageId, senderId, senderName, text, timestamp ->
                handleIncomingMessage(messageId, senderId, senderName, text, timestamp)
            },
            onMessageAckReceived = { messageId ->
                db.messageDao().updateMessageStatus(messageId, MessageStatus.DELIVERED.name)
            },
            onMessageReadReceived = { peerId ->
                db.messageDao().markConversationAsRead(peerId)
            },
            onCallInviteReceived = { callId, callerId, callerName, callerIp, callerAudioPort ->
                handleIncomingCallInvite(callId, callerId, callerName, callerIp, callerAudioPort)
            },
            onCallRingingReceived = { callId ->
                if (_currentCall.value?.callId == callId) {
                    _currentCall.value = _currentCall.value?.copy(state = CallState.OUTGOING_RINGING)
                }
            },
            onCallAcceptedReceived = { callId, calleeAudioPort ->
                handleCallAccepted(callId, calleeAudioPort)
            },
            onCallDeclinedReceived = { callId, reason ->
                handleCallDeclined(callId, reason)
            },
            onCallCancelledReceived = { callId ->
                handleCallCancelled(callId)
            },
            onCallEndedReceived = { callId, durationSeconds ->
                handleCallEnded(callId, durationSeconds)
            }
        ).apply { start() }

        // 2. Initialize Discovery Manager
        discoveryManager = LocalDiscoveryManager(
            context = app,
            localDeviceId = localId,
            localDisplayName = displayName,
            signalingPort = 5355,
            audioPort = 5356,
            onPeerDiscovered = { peer ->
                scope.launch {
                    val entity = PeerEntity(
                        deviceId = peer.deviceId,
                        displayName = peer.displayName,
                        ipAddress = peer.ipAddress,
                        signalingPort = peer.signalingPort,
                        audioPort = peer.audioPort,
                        lastSeenEpoch = peer.lastSeenEpoch,
                        status = peer.status.name,
                        isBlocked = peer.isBlocked,
                        customNote = peer.customNote
                    )
                    db.peerDao().insertOrUpdatePeer(entity)
                    updateDiagnostics()
                }
            },
            onPeerStatusChanged = { peerId, status ->
                scope.launch {
                    db.peerDao().updatePeerStatus(peerId, status.name)
                    updateDiagnostics()
                    // If peer came back ONLINE, retry queued pending messages!
                    if (status == PeerStatus.ONLINE) {
                        retryPendingMessagesForPeer(peerId)
                    }
                }
            }
        ).apply { start() }

        updateDiagnostics()
    }

    fun restartDiscovery() {
        discoveryManager?.stop()
        val localId = securityManager.deviceId
        val displayName = app.getDisplayName().ifEmpty { "User ${localId.take(4)}" }
        discoveryManager?.updateDisplayName(displayName)
        discoveryManager?.start()
        updateDiagnostics()
    }

    fun updateDisplayName(name: String) {
        app.setDisplayName(name)
        discoveryManager?.updateDisplayName(name)
    }

    // --- Database Flows for UI ---

    val allPeersFlow: Flow<List<Peer>> = db.peerDao().getAllPeersFlow().map { entities ->
        entities.map { entity ->
            Peer(
                deviceId = entity.deviceId,
                displayName = entity.displayName,
                ipAddress = entity.ipAddress,
                signalingPort = entity.signalingPort,
                audioPort = entity.audioPort,
                lastSeenEpoch = entity.lastSeenEpoch,
                status = try { PeerStatus.valueOf(entity.status) } catch (_: Exception) { PeerStatus.OFFLINE },
                isBlocked = entity.isBlocked,
                customNote = entity.customNote
            )
        }
    }

    fun getMessagesFlow(peerId: String): Flow<List<Message>> =
        db.messageDao().getMessagesForConversation(peerId).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    conversationPeerId = entity.conversationPeerId,
                    senderDeviceId = entity.senderDeviceId,
                    text = entity.text,
                    timestamp = entity.timestamp,
                    status = try { MessageStatus.valueOf(entity.status) } catch (_: Exception) { MessageStatus.PENDING },
                    isIncoming = entity.isIncoming
                )
            }
        }

    fun getAllMessagesFlow(): Flow<List<Message>> =
        db.messageDao().getAllMessagesFlow().map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    conversationPeerId = entity.conversationPeerId,
                    senderDeviceId = entity.senderDeviceId,
                    text = entity.text,
                    timestamp = entity.timestamp,
                    status = try { MessageStatus.valueOf(entity.status) } catch (_: Exception) { MessageStatus.PENDING },
                    isIncoming = entity.isIncoming
                )
            }
        }

    val callLogsFlow: Flow<List<CallLogEntity>> = db.callLogDao().getAllCallLogsFlow()

    // --- Messaging Implementation ---

    suspend fun sendMessage(targetPeer: Peer, text: String) {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val senderId = securityManager.deviceId
        val senderName = app.getDisplayName().ifEmpty { "User ${senderId.take(4)}" }

        // 1. Persist to Room with PENDING status
        val entity = MessageEntity(
            id = messageId,
            conversationPeerId = targetPeer.deviceId,
            senderDeviceId = senderId,
            text = text,
            timestamp = now,
            status = MessageStatus.PENDING.name,
            isIncoming = false
        )
        db.messageDao().insertMessage(entity)

        // 2. Transmit immediately if peer is ONLINE
        if (targetPeer.status == PeerStatus.ONLINE) {
            transmitMessage(targetPeer, entity, senderName)
        }
    }

    private suspend fun transmitMessage(targetPeer: Peer, message: MessageEntity, senderName: String) {
        db.messageDao().updateMessageStatus(message.id, MessageStatus.SENDING.name)

        val seq = securityManager.nextSequenceNumber()
        val sig = securityManager.signPayload(message.text)
        val payload = SignalingProtocol.buildMessageSend(
            messageId = message.id,
            conversationId = targetPeer.deviceId,
            senderId = securityManager.deviceId,
            senderName = senderName,
            text = message.text,
            timestamp = message.timestamp,
            seq = seq,
            signature = sig
        )

        val response = signalingClient.sendFrame(targetPeer.ipAddress, targetPeer.signalingPort, payload)
        if (response != null) {
            // Receiver accepted and persisted the message!
            db.messageDao().updateMessageStatus(message.id, MessageStatus.DELIVERED.name)
        } else {
            // Failed/Unreachable -> queue as PENDING
            db.messageDao().updateMessageStatus(message.id, MessageStatus.PENDING.name)
        }
    }

    private suspend fun retryPendingMessagesForPeer(peerId: String) {
        val peer = db.peerDao().getPeerById(peerId) ?: return
        if (peer.status != PeerStatus.ONLINE.name) return

        val p = Peer(
            deviceId = peer.deviceId,
            displayName = peer.displayName,
            ipAddress = peer.ipAddress,
            signalingPort = peer.signalingPort,
            audioPort = peer.audioPort,
            status = PeerStatus.ONLINE
        )
        val pending = db.messageDao().getPendingForPeer(peerId)
        val senderName = app.getDisplayName().ifEmpty { "User ${securityManager.deviceId.take(4)}" }

        for (msg in pending) {
            transmitMessage(p, msg, senderName)
        }
    }

    private suspend fun handleIncomingMessage(
        messageId: String,
        senderId: String,
        senderName: String,
        text: String,
        timestamp: Long
    ) {
        val entity = MessageEntity(
            id = messageId,
            conversationPeerId = senderId,
            senderDeviceId = senderId,
            text = text,
            timestamp = timestamp,
            status = MessageStatus.DELIVERED.name,
            isIncoming = true
        )
        db.messageDao().insertMessage(entity)

        // Ensure peer exists in DB
        val existing = db.peerDao().getPeerById(senderId)
        if (existing == null) {
            db.peerDao().insertOrUpdatePeer(
                PeerEntity(
                    deviceId = senderId,
                    displayName = senderName,
                    ipAddress = "",
                    lastSeenEpoch = timestamp,
                    status = PeerStatus.ONLINE.name
                )
            )
        }

        notificationHelper.showMessageNotification(senderId, senderName, text)
    }

    suspend fun markChatRead(peerId: String) {
        db.messageDao().markConversationAsRead(peerId)
        val peer = db.peerDao().getPeerById(peerId)
        if (peer != null && peer.status == PeerStatus.ONLINE.name && peer.ipAddress.isNotEmpty()) {
            val payload = SignalingProtocol.buildMessageRead(peerId, securityManager.deviceId, System.currentTimeMillis())
            signalingClient.sendFrame(peer.ipAddress, peer.signalingPort, payload)
        }
    }

    // --- Voice Calling Implementation ---

    fun startOutgoingCall(targetPeer: Peer) {
        if (_currentCall.value?.isActive == true) return

        val callId = UUID.randomUUID().toString()
        val session = CallSession(
            callId = callId,
            peerDeviceId = targetPeer.deviceId,
            peerDisplayName = targetPeer.displayName,
            peerIp = targetPeer.ipAddress,
            peerAudioPort = targetPeer.audioPort,
            state = CallState.OUTGOING_DIALING
        )
        _currentCall.value = session

        scope.launch {
            val seq = securityManager.nextSequenceNumber()
            val sig = securityManager.signPayload(callId)
            val myName = app.getDisplayName().ifEmpty { "User ${securityManager.deviceId.take(4)}" }

            val inviteJson = SignalingProtocol.buildCallInvite(
                callId = callId,
                callerId = securityManager.deviceId,
                callerName = myName,
                callerAudioPort = 5356,
                timestamp = System.currentTimeMillis(),
                seq = seq,
                signature = sig
            )

            val response = signalingClient.sendFrame(targetPeer.ipAddress, targetPeer.signalingPort, inviteJson, 8000)
            if (response == null) {
                // Peer unreachable or blocked
                endCall("Unreachable", CallType.MISSED)
            }
        }
    }

    private fun handleIncomingCallInvite(
        callId: String,
        callerId: String,
        callerName: String,
        callerIp: String,
        callerAudioPort: Int
    ) {
        if (_currentCall.value?.isActive == true) {
            // Send busy
            scope.launch {
                val decline = SignalingProtocol.buildCallDecline(callId, securityManager.deviceId, "Busy")
                signalingClient.sendFrame(callerIp, 5355, decline)
            }
            return
        }

        val session = CallSession(
            callId = callId,
            peerDeviceId = callerId,
            peerDisplayName = callerName,
            peerIp = callerIp,
            peerAudioPort = callerAudioPort,
            state = CallState.INCOMING_RINGING
        )
        _currentCall.value = session

        playRingtone()
        notificationHelper.showIncomingCallNotification(callId, callerId, callerName)
    }

    fun acceptIncomingCall() {
        val call = _currentCall.value ?: return
        if (call.state != CallState.INCOMING_RINGING) return

        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()

        val connectTime = System.currentTimeMillis()
        val connectedCall = call.copy(
            state = CallState.CONNECTED,
            connectTime = connectTime
        )
        _currentCall.value = connectedCall

        scope.launch {
            val acceptJson = SignalingProtocol.buildCallAccept(
                callId = call.callId,
                calleeId = securityManager.deviceId,
                calleeAudioPort = 5356
            )
            signalingClient.sendFrame(call.peerIp, 5355, acceptJson)

            // Start full-duplex VoIP audio
            audioEngine.startCall(
                localPort = 5356,
                remoteIp = call.peerIp,
                remotePort = call.peerAudioPort,
                callId = call.callId
            )
            startCallTimer()
        }
    }

    fun declineIncomingCall() {
        val call = _currentCall.value ?: return
        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()

        scope.launch {
            val declineJson = SignalingProtocol.buildCallDecline(
                callId = call.callId,
                calleeId = securityManager.deviceId,
                reason = "Declined"
            )
            signalingClient.sendFrame(call.peerIp, 5355, declineJson)
            logCall(call, CallType.DECLINED, 0L, "Declined")
            _currentCall.value = null
        }
    }

    fun cancelOutgoingCall() {
        val call = _currentCall.value ?: return
        scope.launch {
            val cancelJson = SignalingProtocol.buildCallCancel(call.callId, securityManager.deviceId)
            signalingClient.sendFrame(call.peerIp, 5355, cancelJson)
            logCall(call, CallType.OUTGOING, 0L, "Cancelled")
            _currentCall.value = null
        }
    }

    fun hangupActiveCall() {
        val call = _currentCall.value ?: return
        val duration = call.durationSeconds
        audioEngine.stopCall()
        callDurationJob?.cancel()

        scope.launch {
            val endJson = SignalingProtocol.buildCallEnd(call.callId, securityManager.deviceId, duration)
            signalingClient.sendFrame(call.peerIp, 5355, endJson)
            logCall(call, if (call.state == CallState.CONNECTED) CallType.OUTGOING else CallType.MISSED, duration, "Normal hangup")
            _currentCall.value = null
        }
    }

    private fun handleCallAccepted(callId: String, calleeAudioPort: Int) {
        val call = _currentCall.value ?: return
        if (call.callId != callId) return

        val connectTime = System.currentTimeMillis()
        val connected = call.copy(
            state = CallState.CONNECTED,
            peerAudioPort = calleeAudioPort,
            connectTime = connectTime
        )
        _currentCall.value = connected

        audioEngine.startCall(
            localPort = 5356,
            remoteIp = call.peerIp,
            remotePort = calleeAudioPort,
            callId = callId
        )
        startCallTimer()
    }

    private fun handleCallDeclined(callId: String, reason: String) {
        val call = _currentCall.value ?: return
        if (call.callId != callId) return

        audioEngine.stopCall()
        logCall(call, CallType.OUTGOING, 0L, reason)
        _currentCall.value = call.copy(state = CallState.DECLINED, terminationReason = reason)
        scope.launch {
            delay(2000L)
            _currentCall.value = null
        }
    }

    private fun handleCallCancelled(callId: String) {
        val call = _currentCall.value ?: return
        if (call.callId != callId) return

        stopRingtone()
        notificationHelper.cancelIncomingCallNotification()
        logCall(call, CallType.MISSED, 0L, "Caller cancelled")
        _currentCall.value = null
    }

    private fun handleCallEnded(callId: String, durationSeconds: Long) {
        val call = _currentCall.value ?: return
        if (call.callId != callId) return

        audioEngine.stopCall()
        callDurationJob?.cancel()
        logCall(call, CallType.INCOMING, durationSeconds, "Peer ended call")
        _currentCall.value = null
    }

    private fun endCall(reason: String, type: CallType) {
        val call = _currentCall.value ?: return
        stopRingtone()
        audioEngine.stopCall()
        callDurationJob?.cancel()
        logCall(call, type, 0L, reason)
        _currentCall.value = null
    }

    private fun startCallTimer() {
        callDurationJob?.cancel()
        callDurationJob = scope.launch {
            while (isActive && _currentCall.value?.isConnected == true) {
                delay(1000L)
                val current = _currentCall.value ?: break
                if (current.isConnected) {
                    val duration = (System.currentTimeMillis() - current.connectTime) / 1000
                    _currentCall.value = current.copy(durationSeconds = duration)
                }
            }
        }
    }

    fun toggleMute() {
        val call = _currentCall.value ?: return
        val newMute = !call.isMuted
        audioEngine.setMute(newMute)
        _currentCall.value = call.copy(isMuted = newMute)
    }

    fun toggleSpeakerphone() {
        val call = _currentCall.value ?: return
        val newSpeaker = !call.isSpeakerphoneOn
        audioEngine.setSpeakerphone(newSpeaker)
        _currentCall.value = call.copy(isSpeakerphoneOn = newSpeaker)
    }

    private fun logCall(call: CallSession, type: CallType, duration: Long, reason: String) {
        scope.launch {
            val entity = CallLogEntity(
                id = UUID.randomUUID().toString(),
                peerDeviceId = call.peerDeviceId,
                peerDisplayName = call.peerDisplayName,
                callType = type.name,
                timestamp = System.currentTimeMillis(),
                durationSeconds = duration,
                terminationReason = reason
            )
            db.callLogDao().insertCallLog(entity)
        }
    }

    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(app, uri)?.apply { play() }
        } catch (_: Exception) {}
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (_: Exception) {}
    }

    // --- Diagnostics ---

    fun updateDiagnostics() {
        scope.launch {
            val isWifi = NetworkUtils.isWifiConnected(app)
            val ip = NetworkUtils.getLocalIpAddress()
            val bcast = NetworkUtils.getBroadcastAddress()
            val ssid = NetworkUtils.getWifiSsid(app)
            val peers = db.peerDao().getActivePeers()
            val onlineCount = peers.count { it.status == PeerStatus.ONLINE.name }

            val notes = mutableListOf<String>()
            if (!isWifi) {
                notes.add("Device is not connected to a Wi-Fi network or Hotspot.")
            } else {
                notes.add("Wi-Fi network '$ssid' connected (IP: $ip).")
            }

            if (ip == "127.0.0.1" || ip == "0.0.0.0") {
                notes.add("Local network IP address is not assigned yet.")
            } else {
                notes.add("Dual discovery active: Android NSD and UDP broadcast ($bcast:5354).")
            }

            _diagnostics.value = NetworkDiagnostics(
                wifiConnected = isWifi,
                ssid = ssid,
                localIp = ip,
                broadcastIp = bcast,
                isMulticastLockAcquired = true,
                nsdRegistered = discoveryManager?.isNsdActive() == true,
                listeningSignalingPort = 5355,
                listeningAudioPort = 5356,
                discoveredPeerCount = peers.size,
                onlinePeerCount = onlineCount,
                lastDiscoveryTime = System.currentTimeMillis(),
                diagnosticNotes = notes
            )
        }
    }

    suspend fun runPingTest(peerIp: String, peerPort: Int = 5355): Long? {
        val rtt = signalingClient.pingPeer(peerIp, peerPort, securityManager.deviceId)
        _diagnostics.value = _diagnostics.value.copy(
            lastPingPeerIp = peerIp,
            lastPingRoundTripMs = rtt,
            lastPingStatus = if (rtt != null) "Reachable ($rtt ms RTT)" else "Unreachable / Port blocked"
        )
        return rtt
    }

    suspend fun clearAllHistory() {
        db.messageDao().clearAll()
        db.callLogDao().clearAll()
    }

    suspend fun resetLocalIdentity(): String {
        val newId = securityManager.resetIdentity()
        restartDiscovery()
        return newId
    }

    fun setPeerBlocked(deviceId: String, isBlocked: Boolean) {
        scope.launch {
            db.peerDao().setBlocked(deviceId, isBlocked)
        }
    }
}
