package com.bossassistant.plugin

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
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
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

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
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)

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
            sendButtonTexts = etSendTexts.textCsvOr(old.sendButtonTexts)
        )
        ConfigStore.save(this, cfg)
    }

    private fun refreshStatus() {
        val enabled = ConfigStore.isEnabled(this)
        val serviceOn = BossAccessibilityService.isAccessibilityEnabled(this)
        val status = buildString {
            append("调度状态：")
            append(if (enabled) "运行中" else "已暂停")
            append("\n无障碍权限：")
            append(if (serviceOn) "已开启" else "未开启")
        }
        tvStatus.text = status
        tvLog.text = ConfigStore.getLastLog(this).ifBlank { "暂无日志" }
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
}
