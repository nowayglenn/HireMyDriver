package com.example.hiremydriver

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout

class PopularDriversActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popular_drivers)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val parent = findViewById<View>(R.id.driver1).parent as LinearLayout
        parent.removeAllViews()
        OfflineSkeleton.bind(this, parent, R.layout.item_driver, 2)
        var currentRows = emptyList<RegisteredDriver>()
        RegisteredDriverDirectory.observe(this) { drivers, message ->
            currentRows = drivers
            RegisteredDriverDirectory.render(this, parent, drivers, message)
        }
        val refresh = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.popularDriversRefresh)
        val scroll = findViewById<View>(R.id.popularDriversScroll)
        var pending = false
        fun reload() {
            if (pending) return
            if (!NetworkStatus.requireConnection(this) { reload() }) { refresh.isRefreshing = false; return }
            pending = true
            refresh.isRefreshing = true
            if (currentRows.isEmpty()) RegisteredDriverDirectory.render(this, parent, currentRows, "Loading registered drivers…")
            RegisteredDriverDirectory.refresh(this) { rows, message ->
                pending = false
                if (isFinishing || isDestroyed) return@refresh
                refresh.isRefreshing = false
                if (rows != null) currentRows = rows
                if (rows != null || currentRows.isEmpty()) {
                    RegisteredDriverDirectory.render(this, parent, currentRows, message.orEmpty())
                } else android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        refresh.setColorSchemeColors(android.graphics.Color.rgb(255, 122, 0))
        refresh.setProgressBackgroundColorSchemeColor(android.graphics.Color.WHITE)
        refresh.setOnChildScrollUpCallback { _, _ -> scroll.canScrollVertically(-1) }
        refresh.setOnRefreshListener { reload() }
        androidx.core.view.ViewCompat.addAccessibilityAction(refresh, getString(R.string.refresh_drivers)) { _, _ -> reload(); true }
    }

}
