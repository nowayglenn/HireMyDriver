package com.example.hiremydriver.maps

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import com.example.hiremydriver.R
import com.google.android.material.bottomsheet.BottomSheetBehavior

/** Keeps the existing rounded content above an unobstructed, correctly sized map viewport. */
class MapDetailSheet(private val activity: Activity, saved: Bundle?, private val expand: ImageButton? = null) {
    private val root = activity.findViewById<CoordinatorLayout>(R.id.mapFlowScroll)
    private val sheet = activity.findViewById<NestedScrollView>(R.id.mapBottomSheet)
    private val mapArea = activity.findViewById<View>(R.id.mapArea)
    private val overlay = activity.findViewById<View>(R.id.mapOverlayContent)
    private val behavior = BottomSheetBehavior.from(sheet)
    private val density = activity.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    init {
        behavior.isHideable = false
        behavior.isFitToContents = false
        behavior.peekHeight = dp(84)
        behavior.halfExpandedRatio = 0.52f
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) { resizeMap() }
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                resizeMap()
                val hidden = newState == BottomSheetBehavior.STATE_COLLAPSED
                expand?.setImageResource(if (hidden) R.drawable.ic_map_flow_collapse else R.drawable.ic_map_flow_expand)
                expand?.contentDescription = if (hidden) "Show location details" else "Full-screen map"
            }
        })
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateSheetBounds()
            resizeMap()
        }
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSheetBounds() }
        sheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resizeMap() }
        // Position controls from the final rendered sheet position, including restored
        // state, system insets and settling animations, without relaying out the sheet.
        root.viewTreeObserver.addOnPreDrawListener { resizeMap(); true }
        expand?.setOnClickListener {
            (activity as? MapScreenActivity)?.preservePickerSelection()
            behavior.state = if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED)
                BottomSheetBehavior.STATE_HALF_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
            (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(sheet.windowToken, 0)
            sheet.clearFocus()
        }
        root.post {
            val initial = BottomSheetBehavior.STATE_HALF_EXPANDED
            val state = saved?.getInt("map_sheet_state", initial) ?: initial
            behavior.state = when (state) {
                BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_COLLAPSED -> state
                BottomSheetBehavior.STATE_HIDDEN -> BottomSheetBehavior.STATE_COLLAPSED
                else -> BottomSheetBehavior.STATE_HALF_EXPANDED
            }
            resizeMap()
        }
    }

    fun expandForInput() { behavior.state = BottomSheetBehavior.STATE_EXPANDED }

    private fun updateSheetBounds() {
        if (root.height <= 0) return
        val offset = maxOf(dp(180), overlay.height + dp(100))
            .coerceAtMost((root.height - dp(100)).coerceAtLeast(0))
        behavior.expandedOffset = offset
        val maxHeight = (root.height - offset).coerceAtLeast(dp(100))
        if (behavior.maxHeight != maxHeight) { behavior.maxHeight = maxHeight; sheet.requestLayout() }
    }

    private fun resizeMap() {
        if (root.height <= 0 || !sheet.isLaidOut) return
        // Let the parent measure the surface once, including keyboard resizing.
        // Replacing its height from pre-draw caused another surface layout on the next frame.
        if (mapArea.layoutParams.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT) {
            mapArea.layoutParams = mapArea.layoutParams.apply { height = android.view.ViewGroup.LayoutParams.MATCH_PARENT }
        }
        val covered = (root.height - sheet.top).coerceIn(0, root.height)
        (activity as? MapScreenActivity)?.setMapSheetClearance(covered)
    }

    fun save(out: Bundle) { out.putInt("map_sheet_state", behavior.state) }
}
