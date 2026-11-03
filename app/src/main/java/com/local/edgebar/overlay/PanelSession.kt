package com.local.edgebar.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.local.edgebar.core.Side
import kotlin.math.abs

/**
 * One opening of the sidebar: owns the scrim window, the panel window, the ComposeView
 * and its lifecycle owner. Everything is created in [show] and released in [teardown];
 * a closed session holds no windows, no composition and no callbacks.
 */
class PanelSession(
    private val context: Context,
    private val wm: WindowManager,
    private val side: Side,
    private val panelWidthPx: Int,
    private val triggerNanos: Long,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onFirstVisibleFrame(latencyMs: Double)
        fun onClosed()
        fun onOverlayError(message: String, t: Throwable?)
    }

    private enum class State { NEW, OPEN, CLOSING, CLOSED }

    private var state = State.NEW
    private val owner = OverlayLifecycleOwner()
    private var scrim: View? = null
    private var root: PanelRootLayout? = null
    private var composeView: ComposeView? = null
    private var backCallback: OnBackInvokedCallback? = null
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private val pendingAfterClose = mutableListOf<() -> Unit>()

    val isOpen: Boolean get() = state == State.OPEN

    private val offscreen: Float get() = if (side == Side.LEFT) -panelWidthPx.toFloat() else panelWidthPx.toFloat()

    fun show(content: @Composable () -> Unit): Boolean {
        check(state == State.NEW)
        try {
            owner.create()

            val scrimView = View(context).apply {
                setBackgroundColor(SCRIM_COLOR)
                alpha = 0f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnTouchListener { _, e ->
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) close()
                    true
                }
            }
            scrim = scrimView
            wm.addView(scrimView, OverlayWindows.scrim())

            val compose = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent(content)
            }
            composeView = compose
            val rootView = PanelRootLayout(
                context,
                side,
                onSwipeClosed = { close() },
                onSwipeCancelled = { root?.animate()?.translationX(0f)?.setDuration(OPEN_MS)?.start() },
                onBack = { close() },
            ).apply {
                addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                // Start mostly off-screen but with a sliver visible, so the very first
                // frame drawn is already a visible panel frame.
                translationX = offscreen * START_OFFSCREEN_FRACTION
                isFocusable = true
                isFocusableInTouchMode = true
            }
            root = rootView
            watchFirstVisibleFrame(rootView)
            registerBack(rootView)
            wm.addView(rootView, OverlayWindows.panel(side, panelWidthPx))
            rootView.requestFocus()

            owner.resume()
            state = State.OPEN

            rootView.animate().translationX(0f).setDuration(OPEN_MS)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
            scrimView.animate().alpha(1f).setDuration(OPEN_MS).start()
            return true
        } catch (t: Throwable) {
            callbacks.onOverlayError("panel open failed", t)
            teardown(notify = false)
            return false
        }
    }

    /**
     * Records the time from gesture recognition to the commit of the first frame in
     * which the panel is visible on screen.
     */
    private fun watchFirstVisibleFrame(view: View) {
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (abs(view.translationX) < panelWidthPx - 0.5f) {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    preDrawListener = null
                    view.viewTreeObserver.registerFrameCommitCallback {
                        callbacks.onFirstVisibleFrame((System.nanoTime() - triggerNanos) / 1_000_000.0)
                    }
                }
                return true
            }
        }
        preDrawListener = listener
        view.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun registerBack(view: View) {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val dispatcher = v.findOnBackInvokedDispatcher() ?: return
                val cb = OnBackInvokedCallback { close() }
                dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb)
                backCallback = cb
            }

            override fun onViewDetachedFromWindow(v: View) {
                unregisterBack(v)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    private fun unregisterBack(v: View) {
        val cb = backCallback ?: return
        backCallback = null
        runCatching { v.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb) }
    }

    /**
     * Closes the panel. With [animated] false the windows are removed synchronously,
     * which is what app launches, screenshots and lock need. [then] runs once the
     * windows are gone.
     */
    fun close(animated: Boolean = true, then: (() -> Unit)? = null) {
        then?.let { pendingAfterClose += it }
        when (state) {
            State.CLOSED, State.NEW -> { runPending(); return }
            State.CLOSING -> { if (!animated) teardown(notify = true); return }
            State.OPEN -> Unit
        }
        state = State.CLOSING
        root?.let { unregisterBack(it) }
        val r = root
        val s = scrim
        if (!animated || r == null) {
            teardown(notify = true)
            return
        }
        r.animate().cancel()
        s?.animate()?.cancel()
        s?.animate()?.alpha(0f)?.setDuration(CLOSE_MS)?.start()
        r.animate().translationX(offscreen).setDuration(CLOSE_MS)
            .setInterpolator(AccelerateInterpolator(1.4f))
            .withEndAction { teardown(notify = true) }
            .start()
    }

    private fun teardown(notify: Boolean) {
        if (state == State.CLOSED) return
        state = State.CLOSED
        root?.animate()?.cancel()
        scrim?.animate()?.cancel()
        root?.let { r ->
            preDrawListener?.let { if (r.viewTreeObserver.isAlive) r.viewTreeObserver.removeOnPreDrawListener(it) }
            unregisterBack(r)
        }
        preDrawListener = null
        val errors = listOfNotNull(OverlayWindows.safeRemove(wm, root), OverlayWindows.safeRemove(wm, scrim))
        composeView?.disposeComposition()
        owner.destroy()
        composeView = null
        root = null
        scrim = null
        errors.firstOrNull()?.let { callbacks.onOverlayError("panel window removal failed", it) }
        if (notify) callbacks.onClosed()
        runPending()
    }

    private fun runPending() {
        val actions = pendingAfterClose.toList()
        pendingAfterClose.clear()
        actions.forEach { it() }
    }

    companion object {
        const val OPEN_MS = 160L
        const val CLOSE_MS = 130L
        private const val START_OFFSCREEN_FRACTION = 0.85f
        private const val SCRIM_COLOR = 0x8C000000.toInt()
    }
}
