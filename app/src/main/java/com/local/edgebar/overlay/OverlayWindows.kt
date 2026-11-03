package com.local.edgebar.overlay

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import com.local.edgebar.core.Side

/**
 * LayoutParams factories. Every window is TYPE_ACCESSIBILITY_OVERLAY: no
 * SYSTEM_ALERT_WINDOW permission, no foreground service, no notification.
 */
object OverlayWindows {

    @SuppressLint("RtlHardcoded") // physical edges on purpose
    fun sideGravity(side: Side): Int = Gravity.TOP or (if (side == Side.LEFT) Gravity.LEFT else Gravity.RIGHT)

    private fun base(width: Int, height: Int, flags: Int, title: String) = LP(
        width,
        height,
        LP.TYPE_ACCESSIBILITY_OVERLAY,
        flags or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        windowAnimations = 0
        this.title = title
    }

    /** Narrow, touchable, never focusable: touches outside it go straight to the app below. */
    fun handle(side: Side, edgeOffset: Int, top: Int, width: Int, height: Int): LP =
        base(width, height, LP.FLAG_NOT_FOCUSABLE, "EdgeBarHandle").apply {
            gravity = sideGravity(side)
            x = edgeOffset
            y = top
        }

    /**
     * 1x1 window that is neither touchable nor focusable. It exists only to receive
     * WindowInsets so we can tell when the IME or system bars are shown/hidden.
     */
    fun insetsProbe(): LP =
        base(1, 1, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCHABLE, "EdgeBarInsetsProbe").apply {
            gravity = Gravity.TOP or Gravity.START
        }

    /** Full-screen dimming layer under the panel; a tap on it closes the panel. */
    fun scrim(): LP =
        base(LP.MATCH_PARENT, LP.MATCH_PARENT, LP.FLAG_NOT_FOCUSABLE, "EdgeBarScrim")

    /**
     * The panel is focusable so that Back (key or OnBackInvoked) can be delivered to it.
     * NOT_TOUCH_MODAL lets touches outside it fall through to the scrim.
     */
    fun panel(side: Side, width: Int): LP =
        base(width, LP.MATCH_PARENT, LP.FLAG_NOT_TOUCH_MODAL, "EdgeBarPanel").apply {
            gravity = sideGravity(side)
        }

    fun safeRemove(wm: WindowManager, view: android.view.View?): Throwable? {
        if (view == null || !view.isAttachedToWindow) return null
        return runCatching { wm.removeViewImmediate(view) }.exceptionOrNull()
    }
}
