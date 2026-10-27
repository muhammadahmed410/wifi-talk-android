package com.example.model

enum class PeerStatus {
    ONLINE,
    UNREACHABLE,
    OFFLINE
}

data class Peer(
    val deviceId: String,
    val displayName: String,
    val ipAddress: String,
    val signalingPort: Int = 5355,
    val audioPort: Int = 5356,
    val lastSeenEpoch: Long = System.currentTimeMillis(),
    val status: PeerStatus = PeerStatus.ONLINE,
    val isBlocked: Boolean = false,
    val customNote: String = ""
) {
    val isReachable: Boolean
        get() = status == PeerStatus.ONLINE
}
