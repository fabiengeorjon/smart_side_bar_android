package com.local.edgebar.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ComponentCallbacks2
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.local.edgebar.actions.TorchController
import com.local.edgebar.core.Box
import com.local.edgebar.core.EdgeBarSettings
import com.local.edgebar.core.HandleGeometry
import com.local.edgebar.core.HandleLayout
import com.local.edgebar.core.PanelGeometry
import com.local.edgebar.core.PinLogic
import com.local.edgebar.core.QuickTool
import com.local.edgebar.core.Side
import com.local.edgebar.data.AppCatalog
import com.local.edgebar.data.AppInfoCache
import com.local.edgebar.data.SettingsRepository
import com.local.edgebar.diag.Diagnostics
import com.local.edgebar.diag.Diagnostics.Kind
import com.local.edgebar.ui.MainActivity
import com.local.edgebar.ui.panel.PanelActions
import com.local.edgebar.ui.panel.PanelApp
import com.local.edgebar.ui.panel.PanelContent
import com.local.edgebar.ui.panel.PanelInsets
import com.local.edgebar.ui.panel.PanelUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns every overlay window of the service: the edge handle, the insets probe and the
 * per-opening PanelSession. Lifecycle: [start] when the service connects, [stop] when it
 * unbinds or is destroyed. stop() removes all windows and cancels every callback,
 * listener and coroutine it started.
 *
 * Idle cost: two tiny windows (handle + 1x1 probe), one settings Flow collector and a
 * DisplayListener. There are no timers, no polling and no Compose hierarchy while the
 * panel is closed.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val repo: SettingsRepository,
) : EdgeHandleView.Listener {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val displayManager = service.getSystemService(DisplayManager::class.java)
    private val pm = service.packageManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = AppInfoCache()
    private val torch = TorchController(service)
    private val density: Float get() = service.resources.displayMetrics.density

    private var started = false
    private var settings = EdgeBarSettings()
    private var settingsLoaded = false
    private var eventsSubscribed: Boolean? = null

    private var handle: EdgeHandleView? = null
    private var handleDensity = 0f
    private var lastHandleLog: String? = null
    private var probe: InsetsProbe? = null
    private var panel: PanelSession? = null
    private val panelJobs = mutableListOf<Job>()
    private var prefetchJob: Job? = null

    private var keyboardVisible = false
    private var fullscreen = false
    private var foregroundPackage: String? = null
    private var hiddenForScreenshot = false
    private var lastRotation = -1
    private var lastBounds: android.graphics.Rect? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onGeometryMaybeChanged()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    fun start() {
        if (started) return
        started = true
        addProbe()
        displayManager.registerDisplayListener(displayListener, mainHandler)
        rememberGeometry()
        scope.launch { repo.settings.collect { applySettings(it) } }
    }

    fun stop() {
        if (!started) return
        started = false
        panel?.close(animated = false)
        panel = null
        cancelPanelJobs()
        prefetchJob?.cancel()
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        torch.stopListening()
        OverlayWindows.safeRemove(wm, handle)
        OverlayWindows.safeRemove(wm, probe)
        handle = null
        probe = null
        cache.clear()
        Diagnostics.update { it.copy(handleShown = false, handleHiddenReason = "service stopped") }
    }

    fun onConfigurationChanged() {
        if (!started) return
        if (handle != null && handleDensity != density) {
            // Display size / density changed: rebuild the view so dp maths stay right.
            OverlayWindows.safeRemove(wm, handle)
            handle = null
        }
        onGeometryMaybeChanged(force = true)
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.clear()
    }

    /** Called for TYPE_WINDOW_STATE_CHANGED, which we subscribe to only if the hide list is non-empty. */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !event.isFullScreen) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == service.packageName || pkg == SYSTEM_UI) return
        if (pkg != foregroundPackage) {
            foregroundPackage = pkg
            refreshHandle()
        }
    }

    // ------------------------------------------------------------------ settings & geometry

    private fun applySettings(s: EdgeBarSettings) {
        val sideChanged = settings.side != s.side
        settings = s
        settingsLoaded = true
        updateEventSubscription(s.blocklist.isNotEmpty())
        if (sideChanged) panel?.close(animated = false)
        refreshHandle()
    }

    /** Only receive window-state events while there is a hide list that needs them. */
    private fun updateEventSubscription(wanted: Boolean) {
        if (eventsSubscribed == wanted) return
        val info = service.serviceInfo ?: return
        info.eventTypes = if (wanted) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
        service.serviceInfo = info
        eventsSubscribed = wanted
        if (!wanted) foregroundPackage = null
    }

    private fun rememberGeometry(): Boolean {
        val rotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        val bounds = wm.currentWindowMetrics.bounds
        val changed = rotation != lastRotation || bounds != lastBounds
        lastRotation = rotation
        lastBounds = bounds
        return changed
    }

    /** DisplayListener also fires for unrelated changes, so compare before doing work. */
    private fun onGeometryMaybeChanged(force: Boolean = false) {
        if (!started) return
        if (rememberGeometry() || force) {
            panel?.close(animated = false)
            refreshHandle()
        }
    }

    private fun computeHandleLayout(): HandleLayout {
        val s = settings
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets
        val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val cutouts = insets.displayCutout?.boundingRects.orEmpty().map { Box(it.left, it.top, it.right, it.bottom) }
        val d = density
        return HandleGeometry.compute(
            screenWidth = metrics.bounds.width(),
            screenHeight = metrics.bounds.height(),
            side = s.side,
            mode = s.triggerMode,
            verticalPosition = s.verticalPosition,
            heightPx = (s.handleHeightDp * d).roundToInt(),
            widthPx = (s.handleWidthDp * d).roundToInt(),
            pillInsetPx = (s.pillInsetDp * d).roundToInt(),
            safeTop = bars.top,
            safeBottom = bars.bottom,
            cutouts = cutouts,
        )
    }

    // ------------------------------------------------------------------ handle

    private fun hiddenReason(): String? {
        val s = settings
        return when {
            !s.handleEnabled -> "handle disabled in settings"
            hiddenForScreenshot -> "hidden while taking a screenshot"
            s.hideOnKeyboard && keyboardVisible -> "keyboard is open"
            s.hideInFullscreen && fullscreen -> "full-screen app"
            foregroundPackage?.let { it in s.blocklist } == true -> "hidden in $foregroundPackage (hide list)"
            else -> null
        }
    }

    private fun refreshHandle() {
        if (!started || !settingsLoaded) return
        val reason = hiddenReason()
        if (reason != null) {
            // Remove the window entirely: no touch area and no gesture-exclusion rect left behind.
            OverlayWindows.safeRemove(wm, handle)?.let { overlayError("could not remove handle", it) }
            Diagnostics.update { it.copy(handleShown = false, handleHiddenReason = reason) }
            return
        }
        val s = settings
        val layout = computeHandleLayout()
        val view = handle ?: EdgeHandleView(service, this).also {
            handle = it
            handleDensity = density
        }
        view.configure(s.side, s.triggerMode, s.showEdgeHint)
        val lp = OverlayWindows.handle(s.side, layout.edgeOffset, layout.top, layout.width, layout.height)
        try {
            if (OverlayWindows.isAdded(view)) wm.updateViewLayout(view, lp) else wm.addView(view, lp)
        } catch (t: Throwable) {
            overlayError("could not show handle", t)
            Diagnostics.update { it.copy(handleShown = false, handleHiddenReason = "overlay error: ${t.message}") }
            return
        }
        val screenW = wm.currentWindowMetrics.bounds.width()
        val screenH = wm.currentWindowMetrics.bounds.height()
        val b = layout.screenBounds(s.side, screenW)
        val line = "HANDLE_LAYOUT side=${s.side} mode=${s.triggerMode} left=${b.left} top=${b.top} " +
            "right=${b.right} bottom=${b.bottom} screen=${screenW}x$screenH"
        if (line != lastHandleLog) {
            lastHandleLog = line
            Diagnostics.log(Kind.INFO, line)
        }
        val d = density
        Diagnostics.update {
            it.copy(
                handleShown = true,
                handleHiddenReason = null,
                handleBoundsPx = "x ${b.left}–${b.right}, y ${b.top}–${b.bottom} on ${screenW}×$screenH (${s.side}, ${s.triggerMode})",
                handleSizeDp = "%.0f × %.0f dp".format(layout.width / d, layout.height / d),
            )
        }
    }

    private fun addProbe() {
        val view = InsetsProbe(service) { ime, fs ->
            if (ime != keyboardVisible || fs != fullscreen) {
                keyboardVisible = ime
                fullscreen = fs
                Diagnostics.update { it.copy(keyboardVisible = ime, fullscreen = fs) }
                refreshHandle()
            }
        }
        try {
            wm.addView(view, OverlayWindows.insetsProbe())
            probe = view
        } catch (t: Throwable) {
            overlayError("insets probe unavailable; keyboard/full-screen hiding disabled", t)
        }
    }

    // ------------------------------------------------------------------ EdgeHandleView.Listener

    override fun onHandleGrabbed() {
        // Warm the icon cache while the finger is still moving; it shortens the first open.
        val pins = settings.pinnedPackages.filter { cache.icon(it) == null }
        if (pins.isEmpty() || prefetchJob?.isActive == true) return
        prefetchJob = scope.launch { withContext(Dispatchers.IO) { pins.forEach { loadIntoCache(it) } } }
    }

    override fun onOpenGesture(source: String, triggerNanos: Long, detail: String) {
        Diagnostics.gesture("OPEN source=$source $detail")
        openPanel(source, triggerNanos)
    }

    override fun onGestureRejected(reason: String) = Diagnostics.gesture("NO_OPEN $reason")

    // ------------------------------------------------------------------ panel

    private fun openPanel(source: String, triggerNanos: Long) {
        if (panel != null || !started) return
        val s = settings
        val metrics = wm.currentWindowMetrics
        val d = density
        val width = PanelGeometry.panelWidthPx(metrics.bounds.width(), d, s.panelWidthFraction)
        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val insets = PanelInsets(
            top = (bars.top / d).dp,
            bottom = (bars.bottom / d).dp,
            left = if (s.side == Side.LEFT) (bars.left / d).dp else 0.dp,
            right = if (s.side == Side.RIGHT) (bars.right / d).dp else 0.dp,
        )
        val ui = PanelUiState(s.side, s.orderedTools, insets)
        // Synchronous fill from cache so a warm open renders complete in its first frame.
        val cached = s.pinnedPackages.map { pkg ->
            PanelApp(pkg, cache.label(pkg) ?: "", cache.icon(pkg)?.bitmap?.asImageBitmap())
        }
        ui.apps = cached
        ui.appsLoaded = cached.isNotEmpty() && cached.all { it.icon != null && it.label.isNotEmpty() }
        ui.torch = torch.state.value

        lateinit var session: PanelSession
        session = PanelSession(service, wm, s.side, width, triggerNanos, object : PanelSession.Callbacks {
            override fun onFirstVisibleFrame(latencyMs: Double) = Diagnostics.latency(latencyMs, source)
            override fun onClosed() {
                if (panel === session) panel = null
                cancelPanelJobs()
                torch.stopListening()
                Diagnostics.log(Kind.INFO, "PANEL_CLOSED")
            }
            override fun onOverlayError(message: String, t: Throwable?) = overlayError(message, t)
        })
        panel = session
        if (!session.show { PanelContent(ui, panelActions) }) {
            panel = null
            Toast.makeText(service, "Edge Bar could not open its panel", Toast.LENGTH_SHORT).show()
            return
        }
        if (QuickTool.TORCH in s.enabledTools) {
            torch.startListening(mainHandler)
            panelJobs += scope.launch { torch.state.collect { ui.torch = it } }
        }
        if (!ui.appsLoaded) panelJobs += scope.launch { loadPinned(ui, s.pinnedPackages) }
    }

    private fun cancelPanelJobs() {
        panelJobs.forEach { it.cancel() }
        panelJobs.clear()
    }

    /** Off the main thread: resolves labels/icons, and prunes pins whose app is gone. */
    private suspend fun loadPinned(ui: PanelUiState, pins: List<String>) {
        val (apps, gone) = withContext(Dispatchers.IO) {
            val gone = mutableListOf<String>()
            val apps = pins.mapNotNull { pkg ->
                if (!AppCatalog.isLaunchable(pm, pkg)) {
                    gone += pkg
                    null
                } else {
                    loadIntoCache(pkg)
                    PanelApp(pkg, cache.label(pkg) ?: pkg, cache.icon(pkg)?.bitmap?.asImageBitmap())
                }
            }
            apps to gone
        }
        ui.apps = apps
        ui.appsLoaded = true
        if (gone.isNotEmpty()) removeStalePins(gone)
    }

    private fun loadIntoCache(pkg: String) {
        if (cache.label(pkg) == null) AppCatalog.label(pm, pkg)?.let { cache.putLabel(pkg, it) }
        if (cache.icon(pkg) == null) {
            AppCatalog.loadIcon(pm, pkg, (ICON_DP * density).roundToInt())?.let { cache.putIcon(pkg, it) }
        }
    }

    private fun removeStalePins(gone: List<String>) {
        gone.forEach(cache::invalidate)
        Diagnostics.log(Kind.INFO, "Removed pins for apps no longer installed: ${gone.joinToString()}")
        scope.launch {
            repo.update { s -> s.copy(pinnedPackages = PinLogic.prune(s.pinnedPackages) { it !in gone }.kept) }
        }
    }

    // ------------------------------------------------------------------ actions

    private val panelActions = object : PanelActions {
        override fun launch(packageName: String) = closePanelThen { launchApp(packageName) }

        override fun runTool(tool: QuickTool) {
            when (tool) {
                QuickTool.CALCULATOR -> closePanelThen { launchCalculator() }
                QuickTool.TORCH -> toggleTorch()
                QuickTool.SCREENSHOT -> takeScreenshot()
                QuickTool.LOCK -> closePanelThen {
                    if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
                        fail("Could not lock the screen", Kind.OVERLAY_ERROR, "GLOBAL_ACTION_LOCK_SCREEN returned false")
                    }
                }
            }
        }

        override fun openSettings() = closePanelThen {
            startActivitySafely(
                Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                "Edge Bar settings",
            )
        }
    }

    /** Removes the panel windows synchronously, then runs [action]. */
    private fun closePanelThen(action: () -> Unit) {
        val p = panel
        if (p == null) action() else p.close(animated = false, then = action)
    }

    private fun launchApp(pkg: String) {
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            fail("App is not available", Kind.LAUNCH_FAILURE, "$pkg: no launch intent (uninstalled or disabled)")
            if (AppCatalog.label(pm, pkg) == null) removeStalePins(listOf(pkg))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startActivitySafely(intent, pkg)) Diagnostics.log(Kind.INFO, "LAUNCH $pkg")
    }

    private fun launchCalculator() {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivitySafely(intent, "calculator (CATEGORY_APP_CALCULATOR)", userMessage = "No calculator app found")
    }

    private fun startActivitySafely(intent: Intent, what: String, userMessage: String = "Could not open $what"): Boolean =
        try {
            service.startActivity(intent)
            true
        } catch (t: Exception) {
            fail(userMessage, Kind.LAUNCH_FAILURE, "start $what failed", t)
            false
        }

    private fun toggleTorch() {
        try {
            torch.toggle()
        } catch (t: Exception) {
            fail("Torch unavailable", Kind.OVERLAY_ERROR, "torch toggle failed", t)
        }
    }

    private fun takeScreenshot() {
        hiddenForScreenshot = true
        refreshHandle()
        closePanelThen {
            // Windows are already removed; give the compositor a few frames to drop them.
            mainHandler.postDelayed({
                if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) {
                    fail("Screenshot failed", Kind.OVERLAY_ERROR, "GLOBAL_ACTION_TAKE_SCREENSHOT returned false")
                }
                mainHandler.postDelayed({
                    hiddenForScreenshot = false
                    refreshHandle()
                }, SCREENSHOT_RESTORE_MS)
            }, SCREENSHOT_DELAY_MS)
        }
    }

    private fun fail(userMessage: String, kind: Kind, detail: String, t: Throwable? = null) {
        Diagnostics.log(kind, detail, t)
        Toast.makeText(service, userMessage, Toast.LENGTH_SHORT).show()
    }

    private fun overlayError(message: String, t: Throwable?) = Diagnostics.log(Kind.OVERLAY_ERROR, message, t)

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val ICON_DP = 38
        const val SCREENSHOT_DELAY_MS = 250L
        const val SCREENSHOT_RESTORE_MS = 1500L
    }
}
