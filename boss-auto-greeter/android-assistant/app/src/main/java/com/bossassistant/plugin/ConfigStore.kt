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
    private const val KEY_SEARCH_TEXTS = "cfg_search_texts"
    private const val KEY_CHAT_TEXTS = "cfg_chat_texts"
    private const val KEY_INPUT_TEXTS = "cfg_input_texts"
    private const val KEY_SEND_TEXTS = "cfg_send_texts"

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
            searchBoxTexts = parseTexts(p.getString(KEY_SEARCH_TEXTS, null), defaults.searchBoxTexts),
            chatButtonTexts = parseTexts(p.getString(KEY_CHAT_TEXTS, null), defaults.chatButtonTexts),
            inputHintTexts = parseTexts(p.getString(KEY_INPUT_TEXTS, null), defaults.inputHintTexts),
            sendButtonTexts = parseTexts(p.getString(KEY_SEND_TEXTS, null), defaults.sendButtonTexts)
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
            .putString(KEY_SEARCH_TEXTS, toCsv(cfg.searchBoxTexts))
            .putString(KEY_CHAT_TEXTS, toCsv(cfg.chatButtonTexts))
            .putString(KEY_INPUT_TEXTS, toCsv(cfg.inputHintTexts))
            .putString(KEY_SEND_TEXTS, toCsv(cfg.sendButtonTexts))
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
}
