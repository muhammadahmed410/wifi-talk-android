package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.MainTab
import com.example.model.MessageStatus
import com.example.service.WiFiTalkService
import com.example.ui.WiFiTalkViewModel
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.CallOverlayScreen
import com.example.ui.screens.ChatConversationScreen
import com.example.ui.screens.ChatsOverviewScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.DialPadScreen
import com.example.ui.screens.DirectIpCallDialog
import com.example.ui.screens.EditProfileDialog
import com.example.ui.screens.FirstLaunchProfileDialog
import com.example.ui.screens.PeopleScreen
import com.example.ui.theme.CallGreen
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusUnreachable
import com.example.ui.theme.WiFiTalkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WiFiTalkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start background foreground service for local discovery and availability
        WiFiTalkService.startService(this)

        handleIntent(intent)

        setContent {
            WiFiTalkTheme {
                RequestRuntimePermissions()
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            WiFiTalkService.ACTION_ANSWER_CALL -> {
                viewModel.acceptCall()
            }
            WiFiTalkService.ACTION_DECLINE_CALL -> {
                viewModel.declineCall()
            }
        }
        val openChatPeerId = intent?.getStringExtra("EXTRA_OPEN_CHAT_PEER_ID")
        if (!openChatPeerId.isNullOrEmpty()) {
            val peer = viewModel.peers.value.firstOrNull { it.deviceId == openChatPeerId }
            if (peer != null) {
                viewModel.openChat(peer)
                viewModel.selectTab(MainTab.CHATS)
            }
        }
    }

    @Composable
    private fun RequestRuntimePermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Permissions result handled gracefully */ }

        LaunchedEffect(Unit) {
            val ungranted = permissionsToRequest.filter {
                ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
            }
            if (ungranted.isNotEmpty()) {
                launcher.launch(ungranted.toTypedArray())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: WiFiTalkViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedChatPeer by viewModel.selectedChatPeer.collectAsState()
    val currentCall by viewModel.currentCall.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()
    val currentChatMessages by viewModel.currentChatMessages.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dialInput by viewModel.dialInput.collectAsState()
    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()
    val showProfileDialog by viewModel.showProfileDialog.collectAsState()
    val showDirectIpDialog by viewModel.showDirectIpDialog.collectAsState()
    val pingStatus by viewModel.pingStatusMessage.collectAsState()

    val totalUnreadCount = allMessages.count { it.isIncoming && it.status != MessageStatus.READ }

    val app = WiFiTalkApplication.instance
    val localDeviceId = app.securityManager.deviceId
    val displayName = app.getDisplayName().ifEmpty { "User ${localDeviceId.take(4)}" }

    Scaffold(
        topBar = {
            if (selectedChatPeer == null) {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "WiFi Talk",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Wi-Fi status pill
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (diagnostics.wifiConnected) CallGreen.copy(alpha = 0.15f) else StatusUnreachable.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (diagnostics.wifiConnected) CallGreen else StatusUnreachable)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (diagnostics.wifiConnected) diagnostics.ssid.take(16) else "Offline",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (diagnostics.wifiConnected) CallGreen else StatusUnreachable
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "IP: ${diagnostics.localIp} • No Internet Needed",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.showDirectIpDialog.value = true },
                            modifier = Modifier.testTag("top_bar_direct_ip")
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = "Direct IP Call")
                        }
                        IconButton(
                            onClick = { viewModel.showProfileDialog.value = true },
                            modifier = Modifier.testTag("top_bar_profile")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = displayName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (selectedChatPeer == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == MainTab.PEOPLE,
                        onClick = { viewModel.selectTab(MainTab.PEOPLE) },
                        icon = {
                            BadgedBox(badge = {
                                if (peers.isNotEmpty()) {
                                    Badge { Text("${peers.size}") }
                                }
                            }) {
                                Icon(Icons.Default.People, contentDescription = "People")
                            }
                        },
                        label = { Text("People") },
                        modifier = Modifier.testTag("tab_people")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.CHATS,
                        onClick = { viewModel.selectTab(MainTab.CHATS) },
                        icon = {
                            BadgedBox(badge = {
                                if (totalUnreadCount > 0) {
                                    Badge { Text("$totalUnreadCount") }
                                }
                            }) {
                                Icon(Icons.Default.ChatBubble, contentDescription = "Chats")
                            }
                        },
                        label = { Text("Chats") },
                        modifier = Modifier.testTag("tab_chats")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.DIAL_PAD,
                        onClick = { viewModel.selectTab(MainTab.DIAL_PAD) },
                        icon = { Icon(Icons.Default.Dialpad, contentDescription = "Dial Pad") },
                        label = { Text("Dial Pad") },
                        modifier = Modifier.testTag("tab_dial_pad")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.CALL_HISTORY,
                        onClick = { viewModel.selectTab(MainTab.CALL_HISTORY) },
                        icon = { Icon(Icons.Default.Call, contentDescription = "Call History") },
                        label = { Text("History") },
                        modifier = Modifier.testTag("tab_call_history")
                    )

                    NavigationBarItem(
                        selected = currentTab == MainTab.DIAGNOSTICS,
                        onClick = { viewModel.selectTab(MainTab.DIAGNOSTICS) },
                        icon = { Icon(Icons.Default.NetworkCheck, contentDescription = "Diagnostics") },
                        label = { Text("Diagnostics") },
                        modifier = Modifier.testTag("tab_diagnostics")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedChatPeer != null) {
                ChatConversationScreen(
                    peer = selectedChatPeer!!,
                    messages = currentChatMessages,
                    onSendMessage = { text -> viewModel.sendMessage(text) },
                    onCallPeer = { viewModel.startCall(selectedChatPeer!!) },
                    onBack = { viewModel.closeChat() }
                )
            } else {
                when (currentTab) {
                    MainTab.PEOPLE -> {
                        PeopleScreen(
                            peers = peers,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.searchQuery.value = it },
                            onCallPeer = { peer -> viewModel.startCall(peer) },
                            onChatPeer = { peer -> viewModel.openChat(peer) },
                            onRefresh = { viewModel.refreshNetwork() },
                            onDirectIpClick = { viewModel.showDirectIpDialog.value = true },
                            onBlockPeer = { peerId, blocked -> viewModel.blockPeer(peerId, blocked) }
                        )
                    }

                    MainTab.CHATS -> {
                        ChatsOverviewScreen(
                            peers = peers,
                            allMessages = allMessages,
                            onSelectPeerChat = { peer -> viewModel.openChat(peer) }
                        )
                    }

                    MainTab.DIAL_PAD -> {
                        DialPadScreen(
                            dialInput = dialInput,
                            onDialInputChange = { viewModel.dialInput.value = it },
                            peers = peers,
                            onCallClick = { input ->
                                // If input matches peer IP or device ID, call peer; else direct IP call
                                val matchingPeer = peers.firstOrNull { it.ipAddress == input || it.deviceId.startsWith(input) }
                                if (matchingPeer != null) {
                                    viewModel.startCall(matchingPeer)
                                } else {
                                    viewModel.startDirectCall(input)
                                }
                            },
                            onSelectPeer = { peer ->
                                viewModel.startCall(peer)
                            }
                        )
                    }

                    MainTab.CALL_HISTORY -> {
                        CallHistoryScreen(
                            callLogs = callLogs,
                            onRedial = { deviceId, _ ->
                                val peer = peers.firstOrNull { it.deviceId == deviceId }
                                if (peer != null) {
                                    viewModel.startCall(peer)
                                }
                            },
                            onClearHistory = { viewModel.clearHistory() }
                        )
                    }

                    MainTab.DIAGNOSTICS -> {
                        DiagnosticsScreen(
                            diagnostics = diagnostics,
                            localDeviceId = localDeviceId,
                            displayName = displayName,
                            pingStatus = pingStatus,
                            onPingTest = { ip -> viewModel.runPingTest(ip) },
                            onRefreshNetwork = { viewModel.refreshNetwork() },
                            onEditProfile = { viewModel.showProfileDialog.value = true },
                            onClearHistory = { viewModel.clearHistory() },
                            onResetIdentity = { viewModel.resetIdentity() }
                        )
                    }
                }
            }

            // Full-screen Call Overlay Screen (Ringing, Calling, Connected VoIP)
            AnimatedVisibility(
                visible = currentCall != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                currentCall?.let { call ->
                    CallOverlayScreen(
                        call = call,
                        onAccept = { viewModel.acceptCall() },
                        onDecline = { viewModel.declineCall() },
                        onCancel = { viewModel.cancelCall() },
                        onHangup = { viewModel.hangupCall() },
                        onToggleMute = { viewModel.toggleMute() },
                        onToggleSpeaker = { viewModel.toggleSpeaker() }
                    )
                }
            }

            // First Launch Onboarding Dialog
            if (isFirstLaunch) {
                FirstLaunchProfileDialog(
                    initialName = displayName,
                    deviceId = localDeviceId,
                    onSaveName = { name -> viewModel.saveDisplayName(name) }
                )
            }

            // Edit Profile Dialog
            if (showProfileDialog) {
                EditProfileDialog(
                    currentName = displayName,
                    deviceId = localDeviceId,
                    onSaveName = { name -> viewModel.saveDisplayName(name) },
                    onDismiss = { viewModel.showProfileDialog.value = false }
                )
            }

            // Direct IP Call Dialog
            if (showDirectIpDialog) {
                DirectIpCallDialog(
                    onCallIp = { ip -> viewModel.startDirectCall(ip) },
                    onDismiss = { viewModel.showDirectIpDialog.value = false }
                )
            }
        }
    }
}
