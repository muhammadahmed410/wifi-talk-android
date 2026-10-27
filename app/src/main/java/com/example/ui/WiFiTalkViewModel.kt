package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.WiFiTalkApplication
import com.example.db.CallLogEntity
import com.example.model.CallSession
import com.example.model.MainTab
import com.example.model.Message
import com.example.model.NetworkDiagnostics
import com.example.model.Peer
import com.example.repository.WiFiTalkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WiFiTalkViewModel(
    private val app: WiFiTalkApplication = WiFiTalkApplication.instance,
    val repository: WiFiTalkRepository = WiFiTalkRepository(app)
) : ViewModel() {

    // Main Navigation Tab
    private val _currentTab = MutableStateFlow(MainTab.PEOPLE)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    // Active conversation
    private val _selectedChatPeer = MutableStateFlow<Peer?>(null)
    val selectedChatPeer: StateFlow<Peer?> = _selectedChatPeer.asStateFlow()

    // Search query on People list
    val searchQuery = MutableStateFlow("")

    // Dial pad input
    val dialInput = MutableStateFlow("")

    // First launch display name setup
    val isFirstLaunch = MutableStateFlow(app.getDisplayName().isEmpty())

    // Active call session
    val currentCall: StateFlow<CallSession?> = repository.currentCall

    // Discovered peers
    val peers: StateFlow<List<Peer>> = repository.allPeersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All messages (for chats overview thread list)
    val allMessages: StateFlow<List<Message>> = repository.getAllMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Messages for selected chat
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentChatMessages: StateFlow<List<Message>> = _selectedChatPeer
        .flatMapLatest { peer ->
            if (peer != null) {
                repository.getMessagesFlow(peer.deviceId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Call logs
    val callLogs: StateFlow<List<CallLogEntity>> = repository.callLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Network diagnostics
    val diagnostics: StateFlow<NetworkDiagnostics> = repository.diagnostics

    // Profile & Direct IP Dialogs
    val showProfileDialog = MutableStateFlow(false)
    val showDirectIpDialog = MutableStateFlow(false)
    val pingStatusMessage = MutableStateFlow<String?>(null)

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun openChat(peer: Peer) {
        _selectedChatPeer.value = peer
        viewModelScope.launch {
            repository.markChatRead(peer.deviceId)
        }
    }

    fun closeChat() {
        _selectedChatPeer.value = null
    }

    fun sendMessage(text: String) {
        val peer = _selectedChatPeer.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(peer, text.trim())
        }
    }

    fun startCall(peer: Peer) {
        repository.startOutgoingCall(peer)
    }

    fun startDirectCall(ip: String, port: Int = 5356) {
        if (ip.isBlank()) return
        val tempPeer = Peer(
            deviceId = "ip_${ip.replace(".", "_")}",
            displayName = "Direct $ip",
            ipAddress = ip.trim(),
            signalingPort = 5355,
            audioPort = port
        )
        repository.startOutgoingCall(tempPeer)
    }

    fun acceptCall() {
        repository.acceptIncomingCall()
    }

    fun declineCall() {
        repository.declineIncomingCall()
    }

    fun cancelCall() {
        repository.cancelOutgoingCall()
    }

    fun hangupCall() {
        repository.hangupActiveCall()
    }

    fun toggleMute() {
        repository.toggleMute()
    }

    fun toggleSpeaker() {
        repository.toggleSpeakerphone()
    }

    fun saveDisplayName(name: String) {
        val clean = name.trim().ifEmpty { "User ${app.securityManager.deviceId.take(4)}" }
        repository.updateDisplayName(clean)
        isFirstLaunch.value = false
        showProfileDialog.value = false
    }

    fun runPingTest(ip: String) {
        viewModelScope.launch {
            pingStatusMessage.value = "Pinging $ip..."
            val rtt = repository.runPingTest(ip)
            pingStatusMessage.value = if (rtt != null) {
                "Ping to $ip succeeded: ${rtt}ms RTT"
            } else {
                "Ping failed: Peer at $ip unreachable or port 5355 blocked."
            }
        }
    }

    fun blockPeer(peerId: String, isBlocked: Boolean) {
        repository.setPeerBlocked(peerId, isBlocked)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun resetIdentity() {
        viewModelScope.launch {
            repository.resetLocalIdentity()
        }
    }

    fun refreshNetwork() {
        repository.restartDiscovery()
    }
}
