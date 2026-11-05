package com.example.hiremydriver.messaging

/** Presents all existing booking threads for the same participants as one history, without moving/deleting messages. */
class PairChatRepository(private val source: ChatRepository) : ChatRepository {
    override val available get() = source.available
    override fun observeUser(listener: (String?) -> Unit) = source.observeUser(listener)
    override fun inbox(uid: String, role: ChatRole, listener: (Result<List<Conversation>>) -> Unit): ChatSubscription {
        var closed = false
        var rows = emptyList<Conversation>()
        val watches = mutableMapOf<String, ChatSubscription>()
        val messages = mutableMapOf<String, List<ChatMessage>>()
        fun publish() {
            // Wait for unread evidence instead of briefly showing every row as read on reopen.
            if (rows.any { it.updatedAt > (it.readAt[uid] ?: 0) && it.id !in messages }) return
            if (!closed) listener(Result.success(rows.map { thread -> thread.copy(incomingUnread =
                messages[thread.id].orEmpty().any { it.senderId != uid && it.state == SendState.SENT && it.createdAt > (thread.readAt[uid] ?: 0) }) }))
        }
        val inbox = source.inbox(uid, role) { result ->
            if (!closed) {
                if (result.isFailure) listener(Result.failure(result.exceptionOrNull()!!))
                else {
                    rows = result.getOrThrow()
                    val needed = rows.filter { it.updatedAt > (it.readAt[uid] ?: 0) }.map { it.id }.toSet()
                    (watches.keys - needed).forEach { watches.remove(it)?.cancel(); messages.remove(it) }
                    needed.forEach { id -> if (id !in watches) {
                        watches[id] = source.messages(id, uid) { values ->
                            if (!closed && rows.any { it.id == id }) {
                                if (values.isSuccess) { messages[id] = values.getOrThrow(); publish() }
                                else listener(Result.failure(values.exceptionOrNull()!!))
                            }
                        }
                    } }
                    publish()
                }
            }
        }
        return ChatSubscription { closed = true; inbox.cancel(); watches.values.forEach { it.cancel() } }
    }

    private fun pair(id: String, uid: String, changed: (Result<List<Conversation>>) -> Unit): ChatSubscription {
        var closed = false
        var watch: ChatSubscription? = null
        var identity: Pair<String, String>? = null
        val anchor = source.conversation(id, uid) { result ->
            if (!closed) {
                val thread = result.getOrNull()
                if (thread == null || !thread.includes(uid)) {
                    watch?.cancel(); watch = null; identity = null
                    changed(Result.failure(result.exceptionOrNull() ?: IllegalStateException("Unavailable conversation")))
                } else if (identity != (thread.customerId to thread.driverId)) {
                    identity = thread.customerId to thread.driverId
                    watch?.cancel()
                    watch = source.inbox(uid, if (uid == thread.customerId) ChatRole.CUSTOMER else ChatRole.DRIVER) { all ->
                        if (!closed) changed(all.map { rows ->
                            rows.filter { it.customerId == thread.customerId && it.driverId == thread.driverId }
                                .ifEmpty { listOf(thread) }
                        })
                    }
                }
            }
        }
        return ChatSubscription { closed = true; anchor.cancel(); watch?.cancel() }
    }

    override fun conversation(id: String, uid: String, listener: (Result<Conversation>) -> Unit) = pair(id, uid) { result ->
        listener(result.map { rows -> merge(rows).copy(id = id) })
    }

    override fun messages(id: String, uid: String, listener: (Result<List<ChatMessage>>) -> Unit): ChatSubscription {
        var closed = false
        var threads = emptyList<Conversation>()
        val watches = mutableMapOf<String, ChatSubscription>()
        val loaded = mutableMapOf<String, List<ChatMessage>>()
        fun publish() {
            if (closed || threads.any { it.id !in loaded }) return
            val result = threads.flatMap { thread -> loaded[thread.id].orEmpty().map { message ->
                message.copy(id = if (thread.id == id) message.id else "${thread.id}/${message.id}",
                    unread = message.state == SendState.SENT && message.senderId != uid && message.createdAt > (thread.readAt[uid] ?: 0))
            } }.sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
            listener(Result.success(result))
        }
        val membership = pair(id, uid) { result ->
            if (!closed) {
                val rows = result.getOrNull()
                if (rows == null) listener(Result.failure(result.exceptionOrNull()!!))
                else {
                    threads = rows
                    val ids = rows.map { it.id }.toSet()
                    (watches.keys - ids).forEach { watches.remove(it)?.cancel(); loaded.remove(it) }
                    rows.forEach { thread ->
                        if (thread.id !in watches) {
                            watches[thread.id] = source.messages(thread.id, uid) { messages ->
                                if (!closed && threads.any { it.id == thread.id }) {
                                    if (messages.isSuccess) { loaded[thread.id] = messages.getOrThrow(); publish() }
                                    else listener(Result.failure(messages.exceptionOrNull()!!))
                                }
                            }
                        }
                    }
                    publish()
                }
            }
        }
        return ChatSubscription { closed = true; membership.cancel(); watches.values.forEach { it.cancel() } }
    }

    override fun send(id: String, uid: String, messageId: String, text: String, done: (Result<Unit>) -> Unit) =
        source.send(id, uid, messageId, text, done)

    override fun markRead(id: String, uid: String, through: Long, done: (Boolean) -> Unit) {
        // Resolve the pair once; each thread keeps its own independently monotonic server cursor.
        var watch: ChatSubscription? = null
        var handled = false
        watch = pair(id, uid) { result ->
            if (!handled) {
                handled = true
                watch?.cancel()
                val rows = result.getOrNull()
                if (rows == null) done(false)
                else {
                    var remaining = rows.size; var success = true
                    rows.forEach { source.markRead(it.id, uid, through) { ok ->
                        success = success && ok
                        if (--remaining == 0) done(success)
                    } }
                }
            }
        }
        if (handled) watch.cancel()
    }

    companion object {
        fun merge(rows: List<Conversation>): Conversation {
            val latest = rows.maxWith(compareBy<Conversation> { if (it.lastText.isBlank()) 0 else it.updatedAt }.thenBy { it.id })
            return latest.copy(active = rows.any { it.active }, parts = rows)
        }
    }
}
