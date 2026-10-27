package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CallSession
import com.example.model.CallState
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CallRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceVariant

@Composable
fun CallOverlayScreen(
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    // Pulse animation for ringing and dialing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("call_overlay_screen"),
        color = DarkBackground.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Text(
                    text = "WiFi Talk VoIP Call",
                    fontSize = 14.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = call.peerDisplayName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${call.peerIp} • Direct Local Wi-Fi",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Call State Label / Timer
                val stateLabel = when (call.state) {
                    CallState.OUTGOING_DIALING -> "Dialing peer..."
                    CallState.OUTGOING_RINGING -> "Ringing..."
                    CallState.INCOMING_RINGING -> "Incoming Call..."
                    CallState.CONNECTED -> formatTimer(call.durationSeconds)
                    CallState.DECLINED -> "Call Declined: ${call.terminationReason}"
                    CallState.BUSY -> "Peer is Busy"
                    CallState.MISSED -> "Call Missed"
                    CallState.FAILED -> "Call Failed / Unreachable"
                    CallState.ENDED -> "Call Ended"
                    CallState.IDLE -> ""
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (call.isConnected) CallGreen.copy(alpha = 0.2f) else DarkSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = stateLabel,
                        fontSize = if (call.isConnected) 20.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (call.isConnected) CallGreen else Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Center Avatar with optional animated wave
            Box(contentAlignment = Alignment.Center) {
                if (call.state == CallState.INCOMING_RINGING || call.state == CallState.OUTGOING_RINGING) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f))
                    )
                }
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = call.peerDisplayName.take(1).uppercase(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Bottom Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                when {
                    // Incoming Call: Answer & Decline
                    call.state == CallState.INCOMING_RINGING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decline
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onDecline,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(CallRed)
                                        .testTag("call_decline_button")
                                ) {
                                    Icon(
                                        Icons.Default.CallEnd,
                                        contentDescription = "Decline Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Decline", color = Color.White, fontSize = 13.sp)
                            }

                            // Answer
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onAccept,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(CallGreen)
                                        .testTag("call_accept_button")
                                ) {
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = "Answer Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Answer", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    // Connected Call: Mute, Speaker, Hangup
                    call.isConnected -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mute button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onToggleMute,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(if (call.isMuted) CyanAccent else DarkSurfaceVariant)
                                        .testTag("call_mute_toggle")
                                ) {
                                    Icon(
                                        if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Mute",
                                        tint = if (call.isMuted) DarkBackground else Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(if (call.isMuted) "Muted" else "Mute", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }

                            // End call button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onHangup,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(CallRed)
                                        .testTag("call_hangup_button")
                                ) {
                                    Icon(
                                        Icons.Default.CallEnd,
                                        contentDescription = "End Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("End Call", color = Color.White, fontSize = 13.sp)
                            }

                            // Speakerphone button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onToggleSpeaker,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(if (call.isSpeakerphoneOn) CyanAccent else DarkSurfaceVariant)
                                        .testTag("call_speaker_toggle")
                                ) {
                                    Icon(
                                        if (call.isSpeakerphoneOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                        contentDescription = "Speaker",
                                        tint = if (call.isSpeakerphoneOn) DarkBackground else Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(if (call.isSpeakerphoneOn) "Speaker On" else "Speaker", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                        }
                    }

                    // Outgoing Dialing / Ringing: Cancel button
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(CallRed)
                                    .testTag("call_cancel_button")
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "Cancel Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cancel", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimer(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
