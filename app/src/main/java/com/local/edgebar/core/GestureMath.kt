package com.local.edgebar.core

import kotlin.math.abs

/** Pure gesture classification shared by the handle and the panel. Units are pixels / ms. */
object GestureMath {
    /** Positive when the pointer has moved away from [side]'s edge, toward screen centre. */
    fun inwardDistance(side: Side, downX: Float, x: Float): Float =
        if (side == Side.LEFT) x - downX else downX - x

    /**
     * True once the pointer has travelled at least [thresholdPx] inward and the motion is
     * mostly horizontal (inward travel >= vertical travel, i.e. within 45 degrees).
     */
    fun isOpenSwipe(side: Side, downX: Float, downY: Float, x: Float, y: Float, thresholdPx: Float): Boolean {
        val inward = inwardDistance(side, downX, x)
        return inward >= thresholdPx && inward >= abs(y - downY)
    }

    /** Swipe back toward the panel's own edge, used to dismiss the panel. */
    fun isCloseSwipe(side: Side, downX: Float, downY: Float, x: Float, y: Float, thresholdPx: Float): Boolean {
        val outward = -inwardDistance(side, downX, x)
        return outward >= thresholdPx && outward >= abs(y - downY)
    }

    /** True while an in-progress drag should be claimed by the panel's swipe-to-close. */
    fun shouldInterceptClose(side: Side, downX: Float, downY: Float, x: Float, y: Float, slopPx: Float): Boolean {
        val outward = -inwardDistance(side, downX, x)
        return outward > slopPx && outward > 2f * abs(y - downY)
    }

    fun isTap(dx: Float, dy: Float, durationMs: Long, slopPx: Float, tapTimeoutMs: Long): Boolean =
        abs(dx) <= slopPx && abs(dy) <= slopPx && durationMs <= tapTimeoutMs
}
