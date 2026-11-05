package com.example.hiremydriver.messaging

import java.util.UUID

data class ChatState(
    val userId: String? = null, val conversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(), val loading: Boolean = false,
    val notice: String? = null, val canSend: Boolean = false
)

/** UI-independent session: account isolation, lifecycle cleanup and idempotent retries. */
class ChatSession(
    private val repository: ChatRepository, private val conversationId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val history: ChatHistoryCache = ChatHistoryCache(),
    private val changed: (ChatState) -> Unit
) {
    var state = ChatState(); private set
    private var userId: String? = null
    private var active = false
    private var disposed = false
    private var generation = 0
    private var auth: ChatSubscription? = null
    private var threadWatch: ChatSubscription? = null
    private var messageWatch: ChatSubscription? = null
    private var messagesAttached = false
    private var messagesLoaded = false
    private var remote = emptyList<ChatMessage>()
    private val pending = linkedMapOf<String, ChatMessage>()
    private var markedThrough = 0L
    private var marking = false

    fun start() {
        if (disposed || active) return
        active = true
        if (!repository.available) { publish(ChatState(notice = "Messaging will be available soon.")); return }
        if (!ChatValidation.documentId(conversationId)) {
            publish(ChatState(notice = "Chat will be available once your booking is connected.")); return
        }
        if (auth == null) auth = repository.observeUser { uid ->
            if (disposed) return@observeUser
            if (uid != userId) {
                userId?.let(history::clear)
                detach(); userId = uid; remote = emptyList(); pending.clear()
                val cached = uid?.let { history.get(it, conversationId) }
                remote = cached?.messages.orEmpty()
                publish(cached ?: ChatState(userId = uid, loading = uid != null))
            }
            if (active && threadWatch == null) attach()
        } else attach()
    }

    private fun attach() {
        detach()
        val uid = userId
        if (uid == null) { publish(ChatState(notice = "Sign in to view your messages.")); return }
        val ticket = generation
        publish(state.copy(userId = uid, loading = state.messages.isEmpty(), canSend = false, notice = null))
        threadWatch = repository.conversation(conversationId, uid) { result ->
            if (!current(ticket, uid)) return@conversation
            val thread = result.getOrNull()
            if (thread == null || !thread.includes(uid)) {
                history.remove(uid, conversationId)
                detach()
                remote = emptyList(); pending.clear()
                publish(ChatState(userId = uid, notice = "This conversation is unavailable. Tap Retry."))
                return@conversation
            }
            publish(state.copy(conversation = thread, canSend = thread.active && messagesLoaded,
                notice = if (thread.active) null else "This conversation is closed."))
            if (!messagesAttached) {
                messagesAttached = true
                messageWatch = repository.messages(conversationId, uid) { messages ->
                    if (!current(ticket, uid)) return@messages
                    if (messages.isFailure) {
                        messagesLoaded = false
                        // Keep already retrieved history during transient network failures.
                        publish(state.copy(loading = false, canSend = false,
                            notice = "Could not load messages. Tap Retry."))
                    } else {
                        messagesLoaded = true
                        remote = messages.getOrThrow()
                        remote.filter { it.state == SendState.SENT }.forEach { pending.remove(it.id) }
                        render(state.copy(loading = false, canSend = state.conversation?.active == true,
                            notice = if (state.conversation?.active == true) null else "This conversation is closed."))
                    }
                }
            }
        }
    }

    fun send(text: String): Boolean {
        val clean = text.trim()
        if (!active || !state.canSend || userId == null || ChatValidation.textError(clean) != null ||
            pending.values.any { it.state == SendState.SENDING }) return false
        val message = ChatMessage(newId(), userId!!, clean, clock(), SendState.SENDING)
        pending[message.id] = message
        dispatch(message)
        return true
    }

    fun retry(messageId: String) {
        val message = pending[messageId] ?: return
        if (!active || !state.canSend || message.senderId != userId || message.state != SendState.FAILED ||
            pending.values.any { it.state == SendState.SENDING }) return
        dispatch(message.copy(state = SendState.SENDING))
    }

    private fun dispatch(message: ChatMessage) {
        pending[message.id] = message
        render()
        repository.send(conversationId, message.senderId, message.id, message.text) { result ->
            if (disposed || userId != message.senderId || !pending.containsKey(message.id)) return@send
            pending[message.id] = message.copy(state = if (result.isSuccess) SendState.SENT else SendState.FAILED)
            render()
        }
    }

    fun acknowledgeVisibleMessages() {
        val uid = userId ?: return
        if (!active || state.conversation?.includes(uid) != true || state.loading || !messagesLoaded) return
        val through = remote.filter { it.state == SendState.SENT }.maxOfOrNull { it.createdAt } ?: return
        if (through > markedThrough && !marking) {
            marking = true
            val ticket = generation
            repository.markRead(conversationId, uid, through) { success ->
                if (current(ticket, uid)) {
                    marking = false
                    if (success) markedThrough = through
                }
            }
        }
    }

    fun refresh() { if (active && userId != null) attach() }
    fun stop() { active = false; detach() }
    fun close() { disposed = true; stop(); auth?.cancel(); auth = null; pending.clear(); remote = emptyList() }
    private fun current(ticket: Int, uid: String) = !disposed && active && ticket == generation && uid == userId
    private fun detach() {
        generation++; threadWatch?.cancel(); messageWatch?.cancel()
        threadWatch = null; messageWatch = null; messagesAttached = false; messagesLoaded = false; markedThrough = 0; marking = false
    }
    private fun render(next: ChatState = state) {
        // A local echo and a server snapshot with the same ID represent one message.
        val combined = pending.toMutableMap()
        remote.forEach {
            combined[it.id] = if (it.createdAt == 0L) it.copy(createdAt = pending[it.id]?.createdAt ?: 0L) else it
        }
        publish(next.copy(messages = combined.values.sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })))
    }
    private fun publish(next: ChatState) {
        if (next == state) return
        state = next
        if (messagesLoaded) userId?.let { history.put(it, conversationId, next) }
        changed(next)
    }
}
