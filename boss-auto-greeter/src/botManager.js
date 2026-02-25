import cron from "node-cron";
import { BossWebClient } from "./bossWebClient.js";
import { buildJobKey, jobMatches } from "./filters.js";
import { renderGreeting } from "./greeting.js";
import { loadRawConfig, parseConfigObject, resolveConfigPath, saveRawConfig } from "./config.js";
import { ProcessedStore } from "./store.js";

function wait(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value));
}

export class BotManager {
  constructor({ configPath, logger }) {
    this.logger = logger;
    this.configPath = resolveConfigPath(configPath);

    this.rawConfig = null;
    this.config = null;

    this.started = false;
    this.roundRunning = false;
    this.roundRunningSince = null;
    this.currentTrigger = null;
    this.lastRoundStartedAt = null;
    this.lastRoundFinishedAt = null;
    this.lastRoundStats = null;
    this.lastError = null;

    this.store = null;
    this.client = null;
    this.task = null;
    this.stopRequested = false;

    this.operationQueue = Promise.resolve();
  }

  enqueue(operationName, fn) {
    const run = this.operationQueue.catch(() => undefined).then(async () => {
      this.logger.info(`Operation started: ${operationName}`);
      const result = await fn();
      this.logger.info(`Operation finished: ${operationName}`);
      return result;
    });

    this.operationQueue = run.catch(() => undefined);
    return run;
  }

  async init() {
    await this.reloadConfigFromDisk({ restartIfRunning: false });
  }

  getStatus() {
    return {
      started: this.started,
      roundRunning: this.roundRunning,
      roundRunningSince: this.roundRunningSince,
      currentTrigger: this.currentTrigger,
      configPath: this.configPath,
      scheduler: this.config
        ? {
            cron: this.config.cron,
            timezone: this.config.timezone,
            maxJobsPerSearch: this.config.maxJobsPerSearch,
            maxGreetingsPerRound: this.config.maxGreetingsPerRound
          }
        : null,
      mode: this.config
        ? {
            dryRun: this.config.dryRun,
            autoSend: this.config.autoSend
          }
        : null,
      lastRoundStartedAt: this.lastRoundStartedAt,
      lastRoundFinishedAt: this.lastRoundFinishedAt,
      lastRoundStats: this.lastRoundStats,
      processedCount: this.store ? this.store.size() : 0,
      lastError: this.lastError
    };
  }

  getEditableConfig() {
    return this.rawConfig ? cloneJson(this.rawConfig) : null;
  }

  async reloadConfigFromDisk({ restartIfRunning = true } = {}) {
    return this.enqueue("reload_config", async () => {
      const rawConfig = await loadRawConfig(this.configPath);
      const parsedConfig = parseConfigObject(rawConfig, this.configPath);

      this.rawConfig = rawConfig;
      this.config = parsedConfig;
      this.lastError = null;

      if (restartIfRunning && this.started) {
        await this.stopUnsafe();
        await this.startUnsafe({ runOnStartup: false });
      }

      return cloneJson(rawConfig);
    });
  }

  async saveConfig(rawConfig, { restartIfRunning = true } = {}) {
    return this.enqueue("save_config", async () => {
      const parsedConfig = parseConfigObject(rawConfig, this.configPath);
      await saveRawConfig(rawConfig, this.configPath);

      this.rawConfig = cloneJson(rawConfig);
      this.config = parsedConfig;
      this.lastError = null;

      if (restartIfRunning && this.started) {
        await this.stopUnsafe();
        await this.startUnsafe({ runOnStartup: false });
      }

      return cloneJson(this.rawConfig);
    });
  }

  async start() {
    return this.enqueue("start", async () => this.startUnsafe());
  }

  async pause() {
    return this.enqueue("pause", async () => this.stopUnsafe());
  }

  async shutdown() {
    return this.enqueue("shutdown", async () => this.stopUnsafe());
  }

  async runOnce() {
    return this.enqueue("run_once", async () => this.runRound("manual"));
  }

