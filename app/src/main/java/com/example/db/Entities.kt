package com.example.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.CallType
import com.example.model.MessageStatus
import com.example.model.PeerStatus

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val ipAddress: String,
    val signalingPort: Int = 5355,
    val audioPort: Int = 5356,
    val lastSeenEpoch: Long = System.currentTimeMillis(),
    val status: String = PeerStatus.ONLINE.name,
    val isBlocked: Boolean = false,
    val customNote: String = ""
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationPeerId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationPeerId: String,
    val senderDeviceId: String,
    val text: String,
    val timestamp: Long,
    val status: String = MessageStatus.PENDING.name,
    val isIncoming: Boolean
)

@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["peerDeviceId"]),
        Index(value = ["timestamp"])
    ]
)
data class CallLogEntity(
    @PrimaryKey val id: String,
    val peerDeviceId: String,
    val peerDisplayName: String,
    val callType: String = CallType.OUTGOING.name,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val terminationReason: String = ""
)
