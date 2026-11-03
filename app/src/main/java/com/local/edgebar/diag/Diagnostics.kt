package com.local.edgebar.diag

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process diagnostics shared by the accessibility service and the settings activity
 * (both run in the app's single process). Event-driven only; nothing here polls.
 *
 * Lines are also written to logcat under tag "EdgeBar" in a stable key=value format that
 * scripts/phase1-gesture-test.sh parses.
 */
object Diagnostics {
    const val TAG = "EdgeBar"
    private const val MAX_EVENTS = 60
    private const val MAX_LATENCY_SAMPLES = 50

    enum class Kind { INFO, GESTURE, LATENCY, LAUNCH_FAILURE, OVERLAY_ERROR }

    data class Event(val wallTimeMs: Long, val kind: Kind, val message: String)

    data class Snapshot(
        val serviceRunning: Boolean = false,
        val serviceConnectedAtMs: Long? = null,
        val handleShown: Boolean = false,
        val handleHiddenReason: String? = null,
        val handleBoundsPx: String? = null,
        val handleSizeDp: String? = null,
        val keyboardVisible: Boolean = false,
        val fullscreen: Boolean = false,
        val lastGesture: String? = null,
        val latenciesMs: List<Double> = emptyList(),
        val events: List<Event> = emptyList(),
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun update(transform: (Snapshot) -> Snapshot) = _state.update(transform)

    fun log(kind: Kind, message: String, t: Throwable? = null) {
        when (kind) {
            Kind.LAUNCH_FAILURE, Kind.OVERLAY_ERROR -> Log.w(TAG, "${kind.name} $message", t)
            else -> Log.i(TAG, "${kind.name} $message")
        }
        val detail = if (t != null) "$message (${t.javaClass.simpleName}: ${t.message})" else message
        _state.update { s ->
            s.copy(events = (listOf(Event(System.currentTimeMillis(), kind, detail)) + s.events).take(MAX_EVENTS))
        }
    }

    fun gesture(result: String) {
        log(Kind.GESTURE, result)
        _state.update { it.copy(lastGesture = result) }
    }

    fun latency(ms: Double, source: String) {
        log(Kind.LATENCY, "OPEN_FRAME source=$source latency_ms=%.1f".format(ms))
        _state.update { it.copy(latenciesMs = (it.latenciesMs + ms).takeLast(MAX_LATENCY_SAMPLES)) }
    }

    fun clearEvents() = _state.update { it.copy(events = emptyList(), latenciesMs = emptyList(), lastGesture = null) }
}
