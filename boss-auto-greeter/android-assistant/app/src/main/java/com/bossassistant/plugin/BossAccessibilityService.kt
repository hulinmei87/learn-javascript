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

class BossAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var roundRunning = false
    private var stopRequested = false
    private var commandReceiver: BroadcastReceiver? = null

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
        // 本服务以主动调度为主，事件仅用于维持连接。
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
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_RUN_ROUND)
            addAction(ACTION_PAUSE)
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
        val cfg = ConfigStore.load(this)
        ConfigStore.appendLog(this, "开始执行一轮自动化，来源：$source")

        if (!launchBossApp(cfg.bossPackageName)) {
            finishRound("无法拉起 Boss App，请检查包名或安装状态。")
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
                onFail = { finishRound("搜索阶段失败：无法获取当前窗口节点。") }
            )
            return
        }

        val searchNode = NodeUtils.findNodeByTexts(root, cfg.searchBoxTexts)
        val clicked = NodeUtils.clickNodeOrClickableParent(searchNode)
        if (!clicked) {
            retryOrFinish(
                retries = retries,
                limit = 6,
                retryDelayMs = 1200L,
                onRetry = { stepSearch(cfg, retries + 1) },
                onFail = {
                    finishRound(
                        "搜索阶段失败：未找到搜索入口。请在配置中调整 searchBoxTexts，或手动先进入职位列表页。"
                    )
                }
            )
            return
        }

        handler.postDelayed({
            val query = cfg.keywordQuery()
            val editable = NodeUtils.findEditableNode(rootInActiveWindow)
            if (editable != null) {
                NodeUtils.setText(editable, query)
            }
            val submit = NodeUtils.findNodeByTexts(rootInActiveWindow, listOf("搜索", "查找", "确定"))
            NodeUtils.clickNodeOrClickableParent(submit)
            handler.postDelayed({
                stepTryGreeting(cfg, sent = 0, attempts = 0)
            }, 2000L)
        }, 1200L)
    }

    private fun stepTryGreeting(cfg: AutomationConfig, sent: Int, attempts: Int) {
        if (shouldStop()) return
        if (sent >= cfg.maxGreetingsPerRound) {
            finishRound("本轮完成：已达到最大发送数 ${cfg.maxGreetingsPerRound}。")
            return
        }
        if (attempts >= cfg.maxGreetingsPerRound * 8) {
            finishRound("本轮完成：超过尝试上限，停止继续滑动。")
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            handler.postDelayed({
                stepTryGreeting(cfg, sent, attempts + 1)
            }, 1200L)
            return
        }

        val chatButton = NodeUtils.findNodeByTexts(root, cfg.chatButtonTexts)
        if (chatButton == null || !NodeUtils.clickNodeOrClickableParent(chatButton)) {
            swipeUp()
            handler.postDelayed({
                stepTryGreeting(cfg, sent, attempts + 1)
            }, 1500L)
            return
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

            val input = NodeUtils.findEditableNode(currentRoot)
                ?: NodeUtils.findNodeByTexts(currentRoot, cfg.inputHintTexts)
            NodeUtils.clickNodeOrClickableParent(input)
            NodeUtils.setText(input, cfg.greetingTemplate)

            val sendBtn = NodeUtils.findNodeByTexts(rootInActiveWindow, cfg.sendButtonTexts)
            val sentOk = NodeUtils.clickNodeOrClickableParent(sendBtn)

            if (sentOk) {
                markDedupe(dedupeKey)
                ConfigStore.appendLog(this, "已发送一条打招呼消息。")
            } else {
                ConfigStore.appendLog(this, "发送按钮未命中，本次跳过。")
            }

            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({
                stepTryGreeting(cfg, sent + if (sentOk) 1 else 0, attempts + 1)
            }, 1200L)
        }, 1200L)
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
            finishRound("检测到调度已暂停，结束当前流程。")
            return true
        }
        if (stopRequested) {
            finishRound("检测到停止请求，结束当前流程。")
            return true
        }
        return false
    }

    private fun finishRound(reason: String) {
        if (!roundRunning) {
            return
        }
        roundRunning = false
        stopRequested = false
        ConfigStore.appendLog(this, reason)
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
