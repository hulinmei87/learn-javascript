# Boss Android Assistant（外挂式辅助应用原型）

这是一个可安装在 Android 手机上的辅助应用原型，目标是：

1. 在手机内修改配置（关键词、城市、薪资、招呼语等）
2. 点击开始/暂停调度
3. 定时拉起 Boss App，并执行搜索、滑动、点击、发送消息等自动化操作
4. 本地去重，避免短时间重复发送

> 技术实现：**Android 无障碍服务 + AlarmManager 定时调度**  
> 不是注入式“篡改 App 二进制”插件，也不依赖 root。

---

## 功能清单（当前原型）

- 可视化配置页（MainActivity）
  - 包名、关键词、城市、地区、薪资范围
  - 招呼语
  - 执行间隔、每轮最大发送数、去重时长
  - 文本选择器（搜索入口/沟通按钮/输入框提示/发送按钮）
- 控制按钮
  - 保存配置
  - 执行一次
  - 开始调度
  - 暂停调度
  - 打开无障碍设置
  - 拉起 Boss App
- 调度能力
  - AlarmManager 定时触发
  - 开机恢复（若之前是运行态）
- 自动化能力（无障碍）
  - 拉起目标 App
  - 查找搜索入口并输入查询词
  - 滑动列表查找“立即沟通/沟通”
  - 输入招呼语并点击发送
  - 去重（按界面文本指纹 + 时间窗口）

---

## 项目结构

```text
android-assistant/
  app/
    src/main/
      AndroidManifest.xml
      java/com/bossassistant/plugin/
        MainActivity.kt
        BossAccessibilityService.kt
        AutomationOrchestrator.kt
        AutomationAlarmReceiver.kt
        BootReceiver.kt
        ConfigStore.kt
        AutomationConfig.kt
        NodeUtils.kt
      res/layout/activity_main.xml
      res/xml/accessibility_service_config.xml
```

---

## 本地构建

需要 Android Studio（Hedgehog 或更新） + Android SDK 35：

1. 用 Android Studio 打开 `android-assistant` 目录
2. 同步 Gradle
3. 连接真机（建议 Android 10+）
4. 直接 Run `app`

---

## 手机上如何使用

1. 安装并打开 App
2. 点“打开无障碍设置”，开启本应用无障碍权限
3. 填写配置并保存
4. 点“开始调度”启动自动执行
5. 需要临时停止时点“暂停调度”
6. 想立即跑一次，点“执行一次”

---

## 关键注意事项

1. 不同手机 ROM 对后台调度限制差异很大，请关闭电池优化以提高定时稳定性。
2. Boss App 页面会改版，可能导致文本选择器失效，需要在配置页调整关键词。
3. 自动化发送行为可能触发风控，请控制频率并遵守平台规则。
4. 当前是 MVP 原型，建议先在 `maxGreetingsPerRound=1` 的低风险模式下验证流程。

---

## 后续可继续增强

1. 视觉识别（OCR）提升控件定位稳定性
2. 更细粒度岗位筛选（企业规模、学历、经验）
3. 招呼语模板变量（岗位名/公司名动态注入）
4. 本地任务历史和失败截图诊断
5. 多账户配置切换
