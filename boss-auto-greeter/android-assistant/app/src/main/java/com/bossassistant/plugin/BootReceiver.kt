package com.bossassistant.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && ConfigStore.isEnabled(context)) {
            AutomationOrchestrator.start(context, runImmediately = false)
            ConfigStore.appendLog(context, "设备开机后已恢复调度。")
        }
    }
}
