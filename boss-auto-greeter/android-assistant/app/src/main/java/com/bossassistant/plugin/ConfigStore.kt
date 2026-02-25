package com.bossassistant.plugin

import android.content.Context

object ConfigStore {
    private const val KEY_BOSS_PACKAGE = "cfg_boss_package"
    private const val KEY_KEYWORD = "cfg_keyword"
    private const val KEY_CITY = "cfg_city"
    private const val KEY_LOCATION = "cfg_location"
    private const val KEY_SALARY_MIN = "cfg_salary_min"
    private const val KEY_SALARY_MAX = "cfg_salary_max"
    private const val KEY_GREETING = "cfg_greeting"
    private const val KEY_INTERVAL = "cfg_interval"
    private const val KEY_MAX_GREETINGS = "cfg_max_greetings"
    private const val KEY_DEDUPE_HOURS = "cfg_dedupe_hours"
    private const val KEY_ENABLE_OCR_FALLBACK = "cfg_enable_ocr_fallback"

    private const val KEY_SEARCH_TEXTS = "cfg_search_texts"
    private const val KEY_SEARCH_VIEW_IDS = "cfg_search_view_ids"
    private const val KEY_SEARCH_CLASSES = "cfg_search_classes"

    private const val KEY_CHAT_TEXTS = "cfg_chat_texts"
    private const val KEY_CHAT_VIEW_IDS = "cfg_chat_view_ids"
    private const val KEY_CHAT_CLASSES = "cfg_chat_classes"

    private const val KEY_INPUT_TEXTS = "cfg_input_texts"
    private const val KEY_INPUT_VIEW_IDS = "cfg_input_view_ids"
    private const val KEY_INPUT_CLASSES = "cfg_input_classes"

    private const val KEY_SEND_TEXTS = "cfg_send_texts"
    private const val KEY_SEND_VIEW_IDS = "cfg_send_view_ids"
    private const val KEY_SEND_CLASSES = "cfg_send_classes"

    private val RECORDING_STAGES = listOf("搜索入口", "沟通按钮", "输入框", "发送按钮")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun parseTexts(raw: String?, fallback: List<String>): List<String> {
        if (raw.isNullOrBlank()) {
            return fallback
        }
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { fallback }
    }

    private fun toCsv(values: List<String>): String = values.joinToString(",")

    fun load(context: Context): AutomationConfig {
        val p = prefs(context)
        val defaults = AutomationConfig()
        return AutomationConfig(
            bossPackageName = p.getString(KEY_BOSS_PACKAGE, defaults.bossPackageName) ?: defaults.bossPackageName,
            keyword = p.getString(KEY_KEYWORD, defaults.keyword) ?: defaults.keyword,
            cityKeyword = p.getString(KEY_CITY, defaults.cityKeyword) ?: defaults.cityKeyword,
            locationKeyword = p.getString(KEY_LOCATION, defaults.locationKeyword) ?: defaults.locationKeyword,
            salaryMinK = p.getInt(KEY_SALARY_MIN, defaults.salaryMinK),
            salaryMaxK = p.getInt(KEY_SALARY_MAX, defaults.salaryMaxK),
            greetingTemplate = p.getString(KEY_GREETING, defaults.greetingTemplate) ?: defaults.greetingTemplate,
            intervalMinutes = p.getInt(KEY_INTERVAL, defaults.intervalMinutes).coerceIn(1, 24 * 60),
            maxGreetingsPerRound = p.getInt(KEY_MAX_GREETINGS, defaults.maxGreetingsPerRound).coerceIn(1, 50),
            dedupeHours = p.getInt(KEY_DEDUPE_HOURS, defaults.dedupeHours).coerceIn(1, 720),
            enableOcrFallback = p.getBoolean(KEY_ENABLE_OCR_FALLBACK, defaults.enableOcrFallback),
            searchBoxTexts = parseTexts(p.getString(KEY_SEARCH_TEXTS, null), defaults.searchBoxTexts),
            searchBoxViewIds = parseTexts(p.getString(KEY_SEARCH_VIEW_IDS, null), defaults.searchBoxViewIds),
            searchBoxClassNames = parseTexts(p.getString(KEY_SEARCH_CLASSES, null), defaults.searchBoxClassNames),
            chatButtonTexts = parseTexts(p.getString(KEY_CHAT_TEXTS, null), defaults.chatButtonTexts),
            chatButtonViewIds = parseTexts(p.getString(KEY_CHAT_VIEW_IDS, null), defaults.chatButtonViewIds),
            chatButtonClassNames = parseTexts(p.getString(KEY_CHAT_CLASSES, null), defaults.chatButtonClassNames),
            inputHintTexts = parseTexts(p.getString(KEY_INPUT_TEXTS, null), defaults.inputHintTexts),
            inputViewIds = parseTexts(p.getString(KEY_INPUT_VIEW_IDS, null), defaults.inputViewIds),
            inputClassNames = parseTexts(p.getString(KEY_INPUT_CLASSES, null), defaults.inputClassNames),
            sendButtonTexts = parseTexts(p.getString(KEY_SEND_TEXTS, null), defaults.sendButtonTexts),
            sendButtonViewIds = parseTexts(p.getString(KEY_SEND_VIEW_IDS, null), defaults.sendButtonViewIds),
            sendButtonClassNames = parseTexts(p.getString(KEY_SEND_CLASSES, null), defaults.sendButtonClassNames)
        )
    }

