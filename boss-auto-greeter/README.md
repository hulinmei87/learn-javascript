# Boss Auto Greeter

一个配置驱动的岗位检索与打招呼服务，支持：

- 按关键词、城市、地点、薪资筛选岗位
- 常驻运行，按 cron 周期执行（24 小时）
- 自动去重，避免重复给同一岗位发送消息
- 手机浏览器控制台：**开始 / 暂停 / 手动执行 / 在线改配置**
- `dryRun` 演练模式（先不实际发送）

> 重要边界：不做注入式篡改（不改 Boss App 安装包），但新增了**手机端外挂式辅助应用原型**（基于无障碍服务）。  
> Android 版本请看：`android-assistant/README.md`。

## 1. 快速开始

```bash
cd /workspace/boss-auto-greeter
cp config.example.json config.json
npm install
npx playwright install chromium
npm start
```

首次运行会打开浏览器窗口。若未登录，请扫码登录，脚本会保存会话状态到 `state/storage-state.json`。

## 2. 手机控制台（核心）

服务启动后，默认监听 `http://0.0.0.0:8787`：

- 电脑访问：`http://127.0.0.1:8787`
- 手机访问：`http://<服务器IP>:8787`（同一局域网）

控制台能力：

1. 实时查看状态（是否运行、上轮统计、失败数、去重数）
2. 一键开始 / 暂停
3. 手动立即执行一轮
4. 在线编辑并保存 `config.json`（保存后自动重载）

### 2.1 安全令牌（建议开启）

可通过环境变量开启 API 令牌保护：

```bash
CONTROL_TOKEN=your-secret-token npm start
```

打开网页后先在“访问令牌”输入框填写令牌，后续浏览器会本地保存。

## 3. 运行参数（环境变量）

- `BOSS_BOT_CONFIG`：配置文件路径，默认 `./config.json`
- `CONTROL_HOST`：控制台监听地址，默认 `0.0.0.0`
- `CONTROL_PORT`：控制台端口，默认 `8787`
- `CONTROL_TOKEN`：控制台 API 令牌（可选但建议）
- `BOSS_BOT_AUTO_START`：是否启动后自动开始，默认 `true`

示例：

```bash
CONTROL_HOST=0.0.0.0 CONTROL_PORT=8787 CONTROL_TOKEN=123456 npm start
```

## 4. 配置文件

编辑 `config.json`：

- `cron`: 定时表达式，例如 `*/30 * * * *`
- `timezone`: 时区，例如 `Asia/Shanghai`
- `runOnStartup`: 引擎启动后是否立刻执行一次
- `dryRun`: `true` 时不真正发送
- `autoSend`: `true` 时允许自动发送（要求 `dryRun=false`）
- `maxJobsPerSearch`: 每个搜索规则最多读取岗位数
- `maxGreetingsPerRound`: 每轮最多发送/演练次数
- `searches`: 搜索规则列表
  - `keyword`: 职位关键词
  - `cityCode`: 城市代码（Boss Web 查询参数）
  - `locations`: 地点关键字数组（模糊匹配）
  - `salary.minK/maxK`: 薪资范围（单位 K/月）
- `greetingTemplates`: 招呼模板，支持 `{jobTitle}` `{companyName}` `{location}` `{salary}`

## 5. 24 小时运行建议

建议使用 PM2 或 systemd 托管，避免终端断开后退出。

```bash
npm i -g pm2
cd /workspace/boss-auto-greeter
pm2 start npm --name boss-auto-greeter -- start
pm2 save
```

## 6. 测试

```bash
npm test
```

当前测试覆盖薪资解析与过滤逻辑（`tests/filters.test.js`）。

## 7. 风险与合规提醒

1. 自动化行为可能触发平台风控（验证码、限流、账号限制等）。
2. 请遵守目标平台用户协议与当地法律法规。
3. 建议先用 `dryRun=true` 长时间观察，再启用自动发送。
4. 页面结构改版后，`selectors` 可能需要调整。
