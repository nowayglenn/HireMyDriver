package com.example.hiremydriver.messaging

import org.junit.Assert.*
import org.junit.Test

class ChatSessionTest {
    @Test fun reopeningShowsCachedHistoryImmediatelyThenUsesLiveMessages() {
        val cache = ChatHistoryCache()
        val firstRepo = FakeRepository()
        val first = ChatSession(firstRepo, thread.id, history = cache, changed = {})
        first.start()
        firstRepo.conversationCallback!!(Result.success(thread))
        val old = ChatMessage("old", "driver", "Previous message", 50)
        firstRepo.messagesCallback!!(Result.success(listOf(old)))
        first.close()
        val secondRepo = FakeRepository()
        val frames = mutableListOf<ChatState>()
        val second = ChatSession(secondRepo, thread.id, history = cache, changed = frames::add)
        second.start()
        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.messages == listOf(old) && !it.loading })
        assertFalse(second.state.canSend)
        secondRepo.conversationCallback!!(Result.success(thread))
        val fresh = ChatMessage("new", "driver", "Latest message", 60)
        secondRepo.messagesCallback!!(Result.success(listOf(old, fresh)))
        assertEquals(listOf(old, fresh), second.state.messages)
        assertTrue(second.state.canSend)
        val before = frames.size
        secondRepo.messagesCallback!!(Result.success(listOf(old, fresh)))
        assertEquals("Identical snapshots must not redraw the conversation", before, frames.size)
        second.close()
    }

    @Test fun cacheNeverLeaksAcrossAccountsAndRevokedChatIsRemoved() {
        val cache = ChatHistoryCache()
        cache.put("customer", thread.id, ChatState(userId="customer", conversation=thread,
            messages=listOf(ChatMessage("private", "driver", "Private", 50))))
        val other = FakeRepository().apply { uid = "another-account" }
        val otherSession = ChatSession(other, thread.id, history=cache, changed={})
        otherSession.start()
        assertTrue(otherSession.state.messages.isEmpty())
        otherSession.close()
        val owner = FakeRepository()
        val ownerSession = ChatSession(owner, thread.id, history=cache, changed={})
        ownerSession.start()
        assertEquals(1, ownerSession.state.messages.size)
        owner.conversationCallback!!(Result.failure(IllegalStateException("Revoked")))
        assertNull(cache.get("customer", thread.id))
        assertTrue(ownerSession.state.messages.isEmpty())
        ownerSession.close()
    }

    @Test fun cacheDoesNotReplayPendingSendsAndEvictsOldThreads() {
        val cache = ChatHistoryCache()
        val state = ChatState(userId="customer", conversation=thread,
            messages=listOf(ChatMessage("sent", "driver", "Saved", 50),
                ChatMessage("pending", "customer", "Not saved", 60, SendState.SENDING)))
        (1..9).forEach { cache.put("customer", "thread-$it", state) }
        assertNull(cache.get("customer", "thread-1"))
        assertEquals(listOf("sent"), cache.get("customer", "thread-9")!!.messages.map { it.id })
        cache.clear("customer")
        assertNull(cache.get("customer", "thread-9"))
    }

    private val thread = Conversation("booking-1", "customer", "driver", "Customer Name", "Driver Name")
    private class FakeRepository : ChatRepository {
        override var available = true
        var uid: String? = "customer"
        var auth: ((String?) -> Unit)? = null
        var conversationCallback: ((Result<Conversation>) -> Unit)? = null
        var messagesCallback: ((Result<List<ChatMessage>>) -> Unit)? = null
        var cancellations = 0
        val sends = mutableListOf<ChatMessage>()
        val completions = mutableListOf<(Result<Unit>) -> Unit>()
        val reads = mutableListOf<Long>()
        var readSucceeds = true
        override fun observeUser(listener: (String?) -> Unit): ChatSubscription { auth = listener; listener(uid); return ChatSubscription { auth = null } }
        override fun inbox(uid: String, role: ChatRole, listener: (Result<List<Conversation>>) -> Unit) = ChatSubscription {}
        override fun conversation(id: String, uid: String, listener: (Result<Conversation>) -> Unit): ChatSubscription {
            conversationCallback = listener; return ChatSubscription { cancellations++ }
        }
        override fun messages(id: String, uid: String, listener: (Result<List<ChatMessage>>) -> Unit): ChatSubscription {
            messagesCallback = listener; return ChatSubscription { cancellations++ }
        }
        override fun send(id: String, uid: String, messageId: String, text: String, done: (Result<Unit>) -> Unit) {
            sends.add(ChatMessage(messageId, uid, text, 0)); completions.add(done)
        }
        override fun markRead(id: String, uid: String, through: Long, done: (Boolean) -> Unit) { reads.add(through); done(readSucceeds) }
        fun user(value: String?) { uid = value; auth?.invoke(value) }
    }
    private fun ready(repo: FakeRepository): ChatSession {
        val session = ChatSession(repo, thread.id, clock = { 100 }, newId = { "stable-id" }, changed = {})
        session.start()
        repo.conversationCallback!!(Result.success(thread))
        assertFalse("Do not send before the message listener is ready", session.state.canSend)
        repo.messagesCallback!!(Result.success(emptyList()))
        return session
    }

    @Test fun offlineFailureRetryReusesMessageIdAndDoesNotClaimDelivery() {
        val repo = FakeRepository(); val session = ready(repo)
        assertTrue(session.send(" hello "))
        assertFalse(session.send("duplicate tap"))
        assertEquals(SendState.SENDING, session.state.messages.single().state)
        repo.completions[0](Result.failure(IllegalStateException("offline")))
        assertEquals(SendState.FAILED, session.state.messages.single().state)
        session.retry("stable-id")
        assertEquals(listOf("stable-id", "stable-id"), repo.sends.map { it.id })
        assertEquals("hello", repo.sends.last().text)
        repo.completions[1](Result.success(Unit))
        assertEquals(SendState.SENT, session.state.messages.single().state)
        repo.messagesCallback!!(Result.success(listOf(ChatMessage("stable-id", "customer", "hello", 120))))
        assertEquals(1, session.state.messages.size)
        assertEquals(120L, session.state.messages.single().createdAt)
        session.close()
    }

    @Test fun switchingAccountsDropsMessagesAndIgnoresLateListenersAndSendCompletion() {
        val repo = FakeRepository(); val session = ready(repo)
        session.send("private text")
        val staleMessages = repo.messagesCallback!!
        val staleCompletion = repo.completions.single()
        repo.user("another-account")
        assertTrue(session.state.messages.isEmpty())
        staleMessages(Result.success(listOf(ChatMessage("late", "customer", "private text", 100))))
        staleCompletion(Result.success(Unit))
        assertTrue(session.state.messages.isEmpty())
        repo.conversationCallback!!(Result.success(thread))
        assertFalse(session.state.canSend)
        assertNull(session.state.conversation)
        session.close()
    }

    @Test fun pauseRemovesListenersAndResumeKeepsPendingSendWithoutResending() {
        val repo = FakeRepository(); val session = ready(repo)
        session.send("keep me")
        val stale = repo.messagesCallback!!
        session.stop()
        assertEquals(2, repo.cancellations)
        stale(Result.success(listOf(ChatMessage("unexpected", "driver", "late", 1))))
        assertEquals("stable-id", session.state.messages.single().id)
        repo.completions.single()(Result.success(Unit))
        session.start()
        repo.conversationCallback!!(Result.success(thread))
        repo.messagesCallback!!(Result.success(listOf(ChatMessage("stable-id", "customer", "keep me", 100))))
        assertEquals(1, session.state.messages.size)
        assertEquals(1, repo.sends.size)
        session.close()
        assertNull(repo.auth)
    }

    @Test fun onlyVisibleServerMessagesAdvanceReadCursor() {
        val repo = FakeRepository(); val session = ready(repo)
        repo.messagesCallback!!(Result.success(listOf(ChatMessage("received", "driver", "hello", 50))))
        assertTrue(repo.reads.isEmpty())
        session.send("reply")
        session.acknowledgeVisibleMessages()
        session.acknowledgeVisibleMessages()
        assertEquals(listOf(50L), repo.reads)
        session.stop(); session.acknowledgeVisibleMessages()
        assertEquals(1, repo.reads.size)
        session.close()
    }

    @Test fun failedReadReceiptCanRetryAndTransientFailureKeepsHistory() {
        val repo = FakeRepository(); val session = ready(repo)
        repo.messagesCallback!!(Result.success(listOf(ChatMessage("received", "driver", "hello", 50))))
        repo.readSucceeds = false
        session.acknowledgeVisibleMessages()
        repo.readSucceeds = true
        session.acknowledgeVisibleMessages(); session.acknowledgeVisibleMessages()
        assertEquals(listOf(50L,50L), repo.reads)
        repo.messagesCallback!!(Result.failure(IllegalStateException("offline")))
        assertEquals("hello", session.state.messages.single().text)
        assertFalse(session.state.canSend)
        session.close()
    }

    @Test fun revokedConversationCannotBeRepopulatedByAStaleMessageCallback() {
        val repo = FakeRepository(); val session = ready(repo)
        val old = repo.messagesCallback!!
        repo.conversationCallback!!(Result.failure(IllegalStateException("permission denied")))
        old(Result.success(listOf(ChatMessage("private", "driver", "secret", 20))))
        assertTrue(session.state.messages.isEmpty())
        assertFalse(session.state.canSend)
        session.close()
    }

    @Test fun closedThreadsAndInvalidMessagesCannotSend() {
        val repo = FakeRepository(); val session = ready(repo)
        assertFalse(session.send("  \n "))
        assertFalse(session.send("x".repeat(2001)))
        repo.conversationCallback!!(Result.success(thread.copy(active = false)))
        assertFalse(session.send("hello"))
        assertTrue(repo.sends.isEmpty())
        session.close()
    }

    @Test fun disabledMessagingAndNamesWithoutConversationIdsNeverConnect() {
        val disabled = FakeRepository().apply { available = false }
        val session = ChatSession(disabled, "booking-1", changed = {})
        session.start(); assertFalse(session.send("hello")); assertNull(disabled.auth); session.close()
        val repo = FakeRepository()
        val legacy = ChatSession(repo, "", changed = {})
        legacy.start(); assertNull(repo.auth); assertFalse(legacy.state.canSend); legacy.close()
    }

    @Test fun unreadIsPerParticipantAndIgnoresOwnLastMessage() {
        val incoming = thread.copy(lastSenderId = "driver", updatedAt = 200, readAt = mapOf("customer" to 100))
        assertTrue(incoming.unread("customer"))
        assertFalse(incoming.unread("driver"))
        assertFalse(incoming.unread("outsider"))
        assertFalse(incoming.copy(readAt = mapOf("customer" to 200)).unread("customer"))
        assertEquals("Driver Name", incoming.peerName("customer"))
    }

    @Test fun sameAccountAuthRefreshDoesNotClearHistoryOrRestartListeners() {
        val repo = FakeRepository(); val session = ready(repo)
        val message = ChatMessage("received", "driver", "Keep visible", 50)
        repo.messagesCallback!!(Result.success(listOf(message)))
        val before = repo.cancellations
        repo.user("customer")
        assertEquals(before, repo.cancellations)
        assertEquals(listOf(message), session.state.messages)
        assertFalse(session.state.loading)
        assertTrue(session.state.canSend)
        session.close()
    }

    @Test fun returningToConversationKeepsHistoryWhileRefreshing() {
        val repo = FakeRepository(); val session = ready(repo)
        val message = ChatMessage("received", "driver", "Keep visible", 50)
        repo.messagesCallback!!(Result.success(listOf(message)))
        session.stop(); session.start()
        assertEquals(listOf(message), session.state.messages)
        assertFalse(session.state.loading)
        assertFalse(session.state.canSend)
        session.close()
    }
}
