package com.local.edgebar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test fun `empty store decodes to defaults`() {
        assertEquals(EdgeBarSettings(), SettingsCodec.decode(emptyMap()))
    }

    @Test fun `defaults match the spec`() {
        val d = EdgeBarSettings()
        assertEquals(16, d.handleWidthDp)
        assertEquals(160, d.handleHeightDp)
        assertEquals(40, EdgeBarSettings.OPEN_THRESHOLD_DP)
        assertEquals(200, EdgeBarSettings.MAX_HEIGHT_DP)
        assertTrue(d.panelWidthFraction in 0.25f..0.30f)
        assertEquals(QuickTool.entries.toSet(), d.enabledTools)
    }

    @Test fun `round trip preserves every field`() {
        val s = EdgeBarSettings(
            handleEnabled = false,
            side = Side.LEFT,
            triggerMode = TriggerMode.PILL,
            verticalPosition = 0.8f,
            handleHeightDp = 120,
            handleWidthDp = 24,
            showEdgeHint = false,
            pillInsetDp = 20,
            hideOnKeyboard = false,
            hideInFullscreen = false,
            panelWidthFraction = 0.25f,
            pinnedPackages = listOf("com.b.app", "com.a.app"),
            enabledTools = setOf(QuickTool.TORCH, QuickTool.LOCK),
            blocklist = setOf("com.google.android.youtube"),
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test fun `encoded values only use DataStore-compatible types`() {
        SettingsCodec.encode(EdgeBarSettings()).values.forEach { v ->
            assertTrue("unsupported ${v::class}", v is Boolean || v is Int || v is Float || v is String || v is Set<*>)
        }
    }

    @Test fun `out of range values are clamped`() {
        val s = SettingsCodec.decode(
            mapOf(
                SettingsCodec.HANDLE_HEIGHT_DP to 999,
                SettingsCodec.HANDLE_WIDTH_DP to 1,
                SettingsCodec.VERTICAL_POSITION to 4f,
                SettingsCodec.PANEL_WIDTH_FRACTION to 0.9f,
                SettingsCodec.PILL_INSET_DP to -5,
            ),
        )
        assertEquals(EdgeBarSettings.MAX_HEIGHT_DP, s.handleHeightDp)
        assertEquals(EdgeBarSettings.MIN_WIDTH_DP, s.handleWidthDp)
        assertEquals(1f, s.verticalPosition)
        assertEquals(EdgeBarSettings.MAX_PANEL_FRACTION, s.panelWidthFraction)
        assertEquals(0, s.pillInsetDp)
    }

    @Test fun `wrong types and unknown enums fall back to defaults`() {
        val s = SettingsCodec.decode(
            mapOf(
                SettingsCodec.SIDE to "TOP",
                SettingsCodec.TRIGGER_MODE to 3,
                SettingsCodec.HANDLE_HEIGHT_DP to "tall",
                SettingsCodec.VERTICAL_POSITION to Float.NaN,
                SettingsCodec.TOOLS to setOf("torch", "teleport"),
            ),
        )
        val d = EdgeBarSettings()
        assertEquals(d.side, s.side)
        assertEquals(d.triggerMode, s.triggerMode)
        assertEquals(d.handleHeightDp, s.handleHeightDp)
        assertEquals(d.verticalPosition, s.verticalPosition)
        assertEquals(setOf(QuickTool.TORCH), s.enabledTools)
    }

    @Test fun `an explicitly empty tool set stays empty`() {
        val s = SettingsCodec.decode(mapOf(SettingsCodec.TOOLS to emptySet<String>()))
        assertTrue(s.enabledTools.isEmpty())
    }

    @Test fun `normalized is idempotent and drops invalid list entries`() {
        val s = EdgeBarSettings(
            pinnedPackages = listOf("com.a.app", "com.a.app", "bad"),
            blocklist = setOf("com.game.x", " "),
        ).normalized()
        assertEquals(listOf("com.a.app"), s.pinnedPackages)
        assertEquals(setOf("com.game.x"), s.blocklist)
        assertEquals(s, s.normalized())
    }

    @Test fun `ordered tools follow enum order`() {
        val s = EdgeBarSettings(enabledTools = setOf(QuickTool.LOCK, QuickTool.CALCULATOR))
        assertEquals(listOf(QuickTool.CALCULATOR, QuickTool.LOCK), s.orderedTools)
    }
}
