package com.example.hiremydriver.messaging

enum class ChatRole(val field: String) { CUSTOMER("customerId"), DRIVER("driverId") }
enum class SendState { SENDING, SENT, FAILED }

data class Conversation(
    val id: String, val customerId: String, val driverId: String,
    val customerName: String, val driverName: String,
    val lastText: String = "", val lastSenderId: String = "", val updatedAt: Long = 0,
    val readAt: Map<String, Long> = emptyMap(), val active: Boolean = true,
    val parts: List<Conversation> = emptyList(), val incomingUnread: Boolean? = null
) {
    fun includes(uid: String) = uid == customerId || uid == driverId
    fun peerName(uid: String) = if (uid == customerId) driverName else customerName
    fun unread(uid: String): Boolean = if (parts.isNotEmpty()) parts.any { it.unread(uid) }
        else includes(uid) && (incomingUnread ?: (lastSenderId.isNotEmpty() && lastSenderId != uid && updatedAt > (readAt[uid] ?: 0)))
}

data class ChatMessage(val id: String, val senderId: String, val text: String,
    val createdAt: Long, val state: SendState = SendState.SENT, val unread: Boolean = false)

object ChatValidation {
    const val MAX_TEXT = 2000
    fun documentId(id: String) = id.isNotBlank() && id.length <= 200 && !id.contains('/') && id != "." && id != ".."
    fun textError(text: String): String? = when {
        text.isBlank() -> "Enter a message."
        text.trim().length > MAX_TEXT -> "Keep your message under $MAX_TEXT characters."
        else -> null
    }
}

fun interface ChatSubscription { fun cancel() }

/** Conversations are provisioned by the booking backend, never by passing a display name. */
interface ChatRepository {
    val available: Boolean
    fun observeUser(listener: (String?) -> Unit): ChatSubscription
    fun inbox(uid: String, role: ChatRole, listener: (Result<List<Conversation>>) -> Unit): ChatSubscription
    fun conversation(id: String, uid: String, listener: (Result<Conversation>) -> Unit): ChatSubscription
    fun messages(id: String, uid: String, listener: (Result<List<ChatMessage>>) -> Unit): ChatSubscription
    fun send(id: String, uid: String, messageId: String, text: String, done: (Result<Unit>) -> Unit)
    fun markRead(id: String, uid: String, through: Long, done: (Boolean) -> Unit)
}

class UnavailableChatRepository : ChatRepository {
    override val available = false
    override fun observeUser(listener: (String?) -> Unit): ChatSubscription { listener(null); return ChatSubscription {} }
    override fun inbox(uid: String, role: ChatRole, listener: (Result<List<Conversation>>) -> Unit) = ChatSubscription {}
    override fun conversation(id: String, uid: String, listener: (Result<Conversation>) -> Unit) = ChatSubscription {}
    override fun messages(id: String, uid: String, listener: (Result<List<ChatMessage>>) -> Unit) = ChatSubscription {}
    override fun send(id: String, uid: String, messageId: String, text: String, done: (Result<Unit>) -> Unit) {
        done(Result.failure(IllegalStateException("Messaging is not available yet.")))
    }
    override fun markRead(id: String, uid: String, through: Long, done: (Boolean) -> Unit) { done(false) }
}
