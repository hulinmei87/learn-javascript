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
    val searchBoxTexts: List<String> = listOf("搜索", "职位", "公司"),
    val chatButtonTexts: List<String> = listOf("立即沟通", "沟通"),
    val inputHintTexts: List<String> = listOf("请输入", "发消息"),
    val sendButtonTexts: List<String> = listOf("发送")
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
}
