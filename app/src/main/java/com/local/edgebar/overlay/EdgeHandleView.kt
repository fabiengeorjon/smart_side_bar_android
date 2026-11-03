package com.local.edgebar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.GestureMath
import com.local.edgebar.core.Side
import com.local.edgebar.core.TriggerMode

/**
 * The edge handle. Deliberately a plain [View] (no Compose) so the always-on window
 * costs almost nothing while idle: no composition, no animations, no timers.
 */
class EdgeHandleView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onHandleGrabbed()
        /** [triggerNanos] = System.nanoTime() when the open gesture was recognised. */
        fun onOpenGesture(source: String, triggerNanos: Long, detail: String)
        fun onGestureRejected(reason: String)
    }

    private val density = resources.displayMetrics.density
    private val openThresholdPx = EdgeBarSettings.OPEN_THRESHOLD_DP * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong() * 2

    private var side = Side.RIGHT
    private var mode = TriggerMode.EDGE
    private var showHint = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val shape = RectF()

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var tracking = false
    private var opened = false

    init {
        contentDescription = "Edge Bar handle"
        isClickable = true
    }

    fun configure(side: Side, mode: TriggerMode, showHint: Boolean) {
        if (this.side == side && this.mode == mode && this.showHint == showHint) return
        this.side = side
        this.mode = mode
        this.showHint = showHint
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Ask the system not to start a Back gesture inside the handle. Android honours at
        // most 200dp per edge, which is why the handle height is capped at 200dp.
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        when {
            mode == TriggerMode.PILL -> {
                val pillW = 4f * density
                val pillH = h * 0.7f
                val cx = w / 2f
                shape.set(cx - pillW / 2f, (h - pillH) / 2f, cx + pillW / 2f, (h + pillH) / 2f)
                paint.alpha = 0xD9
                canvas.drawRoundRect(shape, pillW / 2f, pillW / 2f, paint)
            }
            showHint -> {
                val lineW = 2f * density
                val lineH = h * 0.6f
                val l = if (side == Side.LEFT) 0f else w - lineW
                shape.set(l, (h - lineH) / 2f, l + lineW, (h + lineH) / 2f)
                paint.alpha = 0x59
                canvas.drawRoundRect(shape, lineW / 2f, lineW / 2f, paint)
            }
        }
    }

    // Raw coordinates: the window never moves, and raw values are what the gesture means.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                tracking = true
                opened = false
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                listener.onHandleGrabbed()
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && !opened &&
                    GestureMath.isOpenSwipe(side, downX, downY, event.rawX, event.rawY, openThresholdPx)
                ) {
                    opened = true
                    val inwardDp = GestureMath.inwardDistance(side, downX, event.rawX) / density
                    val sinceDownMs = SystemClock.uptimeMillis() - downTime
                    listener.onOpenGesture(
                        "swipe",
                        System.nanoTime(),
                        "inward=%.0fdp after %dms".format(inwardDp, sinceDownMs),
                    )
                }
            }
            MotionEvent.ACTION_UP -> {
                if (tracking && !opened) {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val tap = GestureMath.isTap(dx, dy, event.eventTime - downTime, touchSlop, tapTimeout)
                    if (tap && mode == TriggerMode.PILL) {
                        opened = true
                        performClick()
                    } else {
                        val inwardDp = GestureMath.inwardDistance(side, downX, event.rawX) / density
                        listener.onGestureRejected(
                            "released before threshold: inward=%.0fdp dy=%.0fdp (need %ddp, mostly horizontal)"
                                .format(inwardDp, dy / density, EdgeBarSettings.OPEN_THRESHOLD_DP),
                        )
                    }
                }
                tracking = false
            }
            MotionEvent.ACTION_CANCEL -> {
                // The system (usually the Back gesture) pilfered the pointer from us.
                if (tracking && !opened) listener.onGestureRejected("cancelled: system took the touch (Back gesture?)")
                tracking = false
            }
        }
        return true
    }

    /** Tap in PILL mode, and the TalkBack / Switch Access activation path. */
    override fun performClick(): Boolean {
        super.performClick()
        listener.onOpenGesture("tap", System.nanoTime(), "tap")
        return true
    }
}
