package com.example.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                // Prioritize wlan0 / Wi-Fi interfaces
                val isWifi = intf.name.contains("wlan", ignoreCase = true) ||
                             intf.name.contains("ap", ignoreCase = true) ||
                             intf.name.contains("eth", ignoreCase = true)

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (isWifi) return hostAddress
                    }
                }
            }

            // Fallback: any non-loopback IPv4
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return "127.0.0.1"
    }

    fun getBroadcastAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (interfaceAddress in intf.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && broadcast is Inet4Address) {
                        return broadcast.hostAddress ?: "255.255.255.255"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating broadcast address", e)
        }
        return "255.255.255.255"
    }

    fun getWifiSsid(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            if (info != null && info.ssid != null) {
                val ssid = info.ssid
                if (ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                    return ssid.replace("\"", "")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wi-Fi SSID", e)
        }
        return "Local Wi-Fi / Hotspot"
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun createMulticastLock(context: Context): WifiManager.MulticastLock? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.createMulticastLock("WiFiTalkMulticastLock").apply {
                setReferenceCounted(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not create MulticastLock", e)
            null
        }
    }
}