  async startUnsafe({ runOnStartup } = {}) {
    if (this.started) {
      return { status: "already_started" };
    }

    if (!this.rawConfig || !this.config) {
      const rawConfig = await loadRawConfig(this.configPath);
      this.rawConfig = rawConfig;
      this.config = parseConfigObject(rawConfig, this.configPath);
    }

    const store = new ProcessedStore(this.config.storePath, this.logger);
    await store.init();

    const client = new BossWebClient(this.config, this.logger);
    try {
      await client.init();
      await client.ensureLogin();
    } catch (error) {
      await client.close().catch(() => undefined);
      throw error;
    }

    const scheduledTask = cron.schedule(
      this.config.cron,
      () => {
        void this.runRound("schedule");
      },
      {
        timezone: this.config.timezone
      }
    );

    scheduledTask.start();

    this.store = store;
    this.client = client;
    this.task = scheduledTask;
    this.started = true;
    this.stopRequested = false;
    this.lastError = null;

    const shouldRunOnStartup = runOnStartup ?? this.config.runOnStartup;
    if (shouldRunOnStartup) {
      await this.runRound("startup");
    }

    return { status: "started" };
  }

  async stopUnsafe() {
    if (!this.started && !this.client && !this.task) {
      return { status: "already_paused" };
    }

    if (this.task) {
      this.task.stop();
      this.task = null;
    }

    this.stopRequested = true;
    const waitLimitMs = 120000;
    const waitStart = Date.now();
    while (this.roundRunning && Date.now() - waitStart < waitLimitMs) {
      await wait(250);
    }

    if (this.client) {
      await this.client.close().catch((error) => {
        this.logger.warn("Failed to close browser context cleanly.", error.message);
      });
    }

    this.client = null;
    this.store = null;
    this.started = false;
    this.stopRequested = false;
    this.currentTrigger = null;

    return { status: "paused" };
  }

  async runRound(trigger = "manual") {
    if (!this.started || !this.client || !this.store || !this.config) {
      return { status: "inactive" };
    }

    if (this.roundRunning) {
      return { status: "busy" };
    }

    this.roundRunning = true;
    this.roundRunningSince = new Date().toISOString();
    this.currentTrigger = trigger;
    this.lastRoundStartedAt = this.roundRunningSince;
    this.lastError = null;

    const stats = {
      trigger,
      fetched: 0,
      matched: 0,
      skippedDuplicate: 0,
      sent: 0,
      dryRun: 0,
      failed: 0
    };

    this.logger.info("A new round started.", { trigger });

    try {
      for (const search of this.config.searches) {
        if (this.stopRequested) {
          this.logger.warn("Stop requested, round exits before next search.");
          break;
        }

        if (stats.sent + stats.dryRun >= this.config.maxGreetingsPerRound) {
          this.logger.info("Round reached maxGreetingsPerRound, remaining searches skipped.");
          break;
        }

        await this.client.openSearch(search);
        const jobs = await this.client.collectJobs(this.config.maxJobsPerSearch);
        stats.fetched += jobs.length;

        const matchedJobs = jobs.filter((job) => jobMatches(job, search));
        stats.matched += matchedJobs.length;

        this.logger.info("Search fetched jobs.", {
          keyword: search.keyword,
          fetched: jobs.length,
          matched: matchedJobs.length
        });

        for (const job of matchedJobs) {
          if (this.stopRequested) {
            this.logger.warn("Stop requested, round exits during job loop.");
            break;
          }

          if (stats.sent + stats.dryRun >= this.config.maxGreetingsPerRound) {
            this.logger.info("Round reached maxGreetingsPerRound, job loop stopped.");
            break;
          }

          const key = buildJobKey(job);
          if (this.store.has(key)) {
            stats.skippedDuplicate += 1;
            continue;
          }

          const message = renderGreeting(this.config.greetingTemplates, job);
          const result = await this.client.sendGreeting(job, message, {
            dryRun: this.config.dryRun,
            autoSend: this.config.autoSend
          });

          if (result.status === "sent" || result.status === "dry_run") {
            await this.store.mark(key, {
              mode: result.status,
              title: job.title,
              salaryText: job.salaryText,
              companyName: job.companyName,
              locationText: job.locationText,
              detailUrl: job.detailUrl,
              message
            });

            if (result.status === "sent") {
              stats.sent += 1;
            } else {
              stats.dryRun += 1;
            }
          } else {
            stats.failed += 1;
            this.logger.warn("Greeting action failed.", {
              id: job.id,
              reason: result.reason ?? "unknown"
            });
          }
        }
      }
    } catch (error) {
      this.lastError = error.stack ?? error.message;
      this.logger.error("Round failed.", this.lastError);
    } finally {
      this.lastRoundStats = stats;
      this.lastRoundFinishedAt = new Date().toISOString();
      this.roundRunning = false;
      this.roundRunningSince = null;
      this.currentTrigger = null;
      this.logger.info("Round finished.", stats);
    }

    return { status: "ok", stats };
  }
}
