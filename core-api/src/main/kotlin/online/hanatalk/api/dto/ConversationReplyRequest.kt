package online.hanatalk.api.dto

import online.hanatalk.client.ChatMessageDto

data class ConversationReplyRequest(
    val history: List<ChatMessageDto>,
    val message: String,
)
