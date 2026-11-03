package com.local.edgebar.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.local.edgebar.data.SettingsRepository
import com.local.edgebar.diag.Diagnostics
import com.local.edgebar.overlay.OverlayController

/**
 * The app's only long-running component. All UI is delegated to [OverlayController];
 * this class only forwards lifecycle signals. Settings live in DataStore, so after a
 * reboot or a service restart the system rebinds the service and the controller
 * restores the same handle from disk.
 */
class EdgeBarAccessibilityService : AccessibilityService() {
    private var controller: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller?.stop()
        controller = OverlayController(this, SettingsRepository.get(this)).also { it.start() }
        Diagnostics.update { it.copy(serviceRunning = true, serviceConnectedAtMs = System.currentTimeMillis()) }
        Diagnostics.log(Diagnostics.Kind.INFO, "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) controller?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        controller?.onTrimMemory(level)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown("SERVICE_UNBOUND")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown("SERVICE_DESTROYED")
        super.onDestroy()
    }

    private fun shutdown(reason: String) {
        val c = controller ?: return
        controller = null
        c.stop()
        Diagnostics.update { it.copy(serviceRunning = false) }
        Diagnostics.log(Diagnostics.Kind.INFO, reason)
    }
}