    fun save(context: Context, cfg: AutomationConfig) {
        prefs(context).edit()
            .putString(KEY_BOSS_PACKAGE, cfg.bossPackageName)
            .putString(KEY_KEYWORD, cfg.keyword)
            .putString(KEY_CITY, cfg.cityKeyword)
            .putString(KEY_LOCATION, cfg.locationKeyword)
            .putInt(KEY_SALARY_MIN, cfg.salaryMinK)
            .putInt(KEY_SALARY_MAX, cfg.salaryMaxK)
            .putString(KEY_GREETING, cfg.greetingTemplate)
            .putInt(KEY_INTERVAL, cfg.intervalMinutes)
            .putInt(KEY_MAX_GREETINGS, cfg.maxGreetingsPerRound)
            .putInt(KEY_DEDUPE_HOURS, cfg.dedupeHours)
            .putBoolean(KEY_ENABLE_OCR_FALLBACK, cfg.enableOcrFallback)
            .putString(KEY_SEARCH_TEXTS, toCsv(cfg.searchBoxTexts))
            .putString(KEY_SEARCH_VIEW_IDS, toCsv(cfg.searchBoxViewIds))
            .putString(KEY_SEARCH_CLASSES, toCsv(cfg.searchBoxClassNames))
            .putString(KEY_CHAT_TEXTS, toCsv(cfg.chatButtonTexts))
            .putString(KEY_CHAT_VIEW_IDS, toCsv(cfg.chatButtonViewIds))
            .putString(KEY_CHAT_CLASSES, toCsv(cfg.chatButtonClassNames))
            .putString(KEY_INPUT_TEXTS, toCsv(cfg.inputHintTexts))
            .putString(KEY_INPUT_VIEW_IDS, toCsv(cfg.inputViewIds))
            .putString(KEY_INPUT_CLASSES, toCsv(cfg.inputClassNames))
            .putString(KEY_SEND_TEXTS, toCsv(cfg.sendButtonTexts))
            .putString(KEY_SEND_VIEW_IDS, toCsv(cfg.sendButtonViewIds))
            .putString(KEY_SEND_CLASSES, toCsv(cfg.sendButtonClassNames))
            .apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun getLastLog(context: Context): String =
        prefs(context).getString(KEY_LAST_LOG, "") ?: ""

    fun appendLog(context: Context, message: String) {
        val now = System.currentTimeMillis()
        val prev = getLastLog(context)
        val newContent = "[${now}] $message\n$prev"
            .take(6000)
        prefs(context).edit()
            .putString(KEY_LAST_LOG, newContent)
            .apply()
    }

    fun isRecording(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECORDING_ENABLED, false)

    fun startRecording(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RECORDING_ENABLED, true)
            .putInt(KEY_RECORDING_STAGE_INDEX, 0)
            .apply()
        appendLog(context, "录制模式已启动，请依次手动点击：${RECORDING_STAGES.joinToString(" -> ")}")
    }

