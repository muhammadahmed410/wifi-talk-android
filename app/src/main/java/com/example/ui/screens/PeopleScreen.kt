package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Peer
import com.example.model.PeerStatus
import com.example.ui.theme.CallGreen
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusUnreachable

@Composable
fun PeopleScreen(
    peers: List<Peer>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCallPeer: (Peer) -> Unit,
    onChatPeer: (Peer) -> Unit,
    onRefresh: () -> Unit,
    onDirectIpClick: () -> Unit,
    onBlockPeer: (String, Boolean) -> Unit
) {
    val filteredPeers = peers.filter {
        val q = searchQuery.trim().lowercase()
        q.isEmpty() ||
                it.displayName.lowercase().contains(q) ||
                it.ipAddress.contains(q) ||
                it.deviceId.lowercase().contains(q)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search & Refresh Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("peer_search_input"),
                placeholder = { Text("Search discovered people...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("refresh_peers_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh peer discovery")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredPeers.isEmpty()) {
            EmptyPeersView(
                onRefresh = onRefresh,
                onDirectIpClick = onDirectIpClick
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("peers_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPeers, key = { it.deviceId }) { peer ->
                    PeerCard(
                        peer = peer,
                        onCall = { onCallPeer(peer) },
                        onChat = { onChatPeer(peer) },
                        onBlock = { onBlockPeer(peer.deviceId, !peer.isBlocked) }
                    )
                }
            }
        }
    }
}

@Composable
fun PeerCard(
    peer: Peer,
    onCall: () -> Unit,
    onChat: () -> Unit,
    onBlock: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val statusColor = when (peer.status) {
        PeerStatus.ONLINE -> StatusOnline
        PeerStatus.UNREACHABLE -> StatusUnreachable
        PeerStatus.OFFLINE -> StatusOffline
    }

    val statusLabel = when (peer.status) {
        PeerStatus.ONLINE -> "Online"
        PeerStatus.UNREACHABLE -> "Unreachable"
        PeerStatus.OFFLINE -> "Offline"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("peer_card_${peer.deviceId}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with status indicator
            Box {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = peer.displayName.take(1).uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Peer details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (peer.isBlocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(Blocked)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusLabel,
                        fontSize = 13.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " • ${peer.ipAddress}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "ID: ${peer.deviceId.take(8)}...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Quick Call & Chat Action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onChat,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("chat_button_${peer.deviceId}")
                ) {
                    Icon(
                        Icons.Default.ChatBubble,
                        contentDescription = "Chat with ${peer.displayName}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onCall,
                    enabled = peer.status == PeerStatus.ONLINE && !peer.isBlocked,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (peer.status == PeerStatus.ONLINE && !peer.isBlocked) CallGreen.copy(alpha = 0.15f) else Color.Transparent)
                        .testTag("call_button_${peer.deviceId}")
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Call ${peer.displayName}",
                        tint = if (peer.status == PeerStatus.ONLINE && !peer.isBlocked) CallGreen else StatusOffline
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (peer.isBlocked) "Unblock Peer" else "Block Peer") },
                            onClick = {
                                menuExpanded = false
                                onBlock()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPeersView(
    onRefresh: () -> Unit,
    onDirectIpClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Scanning Local Wi-Fi...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No other WiFi Talk users discovered yet on this network.\nEnsure other devices are connected to the same Wi-Fi router or Hotspot with WiFi Talk open.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Button(
                onClick = onRefresh,
                modifier = Modifier.testTag("empty_refresh_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Again")
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalButton(
                onClick = onDirectIpClick,
                modifier = Modifier.testTag("direct_ip_button")
            ) {
                Text("Direct IP Call")
            }
        }
    }
}
