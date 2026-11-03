package com.local.edgebar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.Side
import com.local.edgebar.core.TriggerMode
import com.local.edgebar.ui.BodyText
import com.local.edgebar.ui.EdgeCard
import com.local.edgebar.ui.Gap
import com.local.edgebar.ui.SectionLabel
import com.local.edgebar.ui.Segmented
import com.local.edgebar.ui.SettingsUpdate
import com.local.edgebar.ui.SliderRow
import com.local.edgebar.ui.SwitchRow
import com.local.edgebar.ui.theme.EdgeColors
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HandleScreen(s: EdgeBarSettings, update: SettingsUpdate) {
    // Local slider state drives the in-app preview continuously; the value is written to
    // DataStore when the drag ends, and the real handle on screen updates at that moment.
    var position by remember(s.verticalPosition) { mutableFloatStateOf(s.verticalPosition) }
    var height by remember(s.handleHeightDp) { mutableFloatStateOf(s.handleHeightDp.toFloat()) }
    var width by remember(s.handleWidthDp) { mutableFloatStateOf(s.handleWidthDp.toFloat()) }
    var inset by remember(s.pillInsetDp) { mutableFloatStateOf(s.pillInsetDp.toFloat()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HandlePreview(s.side, s.triggerMode, s.showEdgeHint, s.handleEnabled, position, height, width, inset)

        EdgeCard {
            SwitchRow("Show handle", s.handleEnabled) { v -> update { it.copy(handleEnabled = v) } }
        }

        SectionLabel("Edge")
        Segmented(listOf(Side.LEFT to "Left", Side.RIGHT to "Right"), s.side) { v -> update { it.copy(side = v) } }

        SectionLabel("Trigger")
        Segmented(listOf(TriggerMode.EDGE to "Edge swipe", TriggerMode.PILL to "Visible pill"), s.triggerMode) { v ->
            update { it.copy(triggerMode = v) }
        }
        BodyText(
            if (s.triggerMode == TriggerMode.EDGE) {
                "Invisible strip flush with the edge. Swipe inward 40dp. Uses gesture exclusion so Back does not fire on it."
            } else {
                "Back-gesture fallback: a thin visible pill moved slightly inward. Swipe inward or tap it."
            },
        )

        SectionLabel("Size and position")
        EdgeCard {
            SliderRow("Vertical position", "${(position * 100).roundToInt()}%", position, 0f..1f,
                onChange = { position = it }, onFinished = { update { it.copy(verticalPosition = position) } })
            SliderRow(
                "Height", "${height.roundToInt()} DP", height,
                EdgeBarSettings.MIN_HEIGHT_DP.toFloat()..EdgeBarSettings.MAX_HEIGHT_DP.toFloat(),
                onChange = { height = it }, onFinished = { update { it.copy(handleHeightDp = height.roundToInt()) } },
            )
            SliderRow(
                "Touch width", "${width.roundToInt()} DP", width,
                EdgeBarSettings.MIN_WIDTH_DP.toFloat()..EdgeBarSettings.MAX_WIDTH_DP.toFloat(),
                onChange = { width = it }, onFinished = { update { it.copy(handleWidthDp = width.roundToInt()) } },
            )
            if (s.triggerMode == TriggerMode.PILL) {
                SliderRow(
                    "Inset from edge", "${inset.roundToInt()} DP", inset,
                    EdgeBarSettings.MIN_PILL_INSET_DP.toFloat()..EdgeBarSettings.MAX_PILL_INSET_DP.toFloat(),
                    onChange = { inset = it }, onFinished = { update { it.copy(pillInsetDp = inset.roundToInt()) } },
                )
            } else {
                SwitchRow("Faint edge hint", s.showEdgeHint, "A 2dp line so you can find the handle") { v ->
                    update { it.copy(showEdgeHint = v) }
                }
            }
        }

        SectionLabel("Auto-hide")
        EdgeCard {
            SwitchRow("Hide while the keyboard is open", s.hideOnKeyboard) { v -> update { it.copy(hideOnKeyboard = v) } }
            SwitchRow("Hide in full-screen apps", s.hideInFullscreen, "Videos and games that hide both system bars") { v ->
                update { it.copy(hideInFullscreen = v) }
            }
        }
        BodyText("If an app is not detected as full-screen, add it under HIDE.", modifier = Modifier.padding(top = 8.dp))
        Gap(32.dp)
    }
}

/** Scaled phone outline showing exactly where the handle sits. */
@Composable
private fun HandlePreview(
    side: Side,
    mode: TriggerMode,
    showHint: Boolean,
    enabled: Boolean,
    position: Float,
    heightDp: Float,
    widthDp: Float,
    insetDp: Float,
) {
    val config = LocalConfiguration.current
    val screenH = config.screenHeightDp.toFloat().coerceAtLeast(1f)
    val screenW = config.screenWidthDp.toFloat().coerceAtLeast(1f)
    val previewH = 240.dp
    val previewW = previewH * (screenW / screenH)
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.width(previewW).height(previewH).semantics {
                contentDescription = "Preview: handle on the ${side.name.lowercase()} edge at ${(position * 100).roundToInt()} percent"
            },
        ) {
            val scale = size.height / screenH
            drawRoundRect(
                EdgeColors.Border,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
            if (!enabled) return@Canvas
            val top0 = 32f * scale
            val usable = size.height - top0 - 24f * scale
            val h = (heightDp * scale).coerceAtMost(usable)
            val w = max(widthDp * scale, 2.dp.toPx())
            val y = top0 + (usable - h) * position
            val off = if (mode == TriggerMode.PILL) insetDp * scale else 0f
            val x = if (side == Side.LEFT) off else size.width - off - w
            // Touch area
            drawRect(EdgeColors.White.copy(alpha = 0.12f), Offset(x, y), Size(w, h))
            // What is actually drawn on screen
            if (mode == TriggerMode.PILL) {
                val pw = 2.dp.toPx()
                drawRoundRect(EdgeColors.White, Offset(x + (w - pw) / 2, y + h * 0.15f), Size(pw, h * 0.7f), CornerRadius(pw))
            } else if (showHint) {
                val lw = 1.5.dp.toPx()
                val lx = if (side == Side.LEFT) x else x + w - lw
                drawRoundRect(EdgeColors.White.copy(alpha = 0.5f), Offset(lx, y + h * 0.2f), Size(lw, h * 0.6f), CornerRadius(lw))
            }
        }
    }
}
