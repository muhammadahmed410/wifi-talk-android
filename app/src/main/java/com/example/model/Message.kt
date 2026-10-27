package com.example.model

import java.util.UUID

enum class MessageStatus {
    PENDING,
    SENDING,
    DELIVERED,
    READ,
    FAILED
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationPeerId: String,
    val senderDeviceId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.PENDING,
    val isIncoming: Boolean = false
)
