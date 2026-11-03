package com.local.edgebar.core

/** Which screen edge the handle and panel live on. Always physical, never RTL-mirrored. */
enum class Side { LEFT, RIGHT }

/**
 * How the handle is triggered.
 *
 * [EDGE]: an invisible (or faint-hint) strip flush with the screen edge, protected by
 * system-gesture exclusion rects. Swipe inward 40dp to open.
 *
 * [PILL]: the back-gesture fallback. The handle is moved slightly inward and drawn as a
 * thin visible pill that opens on an inward swipe *or* a tap, so it keeps working even
 * when the system back gesture wins at the very edge.
 */
enum class TriggerMode { EDGE, PILL }

enum class QuickTool(val id: String, val label: String) {
    CALCULATOR("calculator", "CALC"),
    TORCH("torch", "TORCH"),
    SCREENSHOT("screenshot", "SHOT"),
    LOCK("lock", "LOCK"),
    ;

    companion object {
        fun fromId(id: String): QuickTool? = entries.firstOrNull { it.id == id }
    }
}

data class EdgeBarSettings(
    val handleEnabled: Boolean = true,
    val side: Side = Side.RIGHT,
    val triggerMode: TriggerMode = TriggerMode.EDGE,
    /** 0 = top of the usable area, 1 = bottom. */
    val verticalPosition: Float = DEFAULT_VERTICAL_POSITION,
    val handleHeightDp: Int = DEFAULT_HEIGHT_DP,
    val handleWidthDp: Int = DEFAULT_WIDTH_DP,
    /** EDGE mode only: draw a faint 2dp line so the handle can be found. */
    val showEdgeHint: Boolean = true,
    /** PILL mode only: distance of the touch strip from the physical edge. */
    val pillInsetDp: Int = DEFAULT_PILL_INSET_DP,
    val hideOnKeyboard: Boolean = true,
    val hideInFullscreen: Boolean = true,
    val panelWidthFraction: Float = DEFAULT_PANEL_FRACTION,
    val pinnedPackages: List<String> = emptyList(),
    val enabledTools: Set<QuickTool> = QuickTool.entries.toSet(),
    /** Packages in which the handle is hidden (fallback when full-screen detection fails). */
    val blocklist: Set<String> = emptySet(),
) {
    /** Returns a copy with every value clamped into its legal range. Idempotent. */
    fun normalized(): EdgeBarSettings = copy(
        verticalPosition = verticalPosition.finiteOr(DEFAULT_VERTICAL_POSITION).coerceIn(0f, 1f),
        handleHeightDp = handleHeightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
        handleWidthDp = handleWidthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP),
        pillInsetDp = pillInsetDp.coerceIn(MIN_PILL_INSET_DP, MAX_PILL_INSET_DP),
        panelWidthFraction = panelWidthFraction.finiteOr(DEFAULT_PANEL_FRACTION)
            .coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION),
        pinnedPackages = PinLogic.sanitize(pinnedPackages),
        blocklist = blocklist.map { it.trim() }.filter { PinLogic.isValidPackageName(it) }.toSet(),
    )

    /** Tools in their fixed display order. */
    val orderedTools: List<QuickTool> get() = QuickTool.entries.filter { it in enabledTools }

    companion object {
        const val MIN_HEIGHT_DP = 48
        /** Hard cap: Android only honours 200dp of gesture exclusion per edge. */
        const val MAX_HEIGHT_DP = 200
        const val DEFAULT_HEIGHT_DP = 160
        const val MIN_WIDTH_DP = 8
        const val MAX_WIDTH_DP = 40
        const val DEFAULT_WIDTH_DP = 16
        const val MIN_PILL_INSET_DP = 0
        const val MAX_PILL_INSET_DP = 32
        const val DEFAULT_PILL_INSET_DP = 12
        const val DEFAULT_VERTICAL_POSITION = 0.35f
        const val MIN_PANEL_FRACTION = 0.25f
        const val MAX_PANEL_FRACTION = 0.30f
        const val DEFAULT_PANEL_FRACTION = 0.28f
        const val MIN_PANEL_WIDTH_DP = 88
        const val OPEN_THRESHOLD_DP = 40
    }
}

private fun Float.finiteOr(default: Float): Float = if (isFinite()) this else default
