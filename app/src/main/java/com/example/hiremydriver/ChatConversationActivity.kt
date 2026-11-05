package com.example.hiremydriver

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.hiremydriver.messaging.*
import java.text.DateFormat
import java.util.Date

class ChatConversationActivity : BaseActivity() {
    private lateinit var model: ChatViewModel
    private lateinit var container: LinearLayout
    private lateinit var scroll: NestedScrollView
    private lateinit var input: EditText
    private lateinit var send: ImageButton
    private var previousUser: String? = null
    private val messageRows = linkedMapOf<String, MessageRow>()
    private var noticeView: TextView? = null
    private data class MessageRow(val root: View, val body: TextView, val status: TextView, var value: ChatMessage)
    private var previousMessages: List<ChatMessage>? = null
    private var screenStarted = false
    private var prepareGeneration = 0
    private val readHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val retryRead = object : Runnable { override fun run() { acknowledge(); readHandler.postDelayed(this, 3000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_conversation)
        container = findViewById(R.id.messagesContainer)
        scroll = findViewById(R.id.chatScrollView)
        input = findViewById(R.id.edtMessage)
        send = findViewById(R.id.btnSend)
        input.isSaveEnabled = false
        input.isEnabled = false; send.isEnabled = false
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (::model.isInitialized) model.draft = s?.toString().orEmpty() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> acknowledge() })
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        send.setOnClickListener {
            val text = input.text.toString()
            val error = ChatValidation.textError(text)
            if (error != null) { input.error = error; return@setOnClickListener }
            if (model.session.send(text)) input.text.clear()
            else Toast.makeText(this, "Please wait or retry when chat is available.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        screenStarted = true
        if (::model.isInitialized) { model.session.start(); return }
        val ticket = ++prepareGeneration
        BookingChat.prepare(this, intent.getStringExtra(Messaging.CONVERSATION_ID).orEmpty(),
            intent.getBooleanExtra(Messaging.DRIVER_SESSION, false)) { ready ->
            if (ticket != prepareGeneration || !screenStarted || isFinishing || isDestroyed) return@prepare
            if (ready != null) {
                intent.putExtra(Messaging.CONVERSATION_ID, ready)
                model = ViewModelProvider(this, ChatViewModel.Factory(
                    Messaging.repository(this, intent.getBooleanExtra(Messaging.DRIVER_SESSION, false)), ready))[ChatViewModel::class.java]
                input.setText(model.draft)
                model.state.observe(this, ::render)
                model.session.start()
            } else {
                Toast.makeText(this, "Could not connect your chat. Check your connection and reopen it.", Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onStop() { screenStarted = false; prepareGeneration++; if (::model.isInitialized) model.session.stop(); super.onStop() }
    override fun onResume() { super.onResume(); readHandler.post(retryRead) }
    override fun onPause() { readHandler.removeCallbacks(retryRead); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::model.isInitialized) acknowledge()
    }

    private fun acknowledge() {
        if (::model.isInitialized && hasWindowFocus() && !scroll.canScrollVertically(1) && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            model.session.acknowledgeVisibleMessages()
    }

    private fun render(state: ChatState) {
        if (previousUser != state.userId) {
            input.setText(model.draft); previousUser = state.userId
            container.removeAllViews(); messageRows.clear(); noticeView = null; previousMessages = null
        }
        val busy = state.messages.any { it.state == SendState.SENDING }
        input.isEnabled = state.canSend
        send.isEnabled = state.canSend && !busy
        send.alpha = if (send.isEnabled) 1f else 0.4f
        findViewById<TextView>(R.id.txtDriverName).text = state.conversation?.peerName(state.userId.orEmpty()) ?: "Conversation"
        state.conversation?.let {
            val driver = intent.getBooleanExtra(Messaging.DRIVER_SESSION, false)
            PeerProfilePhoto.bind(findViewById(R.id.profileCircle), if (driver) it.customerId else it.driverId, !driver, driver)
        }
        findViewById<TextView>(R.id.txtChatStatus).text = when {
            state.loading -> "Loading…"
            state.conversation?.active == false -> "Closed"
            state.conversation != null -> "Booking conversation"
            else -> ""
        }
        val nearBottom = !scroll.canScrollVertically(1)
        val newMessages = previousMessages?.map { it.id } != state.messages.map { it.id }
        val notice = state.notice ?: if (state.messages.isEmpty() && !state.loading) getString(R.string.messaging_empty) else null
        if (notice == null) {
            noticeView?.let(container::removeView); noticeView = null
        } else {
            val label = noticeView ?: TextView(this).apply {
                textSize = 14f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(102, 112, 133)); setPadding(dp(12), dp(24), dp(12), dp(24))
                container.addView(this, 0); noticeView = this
            }
            if (label.text.toString() != notice) label.text = notice
            label.setOnClickListener { if (notice.contains("retry", true)) model.session.refresh() }
        }
        val ids = state.messages.map { it.id }.toSet()
        (messageRows.keys - ids).forEach { id -> messageRows.remove(id)?.let { container.removeView(it.root) } }
        state.messages.forEachIndexed { index, message ->
            val mine = message.senderId == state.userId
            val row = messageRows.getOrPut(message.id) { createMessageRow(message, mine) }
            val position = index + if (noticeView == null) 0 else 1
            if (container.indexOfChild(row.root) != position) {
                if (row.root.parent == container) container.removeView(row.root)
                container.addView(row.root, position)
            }
            if (!mine) {
                val avatar = (row.root as LinearLayout).getChildAt(0) as android.widget.ImageView
                val driver = intent.getBooleanExtra(Messaging.DRIVER_SESSION, false)
                PeerProfilePhoto.bind(avatar, message.senderId, !driver, driver)
            }
            if (row.body.text.toString() != message.text) row.body.text = message.text
            val bold = !mine && message.unread
            if (row.body.typeface.isBold != bold)
                row.body.setTypeface(row.body.typeface, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            val time = if (message.createdAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)) else ""
            val status = if (!mine) time else when (message.state) {
                SendState.SENDING -> "Sending…"
                SendState.SENT -> "$time · Sent"
                SendState.FAILED -> "Not sent · Tap to retry"
            }
            if (row.status.text.toString() != status) row.status.text = status
            row.status.setTextColor(if (message.state == SendState.FAILED) Color.rgb(180, 35, 24) else Color.rgb(102, 112, 133))
            row.status.minHeight = if (message.state == SendState.FAILED && mine) dp(48) else 0
            row.value = message
        }
        previousMessages = state.messages
        if (newMessages && nearBottom) scroll.post { scroll.fullScroll(View.FOCUS_DOWN); acknowledge() }
        else if (nearBottom) acknowledge()
    }

    private fun createMessageRow(message: ChatMessage, mine: Boolean): MessageRow {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = if (mine) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        }
        if (!mine) {
            val avatar = com.google.android.material.imageview.ShapeableImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) }
                minimumWidth = dp(28); minimumHeight = dp(28)
                setPadding(dp(1), dp(1), dp(1), dp(1)); setContentPadding(0, 0, 0, 0)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(com.google.android.material.shape.RelativeCornerSize(0.5f)).build()
            }
            row.addView(avatar)
            val driver = intent.getBooleanExtra(Messaging.DRIVER_SESSION, false)
            PeerProfilePhoto.bind(avatar, message.senderId, !driver, driver)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(if (mine) -2 else 0, -2, if (mine) 0f else 1f)
        }
        val body = TextView(this).apply {
            text = message.text; textSize = 14f
            layoutParams = LinearLayout.LayoutParams(-2, -2)
            setTextColor(if (mine) Color.WHITE else Color.rgb(52, 64, 84))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            setBackgroundResource(if (mine) R.drawable.bg_chat_bubble_customer else R.drawable.bg_chat_bubble_driver)
        }
        val status = TextView(this).apply { textSize = 11f; setPadding(0, dp(4), 0, 0); gravity = Gravity.CENTER_VERTICAL }
        wrapper.addView(body); wrapper.addView(status); row.addView(wrapper)
        return MessageRow(row, body, status, message).also { holder ->
            status.setOnClickListener { if (mine && holder.value.state == SendState.FAILED) model.session.retry(holder.value.id) }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
