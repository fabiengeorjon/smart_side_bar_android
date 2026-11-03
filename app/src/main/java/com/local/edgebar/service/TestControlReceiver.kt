package com.local.edgebar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.edgebar.core.Side
import com.local.edgebar.core.TriggerMode
import com.local.edgebar.data.SettingsRepository
import com.local.edgebar.diag.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lets scripts/phase1-gesture-test.sh switch the handle edge and trigger mode over adb:
 *
 *   adb shell am broadcast -n com.local.edgebar/.service.TestControlReceiver \
 *       -a com.local.edgebar.TEST_CONFIGURE --es side LEFT --es mode EDGE
 *
 * Protected by android.permission.DUMP in the manifest, which only the adb shell and the
 * system hold, so ordinary apps cannot change Edge Bar settings through it.
 */
class TestControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val side = intent.getStringExtra("side")?.let { s -> Side.entries.firstOrNull { it.name == s } }
        val mode = intent.getStringExtra("mode")?.let { m -> TriggerMode.entries.firstOrNull { it.name == m } }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SettingsRepository.get(context).update { s ->
                    s.copy(side = side ?: s.side, triggerMode = mode ?: s.triggerMode, handleEnabled = true)
                }
                Diagnostics.log(Diagnostics.Kind.INFO, "TEST_CONFIGURE side=$side mode=$mode")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.local.edgebar.TEST_CONFIGURE"
    }
}
