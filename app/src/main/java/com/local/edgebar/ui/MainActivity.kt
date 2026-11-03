package com.local.edgebar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.data.SettingsRepository
import com.local.edgebar.ui.screens.AppsScreen
import com.local.edgebar.ui.screens.DiagnosticsScreen
import com.local.edgebar.ui.screens.HandleScreen
import com.local.edgebar.ui.screens.HideListScreen
import com.local.edgebar.ui.screens.SetupScreen
import com.local.edgebar.ui.screens.ToolsScreen
import com.local.edgebar.ui.theme.DotoLabel
import com.local.edgebar.ui.theme.EdgeBarTheme
import com.local.edgebar.ui.theme.EdgeColors
import kotlinx.coroutines.launch

/** Single-activity companion app: setup, handle, apps, tools, hide list, diagnostics. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EdgeBarTheme { EdgeBarApp() } }
    }
}

enum class Tab(val label: String) { SETUP("SETUP"), HANDLE("HANDLE"), APPS("APPS"), TOOLS("TOOLS"), HIDE("HIDE"), DIAG("DIAG") }

typealias SettingsUpdate = ((EdgeBarSettings) -> EdgeBarSettings) -> Unit

@Composable
private fun EdgeBarApp() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    var serviceEnabled by remember { mutableStateOf(ServiceStatus.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = ServiceStatus.isEnabled(context)
        onPauseOrDispose { }
    }
    var tab by rememberSaveable { mutableStateOf(if (serviceEnabled) Tab.HANDLE else Tab.SETUP) }
    val scope = rememberCoroutineScope()
    val update: SettingsUpdate = { transform -> scope.launch { repo.update(transform) } }

    Column(Modifier.fillMaxSize().background(EdgeColors.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("EDGE BAR", style = DotoLabel.copy(fontSize = 22.sp, letterSpacing = 4.sp), modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (serviceEnabled) EdgeColors.White else EdgeColors.Accent))
            Spacer(Modifier.width(8.dp))
            Text(if (serviceEnabled) "ON" else "OFF", style = DotoLabel.copy(color = if (serviceEnabled) EdgeColors.White else EdgeColors.Accent))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                Column(
                    Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable(role = Role.Tab) { tab = t }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.label, style = DotoLabel.copy(fontSize = 13.sp, color = if (selected) EdgeColors.White else EdgeColors.Secondary))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) EdgeColors.White else EdgeColors.Black))
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            val s = settings
            if (s == null) return@Box
            when (tab) {
                Tab.SETUP -> SetupScreen(serviceEnabled)
                Tab.HANDLE -> HandleScreen(s, update)
                Tab.APPS -> AppsScreen(s, update)
                Tab.TOOLS -> ToolsScreen(s, update)
                Tab.HIDE -> HideListScreen(s, update)
                Tab.DIAG -> DiagnosticsScreen(s, serviceEnabled)
            }
        }
    }
}
