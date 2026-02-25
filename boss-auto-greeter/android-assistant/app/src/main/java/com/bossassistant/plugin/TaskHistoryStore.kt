package com.bossassistant.plugin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TaskHistoryEntry(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long,
    val success: Boolean,
    val sentCount: Int,
    val attemptCount: Int,
    val ocrFallbackHits: Int,
    val reason: String,
    val screenshotPath: String?
)

data class TaskHistorySummary(
    val totalRuns: Int,
    val successRuns: Int,
    val failureRuns: Int,
    val latestFailureScreenshotPath: String?,
    val recentEntries: List<TaskHistoryEntry>
)

object TaskHistoryStore {
    private const val MAX_ENTRIES = 60

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadArray(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_HISTORY_JSON, "[]").orEmpty()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun saveArray(context: Context, array: JSONArray) {
        prefs(context).edit()
            .putString(KEY_HISTORY_JSON, array.toString())
            .apply()
    }

    private fun toEntry(obj: JSONObject): TaskHistoryEntry {
        return TaskHistoryEntry(
            id = obj.optString("id", ""),
            startedAt = obj.optLong("startedAt", 0L),
            finishedAt = obj.optLong("finishedAt", 0L),
            success = obj.optBoolean("success", false),
            sentCount = obj.optInt("sentCount", 0),
            attemptCount = obj.optInt("attemptCount", 0),
            ocrFallbackHits = obj.optInt("ocrFallbackHits", 0),
            reason = obj.optString("reason", ""),
            screenshotPath = obj.optString("screenshotPath", "").ifBlank { null }
        )
    }

    fun append(context: Context, entry: TaskHistoryEntry) {
        val array = loadArray(context)
        val next = JSONArray()

        next.put(
            JSONObject()
                .put("id", entry.id)
                .put("startedAt", entry.startedAt)
                .put("finishedAt", entry.finishedAt)
                .put("success", entry.success)
                .put("sentCount", entry.sentCount)
                .put("attemptCount", entry.attemptCount)
                .put("ocrFallbackHits", entry.ocrFallbackHits)
                .put("reason", entry.reason)
                .put("screenshotPath", entry.screenshotPath.orEmpty())
        )

        val keepCount = minOf(array.length(), MAX_ENTRIES - 1)
        for (i in 0 until keepCount) {
            next.put(array.getJSONObject(i))
        }

        saveArray(context, next)
    }

    fun summary(context: Context, maxRecent: Int = 8): TaskHistorySummary {
        val array = loadArray(context)
        val entries = mutableListOf<TaskHistoryEntry>()
        var success = 0
        var failure = 0
        var latestFailureScreenshotPath: String? = null

        for (i in 0 until array.length()) {
            val entry = toEntry(array.getJSONObject(i))
            entries += entry
            if (entry.success) {
                success += 1
            } else {
                failure += 1
                if (latestFailureScreenshotPath == null && !entry.screenshotPath.isNullOrBlank()) {
                    latestFailureScreenshotPath = entry.screenshotPath
                }
            }
        }

        return TaskHistorySummary(
            totalRuns = entries.size,
            successRuns = success,
            failureRuns = failure,
            latestFailureScreenshotPath = latestFailureScreenshotPath,
            recentEntries = entries.take(maxRecent)
        )
    }
}
