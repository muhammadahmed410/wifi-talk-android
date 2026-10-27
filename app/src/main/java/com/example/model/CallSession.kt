package com.example.model

import java.util.UUID

enum class CallState {
    IDLE,
    OUTGOING_DIALING,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTED,
    ENDED,
    DECLINED,
    BUSY,
    MISSED,
    FAILED
}

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    DECLINED
}

data class CallSession(
    val callId: String = UUID.randomUUID().toString(),
    val peerDeviceId: String,
    val peerDisplayName: String,
    val peerIp: String,
    val peerAudioPort: Int = 5356,
    val state: CallState = CallState.IDLE,
    val startTime: Long = System.currentTimeMillis(),
    val connectTime: Long = 0L,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerphoneOn: Boolean = false,
    val terminationReason: String = ""
) {
    val isActive: Boolean
        get() = state in listOf(
            CallState.OUTGOING_DIALING,
            CallState.OUTGOING_RINGING,
            CallState.INCOMING_RINGING,
            CallState.CONNECTED
        )

    val isConnected: Boolean
        get() = state == CallState.CONNECTED
}
