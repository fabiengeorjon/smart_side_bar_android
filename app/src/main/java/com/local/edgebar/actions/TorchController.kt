package com.local.edgebar.actions

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TorchState(val available: Boolean, val on: Boolean)

/**
 * Torch via CameraManager.setTorchMode (no CAMERA permission needed). The torch callback
 * is registered only while the panel is open, so the idle service holds no camera
 * listener.
 */
class TorchController(context: Context) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val _state = MutableStateFlow(TorchState(available = false, on = false))
    val state: StateFlow<TorchState> = _state
    private var listening = false

    private val cameraId: String? by lazy {
        runCatching {
            val ids = cameraManager.cameraIdList
            fun hasFlash(id: String) =
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            fun isBack(id: String) =
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            ids.firstOrNull { hasFlash(it) && isBack(it) } ?: ids.firstOrNull { hasFlash(it) }
        }.getOrNull()
    }

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) _state.value = TorchState(available = true, on = enabled)
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) _state.value = TorchState(available = false, on = false)
        }
    }

    fun startListening(handler: Handler) {
        if (listening) return
        if (cameraId == null) {
            _state.value = TorchState(available = false, on = false)
            return
        }
        // Registration immediately reports the current state through the callback.
        cameraManager.registerTorchCallback(callback, handler)
        listening = true
    }

    fun stopListening() {
        if (!listening) return
        runCatching { cameraManager.unregisterTorchCallback(callback) }
        listening = false
    }

    /** @throws IllegalStateException if there is no flash; CameraAccessException if busy. */
    fun toggle() {
        val id = cameraId ?: throw IllegalStateException("no camera with a flash unit")
        cameraManager.setTorchMode(id, !_state.value.on)
    }
}
