package com.bitchat.model

data class Message(
    val content: String,
    val sender: String,
    val deviceAddress: String = "",
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val isDeleted: Boolean = false,
    val deletedBy: String? = null, // "me" or "sender"
    val deletedAt: Long? = null,

    // Media URIs
    val imageUri: String? = null,
    val documentUri: String? = null,
    val documentName: String? = null,
    val documentSize: Long? = null,
    val mimeType: String? = null,

    // Audio-specific fields
    val duration: Long? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,

    val status: MessageStatus = MessageStatus.SENT,
    val progress: Float = 1.0f,

    // Reply support
    val replyToMessage: Message? = null,
    val replyToMessageId: String? = null,
    val isReply: Boolean = replyToMessage != null,

    val id: String = "${timestamp}_${content.hashCode()}"
)

data class ScrollInfo(
    val isNearBottom: Boolean,
    val isNearTop: Boolean,
    val hasScrolledUp: Boolean,
    val totalItems: Int,
    val firstVisibleIndex: Int
)

enum class MessageType {
    TEXT,
    IMAGE,
    DOCUMENT,
    AUDIO
}

enum class MessageStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}
