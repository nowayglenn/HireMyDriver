package com.example.hiremydriver.messaging

import android.content.Context
import com.example.hiremydriver.R
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import java.util.Date

object Messaging {
    const val CONVERSATION_ID = "CONVERSATION_ID"
    const val DRIVER_SESSION = "DRIVER_CHAT_SESSION"
    fun repository(context: Context, driver: Boolean = false): ChatRepository {
        if (!context.resources.getBoolean(R.bool.messaging_enabled)) return UnavailableChatRepository()
        val app = if (driver) com.example.hiremydriver.DriverAuth.firebaseApp(context.applicationContext)
            else FirebaseApp.initializeApp(context.applicationContext) ?: return UnavailableChatRepository()
        return PairChatRepository(FirebaseChatRepository(FirebaseAuth.getInstance(app), FirebaseFirestore.getInstance(app)))
    }
}

class FirebaseChatRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : ChatRepository {
    override val available = true
    private fun threads() = db.collection("conversations")
    private fun signedIn(uid: String) = uid.isNotBlank() && auth.currentUser?.uid == uid
    private fun checkSession(uid: String) { check(signedIn(uid)) { "Sign in again to continue." } }
    override fun observeUser(listener: (String?) -> Unit): ChatSubscription {
        val watcher = FirebaseAuth.AuthStateListener { listener(it.currentUser?.uid) }
        auth.addAuthStateListener(watcher)
        return ChatSubscription { auth.removeAuthStateListener(watcher) }
    }

    override fun inbox(uid: String, role: ChatRole, listener: (Result<List<Conversation>>) -> Unit): ChatSubscription {
        if (!signedIn(uid)) return ChatSubscription {}
        // Equality-only query works without a separately deployed composite index and retains history.
        val watch = threads().whereEqualTo(role.field, uid).addSnapshotListener { snapshot, error ->
                if (!signedIn(uid)) return@addSnapshotListener
                if (error != null) listener(Result.failure(error))
                else listener(runCatching { snapshot!!.documents.mapNotNull { runCatching { decodeConversation(it) }.getOrNull() }
                    .filter { it.includes(uid) }.sortedWith(compareByDescending<Conversation> { it.updatedAt }.thenBy { it.id }) })
            }
        return ChatSubscription { watch.remove() }
    }

    override fun conversation(id: String, uid: String, listener: (Result<Conversation>) -> Unit): ChatSubscription {
        if (!ChatValidation.documentId(id) || !signedIn(uid)) return ChatSubscription {}
        val watch = threads().document(id).addSnapshotListener { snapshot, error ->
            if (!signedIn(uid)) return@addSnapshotListener
            if (error != null) listener(Result.failure(error))
            else listener(runCatching { decodeConversation(snapshot!!).also { check(it.includes(uid)) } })
        }
        return ChatSubscription { watch.remove() }
    }

    override fun messages(id: String, uid: String, listener: (Result<List<ChatMessage>>) -> Unit): ChatSubscription {
        if (!ChatValidation.documentId(id) || !signedIn(uid)) return ChatSubscription {}
        val watch = threads().document(id).collection("messages").orderBy("createdAt")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!signedIn(uid)) return@addSnapshotListener
                if (error != null) listener(Result.failure(error))
                else listener(runCatching { snapshot!!.documents.map { doc ->
                    ChatMessage(doc.id, doc.getString("senderId") ?: error("Missing sender"),
                        doc.getString("text") ?: error("Missing message"), doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        if (doc.metadata.hasPendingWrites()) SendState.SENDING else SendState.SENT)
                } })
            }
        return ChatSubscription { watch.remove() }
    }

    override fun send(id: String, uid: String, messageId: String, text: String, done: (Result<Unit>) -> Unit) {
        val clean = text.trim()
        if (!signedIn(uid) || !ChatValidation.documentId(id) || !ChatValidation.documentId(messageId) || ChatValidation.textError(clean) != null) {
            done(Result.failure(IllegalArgumentException("Unable to send this message."))); return
        }
        val conversation = threads().document(id)
        val message = conversation.collection("messages").document(messageId)
        // Transactions fail offline instead of silently queuing an account's private message.
        // Retrying the same message ID cannot insert a second copy after an uncertain response.
        db.runTransaction { transaction ->
            checkSession(uid)
            val thread = decodeConversation(transaction.get(conversation))
            check(thread.includes(uid) && thread.active)
            val existing = transaction.get(message)
            if (existing.exists()) {
                check(existing.getString("senderId") == uid && existing.getString("text") == clean)
            } else {
                transaction.set(message, mapOf("senderId" to uid, "text" to clean, "createdAt" to FieldValue.serverTimestamp()))
                transaction.update(conversation, mapOf("lastMessageId" to messageId, "lastText" to clean,
                    "lastSenderId" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.addOnCompleteListener { task ->
            if (task.isSuccessful && signedIn(uid)) com.example.hiremydriver.notifications.EventRelay.enqueue(
                auth.app.applicationContext, uid, auth.app.name != com.google.firebase.FirebaseApp.DEFAULT_APP_NAME, "message", id, messageId)
            done(if (task.isSuccessful && signedIn(uid)) Result.success(Unit)
                else Result.failure(task.exception ?: IllegalStateException("Sign in again to continue.")))
        }
    }

    override fun markRead(id: String, uid: String, through: Long, done: (Boolean) -> Unit) {
        if (!signedIn(uid) || through <= 0 || !ChatValidation.documentId(id)) { done(false); return }
        val ref = threads().document(id)
        db.runTransaction { transaction ->
            checkSession(uid)
            val thread = decodeConversation(transaction.get(ref))
            check(thread.includes(uid))
            // Mark only the latest rendered server message, not a newer unseen arrival.
            val cursor = minOf(through, thread.updatedAt)
            if (cursor > (thread.readAt[uid] ?: 0))
                transaction.update(ref, FieldPath.of("readAt", uid), Timestamp(Date(cursor)))
        }.addOnCompleteListener { done(it.isSuccessful && signedIn(uid)) }
    }

    private fun decodeConversation(doc: DocumentSnapshot): Conversation {
        check(doc.exists())
        val customer = doc.getString("customerId") ?: error("Missing customer")
        val driver = doc.getString("driverId") ?: error("Missing driver")
        check(customer.isNotBlank() && driver.isNotBlank() && customer != driver)
        val reads = (doc.get("readAt") as? Map<*, *>)?.entries?.mapNotNull { (key, value) ->
            if (key is String && value is Timestamp) key to value.toDate().time else null
        }?.toMap().orEmpty()
        return Conversation(doc.id, customer, driver, doc.getString("customerName") ?: "Customer",
            doc.getString("driverName") ?: "Driver", doc.getString("lastText").orEmpty(),
            doc.getString("lastSenderId").orEmpty(), doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0,
            reads, doc.getBoolean("active") == true)
    }
}
