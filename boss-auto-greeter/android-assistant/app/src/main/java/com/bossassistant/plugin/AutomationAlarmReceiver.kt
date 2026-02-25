package com.bossassistant.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AutomationOrchestrator.onAlarmTriggered(context)
    }
}
