const statusGrid = document.querySelector("#statusGrid");
const configEditor = document.querySelector("#configEditor");
const logBox = document.querySelector("#logBox");
const tokenInput = document.querySelector("#tokenInput");

const refreshStatusBtn = document.querySelector("#refreshStatusBtn");
const startBtn = document.querySelector("#startBtn");
const pauseBtn = document.querySelector("#pauseBtn");
const runOnceBtn = document.querySelector("#runOnceBtn");
const loadConfigBtn = document.querySelector("#loadConfigBtn");
const saveConfigBtn = document.querySelector("#saveConfigBtn");
const reloadConfigBtn = document.querySelector("#reloadConfigBtn");
const saveTokenBtn = document.querySelector("#saveTokenBtn");

let loading = false;

function readToken() {
  return localStorage.getItem("controlToken") ?? "";
}

function saveToken(token) {
  localStorage.setItem("controlToken", token);
}

function log(line) {
  const time = new Date().toLocaleString();
  logBox.textContent = `[${time}] ${line}\n${logBox.textContent}`.slice(0, 12000);
}

function statusPairs(status) {
  const latest = status.lastRoundStats || {};
  return [
    ["运行状态", status.started ? "运行中" : "已暂停"],
    ["回合状态", status.roundRunning ? "执行中" : "空闲"],
    ["执行触发", status.currentTrigger || "-"],
    ["已处理条数", String(status.processedCount ?? 0)],
    ["上次开始", status.lastRoundStartedAt || "-"],
    ["上次结束", status.lastRoundFinishedAt || "-"],
    ["本轮抓取", String(latest.fetched ?? 0)],
    ["本轮命中", String(latest.matched ?? 0)],
    ["已发送", String(latest.sent ?? 0)],
    ["dryRun", String(latest.dryRun ?? 0)],
    ["失败", String(latest.failed ?? 0)],
    ["去重跳过", String(latest.skippedDuplicate ?? 0)]
  ];
}

function renderStatus(status) {
  statusGrid.innerHTML = "";
  for (const [key, value] of statusPairs(status)) {
    const item = document.createElement("div");
    item.className = "status-item";
    item.innerHTML = `<span class="k">${key}</span><span class="v">${value}</span>`;
    statusGrid.append(item);
  }
}

async function api(path, { method = "GET", body } = {}) {
  const headers = {};
  const token = readToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.ok === false) {
    const message = payload.message || payload.error || `HTTP ${response.status}`;
    throw new Error(message);
  }

  return payload.data;
}

async function refreshStatus() {
  const status = await api("/api/status");
  renderStatus(status);
}

async function loadConfig() {
  const config = await api("/api/config");
  configEditor.value = JSON.stringify(config, null, 2);
}

function setButtonsDisabled(disabled) {
  const buttons = [
    refreshStatusBtn,
    startBtn,
    pauseBtn,
    runOnceBtn,
    loadConfigBtn,
    saveConfigBtn,
    reloadConfigBtn,
    saveTokenBtn
  ];

  for (const button of buttons) {
    button.disabled = disabled;
  }
}

async function runAction(label, fn) {
  if (loading) {
    return;
  }

  loading = true;
  setButtonsDisabled(true);
  try {
    await fn();
    log(`${label}：成功`);
  } catch (error) {
    log(`${label}：失败 - ${error.message}`);
  } finally {
    setButtonsDisabled(false);
    loading = false;
  }
}

saveTokenBtn.addEventListener("click", () => {
  const token = tokenInput.value.trim();
  saveToken(token);
  log("访问令牌已保存到浏览器本地存储。");
});

refreshStatusBtn.addEventListener("click", async () => {
  await runAction("刷新状态", async () => {
    await refreshStatus();
  });
});

startBtn.addEventListener("click", async () => {
  await runAction("开始", async () => {
    await api("/api/start", { method: "POST" });
    await refreshStatus();
  });
});

pauseBtn.addEventListener("click", async () => {
  await runAction("暂停", async () => {
    await api("/api/pause", { method: "POST" });
    await refreshStatus();
  });
});

runOnceBtn.addEventListener("click", async () => {
  await runAction("立即执行一次", async () => {
    await api("/api/run-once", { method: "POST" });
    await refreshStatus();
  });
});

loadConfigBtn.addEventListener("click", async () => {
  await runAction("读取配置", async () => {
    await loadConfig();
  });
});

saveConfigBtn.addEventListener("click", async () => {
  await runAction("保存配置", async () => {
    const value = configEditor.value.trim();
    if (!value) {
      throw new Error("配置不能为空");
    }

    let parsed;
    try {
      parsed = JSON.parse(value);
    } catch (error) {
      throw new Error(`JSON 格式错误：${error.message}`);
    }

    await api("/api/config", {
      method: "PUT",
      body: parsed
    });
    await refreshStatus();
  });
});

reloadConfigBtn.addEventListener("click", async () => {
  await runAction("从磁盘重载", async () => {
    await api("/api/reload", { method: "POST" });
    await loadConfig();
    await refreshStatus();
  });
});

async function bootstrap() {
  tokenInput.value = readToken();
  await Promise.all([refreshStatus(), loadConfig()]);
  setInterval(() => {
    void refreshStatus().catch((error) => {
      log(`自动刷新失败：${error.message}`);
    });
  }, 5000);
}

bootstrap().catch((error) => {
  log(`初始化失败：${error.message}`);
});
