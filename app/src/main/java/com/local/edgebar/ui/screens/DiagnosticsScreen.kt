package com.local.edgebar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.diag.Diagnostics
import com.local.edgebar.ui.BodyText
import com.local.edgebar.ui.EdgeButton
import com.local.edgebar.ui.EdgeCard
import com.local.edgebar.ui.Gap
import com.local.edgebar.ui.SectionLabel
import com.local.edgebar.ui.theme.EdgeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(s: EdgeBarSettings, serviceEnabled: Boolean) {
    val d by Diagnostics.state.collectAsStateWithLifecycle()
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
    val day = remember { SimpleDateFormat("MMM d HH:mm", Locale.ROOT) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionLabel("Service") }
        item {
            EdgeCard {
                Field("Enabled in Accessibility", if (serviceEnabled) "yes" else "NO", critical = !serviceEnabled)
                Field("Running", if (d.serviceRunning) "yes" else "no", critical = serviceEnabled && !d.serviceRunning)
                Field("Connected since", d.serviceConnectedAtMs?.let { day.format(Date(it)) } ?: "—")
            }
        }
        item { SectionLabel("Handle") }
        item {
            EdgeCard {
                Field("Shown", if (d.handleShown) "yes" else "no")
                d.handleHiddenReason?.let { Field("Hidden because", it) }
                Field("Edge / trigger", "${s.side} / ${s.triggerMode}")
                Field("Configured size", "${s.handleWidthDp} × ${s.handleHeightDp} dp")
                Field("Actual size", d.handleSizeDp ?: "—")
                Field("Position (px)", d.handleBoundsPx ?: "—")
                Field("Keyboard visible", if (d.keyboardVisible) "yes" else "no")
                Field("Full-screen detected", if (d.fullscreen) "yes" else "no")
            }
        }
        item { SectionLabel("Gesture") }
        item {
            EdgeCard {
                Field("Last gesture", d.lastGesture ?: "none yet")
                val lat = d.latenciesMs
                if (lat.isEmpty()) {
                    Field("Open latency", "not measured yet")
                } else {
                    val sorted = lat.sorted()
                    Field("Open latency (last)", "%.1f ms".format(lat.last()), critical = lat.last() > 100)
                    Field(
                        "Median / max",
                        "%.1f / %.1f ms (n=%d)".format(sorted[(sorted.size - 1) / 2], sorted.last(), sorted.size),
                        critical = sorted.last() > 100,
                    )
                }
                BodyText(
                    "Latency = gesture recognised → first visible panel frame submitted for display.",
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        item { SectionLabel("Events (launch failures, overlay errors, …)") }
        if (d.events.isEmpty()) item { BodyText("No events in this session.") }
        items(d.events) { e ->
            val critical = e.kind == Diagnostics.Kind.LAUNCH_FAILURE || e.kind == Diagnostics.Kind.OVERLAY_ERROR
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(time.format(Date(e.wallTimeMs)), color = EdgeColors.Secondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.kind.name, color = if (critical) EdgeColors.Accent else EdgeColors.Secondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(e.message, color = EdgeColors.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        item {
            Gap()
            EdgeButton("Clear events", primary = false) { Diagnostics.clearEvents() }
            Gap(32.dp)
        }
    }
}

@Composable
private fun Field(name: String, value: String, critical: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(name, color = EdgeColors.Secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (critical) EdgeColors.Accent else Color.White, fontSize = 13.sp, modifier = Modifier.weight(1.3f))
    }
}
