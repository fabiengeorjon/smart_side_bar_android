package com.local.edgebar.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.HandleGeometry
import com.local.edgebar.core.PanelGeometry
import com.local.edgebar.diag.Diagnostics
import kotlin.math.roundToInt

/**
 * PHASE 1 SPIKE ONLY: handle + plain-View test panel, to prove the edge gesture before
 * the full app is built. Replaced by OverlayController in phase 2.
 */
class SpikeController(private val service: AccessibilityService) : EdgeHandleView.Listener {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    var settings = EdgeBarSettings()
        set(value) { field = value; layoutHandle() }

    private var handle: EdgeHandleView? = null
    private var scrim: View? = null
    private var panel: View? = null

    fun start() = layoutHandle()

    fun stop() {
        closePanel()
        OverlayWindows.safeRemove(wm, handle)
        handle = null
    }

    private fun layoutHandle() {
        val metrics = wm.currentWindowMetrics
        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
        val s = settings
        val layout = HandleGeometry.compute(
            metrics.bounds.width(), metrics.bounds.height(), s.side, s.triggerMode, s.verticalPosition,
            (s.handleHeightDp * density).roundToInt(), (s.handleWidthDp * density).roundToInt(),
            (s.pillInsetDp * density).roundToInt(), bars.top, bars.bottom,
        )
        val view = handle ?: EdgeHandleView(service, this).also { handle = it }
        view.configure(s.side, s.triggerMode, s.showEdgeHint)
        val lp = OverlayWindows.handle(s.side, layout.edgeOffset, layout.top, layout.width, layout.height)
        runCatching { if (view.isAttachedToWindow) wm.updateViewLayout(view, lp) else wm.addView(view, lp) }
            .onFailure { Diagnostics.log(Diagnostics.Kind.OVERLAY_ERROR, "spike handle", it) }
        val b = layout.screenBounds(s.side, metrics.bounds.width())
        Diagnostics.log(
            Diagnostics.Kind.INFO,
            "HANDLE_LAYOUT side=${s.side} mode=${s.triggerMode} left=${b.left} top=${b.top} right=${b.right} " +
                "bottom=${b.bottom} screen=${metrics.bounds.width()}x${metrics.bounds.height()}",
        )
    }

    override fun onHandleGrabbed() = Unit

    override fun onOpenGesture(source: String, triggerNanos: Long, detail: String) {
        Diagnostics.gesture("OPEN source=$source $detail")
        if (panel != null) return
        val s = settings
        val width = PanelGeometry.panelWidthPx(wm.currentWindowMetrics.bounds.width(), density, s.panelWidthFraction)
        val scrimView = View(service).apply {
            setBackgroundColor(0x99000000.toInt())
            setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) closePanel(); true }
        }
        val panelView = FrameLayout(service).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                TextView(service).apply { text = "EDGE BAR\nTEST PANEL"; setTextColor(Color.WHITE); gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1),
            )
        }
        panelView.viewTreeObserver.registerFrameCommitCallback {
            Diagnostics.latency((System.nanoTime() - triggerNanos) / 1e6, source)
        }
        runCatching {
            wm.addView(scrimView, OverlayWindows.scrim())
            wm.addView(panelView, OverlayWindows.panel(s.side, width))
        }.onFailure {
            Diagnostics.log(Diagnostics.Kind.OVERLAY_ERROR, "spike panel", it)
            OverlayWindows.safeRemove(wm, scrimView)
            OverlayWindows.safeRemove(wm, panelView)
            return
        }
        scrim = scrimView
        panel = panelView
    }

    override fun onGestureRejected(reason: String) = Diagnostics.gesture("NO_OPEN $reason")

    fun closePanel() {
        if (panel == null && scrim == null) return
        OverlayWindows.safeRemove(wm, panel)
        OverlayWindows.safeRemove(wm, scrim)
        panel = null
        scrim = null
        Diagnostics.log(Diagnostics.Kind.INFO, "PANEL_CLOSED")
    }
}
