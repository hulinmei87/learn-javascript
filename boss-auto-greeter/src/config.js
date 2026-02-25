import { constants } from "node:fs";
import { access, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const DEFAULT_SELECTORS = {
  jobCard: ".job-card-wrapper",
  jobTitle: [".job-name", ".job-title", ".job-name a"],
  salary: [".salary"],
  company: [".company-name", ".company-text"],
  location: [".job-area", ".company-location"],
  detailLink: "a[href*='/job_detail/']",
  contactButtons: [
    "button:has-text('立即沟通')",
    "a:has-text('立即沟通')",
    "button:has-text('沟通')",
    "a:has-text('沟通')"
  ],
  messageInputs: [
    "textarea",
    "div[contenteditable='true']",
    ".chat-editor [contenteditable='true']"
  ],
  sendButtons: [
    "button:has-text('发送')",
    "button:has-text('Send')",
    ".op-btn"
  ]
};

const DEFAULT_CONFIG = {
  cron: "*/30 * * * *",
  timezone: "Asia/Shanghai",
  runOnStartup: true,
  dryRun: true,
  autoSend: false,
  maxJobsPerSearch: 50,
  maxGreetingsPerRound: 20,
  storePath: "./state/processed-jobs.json",
  session: {
    headless: false,
    slowMo: 100,
    loginWaitMs: 180000,
    resultWaitMs: 2500,
    storageStatePath: "./state/storage-state.json"
  },
  selectors: DEFAULT_SELECTORS,
  searches: [],
  greetingTemplates: [
    "你好，我对{jobTitle}岗位很感兴趣，希望进一步沟通。",
    "您好，我有相关经验，想了解{companyName}的{jobTitle}岗位细节。"
  ]
};

function asPositiveInteger(value, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) {
    return fallback;
  }

  return Math.floor(number);
}

function asArray(value) {
  if (!value) {
    return [];
  }

  return Array.isArray(value) ? value : [value];
}

function normalizeSearch(search) {
  return {
    keyword: String(search.keyword ?? "").trim(),
    cityCode: String(search.cityCode ?? ""),
    locations: asArray(search.locations).map((item) => String(item).trim()).filter(Boolean),
    salary: search.salary
      ? {
          minK: search.salary.minK != null ? Number(search.salary.minK) : undefined,
          maxK: search.salary.maxK != null ? Number(search.salary.maxK) : undefined
        }
      : undefined
  };
}

function normalizeConfig(raw, absolutePath) {
  const configDir = path.dirname(absolutePath);

  const merged = {
    ...DEFAULT_CONFIG,
    ...raw,
    session: {
      ...DEFAULT_CONFIG.session,
      ...(raw.session ?? {})
    },
    selectors: {
      ...DEFAULT_SELECTORS,
      ...(raw.selectors ?? {})
    }
  };

  merged.searches = asArray(merged.searches).map(normalizeSearch).filter((item) => item.keyword);
  merged.greetingTemplates = asArray(merged.greetingTemplates).map((line) => String(line).trim()).filter(Boolean);
  merged.maxJobsPerSearch = asPositiveInteger(merged.maxJobsPerSearch, DEFAULT_CONFIG.maxJobsPerSearch);
  merged.maxGreetingsPerRound = asPositiveInteger(
    merged.maxGreetingsPerRound,
    DEFAULT_CONFIG.maxGreetingsPerRound
  );
  merged.session.loginWaitMs = asPositiveInteger(merged.session.loginWaitMs, DEFAULT_CONFIG.session.loginWaitMs);
  merged.session.resultWaitMs = asPositiveInteger(merged.session.resultWaitMs, DEFAULT_CONFIG.session.resultWaitMs);
  merged.session.slowMo = Math.max(0, Number(merged.session.slowMo) || 0);

  merged.storePath = path.resolve(configDir, merged.storePath);
  merged.session.storageStatePath = path.resolve(configDir, merged.session.storageStatePath);
  merged.__configPath = absolutePath;

  return merged;
}

function validateConfig(config) {
  if (!config.searches.length) {
    throw new Error("Config validation failed: at least one search rule is required.");
  }

  if (!config.greetingTemplates.length) {
    throw new Error("Config validation failed: greetingTemplates cannot be empty.");
  }

  if (config.autoSend && config.dryRun) {
    throw new Error("Config validation failed: set dryRun=false when autoSend=true.");
  }
}

export function resolveConfigPath(configPath = process.env.BOSS_BOT_CONFIG ?? "./config.json") {
  return path.resolve(configPath);
}

export function parseConfigObject(rawConfig, configPath = process.env.BOSS_BOT_CONFIG ?? "./config.json") {
  const absolutePath = resolveConfigPath(configPath);
  const config = normalizeConfig(rawConfig, absolutePath);
  validateConfig(config);
  return config;
}

export async function loadRawConfig(configPath = process.env.BOSS_BOT_CONFIG ?? "./config.json") {
  const absolutePath = resolveConfigPath(configPath);

  try {
    await access(absolutePath, constants.R_OK);
  } catch {
    throw new Error(
      `Config file not found: ${absolutePath}. Copy config.example.json to config.json and update settings first.`
    );
  }

  const fileContent = await readFile(absolutePath, "utf8");
  try {
    return JSON.parse(fileContent);
  } catch (error) {
    throw new Error(`Unable to parse JSON config: ${error.message}`);
  }
}

export async function saveRawConfig(
  rawConfig,
  configPath = process.env.BOSS_BOT_CONFIG ?? "./config.json"
) {
  const absolutePath = resolveConfigPath(configPath);
  parseConfigObject(rawConfig, absolutePath);
  await writeFile(absolutePath, `${JSON.stringify(rawConfig, null, 2)}\n`, "utf8");
  return absolutePath;
}

export async function loadConfig(configPath = process.env.BOSS_BOT_CONFIG ?? "./config.json") {
  const absolutePath = path.resolve(configPath);

  const rawConfig = await loadRawConfig(absolutePath);
  return parseConfigObject(rawConfig, absolutePath);
}

export const defaultSelectors = DEFAULT_SELECTORS;
