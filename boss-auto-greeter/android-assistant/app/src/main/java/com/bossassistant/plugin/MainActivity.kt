package com.bossassistant.plugin

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var etBossPackage: EditText
    private lateinit var etKeyword: EditText
    private lateinit var etCity: EditText
    private lateinit var etLocation: EditText
    private lateinit var etSalaryMin: EditText
    private lateinit var etSalaryMax: EditText
    private lateinit var etGreeting: EditText
    private lateinit var etInterval: EditText
    private lateinit var etMaxGreetings: EditText
    private lateinit var etDedupeHours: EditText
    private lateinit var etSearchTexts: EditText
    private lateinit var etChatTexts: EditText
    private lateinit var etInputTexts: EditText
    private lateinit var etSendTexts: EditText
    private lateinit var swOcrFallback: Switch
    private lateinit var tvRecordingState: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvHistory: TextView
    private lateinit var ivLastFailure: ImageView
    private var lastFailureBitmap: Bitmap? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_STATUS) {
                refreshStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBossPackage = findViewById(R.id.etBossPackage)
        etKeyword = findViewById(R.id.etKeyword)
        etCity = findViewById(R.id.etCity)
        etLocation = findViewById(R.id.etLocation)
        etSalaryMin = findViewById(R.id.etSalaryMin)
        etSalaryMax = findViewById(R.id.etSalaryMax)
        etGreeting = findViewById(R.id.etGreeting)
        etInterval = findViewById(R.id.etInterval)
        etMaxGreetings = findViewById(R.id.etMaxGreetings)
        etDedupeHours = findViewById(R.id.etDedupeHours)
        etSearchTexts = findViewById(R.id.etSearchTexts)
        etChatTexts = findViewById(R.id.etChatTexts)
        etInputTexts = findViewById(R.id.etInputTexts)
        etSendTexts = findViewById(R.id.etSendTexts)
        swOcrFallback = findViewById(R.id.swOcrFallback)
        tvRecordingState = findViewById(R.id.tvRecordingState)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        tvHistory = findViewById(R.id.tvHistory)
        ivLastFailure = findViewById(R.id.ivLastFailure)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveConfigFromForm()
            showToast("配置已保存")
            refreshStatus()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            saveConfigFromForm()
            if (!BossAccessibilityService.isAccessibilityEnabled(this)) {
                showToast("请先开启本应用无障碍权限")
            }
            AutomationOrchestrator.start(this, runImmediately = true)
            refreshStatus()
        }

        findViewById<Button>(R.id.btnPause).setOnClickListener {
            AutomationOrchestrator.pause(this)
            refreshStatus()
        }

        findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOpenBoss).setOnClickListener {
            val pkg = etBossPackage.textString().ifBlank { "com.hpbr.bosszhipin" }
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent == null) {
                showToast("未找到目标 App：$pkg")
                return@setOnClickListener
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnRunOnce).setOnClickListener {
            saveConfigFromForm()
            sendBroadcast(Intent(ACTION_RUN_ROUND).apply {
                `package` = packageName
                putExtra("source", "manual_once")
            })
            showToast("已触发立即执行")
        }

        findViewById<Button>(R.id.btnRecordStart).setOnClickListener {
            sendBroadcast(Intent(ACTION_RECORD_START).apply { `package` = packageName })
            showToast("录制已开始，请切到 Boss App 按顺序手动点击")
            refreshStatus()
        }

        findViewById<Button>(R.id.btnRecordStop).setOnClickListener {
            sendBroadcast(Intent(ACTION_RECORD_STOP).apply { `package` = packageName })
            showToast("录制已停止")
            refreshStatus()
        }

        findViewById<Button>(R.id.btnRecordClear).setOnClickListener {
            sendBroadcast(Intent(ACTION_RECORD_CLEAR).apply { `package` = packageName })
            showToast("已清空录制规则")
            refreshStatus()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_REFRESH_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(statusReceiver) }
    }

    override fun onDestroy() {
        super.onDestroy()
        lastFailureBitmap?.recycle()
        lastFailureBitmap = null
    }

    override fun onResume() {
        super.onResume()
        loadConfigToForm()
        refreshStatus()
    }

    private fun loadConfigToForm() {
        val cfg = ConfigStore.load(this)
        etBossPackage.setText(cfg.bossPackageName)
        etKeyword.setText(cfg.keyword)
        etCity.setText(cfg.cityKeyword)
        etLocation.setText(cfg.locationKeyword)
        etSalaryMin.setText(cfg.salaryMinK.toString())
        etSalaryMax.setText(cfg.salaryMaxK.toString())
        etGreeting.setText(cfg.greetingTemplate)
        etInterval.setText(cfg.intervalMinutes.toString())
        etMaxGreetings.setText(cfg.maxGreetingsPerRound.toString())
        etDedupeHours.setText(cfg.dedupeHours.toString())
        etSearchTexts.setText(cfg.searchBoxTexts.joinToString(","))
        etChatTexts.setText(cfg.chatButtonTexts.joinToString(","))
        etInputTexts.setText(cfg.inputHintTexts.joinToString(","))
        etSendTexts.setText(cfg.sendButtonTexts.joinToString(","))
        swOcrFallback.isChecked = cfg.enableOcrFallback
    }

    private fun saveConfigFromForm() {
        val old = ConfigStore.load(this)
        val cfg = old.copy(
            bossPackageName = etBossPackage.textString().ifBlank { old.bossPackageName },
            keyword = etKeyword.textString(),
            cityKeyword = etCity.textString(),
            locationKeyword = etLocation.textString(),
            salaryMinK = etSalaryMin.textIntOr(old.salaryMinK),
            salaryMaxK = etSalaryMax.textIntOr(old.salaryMaxK),
            greetingTemplate = etGreeting.textString().ifBlank { old.greetingTemplate },
            intervalMinutes = etInterval.textIntOr(old.intervalMinutes).coerceIn(1, 24 * 60),
            maxGreetingsPerRound = etMaxGreetings.textIntOr(old.maxGreetingsPerRound).coerceIn(1, 50),
            dedupeHours = etDedupeHours.textIntOr(old.dedupeHours).coerceIn(1, 720),
            searchBoxTexts = etSearchTexts.textCsvOr(old.searchBoxTexts),
            chatButtonTexts = etChatTexts.textCsvOr(old.chatButtonTexts),
            inputHintTexts = etInputTexts.textCsvOr(old.inputHintTexts),
            sendButtonTexts = etSendTexts.textCsvOr(old.sendButtonTexts),
            enableOcrFallback = swOcrFallback.isChecked
        )
        ConfigStore.save(this, cfg)
    }

    private fun refreshStatus() {
        val enabled = ConfigStore.isEnabled(this)
        val serviceOn = BossAccessibilityService.isAccessibilityEnabled(this)
        val recordingOn = ConfigStore.isRecording(this)
        val recordingStage = ConfigStore.currentRecordingStage(this)
        val cfg = ConfigStore.load(this)
        val history = TaskHistoryStore.summary(this)
        val status = buildString {
            append("调度状态：")
            append(if (enabled) "运行中" else "已暂停")
            append("\n无障碍权限：")
            append(if (serviceOn) "已开启" else "未开启")
            append("\nOCR兜底：")
            append(if (cfg.enableOcrFallback) "开启" else "关闭")
            append("\n录制模式：")
            append(if (recordingOn) "进行中" else "未开启")
            append("\n录制阶段：")
            append(recordingStage)
        }
        tvStatus.text = status
        tvRecordingState.text = "录制状态：${if (recordingOn) "进行中（下一步：$recordingStage）" else "未开始"}"
        tvLog.text = ConfigStore.getLastLog(this).ifBlank { "暂无日志" }

        tvHistory.text = buildHistoryText(history)
        renderFailureScreenshot(history.latestFailureScreenshotPath)
    }

    private fun EditText.textString(): String = text?.toString()?.trim().orEmpty()

    private fun EditText.textIntOr(default: Int): Int =
        textString().toIntOrNull() ?: default

    private fun EditText.textCsvOr(default: List<String>): List<String> {
        val parsed = textString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return if (parsed.isEmpty()) default else parsed
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun buildHistoryText(summary: TaskHistorySummary): String {
        val lines = mutableListOf<String>()
        lines += "总轮次: ${summary.totalRuns}  成功: ${summary.successRuns}  失败: ${summary.failureRuns}"
        if (summary.recentEntries.isNotEmpty()) {
            lines += "最近执行："
        }

        summary.recentEntries.forEachIndexed { index, entry ->
            val status = if (entry.success) "成功" else "失败"
            lines += "${index + 1}. $status sent=${entry.sentCount} attempt=${entry.attemptCount} ocr=${entry.ocrFallbackHits}"
            lines += "   reason=${entry.reason}"
            if (!entry.screenshotPath.isNullOrBlank()) {
                lines += "   screenshot=${entry.screenshotPath}"
            }
        }
        return lines.joinToString("\n").ifBlank { "暂无历史" }
    }

    private fun renderFailureScreenshot(path: String?) {
        val bitmap = ScreenshotTools.decode(path)
        lastFailureBitmap?.recycle()
        lastFailureBitmap = bitmap
        if (bitmap != null) {
            ivLastFailure.setImageBitmap(bitmap)
        } else {
            ivLastFailure.setImageDrawable(null)
        }
    }
}
