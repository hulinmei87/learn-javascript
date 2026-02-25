package com.bossassistant.plugin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object AutomationOrchestrator {
    private const val ALARM_REQUEST_CODE = 3001

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutomationAlarmReceiver::class.java).apply {
            action = ACTION_RUN_ROUND
            `package` = context.packageName
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleNext(context: Context, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            alarmPendingIntent(context)
        )
    }

    private fun intervalMs(config: AutomationConfig): Long =
        (config.intervalMinutes.coerceAtLeast(1) * 60_000L)

    fun start(context: Context, runImmediately: Boolean) {
        ConfigStore.setEnabled(context, true)
        val config = ConfigStore.load(context)
        if (runImmediately) {
            dispatchRunRound(context, source = "manual_start")
            scheduleNext(context, intervalMs(config))
        } else {
            scheduleNext(context, 3_000L)
        }
        ConfigStore.appendLog(context, "调度已启动，间隔 ${config.intervalMinutes} 分钟。")
        notifyStatusRefresh(context)
    }

    fun pause(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
        ConfigStore.setEnabled(context, false)
        context.sendBroadcast(Intent(ACTION_PAUSE).apply { `package` = context.packageName })
        ConfigStore.appendLog(context, "调度已暂停。")
        notifyStatusRefresh(context)
    }

    fun onAlarmTriggered(context: Context) {
        if (!ConfigStore.isEnabled(context)) {
            return
        }
        dispatchRunRound(context, source = "alarm")
        val config = ConfigStore.load(context)
        scheduleNext(context, intervalMs(config))
    }

    private fun dispatchRunRound(context: Context, source: String) {
        context.sendBroadcast(Intent(ACTION_RUN_ROUND).apply {
            `package` = context.packageName
            putExtra("source", source)
        })
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
            .apply()
    }

    private fun notifyStatusRefresh(context: Context) {
        context.sendBroadcast(Intent(ACTION_REFRESH_STATUS).apply {
            `package` = context.packageName
        })
    }
}
