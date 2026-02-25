import cron from "node-cron";
import { loadConfig } from "./config.js";
import { createLogger } from "./logger.js";
import { ProcessedStore } from "./store.js";
import { BossWebClient } from "./bossWebClient.js";
import { buildJobKey, jobMatches } from "./filters.js";
import { renderGreeting } from "./greeting.js";

const logger = createLogger();

async function main() {
  const configPath = process.env.BOSS_BOT_CONFIG ?? "./config.json";
  const config = await loadConfig(configPath);

  logger.info("Configuration loaded.", {
    configPath: config.__configPath,
    cron: config.cron,
    timezone: config.timezone,
    dryRun: config.dryRun,
    autoSend: config.autoSend
  });

  const store = new ProcessedStore(config.storePath, logger);
  await store.init();
  logger.info("Processed store loaded.", { count: store.size(), path: config.storePath });

  const client = new BossWebClient(config, logger);
  await client.init();
  await client.ensureLogin();

  let running = false;
  const runRound = async () => {
    if (running) {
      logger.warn("Last round is still running. Current trigger skipped.");
      return;
    }

    running = true;
    const stats = {
      fetched: 0,
      matched: 0,
      skippedDuplicate: 0,
      sent: 0,
      dryRun: 0,
      failed: 0
    };

    logger.info("A new round started.");
    try {
      for (const search of config.searches) {
        if (stats.sent + stats.dryRun >= config.maxGreetingsPerRound) {
          logger.info("Round reached maxGreetingsPerRound, remaining searches skipped.");
          break;
        }

        await client.openSearch(search);
        const jobs = await client.collectJobs(config.maxJobsPerSearch);
        stats.fetched += jobs.length;

        const matchedJobs = jobs.filter((job) => jobMatches(job, search));
        stats.matched += matchedJobs.length;

        logger.info("Search fetched jobs.", {
          keyword: search.keyword,
          fetched: jobs.length,
          matched: matchedJobs.length
        });

        for (const job of matchedJobs) {
          if (stats.sent + stats.dryRun >= config.maxGreetingsPerRound) {
            logger.info("Round reached maxGreetingsPerRound, job loop stopped.");
            break;
          }

          const key = buildJobKey(job);
          if (store.has(key)) {
            stats.skippedDuplicate += 1;
            continue;
          }

          const message = renderGreeting(config.greetingTemplates, job);
          const result = await client.sendGreeting(job, message, {
            dryRun: config.dryRun,
            autoSend: config.autoSend
          });

          if (result.status === "sent" || result.status === "dry_run") {
            await store.mark(key, {
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
            logger.warn("Greeting action failed.", {
              id: job.id,
              reason: result.reason ?? "unknown"
            });
          }
        }
      }
    } catch (error) {
      logger.error("Round failed.", error.stack ?? error.message);
    } finally {
      running = false;
      logger.info("Round finished.", stats);
    }
  };

  const task = cron.schedule(
    config.cron,
    () => {
      void runRound();
    },
    {
      timezone: config.timezone
    }
  );

  task.start();
  logger.info("Scheduler started and waiting for next trigger.");

  if (config.runOnStartup) {
    await runRound();
  }

  let stopping = false;
  const shutdown = async (signal) => {
    if (stopping) {
      return;
    }

    stopping = true;
    logger.info(`Received ${signal}, shutting down.`);
    task.stop();
    await client.close();
    process.exit(0);
  };

  process.on("SIGINT", () => {
    void shutdown("SIGINT");
  });

  process.on("SIGTERM", () => {
    void shutdown("SIGTERM");
  });
}

main().catch((error) => {
  logger.error("Program exited with error.", error.stack ?? error.message);
  process.exit(1);
});
