package com.example.hiremydriver.messaging

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.hiremydriver.ChatConversationActivity
import com.example.hiremydriver.R
import java.text.DateFormat
import java.util.Date

/** Shared inbox binding; screen layouts and customer/driver navigation remain separate. */
class ChatInbox(
    private val activity: AppCompatActivity,
    private val role: ChatRole,
    private val unreadChanged: (Int) -> Unit = {}
) : DefaultLifecycleObserver {
    private val repository = Messaging.repository(activity, role == ChatRole.DRIVER)
    private val container = activity.findViewById<LinearLayout>(R.id.chatListContainer)
    private var auth: ChatSubscription? = null
    private var watch: ChatSubscription? = null
    private var generation = 0
    private var uid: String? = null
    private var items = emptyList<Conversation>()
    private var notice: String? = null
    private var unreadOnly = false
    private var search = ""
    private var opening = false
    private val rowViews = mutableMapOf<String, View>()
    private var rendered: Triple<String, List<Conversation>, String?>? = null

    init { activity.lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { listen() }
    override fun onResume(owner: LifecycleOwner) { opening = false }
    override fun onStop(owner: LifecycleOwner) {
        generation++; watch?.cancel(); auth?.cancel(); watch = null; auth = null
        // Retain already-rendered history while this screen is in the back stack.
    }

    fun filter(unread: Boolean) { unreadOnly = unread; render() }
    fun search(query: String) { search = query.trim(); render() }

    private fun listen() {
        val signedIn = if (role == ChatRole.DRIVER) com.example.hiremydriver.DriverAuth.currentUser(activity)?.uid
            else com.example.hiremydriver.CustomerAuth.currentUser(activity)?.uid
        if (uid != signedIn) { uid = signedIn; items = emptyList(); container.removeAllViews(); rowViews.clear(); rendered = null }
        generation++; watch?.cancel(); auth?.cancel()
        watch = null; auth = null
        if (!repository.available) { notice = activity.getString(R.string.messaging_unavailable); render(); return }
        val ticket = generation
        notice = if (items.isEmpty()) "Loading messages…" else null; render()
        auth = repository.observeUser { user ->
            if (ticket != generation) return@observeUser
            if (uid != user) { items = emptyList(); container.removeAllViews(); rowViews.clear(); rendered = null }
            uid = user; watch?.cancel(); watch = null
            notice = if (user == null) "Sign in to view your messages." else if (items.isEmpty()) "Loading messages…" else null
            render()
            if (user != null) watch = repository.inbox(user, role) { result ->
                if (ticket != generation || uid != user) return@inbox
                if (result.isSuccess) items = result.getOrThrow()
                val failure = result.exceptionOrNull() as? com.google.firebase.firestore.FirebaseFirestoreException
                if (failure?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    failure?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED) items = emptyList()
                notice = if (result.isFailure) "Could not load messages. Tap to retry." else null
                render()
            }
        }
    }

    private fun render() {
        val viewer = uid.orEmpty()

        // Group conversations by peer ID so each conversation partner appears ONLY ONCE
        val deduplicatedItems = items
            .groupBy { if (viewer == it.customerId) it.driverId else it.customerId }
            .map { (_, peerThreads) ->
                PairChatRepository.merge(peerThreads)
            }
            .sortedWith(compareByDescending<Conversation> { it.updatedAt }.thenBy { it.id })

        unreadChanged(deduplicatedItems.count { it.unread(viewer) })

        val rows = deduplicatedItems.filter { (!unreadOnly || it.unread(viewer)) &&
            (search.isBlank() || it.peerName(viewer).contains(search, ignoreCase = true) || it.lastText.contains(search, ignoreCase = true)) }

        val emptyNotice = if (rows.isEmpty()) notice ?: if (search.isNotBlank()) "No matching conversations." else activity.getString(if (unreadOnly) R.string.messaging_unread_empty else R.string.messaging_empty) else null
        val state = Triple(viewer, rows, emptyNotice)
        if (rendered == state) return
        rendered = state
        if (rows.isEmpty()) {
            container.removeAllViews(); rowViews.clear();
            container.addView(TextView(activity).apply {
                text = notice ?: if (search.isNotBlank()) "No matching conversations."
                    else activity.getString(if (unreadOnly) R.string.messaging_unread_empty else R.string.messaging_empty)
                textSize = 15f; setTextColor(Color.rgb(102, 112, 133)); gravity = android.view.Gravity.CENTER
                val gap = (32 * resources.displayMetrics.density).toInt(); setPadding(gap, gap, gap, gap)
                if (notice?.contains("retry") == true) setOnClickListener { listen() }
            })
            return
        }

        fun rowKey(item: Conversation) = if (viewer == item.customerId) item.driverId else item.customerId
        val ids = rows.map(::rowKey).toSet()
        if (rowViews.isEmpty()) container.removeAllViews()
        (rowViews.keys - ids).forEach { id -> rowViews.remove(id)?.let(container::removeView) }
        rows.forEachIndexed { index, item ->
            val row = rowViews.getOrPut(rowKey(item)) { LayoutInflater.from(activity).inflate(R.layout.item_chat_conversation, container, false) }
            row.findViewById<TextView>(R.id.chatRowName).text = item.peerName(viewer)
            com.example.hiremydriver.PeerProfilePhoto.bind(row.findViewById(R.id.chatRowPhoto),
                if (role == ChatRole.DRIVER) item.customerId else item.driverId,
                role == ChatRole.CUSTOMER, role == ChatRole.DRIVER, row.findViewById(R.id.chatRowName))
            row.findViewById<TextView>(R.id.chatRowPreview).text = item.lastText.ifBlank { "Start a conversation" }
            row.findViewById<TextView>(R.id.chatRowTime).text = if (item.updatedAt > 0)
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.updatedAt)) else ""
            listOf(R.id.chatRowName, R.id.chatRowPreview).forEach { viewId ->
                row.findViewById<TextView>(viewId).apply {
                    val bold = item.unread(viewer)
                    if (typeface.isBold != bold) setTypeface(typeface, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
            }
            row.findViewById<View>(R.id.chatRowUnread).visibility = if (item.unread(viewer)) View.VISIBLE else View.GONE
            row.setOnClickListener {
                if (opening || activity.isFinishing) return@setOnClickListener
                opening = true
                activity.startActivity(Intent(activity, ChatConversationActivity::class.java)
                    .putExtra(Messaging.CONVERSATION_ID, item.id)
                    .putExtra(Messaging.DRIVER_SESSION, role == ChatRole.DRIVER))
            }
            if (container.indexOfChild(row) != index) {
                if (row.parent == container) container.removeView(row)
                container.addView(row, index)
                com.example.hiremydriver.PeerProfilePhoto.bind(row.findViewById(R.id.chatRowPhoto),
                    if (role == ChatRole.DRIVER) item.customerId else item.driverId,
                    role == ChatRole.CUSTOMER, role == ChatRole.DRIVER, row.findViewById(R.id.chatRowName))
            }
        }
    }
}
