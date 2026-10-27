package com.example.net

import org.json.JSONObject

object SignalingProtocol {
    const val TYPE_HANDSHAKE = "HANDSHAKE"
    const val TYPE_HANDSHAKE_ACK = "HANDSHAKE_ACK"
    const val TYPE_MSG_SEND = "MSG_SEND"
    const val TYPE_MSG_ACK = "MSG_ACK"
    const val TYPE_MSG_READ = "MSG_READ"
    const val TYPE_CALL_INVITE = "CALL_INVITE"
    const val TYPE_CALL_RINGING = "CALL_RINGING"
    const val TYPE_CALL_ACCEPT = "CALL_ACCEPT"
    const val TYPE_CALL_DECLINE = "CALL_DECLINE"
    const val TYPE_CALL_CANCEL = "CALL_CANCEL"
    const val TYPE_CALL_END = "CALL_END"
    const val TYPE_CALL_PING = "CALL_PING"
    const val TYPE_PING = "PING"
    const val TYPE_PONG = "PONG"

    fun buildHandshake(
        deviceId: String,
        displayName: String,
        signalingPort: Int,
        audioPort: Int,
        timestamp: Long,
        seq: Long,
        signature: String
    ): String {
        return JSONObject().apply {
            put("type", TYPE_HANDSHAKE)
            put("deviceId", deviceId)
            put("displayName", displayName)
            put("signalingPort", signalingPort)
            put("audioPort", audioPort)
            put("timestamp", timestamp)
            put("seq", seq)
            put("sig", signature)
        }.toString()
    }

    fun buildMessageSend(
        messageId: String,
        conversationId: String,
        senderId: String,
        senderName: String,
        text: String,
        timestamp: Long,
        seq: Long,
        signature: String
    ): String {
        return JSONObject().apply {
            put("type", TYPE_MSG_SEND)
            put("messageId", messageId)
            put("conversationId", conversationId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("text", text)
            put("timestamp", timestamp)
            put("seq", seq)
            put("sig", signature)
        }.toString()
    }

    fun buildMessageAck(messageId: String, receiverId: String, timestamp: Long): String {
        return JSONObject().apply {
            put("type", TYPE_MSG_ACK)
            put("messageId", messageId)
            put("receiverId", receiverId)
            put("timestamp", timestamp)
        }.toString()
    }

    fun buildMessageRead(conversationId: String, readerId: String, timestamp: Long): String {
        return JSONObject().apply {
            put("type", TYPE_MSG_READ)
            put("conversationId", conversationId)
            put("readerId", readerId)
            put("timestamp", timestamp)
        }.toString()
    }

    fun buildCallInvite(
        callId: String,
        callerId: String,
        callerName: String,
        callerAudioPort: Int,
        timestamp: Long,
        seq: Long,
        signature: String
    ): String {
        return JSONObject().apply {
            put("type", TYPE_CALL_INVITE)
            put("callId", callId)
            put("callerId", callerId)
            put("callerName", callerName)
            put("callerAudioPort", callerAudioPort)
            put("timestamp", timestamp)
            put("seq", seq)
            put("sig", signature)
        }.toString()
    }

    fun buildCallRinging(callId: String, calleeId: String): String {
        return JSONObject().apply {
            put("type", TYPE_CALL_RINGING)
            put("callId", callId)
            put("calleeId", calleeId)
        }.toString()
    }

    fun buildCallAccept(callId: String, calleeId: String, calleeAudioPort: Int): String {
        return JSONObject().apply {
            put("type", TYPE_CALL_ACCEPT)
            put("callId", callId)
            put("calleeId", calleeId)
            put("calleeAudioPort", calleeAudioPort)
        }.toString()
    }

    fun buildCallDecline(callId: String, calleeId: String, reason: String = "Declined"): String {
        return JSONObject().apply {
            put("type", TYPE_CALL_DECLINE)
            put("callId", callId)
            put("calleeId", calleeId)
            put("reason", reason)
        }.toString()
    }

    fun buildCallCancel(callId: String, callerId: String): String {
        return JSONObject().apply {
            put("type", TYPE_CALL_CANCEL)
            put("callId", callId)
            put("callerId", callerId)
        }.toString()
    }

    fun buildCallEnd(callId: String, senderId: String, durationSeconds: Long): String {
        return JSONObject().apply {
            put("type", TYPE_CALL_END)
            put("callId", callId)
            put("senderId", senderId)
            put("duration", durationSeconds)
        }.toString()
    }

    fun buildPing(senderId: String, timestamp: Long): String {
        return JSONObject().apply {
            put("type", TYPE_PING)
            put("senderId", senderId)
            put("timestamp", timestamp)
        }.toString()
    }

    fun buildPong(senderId: String, origTimestamp: Long): String {
        return JSONObject().apply {
            put("type", TYPE_PONG)
            put("senderId", senderId)
            put("origTimestamp", origTimestamp)
            put("replyTimestamp", System.currentTimeMillis())
        }.toString()
    }
}
