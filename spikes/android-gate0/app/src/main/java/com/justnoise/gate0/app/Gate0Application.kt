package com.justnoise.gate0.app

import android.app.Application
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.platform.notification.SessionNotifications
import com.justnoise.gate0.platform.schedule.ScheduleStartupGuard

open class Gate0Application : Application() {
    override fun onCreate() {
        super.onCreate()
        // A new process cannot inherit proof that the system-bound service is alive.
        // The service publishes a fresh heartbeat after its own connection callback.
        val preferences = Gate0Preferences(this)
        preferences.markServiceDisconnected()
        ScheduleStartupGuard.initialize(this, preferences)
        // An ordinary notification can outlive the previous process. Reconcile only
        // after startup recovery has invalidated its old readiness evidence.
        SessionNotifications.refresh(this)
    }
}
