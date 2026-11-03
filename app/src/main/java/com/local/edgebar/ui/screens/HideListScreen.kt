package com.local.edgebar.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.ui.BodyText
import com.local.edgebar.ui.SectionLabel
import com.local.edgebar.ui.SettingsUpdate

/**
 * Per-app hide list: the fallback when inset-based full-screen detection misses an app.
 * While this list is empty the service does not subscribe to any accessibility events.
 */
@Composable
fun HideListScreen(s: EdgeBarSettings, update: SettingsUpdate) {
    val apps = rememberLaunchableApps()
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionLabel("Hide the handle in these apps (${s.blocklist.size})") }
        item {
            BodyText(
                "The handle already hides in apps that hide both system bars (most videos and games). " +
                    "Add an app here if the handle still appears in it. Edge Bar only sees the package name of the " +
                    "app in front, and only while this list is not empty.",
            )
        }
        item { SectionLabel("Apps") }
        item { SearchField(query) { query = it } }
        appList(apps, query) { app ->
            ToggleRow(app, app.packageName in s.blocklist) {
                update { cur ->
                    val pkg = app.packageName
                    cur.copy(blocklist = if (pkg in cur.blocklist) cur.blocklist - pkg else cur.blocklist + pkg)
                }
            }
        }
    }
}
