package com.bossassistant.plugin

data class AutomationConfig(
    val bossPackageName: String = "com.hpbr.bosszhipin",
    val keyword: String = "前端开发",
    val cityKeyword: String = "",
    val locationKeyword: String = "",
    val salaryMinK: Int = 20,
    val salaryMaxK: Int = 50,
    val greetingTemplate: String = "您好，我对该岗位很感兴趣，期待进一步沟通。",
    val intervalMinutes: Int = 30,
    val maxGreetingsPerRound: Int = 8,
    val dedupeHours: Int = 72,
    val enableOcrFallback: Boolean = true,
    val searchBoxTexts: List<String> = listOf("搜索", "职位", "公司"),
    val searchBoxViewIds: List<String> = emptyList(),
    val searchBoxClassNames: List<String> = emptyList(),
    val chatButtonTexts: List<String> = listOf("立即沟通", "沟通"),
    val chatButtonViewIds: List<String> = emptyList(),
    val chatButtonClassNames: List<String> = emptyList(),
    val inputHintTexts: List<String> = listOf("请输入", "发消息"),
    val inputViewIds: List<String> = emptyList(),
    val inputClassNames: List<String> = listOf("EditText"),
    val sendButtonTexts: List<String> = listOf("发送"),
    val sendButtonViewIds: List<String> = emptyList(),
    val sendButtonClassNames: List<String> = emptyList()
) {
    fun salaryLabel(): String = "${salaryMinK}K-${salaryMaxK}K"

    fun keywordQuery(): String {
        val parts = mutableListOf<String>()
        if (keyword.isNotBlank()) {
            parts += keyword.trim()
        }
        if (cityKeyword.isNotBlank()) {
            parts += cityKeyword.trim()
        }
        if (locationKeyword.isNotBlank()) {
            parts += locationKeyword.trim()
        }
        if (salaryMinK > 0 && salaryMaxK >= salaryMinK) {
            parts += salaryLabel()
        }
        return parts.joinToString(" ")
    }

    fun searchRule() = NodeLocatorRule(
        textCandidates = searchBoxTexts,
        viewIdCandidates = searchBoxViewIds,
        classCandidates = searchBoxClassNames
    )

    fun chatRule() = NodeLocatorRule(
        textCandidates = chatButtonTexts,
        viewIdCandidates = chatButtonViewIds,
        classCandidates = chatButtonClassNames
    )

    fun inputRule() = NodeLocatorRule(
        textCandidates = inputHintTexts,
        viewIdCandidates = inputViewIds,
        classCandidates = inputClassNames
    )

    fun sendRule() = NodeLocatorRule(
        textCandidates = sendButtonTexts,
        viewIdCandidates = sendButtonViewIds,
        classCandidates = sendButtonClassNames
    )
}
