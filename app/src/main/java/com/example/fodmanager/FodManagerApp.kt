package com.example.fodmanager

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class FodManagerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        OneSignal.initWithContext(
            this,
            "c0064c3a-8ab3-46bf-b70e-801258316a2b"
        )
    }
}