    fun stopRecording(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_RECORDING_ENABLED, false)
            .apply()
        appendLog(context, "录制模式已停止。")
    }

    fun clearRecordedRules(context: Context) {
        prefs(context).edit()
            .putString(KEY_SEARCH_VIEW_IDS, "")
            .putString(KEY_SEARCH_CLASSES, "")
            .putString(KEY_CHAT_VIEW_IDS, "")
            .putString(KEY_CHAT_CLASSES, "")
            .putString(KEY_INPUT_VIEW_IDS, "")
            .putString(KEY_INPUT_CLASSES, "")
            .putString(KEY_SEND_VIEW_IDS, "")
            .putString(KEY_SEND_CLASSES, "")
            .putBoolean(KEY_RECORDING_ENABLED, false)
            .putInt(KEY_RECORDING_STAGE_INDEX, 0)
            .apply()
        appendLog(context, "已清空录制生成的控件规则。")
    }

    fun currentRecordingStage(context: Context): String {
        val index = prefs(context).getInt(KEY_RECORDING_STAGE_INDEX, 0)
        return RECORDING_STAGES.getOrElse(index) { "已完成" }
    }

    fun applyRecordingCapture(
        context: Context,
        text: String?,
        viewId: String?,
        className: String?
    ) {
        if (!isRecording(context)) {
            return
        }
        val p = prefs(context)
        val stageIndex = p.getInt(KEY_RECORDING_STAGE_INDEX, 0)
        val stage = stageIndex.coerceAtLeast(0)
        if (stage >= RECORDING_STAGES.size) {
            stopRecording(context)
            return
        }

        val normalizedText = text?.trim().orEmpty()
        val normalizedViewId = viewId?.trim().orEmpty()
        val normalizedClass = className?.trim().orEmpty()

        val (viewKey, classKey, textKey) = when (stage) {
            0 -> Triple(KEY_SEARCH_VIEW_IDS, KEY_SEARCH_CLASSES, KEY_SEARCH_TEXTS)
            1 -> Triple(KEY_CHAT_VIEW_IDS, KEY_CHAT_CLASSES, KEY_CHAT_TEXTS)
            2 -> Triple(KEY_INPUT_VIEW_IDS, KEY_INPUT_CLASSES, KEY_INPUT_TEXTS)
            else -> Triple(KEY_SEND_VIEW_IDS, KEY_SEND_CLASSES, KEY_SEND_TEXTS)
        }

        val editor = p.edit()
        if (normalizedViewId.isNotBlank()) {
            editor.putString(viewKey, upsertCsvUnique(p.getString(viewKey, ""), normalizedViewId))
        }
        if (normalizedClass.isNotBlank()) {
            editor.putString(classKey, upsertCsvUnique(p.getString(classKey, ""), normalizedClass))
        }
        if (normalizedText.isNotBlank()) {
            editor.putString(textKey, upsertCsvUnique(p.getString(textKey, ""), normalizedText))
        }
        editor.putInt(KEY_RECORDING_STAGE_INDEX, stage + 1).apply()

        appendLog(
            context,
            "录制捕获 ${RECORDING_STAGES[stage]}：text=$normalizedText viewId=$normalizedViewId class=$normalizedClass"
        )

        if (stage + 1 >= RECORDING_STAGES.size) {
            stopRecording(context)
            appendLog(context, "录制已完成，自动生成规则已写入配置。")
        } else {
            appendLog(context, "请继续点击下一步：${RECORDING_STAGES[stage + 1]}")
        }
    }

    private fun upsertCsvUnique(previous: String?, value: String): String {
        val values = previous.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        if (values.none { it.equals(value, ignoreCase = true) }) {
            values.add(value)
        }
        return values.joinToString(",")
    }
}
