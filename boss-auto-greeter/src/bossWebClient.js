import { constants } from "node:fs";
import path from "node:path";
import { access, mkdir } from "node:fs/promises";
import { chromium } from "playwright";

const BASE_URL = "https://www.zhipin.com";

function asArray(value) {
  if (!value) {
    return [];
  }

  return Array.isArray(value) ? value : [value];
}

async function fileExists(filePath) {
  try {
    await access(filePath, constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

function normalizeText(value) {
  return String(value ?? "").replace(/\s+/g, " ").trim();
}

function normalizeBossUrl(href) {
  if (!href) {
    return null;
  }

  if (/^https?:\/\//.test(href)) {
    return href;
  }

  return new URL(href, BASE_URL).toString();
}

function extractJobId(detailUrl) {
  if (!detailUrl) {
    return null;
  }

  const match = detailUrl.match(/job_detail\/([^./?]+)/i);
  if (match) {
    return match[1];
  }

  return null;
}

export class BossWebClient {
  constructor(config, logger) {
    this.config = config;
    this.logger = logger;
    this.browser = null;
    this.context = null;
    this.page = null;
  }

  async init() {
    const contextOptions = {};
    if (await fileExists(this.config.session.storageStatePath)) {
      contextOptions.storageState = this.config.session.storageStatePath;
      this.logger.info("Found existing session state and will reuse it.");
    }

    this.browser = await chromium.launch({
      headless: Boolean(this.config.session.headless),
      slowMo: Number(this.config.session.slowMo) || 0
    });

    this.context = await this.browser.newContext(contextOptions);
    this.page = await this.context.newPage();
    this.page.setDefaultTimeout(10000);
  }

  async close() {
    if (this.context) {
      await this.context.close();
      this.context = null;
    }

    if (this.browser) {
      await this.browser.close();
      this.browser = null;
    }
  }

  async isLoggedIn() {
    const loginMarkers = [
      "text=登录/注册",
      "a:has-text('登录')",
      "text=扫码登录",
      "text=马上登录"
    ];

    for (const marker of loginMarkers) {
      const locator = this.page.locator(marker).first();
      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        return false;
      }
    }

    return true;
  }

  async persistSession() {
    const target = this.config.session.storageStatePath;
    await mkdir(path.dirname(target), { recursive: true });
    await this.context.storageState({ path: target });
  }

  async ensureLogin() {
    await this.page.goto(`${BASE_URL}/web/geek/job`, { waitUntil: "domcontentloaded" });
    await this.page.waitForTimeout(1500);

    if (await this.isLoggedIn()) {
      await this.persistSession();
      return;
    }

    this.logger.warn(
      "Login required. Please complete the login in the opened browser window before timeout."
    );

    const timeoutMs = this.config.session.loginWaitMs;
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      if (await this.isLoggedIn()) {
        this.logger.info("Login detected, session state will be saved.");
        await this.persistSession();
        return;
      }

      await this.page.waitForTimeout(2000);
    }

    throw new Error("Login timeout reached. Increase session.loginWaitMs and retry.");
  }

  async openSearch(search) {
    const url = new URL("/web/geek/job", BASE_URL);
    url.searchParams.set("query", search.keyword);

    if (search.cityCode) {
      url.searchParams.set("city", String(search.cityCode));
    }

    await this.page.goto(url.toString(), { waitUntil: "domcontentloaded" });
    await this.page.waitForTimeout(this.config.session.resultWaitMs);
  }

  async readText(scope, selectorCandidates) {
    for (const selector of asArray(selectorCandidates)) {
      const locator = scope.locator(selector).first();
      const count = await locator.count().catch(() => 0);
      if (count === 0) {
        continue;
      }

      const text = await locator.innerText().catch(() => null);
      const normalized = normalizeText(text);
      if (normalized) {
        return normalized;
      }
    }

    return "";
  }

  async collectJobs(limit = 50) {
    const selectors = this.config.selectors;
    const cards = this.page.locator(selectors.jobCard);
    await cards.first().waitFor({ timeout: 10000 }).catch(() => null);

    const total = await cards.count();
    const size = Math.min(limit, total);
    const jobs = [];

    for (let index = 0; index < size; index += 1) {
      const card = cards.nth(index);
      const title = await this.readText(card, selectors.jobTitle);
      if (!title) {
        continue;
      }

      const salaryText = await this.readText(card, selectors.salary);
      const companyName = await this.readText(card, selectors.company);
      const locationText = await this.readText(card, selectors.location);

      const primaryHref = await card.locator(selectors.detailLink).first().getAttribute("href").catch(() => null);
      const fallbackHref = await card.locator("a").first().getAttribute("href").catch(() => null);
      const detailUrl = normalizeBossUrl(primaryHref ?? fallbackHref);
      const id = extractJobId(detailUrl) ?? `${index}:${companyName}:${title}`;

      jobs.push({
        id,
        title,
        salaryText,
        companyName,
        locationText,
        detailUrl
      });
    }

    return jobs;
  }

  async firstVisibleLocator(scope, selectors) {
    for (const selector of asArray(selectors)) {
      const locator = scope.locator(selector).first();
      const count = await locator.count().catch(() => 0);
      if (count === 0) {
        continue;
      }

      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        return locator;
      }
    }

    return null;
  }

  async sendGreeting(job, message, { dryRun, autoSend }) {
    if (!job.detailUrl) {
      return { status: "skipped", reason: "missing_detail_url" };
    }

    await this.page.goto(job.detailUrl, { waitUntil: "domcontentloaded" });
    await this.page.waitForTimeout(1000);

    const contactButton = await this.firstVisibleLocator(this.page, this.config.selectors.contactButtons);
    if (!contactButton) {
      return { status: "failed", reason: "contact_button_not_found" };
    }

    await contactButton.click({ timeout: 5000 }).catch(() => null);
    await this.page.waitForTimeout(1200);

    const input = await this.firstVisibleLocator(this.page, this.config.selectors.messageInputs);
    if (!input) {
      return { status: "failed", reason: "message_input_not_found" };
    }

    await input.click().catch(() => null);
    await input.fill(message).catch(async () => {
      await input.type(message, { delay: 30 });
    });

    if (dryRun || !autoSend) {
      this.logger.info("Dry run mode, message prepared but not sent.", { jobId: job.id, message });
      return { status: "dry_run" };
    }

    const sendButton = await this.firstVisibleLocator(this.page, this.config.selectors.sendButtons);
    if (sendButton) {
      await sendButton.click({ timeout: 5000 }).catch(() => null);
    } else {
      await this.page.keyboard.press("Enter").catch(() => null);
    }

    await this.page.waitForTimeout(800);
    return { status: "sent" };
  }
}
