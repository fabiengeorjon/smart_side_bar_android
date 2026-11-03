package com.local.edgebar.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.GestureMath
import com.local.edgebar.core.Side
import kotlin.math.max

/**
 * Root of the panel window. Adds two behaviours around the Compose content:
 *  - swipe toward the panel's edge to close (claims only clearly horizontal drags, so
 *    vertical scrolling of the app list is unaffected);
 *  - legacy KEYCODE_BACK delivery (OnBackInvokedCallback is registered by PanelSession).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility") // created in code; drag gesture, not a click
class PanelRootLayout(
    context: Context,
    private val side: Side,
    private val onSwipeClosed: () -> Unit,
    private val onSwipeCancelled: () -> Unit,
    private val onBack: () -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val closeThreshold = EdgeBarSettings.OPEN_THRESHOLD_DP * density

    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> if (!dragging &&
                GestureMath.shouldInterceptClose(side, downX, downY, ev.rawX, ev.rawY, slop)
            ) {
                dragging = true
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && GestureMath.shouldInterceptClose(side, downX, downY, ev.rawX, ev.rawY, slop)) {
                    dragging = true
                }
                if (dragging) {
                    // Follow the finger toward the edge (never past the open position).
                    val outward = max(0f, -GestureMath.inwardDistance(side, downX, ev.rawX))
                    translationX = if (side == Side.LEFT) -outward else outward
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val closed = ev.actionMasked == MotionEvent.ACTION_UP &&
                        GestureMath.isCloseSwipe(side, downX, downY, ev.rawX, ev.rawY, closeThreshold)
                    if (closed) onSwipeClosed() else onSwipeCancelled()
                }
                dragging = false
            }
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
