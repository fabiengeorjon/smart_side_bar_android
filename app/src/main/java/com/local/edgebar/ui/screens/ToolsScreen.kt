package com.local.edgebar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.QuickTool
import com.local.edgebar.ui.BodyText
import com.local.edgebar.ui.EdgeCard
import com.local.edgebar.ui.Gap
import com.local.edgebar.ui.SectionLabel
import com.local.edgebar.ui.SettingsUpdate
import com.local.edgebar.ui.SliderRow
import com.local.edgebar.ui.SwitchRow
import kotlin.math.roundToInt

private fun QuickTool.title() = when (this) {
    QuickTool.CALCULATOR -> "Calculator"
    QuickTool.TORCH -> "Torch"
    QuickTool.SCREENSHOT -> "Screenshot"
    QuickTool.LOCK -> "Lock screen"
}

private fun QuickTool.subtitle() = when (this) {
    QuickTool.CALCULATOR -> "Opens your default calculator app"
    QuickTool.TORCH -> "Toggles the flashlight; the tile shows its state"
    QuickTool.SCREENSHOT -> "Closes the sidebar first, then takes the screenshot"
    QuickTool.LOCK -> "Turns the screen off and locks the phone"
}

@Composable
fun ToolsScreen(s: EdgeBarSettings, update: SettingsUpdate) {
    var fraction by remember(s.panelWidthFraction) { mutableFloatStateOf(s.panelWidthFraction) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionLabel("Quick tools")
        EdgeCard {
            QuickTool.entries.forEach { tool ->
                SwitchRow(tool.title(), tool in s.enabledTools, tool.subtitle()) { on ->
                    update { cur -> cur.copy(enabledTools = if (on) cur.enabledTools + tool else cur.enabledTools - tool) }
                }
            }
        }
        SectionLabel("Sidebar")
        EdgeCard {
            SliderRow(
                "Width", "${(fraction * 100).roundToInt()}%", fraction,
                EdgeBarSettings.MIN_PANEL_FRACTION..EdgeBarSettings.MAX_PANEL_FRACTION,
                onChange = { fraction = it },
                onFinished = { update { it.copy(panelWidthFraction = fraction) } },
            )
        }
        BodyText("25–30% of the screen width, never narrower than 88dp.")
        Gap(32.dp)
    }
}
