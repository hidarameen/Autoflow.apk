package com.autoflow.app

import android.app.Application
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.util.Notifier

class AutoFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        // The engine must be ready before the system services start delivering events,
        // which can happen before MainActivity is ever opened.
        RuleEngine.init(this)
    }
}
