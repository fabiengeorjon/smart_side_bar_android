package com.local.edgebar.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Plain rectangle so geometry stays testable without android.graphics.Rect. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Handle window placement.
 * [edgeOffset] is the WindowManager.LayoutParams.x value: the distance from the selected
 * physical edge (gravity LEFT or RIGHT). [top] is the y value with gravity TOP.
 */
data class HandleLayout(val edgeOffset: Int, val top: Int, val width: Int, val height: Int) {
    /** Absolute on-screen bounds, for diagnostics and the device test script. */
    fun screenBounds(side: Side, screenWidth: Int): Box {
        val left = if (side == Side.LEFT) edgeOffset else screenWidth - edgeOffset - width
        return Box(left, top, left + width, top + height)
    }
}

object HandleGeometry {
    /**
     * @param safeTop / safeBottom system-bar heights (ignoring visibility) the handle must not cover.
     * @param cutouts display-cutout bounding boxes in screen coordinates.
     */
    fun compute(
        screenWidth: Int,
        screenHeight: Int,
        side: Side,
        mode: TriggerMode,
        verticalPosition: Float,
        heightPx: Int,
        widthPx: Int,
        pillInsetPx: Int,
        safeTop: Int,
        safeBottom: Int,
        cutouts: List<Box> = emptyList(),
    ): HandleLayout {
        val usable = max(1, screenHeight - safeTop - safeBottom)
        val height = heightPx.coerceIn(1, usable)
        val width = widthPx.coerceIn(1, max(1, screenWidth / 4))
        val fraction = if (verticalPosition.isFinite()) verticalPosition.coerceIn(0f, 1f) else 0.5f
        val top = safeTop + ((usable - height) * fraction).roundToInt()

        var offset = if (mode == TriggerMode.PILL) max(0, pillInsetPx) else 0
        // A cutout touching our edge that overlaps the handle's vertical span (typical in
        // landscape) pushes the handle inward so it stays fully touchable.
        for (c in cutouts) {
            val touchesEdge = if (side == Side.LEFT) c.left <= 0 else c.right >= screenWidth
            val overlaps = c.top < top + height && c.bottom > top
            if (touchesEdge && overlaps) {
                val clearance = if (side == Side.LEFT) c.right else screenWidth - c.left
                offset = max(offset, clearance)
            }
        }
        offset = min(offset, max(0, screenWidth - width))
        return HandleLayout(offset, top, width, height)
    }
}

object PanelGeometry {
    /** 25–30 % of the screen width, never narrower than 88dp nor wider than the screen. */
    fun panelWidthPx(screenWidthPx: Int, density: Float, fraction: Float): Int {
        val f = if (fraction.isFinite()) {
            fraction.coerceIn(EdgeBarSettings.MIN_PANEL_FRACTION, EdgeBarSettings.MAX_PANEL_FRACTION)
        } else {
            EdgeBarSettings.DEFAULT_PANEL_FRACTION
        }
        val minPx = (EdgeBarSettings.MIN_PANEL_WIDTH_DP * density).roundToInt()
        return min(max((screenWidthPx * f).roundToInt(), minPx), screenWidthPx)
    }
}
