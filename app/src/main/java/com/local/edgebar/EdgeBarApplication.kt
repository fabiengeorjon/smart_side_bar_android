package com.local.edgebar

import android.app.Application
import com.local.edgebar.diag.CrashLog

class EdgeBarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
