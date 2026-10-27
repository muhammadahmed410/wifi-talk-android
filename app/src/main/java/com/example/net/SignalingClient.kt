package com.example.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class SignalingClient {
    private val tag = "SignalingClient"

    /**
     * Send payload to target peer over TCP and optionally read response line.
     */
    suspend fun sendFrame(
        peerIp: String,
        peerPort: Int,
        payload: String,
        timeoutMs: Int = 4000
    ): String? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(peerIp, peerPort), timeoutMs)
            socket.soTimeout = timeoutMs

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            writer.println(payload)
            val response = reader.readLine()
            response
        } catch (e: Exception) {
            Log.w(tag, "Failed to send frame to $peerIp:$peerPort: ${e.message}")
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Measures round trip ping to peer.
     * @return latency in milliseconds or null if unreachable
     */
    suspend fun pingPeer(peerIp: String, peerPort: Int, localDeviceId: String): Long? =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val pingMsg = SignalingProtocol.buildPing(localDeviceId, start)
            val response = sendFrame(peerIp, peerPort, pingMsg, 3000)
            if (response != null) {
                try {
                    val json = JSONObject(response)
                    if (json.optString("type") == SignalingProtocol.TYPE_PONG) {
                        return@withContext System.currentTimeMillis() - start
                    }
                } catch (_: Exception) {}
            }
            null
        }
}
