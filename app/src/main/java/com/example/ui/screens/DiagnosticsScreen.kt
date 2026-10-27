package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkDiagnostics
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CallRed
import com.example.ui.theme.StatusUnreachable

@Composable
fun DiagnosticsScreen(
    diagnostics: NetworkDiagnostics,
    localDeviceId: String,
    displayName: String,
    pingStatus: String?,
    onPingTest: (String) -> Unit,
    onRefreshNetwork: () -> Unit,
    onEditProfile: () -> Unit,
    onClearHistory: () -> Unit,
    onResetIdentity: () -> Unit
) {
    var testIpInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Network Status Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (diagnostics.wifiConnected) CallGreen else StatusUnreachable
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (diagnostics.wifiConnected) "Wi-Fi Connected" else "No Wi-Fi Connection",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onRefreshNetwork,
                        modifier = Modifier.testTag("diag_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Network")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                DiagRow("SSID", diagnostics.ssid)
                DiagRow("Local IP Address", diagnostics.localIp)
                DiagRow("Subnet Broadcast", diagnostics.broadcastIp)
                DiagRow("TCP Signaling Port", "${diagnostics.listeningSignalingPort} (Listening)")
                DiagRow("UDP VoIP Audio Port", "${diagnostics.listeningAudioPort} (G.711 mu-law)")
                DiagRow("Android NSD Service", if (diagnostics.nsdRegistered) "Active (_wifitalk._tcp)" else "Starting...")
                DiagRow("UDP Beacon Discovery", "Active (Port 5354)")
                DiagRow("Active Online Peers", "${diagnostics.onlinePeerCount} reachable")
            }
        }

        // Ping & Reachability Tester Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device-to-Device Reachability Test",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Verify whether your Wi-Fi router allows direct communication between devices or blocks local traffic.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testIpInput,
                        onValueChange = { testIpInput = it },
                        placeholder = { Text("e.g. 192.168.1.45") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ping_ip_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (testIpInput.isNotBlank()) {
                                onPingTest(testIpInput.trim())
                            }
                        },
                        enabled = testIpInput.isNotBlank(),
                        modifier = Modifier.testTag("ping_submit_button")
                    ) {
                        Text("Test Ping")
                    }
                }

                if (pingStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = pingStatus,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (pingStatus.contains("succeeded")) CallGreen else CallRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Router & Hotspot Troubleshooting Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Router & Isolation Diagnostics",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TroubleItem(
                    title = "AP Client Isolation (Guest Networks)",
                    description = "Public, hotel, and guest Wi-Fi networks frequently enable 'Client Isolation', which forbids devices from talking directly to each other. If peers cannot see each other, disable AP Isolation in router settings or use a Phone Hotspot."
                )

                TroubleItem(
                    title = "Zero-Internet Phone Hotspot Mode",
                    description = "Any phone can turn on its Wi-Fi Hotspot (even without cellular SIM or mobile data!). The other phone simply connects to that hotspot Wi-Fi. WiFi Talk operates flawlessly in full offline mode."
                )

                TroubleItem(
                    title = "Multicast & mDNS Filtering",
                    description = "WiFi Talk automatically sends UDP broadcast packets alongside DNS-SD. Even if your router drops mDNS, peer discovery continues over the UDP fallback."
                )
            }
        }

        // Identity & Security Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Identity & Security Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                DiagRow("Display Name", displayName)
                DiagRow("Device ID", localDeviceId)
                DiagRow("Packet Security", "HMAC-SHA256 frame authentication")
                DiagRow("Audio Codec", "ITU-T G.711 u-law (Full Duplex VoIP)")

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onEditProfile,
                        modifier = Modifier.weight(1f).testTag("edit_display_name_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Name")
                    }

                    OutlinedButton(
                        onClick = onResetIdentity,
                        modifier = Modifier.weight(1f).testTag("reset_identity_button")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset ID")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier.fillMaxWidth().testTag("clear_all_data_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All Messages & Call History")
                }
            }
        }
    }
}

@Composable
fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TroubleItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "• $title",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )
    }
}
