package com.local.edgebar.core

/**
 * Converts [EdgeBarSettings] to and from a flat key/value map whose value types match
 * what Preferences DataStore can store (Boolean, Int, Float, String, Set<String>).
 *
 * Decoding is tolerant: a missing key, a value of the wrong type or an unknown enum
 * name falls back to the default for that field, so a corrupt or old file never
 * crashes the service.
 */
object SettingsCodec {
    const val HANDLE_ENABLED = "handle_enabled"
    const val SIDE = "side"
    const val TRIGGER_MODE = "trigger_mode"
    const val VERTICAL_POSITION = "vertical_position"
    const val HANDLE_HEIGHT_DP = "handle_height_dp"
    const val HANDLE_WIDTH_DP = "handle_width_dp"
    const val SHOW_EDGE_HINT = "show_edge_hint"
    const val PILL_INSET_DP = "pill_inset_dp"
    const val HIDE_ON_KEYBOARD = "hide_on_keyboard"
    const val HIDE_IN_FULLSCREEN = "hide_in_fullscreen"
    const val PANEL_WIDTH_FRACTION = "panel_width_fraction"
    const val PINNED = "pinned_packages"
    const val TOOLS = "enabled_tools"
    const val BLOCKLIST = "blocklist"

    fun decode(raw: Map<String, Any?>): EdgeBarSettings {
        val d = EdgeBarSettings()
        return EdgeBarSettings(
            handleEnabled = raw.bool(HANDLE_ENABLED) ?: d.handleEnabled,
            side = raw.enumOf<Side>(SIDE) ?: d.side,
            triggerMode = raw.enumOf<TriggerMode>(TRIGGER_MODE) ?: d.triggerMode,
            verticalPosition = raw.float(VERTICAL_POSITION) ?: d.verticalPosition,
            handleHeightDp = raw.int(HANDLE_HEIGHT_DP) ?: d.handleHeightDp,
            handleWidthDp = raw.int(HANDLE_WIDTH_DP) ?: d.handleWidthDp,
            showEdgeHint = raw.bool(SHOW_EDGE_HINT) ?: d.showEdgeHint,
            pillInsetDp = raw.int(PILL_INSET_DP) ?: d.pillInsetDp,
            hideOnKeyboard = raw.bool(HIDE_ON_KEYBOARD) ?: d.hideOnKeyboard,
            hideInFullscreen = raw.bool(HIDE_IN_FULLSCREEN) ?: d.hideInFullscreen,
            panelWidthFraction = raw.float(PANEL_WIDTH_FRACTION) ?: d.panelWidthFraction,
            pinnedPackages = (raw[PINNED] as? String)?.let(PinLogic::decode) ?: d.pinnedPackages,
            enabledTools = raw.stringSet(TOOLS)?.mapNotNull(QuickTool::fromId)?.toSet() ?: d.enabledTools,
            blocklist = raw.stringSet(BLOCKLIST) ?: d.blocklist,
        ).normalized()
    }

    fun encode(settings: EdgeBarSettings): Map<String, Any> {
        val s = settings.normalized()
        return mapOf(
            HANDLE_ENABLED to s.handleEnabled,
            SIDE to s.side.name,
            TRIGGER_MODE to s.triggerMode.name,
            VERTICAL_POSITION to s.verticalPosition,
            HANDLE_HEIGHT_DP to s.handleHeightDp,
            HANDLE_WIDTH_DP to s.handleWidthDp,
            SHOW_EDGE_HINT to s.showEdgeHint,
            PILL_INSET_DP to s.pillInsetDp,
            HIDE_ON_KEYBOARD to s.hideOnKeyboard,
            HIDE_IN_FULLSCREEN to s.hideInFullscreen,
            PANEL_WIDTH_FRACTION to s.panelWidthFraction,
            PINNED to PinLogic.encode(s.pinnedPackages),
            TOOLS to s.enabledTools.map { it.id }.toSet(),
            BLOCKLIST to s.blocklist,
        )
    }

    private fun Map<String, Any?>.bool(key: String) = this[key] as? Boolean
    private fun Map<String, Any?>.int(key: String) = this[key] as? Int
    private fun Map<String, Any?>.float(key: String) = (this[key] as? Float)?.takeIf { it.isFinite() }
    private fun Map<String, Any?>.stringSet(key: String): Set<String>? =
        (this[key] as? Set<*>)?.filterIsInstance<String>()?.toSet()

    private inline fun <reified E : Enum<E>> Map<String, Any?>.enumOf(key: String): E? =
        (this[key] as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
}
