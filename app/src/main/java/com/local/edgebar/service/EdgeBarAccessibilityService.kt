package com.local.edgebar.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.local.edgebar.data.SettingsRepository
import com.local.edgebar.diag.Diagnostics
import com.local.edgebar.overlay.SpikeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** PHASE 1 SPIKE version of the service. */
class EdgeBarAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private var spike: SpikeController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val controller = SpikeController(this).also { spike = it }
        controller.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { SettingsRepository.get(this@EdgeBarAccessibilityService).settings.collect { controller.settings = it } }
        }
        Diagnostics.update { it.copy(serviceRunning = true, serviceConnectedAtMs = System.currentTimeMillis()) }
        Diagnostics.log(Diagnostics.Kind.INFO, "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope?.cancel()
        spike?.stop()
        spike = null
        Diagnostics.update { it.copy(serviceRunning = false) }
        Diagnostics.log(Diagnostics.Kind.INFO, "SERVICE_DESTROYED")
        super.onDestroy()
    }
}
