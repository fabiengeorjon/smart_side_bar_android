package com.local.edgebar.ui.screens

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.edgebar.service.EdgeBarAccessibilityService
import com.local.edgebar.ui.BodyText
import com.local.edgebar.ui.EdgeButton
import com.local.edgebar.ui.EdgeCard
import com.local.edgebar.ui.Gap
import com.local.edgebar.ui.SectionLabel
import com.local.edgebar.ui.theme.DotoLabel
import com.local.edgebar.ui.theme.EdgeColors

private const val ACTION_A11Y_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

/** Opens Edge Bar's own accessibility page where supported, else the accessibility list. */
fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, EdgeBarAccessibilityService::class.java).flattenToString()
    val details = Intent(ACTION_A11Y_DETAILS)
        .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: SecurityException) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
fun SetupScreen(serviceEnabled: Boolean) {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EdgeCard {
            Gap()
            Text(
                if (serviceEnabled) "SERVICE ON" else "SERVICE OFF",
                style = DotoLabel.copy(fontSize = 20.sp, color = if (serviceEnabled) EdgeColors.White else EdgeColors.Accent),
            )
            Gap(6.dp)
            BodyText(
                if (serviceEnabled) {
                    "Edge Bar is running. Swipe inward from the handle on the screen edge to open the sidebar."
                } else {
                    "Edge Bar needs its accessibility service to draw the edge handle and sidebar."
                },
            )
            Gap()
            EdgeButton(if (serviceEnabled) "Accessibility settings" else "Enable Edge Bar", primary = !serviceEnabled) {
                openAccessibilitySettings(context)
            }
            Gap()
        }

        SectionLabel("Sideloaded? Allow restricted settings")
        EdgeCard {
            Gap(8.dp)
            BodyText(
                "Android 13 and later block accessibility access for apps installed from a downloaded APK file. " +
                    "If the Edge Bar switch is greyed out or shows \"Restricted setting\":",
            )
            Gap(8.dp)
            BodyText(
                "1. Tap the greyed-out Edge Bar entry once, then close the dialog.\n" +
                    "2. Open App info below.\n" +
                    "3. Tap ⋮ (top right) → Allow restricted settings, and confirm.\n" +
                    "4. Go back to Accessibility → Edge Bar and turn it on.",
                color = EdgeColors.White,
            )
            Gap(8.dp)
            BodyText("Apps installed with `adb install` are not restricted, so this step is usually not needed there.")
            Gap()
            EdgeButton("Open App info", primary = false) { openAppInfo(context) }
            Gap()
        }

        SectionLabel("Gesture tip")
        EdgeCard {
            Gap(8.dp)
            BodyText(
                "Swipe inward about 40dp from the handle. If the system Back gesture fires instead, open HANDLE and " +
                    "switch the trigger to Visible pill: it sits slightly inward and also opens on a tap.",
            )
            Gap()
        }

        SectionLabel("Privacy")
        EdgeCard {
            Gap(8.dp)
            BodyText(
                "No internet permission, analytics, ads or accounts. Edge Bar cannot read screen content " +
                    "(canRetrieveWindowContent=false). Settings stay on this phone.",
            )
            Gap()
            EdgeButton("Font license (Doto, SIL OFL 1.1)", primary = false) { showLicense = true }
            Gap()
        }
        Gap(32.dp)
    }

    if (showLicense) {
        val text = remember {
            runCatching { context.assets.open("licenses/doto-OFL.txt").bufferedReader().use { it.readText() } }
                .getOrDefault("License file missing.")
        }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close", color = EdgeColors.White) } },
            title = { Text("Doto font license") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, fontSize = 11.sp, lineHeight = 15.sp) }
            },
            containerColor = EdgeColors.Surface,
        )
    }
}

