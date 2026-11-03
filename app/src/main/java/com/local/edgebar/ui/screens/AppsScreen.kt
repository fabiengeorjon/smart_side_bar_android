package com.local.edgebar.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.PinLogic
import com.local.edgebar.data.AppCatalog
import com.local.edgebar.data.LaunchableApp
import com.local.edgebar.ui.AppIconImage
import com.local.edgebar.ui.BodyText
import com.local.edgebar.ui.SectionLabel
import com.local.edgebar.ui.SettingsUpdate
import com.local.edgebar.ui.SmallTextButton
import com.local.edgebar.ui.theme.EdgeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberLaunchableApps(): List<LaunchableApp>? {
    val pm = LocalContext.current.packageManager
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.IO) { AppCatalog.queryLaunchable(pm) }
    }
    return apps
}

@Composable
fun AppsScreen(s: EdgeBarSettings, update: SettingsUpdate) {
    val pm = LocalContext.current.packageManager
    val apps = rememberLaunchableApps()
    var query by rememberSaveable { mutableStateOf("") }
    val labels = remember(apps) { apps?.associate { it.packageName to it.label }.orEmpty() }

    // Clean up pins for apps that were uninstalled while the service was not looking.
    LaunchedEffect(apps) {
        if (apps == null) return@LaunchedEffect
        val stale = withContext(Dispatchers.IO) { s.pinnedPackages.filterNot { AppCatalog.isLaunchable(pm, it) } }
        if (stale.isNotEmpty()) update { cur -> cur.copy(pinnedPackages = PinLogic.prune(cur.pinnedPackages) { it !in stale }.kept) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionLabel("Pinned (${s.pinnedPackages.size})") }
        if (s.pinnedPackages.isEmpty()) {
            item { BodyText("Nothing pinned yet. Pin apps from the list below; they appear in the sidebar in this order.") }
        }
        val pins = s.pinnedPackages
        pins.forEachIndexed { index, pkg ->
            item(key = "pin:$pkg") {
                PinnedRow(
                    pm = pm,
                    packageName = pkg,
                    label = labels[pkg],
                    canUp = index > 0,
                    canDown = index < pins.lastIndex,
                    onUp = { update { it.copy(pinnedPackages = PinLogic.move(it.pinnedPackages, pkg, -1)) } },
                    onDown = { update { it.copy(pinnedPackages = PinLogic.move(it.pinnedPackages, pkg, +1)) } },
                    onRemove = { update { it.copy(pinnedPackages = PinLogic.unpin(it.pinnedPackages, pkg)) } },
                )
            }
        }
        item { SectionLabel("All apps") }
        item { SearchField(query) { query = it } }
        appList(apps, query) { app ->
            ToggleRow(app, app.packageName in s.pinnedPackages) {
                update { it.copy(pinnedPackages = PinLogic.toggle(it.pinnedPackages, app.packageName)) }
            }
        }
    }
}

@Composable
private fun PinnedRow(
    pm: PackageManager,
    packageName: String,
    label: String?,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = label ?: remember(packageName) { AppCatalog.label(pm, packageName) ?: packageName }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconImage(packageName, 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, color = EdgeColors.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        SmallTextButton("UP", enabled = canUp, onClick = onUp)
        SmallTextButton("DN", enabled = canDown, onClick = onDown)
        SmallTextButton("X", onClick = onRemove)
    }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search apps") },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EdgeColors.White,
            unfocusedBorderColor = EdgeColors.Border,
            cursorColor = EdgeColors.White,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

fun LazyListScope.appList(apps: List<LaunchableApp>?, query: String, row: @Composable (LaunchableApp) -> Unit) {
    if (apps == null) {
        item { BodyText("Loading apps…") }
        return
    }
    val q = query.trim()
    val filtered = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
    if (filtered.isEmpty()) item { BodyText("No matching apps.") }
    items(filtered, key = { "app:${it.packageName}" }) { row(it) }
}

@Composable
fun ToggleRow(app: LaunchableApp, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch, onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app.packageName, 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(app.label, color = EdgeColors.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, color = EdgeColors.Secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = EdgeColors.Black,
                checkedTrackColor = EdgeColors.White,
                uncheckedThumbColor = EdgeColors.Secondary,
                uncheckedTrackColor = EdgeColors.Black,
                uncheckedBorderColor = EdgeColors.Border,
            ),
        )
    }
}
