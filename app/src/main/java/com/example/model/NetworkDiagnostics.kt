package com.example.model

data class NetworkDiagnostics(
    val wifiConnected: Boolean = false,
    val ssid: String = "Not connected",
    val localIp: String = "0.0.0.0",
    val broadcastIp: String = "255.255.255.255",
    val subnetMask: String = "255.255.255.0",
    val isMulticastLockAcquired: Boolean = false,
    val nsdRegistered: Boolean = false,
    val nsdServiceName: String = "",
    val listeningSignalingPort: Int = 5355,
    val listeningAudioPort: Int = 5356,
    val discoveredPeerCount: Int = 0,
    val onlinePeerCount: Int = 0,
    val lastDiscoveryTime: Long = 0L,
    val lastPingPeerIp: String? = null,
    val lastPingRoundTripMs: Long? = null,
    val lastPingStatus: String? = null,
    val diagnosticNotes: List<String> = emptyList()
)

data class AppSettings(
    val deviceId: String,
    val displayName: String,
    val signalingPort: Int = 5355,
    val audioPort: Int = 5356,
    val isBackgroundServiceEnabled: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isAutoAcceptKnownContacts: Boolean = false
)

enum class MainTab {
    PEOPLE,
    CHATS,
    DIAL_PAD,
    CALL_HISTORY,
    DIAGNOSTICS
}
