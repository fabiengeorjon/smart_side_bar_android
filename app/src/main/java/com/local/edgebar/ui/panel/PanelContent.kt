package com.local.edgebar.ui.panel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.edgebar.actions.TorchState
import com.local.edgebar.core.QuickTool
import com.local.edgebar.core.Side
import com.local.edgebar.ui.theme.DotGlyph
import com.local.edgebar.ui.theme.Doto
import com.local.edgebar.ui.theme.EdgeColors
import com.local.edgebar.ui.theme.Glyphs

data class PanelApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/** Physical (not RTL-mirrored) insets: the panel is attached to a physical edge. */
data class PanelInsets(val top: Dp, val bottom: Dp, val left: Dp, val right: Dp)

/** Per-opening state. Lives exactly as long as one PanelSession. */
@Stable
class PanelUiState(val side: Side, val tools: List<QuickTool>, val insets: PanelInsets) {
    var apps by mutableStateOf<List<PanelApp>>(emptyList())
    var appsLoaded by mutableStateOf(false)
    var torch by mutableStateOf(TorchState(available = true, on = false))
}

interface PanelActions {
    fun launch(packageName: String)
    fun runTool(tool: QuickTool)
    fun openSettings()
}

private val labelStyle = TextStyle(color = EdgeColors.White, fontSize = 11.sp, textAlign = TextAlign.Center)
private val dotoStyle = TextStyle(fontFamily = Doto, color = EdgeColors.White, letterSpacing = 2.sp)

@Composable
fun PanelContent(state: PanelUiState, actions: PanelActions) {
    val shape = if (state.side == Side.RIGHT) {
        RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
    } else {
        RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
    }
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(EdgeColors.Black)
            .border(1.dp, EdgeColors.Border, shape)
            .absolutePadding(
                top = state.insets.top + 14.dp,
                bottom = state.insets.bottom + 14.dp,
                left = state.insets.left + 10.dp,
                right = state.insets.right + 10.dp,
            ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("EDGE", style = dotoStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = "Open Edge Bar settings") { actions.openSettings() }
                    .semantics { contentDescription = "Edge Bar settings" },
                contentAlignment = Alignment.Center,
            ) { DotGlyph(Glyphs.MENU, 16.dp, EdgeColors.White) }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.appsLoaded && state.apps.isEmpty()) {
                item { EmptyPins(onClick = actions::openSettings) }
            }
            items(state.apps, key = { it.packageName }) { app ->
                AppCell(app) { actions.launch(app.packageName) }
            }
        }

        if (state.tools.isNotEmpty()) {
            DotDivider()
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tools.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { tool ->
                            ToolCell(tool, state.torch, Modifier.weight(1f)) { actions.runTool(tool) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCell(app: PanelApp, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Open ${app.label}", onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(EdgeColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (app.icon != null) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(38.dp))
            } else {
                DotGlyph(Glyphs.APP, 18.dp, EdgeColors.Secondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        BasicText(app.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolCell(tool: QuickTool, torch: TorchState, modifier: Modifier, onClick: () -> Unit) {
    val active = tool == QuickTool.TORCH && torch.on
    val enabled = tool != QuickTool.TORCH || torch.available
    val glyph = when (tool) {
        QuickTool.CALCULATOR -> Glyphs.CALCULATOR
        QuickTool.TORCH -> Glyphs.TORCH
        QuickTool.SCREENSHOT -> Glyphs.SCREENSHOT
        QuickTool.LOCK -> Glyphs.LOCK
    }
    val shape = RoundedCornerShape(10.dp)
    val fg = if (active) EdgeColors.Black else EdgeColors.White
    Column(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (active) EdgeColors.White else EdgeColors.Surface)
            .border(1.dp, EdgeColors.Border, shape)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(role = Role.Button, onClickLabel = tool.label, onClick = onClick)
            .semantics {
                contentDescription = tool.label
                if (tool == QuickTool.TORCH) stateDescription = if (torch.on) "On" else "Off"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotGlyph(glyph, 18.dp, fg)
        Spacer(Modifier.height(4.dp))
        BasicText(tool.label, style = dotoStyle.copy(fontSize = 9.sp, letterSpacing = 1.sp, color = fg), maxLines = 1)
    }
}

@Composable
private fun EmptyPins(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("NO PINS", style = dotoStyle.copy(fontSize = 11.sp))
        Spacer(Modifier.height(4.dp))
        BasicText("Tap to choose apps", style = labelStyle.copy(color = EdgeColors.Secondary, fontSize = 10.sp))
    }
}

@Composable
private fun DotDivider() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(9) { Box(Modifier.size(2.dp).clip(RoundedCornerShape(1.dp)).background(EdgeColors.Border)) }
    }
}
