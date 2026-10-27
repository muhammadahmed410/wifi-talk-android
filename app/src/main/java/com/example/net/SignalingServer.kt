package com.example.net

import android.util.Log
import com.example.crypto.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Listens for incoming TCP signaling frames (chat messages, call signaling, ACKs).
 */
class SignalingServer(
    private val port: Int = 5355,
    private val securityManager: SecurityManager,
    private val isPeerBlocked: (String) -> Boolean,
    private val onMessageReceived: suspend (messageId: String, senderId: String, senderName: String, text: String, timestamp: Long) -> Unit,
    private val onMessageAckReceived: suspend (messageId: String) -> Unit,
    private val onMessageReadReceived: suspend (peerId: String) -> Unit,
    private val onCallInviteReceived: (callId: String, callerId: String, callerName: String, callerIp: String, callerAudioPort: Int) -> Unit,
    private val onCallRingingReceived: (callId: String) -> Unit,
    private val onCallAcceptedReceived: (callId: String, calleeAudioPort: Int) -> Unit,
    private val onCallDeclinedReceived: (callId: String, reason: String) -> Unit,
    private val onCallCancelledReceived: (callId: String) -> Unit,
    private val onCallEndedReceived: (callId: String, durationSeconds: Long) -> Unit
) {
    private val tag = "SignalingServer"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(tag, "SignalingServer listening on TCP port $port")

                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClientConnection(clientSocket)
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "SignalingServer server socket failed", e)
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 5000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(socket.getOutputStream(), true)

                val line = reader.readLine()
                if (line != null && line.length < 65536) {
                    val json = JSONObject(line)
                    val type = json.optString("type")
                    val senderIp = socket.inetAddress?.hostAddress ?: ""

                    when (type) {
                        SignalingProtocol.TYPE_MSG_SEND -> {
                            val msgId = json.optString("messageId")
                            val senderId = json.optString("senderId")
                            val senderName = json.optString("senderName", "Peer")
                            val rawText = json.optString("text")
                            val ts = json.optLong("timestamp", System.currentTimeMillis())

                            if (isPeerBlocked(senderId)) {
                                Log.w(tag, "Ignoring message from blocked peer $senderId")
                            } else {
                                val cleanText = securityManager.sanitizeMessageText(rawText)
                                // 1. Persist to Room database first
                                onMessageReceived(msgId, senderId, senderName, cleanText, ts)
                                // 2. Send delivery acknowledgment back
                                val ackJson = SignalingProtocol.buildMessageAck(
                                    messageId = msgId,
                                    receiverId = securityManager.deviceId,
                                    timestamp = System.currentTimeMillis()
                                )
                                writer.println(ackJson)
                            }
                        }

                        SignalingProtocol.TYPE_MSG_ACK -> {
                            val msgId = json.optString("messageId")
                            onMessageAckReceived(msgId)
                        }

                        SignalingProtocol.TYPE_MSG_READ -> {
                            val readerId = json.optString("readerId")
                            onMessageReadReceived(readerId)
                        }

                        SignalingProtocol.TYPE_CALL_INVITE -> {
                            val callId = json.optString("callId")
                            val callerId = json.optString("callerId")
                            val callerName = json.optString("callerName", "Peer")
                            val callerAudioPort = json.optInt("callerAudioPort", 5356)

                            if (isPeerBlocked(callerId)) {
                                val decline = SignalingProtocol.buildCallDecline(callId, securityManager.deviceId, "Blocked")
                                writer.println(decline)
                            } else {
                                onCallInviteReceived(callId, callerId, callerName, senderIp, callerAudioPort)
                                // Send back ringing indication
                                val ringing = SignalingProtocol.buildCallRinging(callId, securityManager.deviceId)
                                writer.println(ringing)
                            }
                        }

                        SignalingProtocol.TYPE_CALL_RINGING -> {
                            val callId = json.optString("callId")
                            onCallRingingReceived(callId)
                        }

                        SignalingProtocol.TYPE_CALL_ACCEPT -> {
                            val callId = json.optString("callId")
                            val audioPort = json.optInt("calleeAudioPort", 5356)
                            onCallAcceptedReceived(callId, audioPort)
                        }

                        SignalingProtocol.TYPE_CALL_DECLINE -> {
                            val callId = json.optString("callId")
                            val reason = json.optString("reason", "Declined")
                            onCallDeclinedReceived(callId, reason)
                        }

                        SignalingProtocol.TYPE_CALL_CANCEL -> {
                            val callId = json.optString("callId")
                            onCallCancelledReceived(callId)
                        }

                        SignalingProtocol.TYPE_CALL_END -> {
                            val callId = json.optString("callId")
                            val duration = json.optLong("duration", 0L)
                            onCallEndedReceived(callId, duration)
                        }

                        SignalingProtocol.TYPE_PING -> {
                            val senderId = json.optString("senderId")
                            val ts = json.optLong("timestamp")
                            val pong = SignalingProtocol.buildPong(securityManager.deviceId, ts)
                            writer.println(pong)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "Client connection handling error: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        Log.i(tag, "SignalingServer stopped")
    }
}
