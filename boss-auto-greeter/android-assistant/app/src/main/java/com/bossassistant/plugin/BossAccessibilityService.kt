package com.bossassistant.plugin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.util.UUID

class BossAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val ocrClickHelper by lazy { OcrClickHelper(this) }
    private var roundRunning = false
    private var stopRequested = false
    private var commandReceiver: BroadcastReceiver? = null
    private var roundState: RoundState? = null

    private data class RoundState(
        val id: String,
        val startedAt: Long,
        var sentCount: Int = 0,
        var attemptCount: Int = 0,
        var ocrFallbackHits: Int = 0
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerCommandReceiver()
        ConfigStore.appendLog(this, "无障碍服务已连接。")
    }

    override fun onDestroy() {
        super.onDestroy()
        commandReceiver?.let {
            unregisterReceiver(it)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || roundRunning || !ConfigStore.isRecording(this)) {
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return
        }

        val cfg = ConfigStore.load(this)
        val sourcePackage = event.packageName?.toString().orEmpty()
        if (sourcePackage != cfg.bossPackageName) {
            return
        }

        val source = event.source ?: return
        ConfigStore.applyRecordingCapture(
            context = this,
            text = NodeUtils.nodeText(source),
            viewId = source.viewIdResourceName,
            className = source.className?.toString()
        )
        notifyStatusRefresh()
    }

    override fun onInterrupt() {
        ConfigStore.appendLog(this, "无障碍服务被系统中断。")
    }

    private fun registerCommandReceiver() {
        if (commandReceiver != null) {
            return
        }
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    ACTION_RUN_ROUND -> {
                        val source = intent.getStringExtra("source") ?: "unknown"
                        runRound(source)
                    }

                    ACTION_PAUSE -> {
                        stopRequested = true
                        ConfigStore.appendLog(this@BossAccessibilityService, "接收到暂停指令。")
                    }

                    ACTION_RECORD_START -> {
                        ConfigStore.startRecording(this@BossAccessibilityService)
                        notifyStatusRefresh()
                    }

                    ACTION_RECORD_STOP -> {
                        ConfigStore.stopRecording(this@BossAccessibilityService)
                        notifyStatusRefresh()
                    }

                    ACTION_RECORD_CLEAR -> {
                        ConfigStore.clearRecordedRules(this@BossAccessibilityService)
                        notifyStatusRefresh()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_RUN_ROUND)
            addAction(ACTION_PAUSE)
            addAction(ACTION_RECORD_START)
            addAction(ACTION_RECORD_STOP)
            addAction(ACTION_RECORD_CLEAR)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    private fun runRound(source: String) {
        if (!ConfigStore.isEnabled(this)) {
            return
        }
        if (roundRunning) {
            ConfigStore.appendLog(this, "已有执行流程进行中，忽略本次触发。")
            return
        }

        roundRunning = true
        stopRequested = false
        roundState = RoundState(
            id = UUID.randomUUID().toString(),
            startedAt = System.currentTimeMillis()
        )
        val cfg = ConfigStore.load(this)
        ConfigStore.appendLog(this, "开始执行一轮自动化，来源：$source")

        if (!launchBossApp(cfg.bossPackageName)) {
            finishRound("无法拉起 Boss App，请检查包名或安装状态。", success = false)
            return
        }

        handler.postDelayed({
            stepSearch(cfg, retries = 0)
        }, 2500L)
    }

    private fun launchBossApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    private fun stepSearch(cfg: AutomationConfig, retries: Int) {
        if (shouldStop()) return
        val root = rootInActiveWindow
        if (root == null) {
            retryOrFinish(
                retries = retries,
                limit = 6,
                retryDelayMs = 1200L,
                onRetry = { stepSearch(cfg, retries + 1) },
                onFail = { finishRound("搜索阶段失败：无法获取当前窗口节点。", success = false) }
            )
            return
        }

        clickRuleOrFallback(
            cfg = cfg,
            rule = cfg.searchRule(),
            stageLabel = "搜索入口"
        ) { clicked ->
            if (!clicked) {
                retryOrFinish(
                    retries = retries,
                    limit = 6,
                    retryDelayMs = 1200L,
                    onRetry = { stepSearch(cfg, retries + 1) },
                    onFail = {
                        finishRound(
                            "搜索阶段失败：未找到搜索入口。请在配置中调整搜索规则，或手动先进入职位列表页。",
                            success = false
                        )
                    }
                )
                return@clickRuleOrFallback
            }

            handler.postDelayed({
                val query = cfg.keywordQuery()
                setTextOrFallback(
                    cfg = cfg,
                    rule = cfg.inputRule(),
                    text = query,
                    stageLabel = "搜索输入框"
                ) { inputOk ->
                    if (!inputOk) {
                        retryOrFinish(
                            retries = retries,
                            limit = 3,
                            retryDelayMs = 1200L,
                            onRetry = { stepSearch(cfg, retries + 1) },
                            onFail = {
                                finishRound("搜索阶段失败：无法定位输入框。", success = false)
                            }
                        )
                        return@setTextOrFallback
                    }

                    clickRuleOrFallback(
                        cfg = cfg,
                        rule = NodeLocatorRule(textCandidates = listOf("搜索", "查找", "确定")),
                        stageLabel = "搜索确认按钮"
                    ) {
                        handler.postDelayed({
                            stepTryGreeting(cfg, sent = 0, attempts = 0)
                        }, 2000L)
                    }
                }
            }, 1000L)
        }
    }

    private fun stepTryGreeting(cfg: AutomationConfig, sent: Int, attempts: Int) {
        if (shouldStop()) return
        roundState?.attemptCount = attempts
        if (sent >= cfg.maxGreetingsPerRound) {
            finishRound("本轮完成：已达到最大发送数 ${cfg.maxGreetingsPerRound}。", success = true)
            return
        }
        if (attempts >= cfg.maxGreetingsPerRound * 8) {
            finishRound("本轮完成：超过尝试上限，停止继续滑动。", success = true)
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            handler.postDelayed({
                stepTryGreeting(cfg, sent, attempts + 1)
            }, 1200L)
            return
        }

        clickRuleOrFallback(
            cfg = cfg,
            rule = cfg.chatRule(),
            stageLabel = "沟通按钮"
        ) { chatOpened ->
            if (!chatOpened) {
                swipeUp()
                handler.postDelayed({
                    stepTryGreeting(cfg, sent, attempts + 1)
                }, 1500L)
                return@clickRuleOrFallback
            }

            handler.postDelayed({
                val currentRoot = rootInActiveWindow
                val dedupeKey = buildDedupeKey(currentRoot)
                if (isDuplicateAndNotExpired(dedupeKey, cfg.dedupeHours)) {
                    ConfigStore.appendLog(this, "命中去重规则，跳过本次发送。")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    handler.postDelayed({
                        stepTryGreeting(cfg, sent, attempts + 1)
                    }, 1200L)
                    return@postDelayed
                }

                setTextOrFallback(
                    cfg = cfg,
                    rule = cfg.inputRule(),
                    text = cfg.greetingTemplate,
                    stageLabel = "沟通输入框"
                ) { inputOk ->
                    if (!inputOk) {
                        ConfigStore.appendLog(this, "输入框未命中，返回岗位列表继续。")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        handler.postDelayed({
                            stepTryGreeting(cfg, sent, attempts + 1)
                        }, 1200L)
                        return@setTextOrFallback
                    }

                    clickRuleOrFallback(
                        cfg = cfg,
                        rule = cfg.sendRule(),
                        stageLabel = "发送按钮"
                    ) { sentOk ->
                        if (sentOk) {
                            markDedupe(dedupeKey)
                            roundState?.sentCount = (roundState?.sentCount ?: 0) + 1
                            ConfigStore.appendLog(this, "已发送一条打招呼消息。")
                        } else {
                            ConfigStore.appendLog(this, "发送按钮未命中，本次跳过。")
                        }

                        performGlobalAction(GLOBAL_ACTION_BACK)
                        handler.postDelayed({
                            stepTryGreeting(cfg, sent + if (sentOk) 1 else 0, attempts + 1)
                        }, 1200L)
                    }
                }
            }, 1200L)
        }
    }

    private fun clickRuleOrFallback(
        cfg: AutomationConfig,
        rule: NodeLocatorRule,
        stageLabel: String,
        onResult: (Boolean) -> Unit
    ) {
        val root = rootInActiveWindow
        val target = NodeUtils.findNodeByRule(root, rule)
        if (NodeUtils.clickNodeOrClickableParent(target)) {
            onResult(true)
            return
        }

        if (!cfg.enableOcrFallback || rule.textCandidates.isEmpty()) {
            onResult(false)
            return
        }

        ocrClickHelper.clickByKeywords(rule.textCandidates) { ok, message ->
            if (ok) {
                roundState?.ocrFallbackHits = (roundState?.ocrFallbackHits ?: 0) + 1
            }
            ConfigStore.appendLog(this, "$stageLabel OCR兜底：$message")
            onResult(ok)
        }
    }

    private fun setTextOrFallback(
        cfg: AutomationConfig,
        rule: NodeLocatorRule,
        text: String,
        stageLabel: String,
        onResult: (Boolean) -> Unit
    ) {
        val root = rootInActiveWindow
        val inputNode = NodeUtils.findEditableNodeByRule(root, rule)
        if (inputNode != null) {
            NodeUtils.clickNodeOrClickableParent(inputNode)
            if (NodeUtils.setText(inputNode, text)) {
                onResult(true)
                return
            }
        }

        if (!cfg.enableOcrFallback || rule.textCandidates.isEmpty()) {
            onResult(false)
            return
        }

        ocrClickHelper.clickByKeywords(rule.textCandidates) { ok, message ->
            if (ok) {
                roundState?.ocrFallbackHits = (roundState?.ocrFallbackHits ?: 0) + 1
                handler.postDelayed({
                    val retryInput = NodeUtils.findEditableNodeByRule(rootInActiveWindow, rule)
                    val setOk = NodeUtils.setText(retryInput, text)
                    if (!setOk) {
                        ConfigStore.appendLog(this, "$stageLabel OCR后仍未找到可输入控件。")
                    }
                    onResult(setOk)
                }, 550L)
            } else {
                ConfigStore.appendLog(this, "$stageLabel OCR兜底失败：$message")
                onResult(false)
            }
        }
    }

    private fun retryOrFinish(
        retries: Int,
        limit: Int,
        retryDelayMs: Long,
        onRetry: () -> Unit,
        onFail: () -> Unit
    ) {
        if (retries < limit) {
            handler.postDelayed(onRetry, retryDelayMs)
        } else {
            onFail()
        }
    }

    private fun swipeUp() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * 0.5f, height * 0.78f)
            lineTo(width * 0.5f, height * 0.32f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 360L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun shouldStop(): Boolean {
        if (!roundRunning) return true
        if (!ConfigStore.isEnabled(this)) {
            finishRound("检测到调度已暂停，结束当前流程。", success = true)
            return true
        }
        if (stopRequested) {
            finishRound("检测到停止请求，结束当前流程。", success = true)
            return true
        }
        return false
    }

    private fun finishRound(reason: String, success: Boolean) {
        if (!roundRunning) {
            return
        }
        roundRunning = false
        stopRequested = false

        val now = System.currentTimeMillis()
        val snapshot = roundState ?: RoundState(
            id = UUID.randomUUID().toString(),
            startedAt = now
        )
        roundState = null

        val persistHistory: (String?) -> Unit = { screenshotPath ->
            TaskHistoryStore.append(
                context = this,
                entry = TaskHistoryEntry(
                    id = snapshot.id,
                    startedAt = snapshot.startedAt,
                    finishedAt = now,
                    success = success,
                    sentCount = snapshot.sentCount,
                    attemptCount = snapshot.attemptCount,
                    ocrFallbackHits = snapshot.ocrFallbackHits,
                    reason = reason,
                    screenshotPath = screenshotPath
                )
            )
            notifyStatusRefresh()
        }

        if (!success) {
            ScreenshotTools.captureAndSaveFailure(this, reason.take(18)) { path ->
                if (!path.isNullOrBlank()) {
                    ConfigStore.appendLog(this, "失败截图已保存：$path")
                } else {
                    ConfigStore.appendLog(this, "失败截图保存失败（可能系统限制截图）。")
                }
                ConfigStore.appendLog(this, reason)
                persistHistory(path)
            }
            return
        }

        ConfigStore.appendLog(this, reason)
        persistHistory(null)
    }

    private fun buildDedupeKey(root: AccessibilityNodeInfo?): String {
        if (root == null) return "unknown"
        val visibleTexts = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && visibleTexts.size < 8) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) continue
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && text.length <= 30) {
                visibleTexts += text
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return visibleTexts.joinToString("|").ifBlank { "unknown" }.take(120)
    }

    private fun dedupeJson(): JSONObject {
        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEDUPE_JSON, "{}")
            .orEmpty()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun isDuplicateAndNotExpired(key: String, dedupeHours: Int): Boolean {
        if (key.isBlank() || key == "unknown") return false
        val json = dedupeJson()
        val now = System.currentTimeMillis()
        val expiryMs = dedupeHours.coerceAtLeast(1) * 60L * 60L * 1000L
        val ts = if (json.has(key)) json.optLong(key, 0L) else 0L
        if (ts <= 0L) return false
        return now - ts < expiryMs
    }

    private fun markDedupe(key: String) {
        if (key.isBlank() || key == "unknown") return
        val json = dedupeJson()
        json.put(key, System.currentTimeMillis())
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEDUPE_JSON, json.toString())
            .apply()
    }

    private fun notifyStatusRefresh() {
        sendBroadcast(Intent(ACTION_REFRESH_STATUS).apply {
            `package` = packageName
        })
    }

    companion object {
        fun isAccessibilityEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${BossAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
        }
    }
}
