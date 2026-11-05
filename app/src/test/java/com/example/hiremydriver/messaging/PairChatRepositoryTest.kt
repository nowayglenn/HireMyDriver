package com.example.hiremydriver.messaging

import org.junit.Assert.*
import org.junit.Test

class PairChatRepositoryTest {
    private val first = Conversation("first", "c", "d", "Customer", "Driver", "old", "d", 100)
    private val second = first.copy(id = "second", lastText = "new", updatedAt = 200)
    private class Fake(val threads: MutableList<Conversation>) : ChatRepository {
        var delayMessages = false
        val pendingMessages = mutableListOf<() -> Unit>()
        override val available = true
        val data = mutableMapOf<String, List<ChatMessage>>()
        val inboxes = mutableListOf<(Result<List<Conversation>>) -> Unit>()
        val reads = mutableListOf<String>()
        override fun observeUser(listener: (String?) -> Unit) = ChatSubscription {}
        override fun inbox(uid: String, role: ChatRole, listener: (Result<List<Conversation>>) -> Unit): ChatSubscription {
            inboxes.add(listener); listener(Result.success(threads.toList()))
            return ChatSubscription { inboxes.remove(listener) }
        }
        override fun conversation(id: String, uid: String, listener: (Result<Conversation>) -> Unit): ChatSubscription {
            listener(Result.success(threads.first { it.id == id })); return ChatSubscription {}
        }
        override fun messages(id: String, uid: String, listener: (Result<List<ChatMessage>>) -> Unit): ChatSubscription {
            val emit = { listener(Result.success(data[id].orEmpty())) }
            if (delayMessages) pendingMessages.add(emit) else emit()
            return ChatSubscription {}
        }
        override fun send(id: String, uid: String, messageId: String, text: String, done: (Result<Unit>) -> Unit) { done(Result.success(Unit)) }
        override fun markRead(id: String, uid: String, through: Long, done: (Boolean) -> Unit) {
            reads.add(id)
            val i = threads.indexOfFirst { it.id == id }
            threads[i] = threads[i].copy(readAt = threads[i].readAt + (uid to minOf(through, threads[i].updatedAt)))
            inboxes.toList().forEach { it(Result.success(threads.toList())) }; done(true)
        }
    }

    @Test fun delayedUnreadLoadingNeverEmitsAFalseReadFrame() {
        val raw = Fake(mutableListOf(first)).apply {
            delayMessages = true
            data["first"] = listOf(ChatMessage("m1", "d", "unread", 100))
        }
        val frames = mutableListOf<List<Conversation>>()
        val watch = PairChatRepository(raw).inbox("c", ChatRole.CUSTOMER) { frames.add(it.getOrThrow()) }
        assertTrue(frames.isEmpty())
        raw.pendingMessages.forEach { it() }
        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.single().unread("c") })
        watch.cancel()
    }

    @Test fun repeatedBookingsShareAllHistoryAndKeepDuplicateLocalMessageIdsDistinct() {
        val stranger = first.copy(id="other", driverId="other-driver")
        val raw = Fake(mutableListOf(first, second, stranger)).apply {
            data["first"] = listOf(ChatMessage("m1", "d", "old", 100))
            data["second"] = listOf(ChatMessage("m1", "d", "new", 200))
            data["other"] = listOf(ChatMessage("secret", "other-driver", "private", 300))
        }
        val repo = PairChatRepository(raw)
        var history = emptyList<ChatMessage>()
        val watch = repo.messages("first", "c") { history = it.getOrThrow() }
        assertEquals(listOf("old", "new"), history.map { it.text })
        assertEquals(2, history.map { it.id }.distinct().size)
        assertTrue(history.all { it.unread })
        var saved = false
        repo.markRead("first", "c", 200) { saved = it }
        assertTrue(saved); assertEquals(setOf("first", "second"), raw.reads.toSet())
        assertTrue(history.none { it.unread })
        watch.cancel()
        repo.messages("second", "c") { assertEquals(listOf("old", "new"), it.getOrThrow().map { m -> m.text }) }.cancel()
        assertEquals(2, raw.data.values.take(2).sumOf { it.size })
    }

    @Test fun unreadCursorsAreNeverMergedAcrossDifferentBookings() {
        val oldUnread = first.copy(readAt=mapOf("c" to 0))
        val newRead = second.copy(readAt=mapOf("c" to 200))
        val merged = PairChatRepository.merge(listOf(oldUnread, newRead))
        assertTrue(merged.unread("c")); assertFalse(merged.unread("d"))
    }

    @Test fun ownLastMessageDoesNotHideEarlierUnreadIncomingMessage() {
        val raw = Fake(mutableListOf(first.copy(lastSenderId="c", updatedAt=200))).apply {
            data["first"] = listOf(ChatMessage("a", "d", "unread", 100), ChatMessage("b", "c", "sent elsewhere", 200))
        }
        PairChatRepository(raw).inbox("c", ChatRole.CUSTOMER) { assertTrue(it.getOrThrow().single().unread("c")) }.cancel()
    }
}
