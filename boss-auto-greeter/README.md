# Boss Auto Greeter

一个配置驱动的岗位检索与打招呼脚本：  
- 按关键词、城市、地点和薪资范围筛选岗位  
- 常驻运行，按 cron 周期执行（24 小时）  
- 自动去重，避免重复给同一岗位发送消息  
- 支持 `dryRun` 演练模式（先只填充消息不发送）

> 说明：Boss 直聘没有公开官方投递 API。该项目基于页面自动化思路实现，默认针对 Web 端（`www.zhipin.com`）。如果你必须操作手机 App，可以复用同样的“调度 + 过滤 + 去重 + 模板渲染”架构，将自动化层替换为 Appium。

## 1. 快速开始

```bash
cd /workspace/boss-auto-greeter
cp config.example.json config.json
npm install
npx playwright install chromium
npm start
```

首次运行会打开浏览器窗口。若未登录，请扫码登录，脚本会在登录成功后保存会话状态（`state/storage-state.json`）。

## 2. 配置文件

编辑 `config.json`：

- `cron`: 定时表达式，例如 `*/30 * * * *`（每 30 分钟）
- `timezone`: 时区，例如 `Asia/Shanghai`
- `dryRun`: `true` 时不真正点击发送
- `autoSend`: `true` 时允许自动发送（要求 `dryRun=false`）
- `searches`: 搜索规则列表
  - `keyword`: 职位关键词
  - `cityCode`: 城市代码（Boss Web 查询参数）
  - `locations`: 地点关键字数组（模糊匹配）
  - `salary.minK/maxK`: 薪资范围（单位 K/月）
- `greetingTemplates`: 招呼模板，支持变量：
  - `{jobTitle}`
  - `{companyName}`
  - `{location}`
  - `{salary}`

## 3. 24 小时运行建议

建议使用 PM2 或 systemd 托管，避免终端断开后进程退出。

PM2 示例：

```bash
npm i -g pm2
cd /workspace/boss-auto-greeter
pm2 start npm --name boss-auto-greeter -- start
pm2 save
```

## 4. 测试

```bash
npm test
```

当前测试覆盖薪资解析和过滤逻辑（`tests/filters.test.js`）。

## 5. 风险与合规提醒

1. 自动化行为可能触发平台风控（验证码、限流、账号限制等）。  
2. 请遵守目标平台用户协议与当地法律法规，避免高频骚扰行为。  
3. 建议先用 `dryRun=true` 长时间观察，再决定是否启用 `autoSend=true`。  
4. `selectors` 可能因页面改版失效，需要按实际页面结构调整。  
