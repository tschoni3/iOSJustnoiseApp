package com.justnoise.gate0.app

import android.app.Application
import com.justnoise.gate0.data.Gate0Preferences

class Gate0Application : Application() {
    override fun onCreate() {
        super.onCreate()
        // A new process cannot inherit proof that the system-bound service is alive.
        // The service publishes a fresh heartbeat after its own connection callback.
        Gate0Preferences(this).markServiceDisconnected()
    }
}
