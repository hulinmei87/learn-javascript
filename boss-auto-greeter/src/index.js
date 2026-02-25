import path from "node:path";
import { fileURLToPath } from "node:url";
import { BotManager } from "./botManager.js";
import { createControlServer } from "./controlServer.js";
import { createLogger } from "./logger.js";

const logger = createLogger();

function asBooleanEnv(value, fallback) {
  if (value == null || value === "") {
    return fallback;
  }

  const normalized = String(value).trim().toLowerCase();
  if (["1", "true", "yes", "y", "on"].includes(normalized)) {
    return true;
  }

  if (["0", "false", "no", "n", "off"].includes(normalized)) {
    return false;
  }

  return fallback;
}

async function main() {
  const configPath = process.env.BOSS_BOT_CONFIG ?? "./config.json";
  const host = process.env.CONTROL_HOST ?? "0.0.0.0";
  const port = Number(process.env.CONTROL_PORT ?? 8787);
  const authToken = process.env.CONTROL_TOKEN || "";
  const autoStart = asBooleanEnv(process.env.BOSS_BOT_AUTO_START, true);
  const currentDir = path.dirname(fileURLToPath(import.meta.url));
  const staticDir = path.resolve(currentDir, "../web");

  const manager = new BotManager({
    configPath,
    logger
  });
  await manager.init();

  logger.info("Manager initialized.", {
    configPath: manager.configPath,
    autoStart
  });

  if (!authToken) {
    logger.warn("CONTROL_TOKEN not set. Dashboard APIs are not protected by token.");
  }

  const controlServer = createControlServer({
    manager,
    logger,
    host,
    port: Number.isFinite(port) ? port : 8787,
    staticDir,
    authToken
  });
  await controlServer.start();

  logger.info("Dashboard ready.", {
    url: `http://${host}:${Number.isFinite(port) ? port : 8787}`
  });

  if (autoStart) {
    await manager.start();
  } else {
    logger.info("Auto start disabled. Use dashboard button or /api/start to launch.");
  }

  let stopping = false;
  const shutdown = async (signal) => {
    if (stopping) {
      return;
    }

    stopping = true;
    logger.info(`Received ${signal}, shutting down.`);
    await controlServer.stop().catch(() => undefined);
    await manager.shutdown().catch(() => undefined);
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
