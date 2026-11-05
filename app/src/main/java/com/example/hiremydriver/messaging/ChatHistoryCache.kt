package com.example.hiremydriver.messaging

/** Small account-scoped memory cache for reopening chats; live listeners remain authoritative. */
class ChatHistoryCache {
    private val entries = LinkedHashMap<Pair<String, String>, ChatState>(8, 0.75f, true)
    @Synchronized fun get(uid: String, id: String): ChatState? = entries[uid to id]
    @Synchronized fun put(uid: String, id: String, state: ChatState) {
        if (state.conversation?.includes(uid) != true || state.userId != uid) return
        val confirmed = state.messages.filter { it.state == SendState.SENT }
        if (confirmed.size > 2000) { entries.remove(uid to id); return }
        entries[uid to id] = state.copy(messages = confirmed.toList(), canSend = false, loading = false, notice = null)
        while (entries.size > 8) entries.remove(entries.keys.first())
    }
    @Synchronized fun remove(uid: String, id: String) { entries.remove(uid to id) }
    @Synchronized fun clear(uid: String) { entries.keys.removeAll { it.first == uid } }
    companion object { val shared = ChatHistoryCache() }
}
