import http from "node:http";
import path from "node:path";
import { readFile } from "node:fs/promises";

function json(res, statusCode, payload) {
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8"
  });
  res.end(`${JSON.stringify(payload)}\n`);
}

function text(res, statusCode, payload) {
  res.writeHead(statusCode, {
    "Content-Type": "text/plain; charset=utf-8"
  });
  res.end(payload);
}

function parseBearerToken(authorizationHeader) {
  if (!authorizationHeader) {
    return null;
  }

  const matched = String(authorizationHeader).match(/^Bearer\s+(.+)$/i);
  return matched ? matched[1] : null;
}

function isApiPath(urlPathname) {
  return urlPathname.startsWith("/api/");
}

function readRequestBody(req, { sizeLimit = 1024 * 1024 } = {}) {
  return new Promise((resolve, reject) => {
    let bytes = 0;
    const chunks = [];

    req.on("data", (chunk) => {
      bytes += chunk.length;
      if (bytes > sizeLimit) {
        reject(new Error("Request body too large."));
        req.destroy();
        return;
      }

      chunks.push(chunk);
    });

    req.on("end", () => {
      resolve(Buffer.concat(chunks).toString("utf8"));
    });

    req.on("error", reject);
  });
}

async function readRequestJson(req) {
  const raw = await readRequestBody(req);
  if (!raw.trim()) {
    return {};
  }

  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(`JSON parse failed: ${error.message}`);
  }
}

function contentTypeByPath(filePath) {
  if (filePath.endsWith(".html")) {
    return "text/html; charset=utf-8";
  }

  if (filePath.endsWith(".css")) {
    return "text/css; charset=utf-8";
  }

  if (filePath.endsWith(".js")) {
    return "application/javascript; charset=utf-8";
  }

  return "application/octet-stream";
}

async function serveStaticFile(res, filePath) {
  try {
    const body = await readFile(filePath);
    res.writeHead(200, {
      "Content-Type": contentTypeByPath(filePath)
    });
    res.end(body);
    return true;
  } catch {
    return false;
  }
}

export function createControlServer({
  manager,
  logger,
  host = "0.0.0.0",
  port = 8787,
  staticDir,
  authToken
}) {
  const server = http.createServer(async (req, res) => {
    const method = req.method ?? "GET";
    const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
    const pathname = url.pathname;

    const verifyApiToken = () => {
      if (!authToken || !isApiPath(pathname)) {
        return true;
      }

      const fromBearer = parseBearerToken(req.headers.authorization);
      const fromQuery = url.searchParams.get("token");
      return fromBearer === authToken || fromQuery === authToken;
    };

    if (!verifyApiToken()) {
      json(res, 401, {
        error: "UNAUTHORIZED",
        message: "Missing or invalid auth token."
      });
      return;
    }

    try {
      if (method === "GET" && pathname === "/api/status") {
        json(res, 200, {
          ok: true,
          data: manager.getStatus()
        });
        return;
      }

      if (method === "GET" && pathname === "/api/config") {
        json(res, 200, {
          ok: true,
          data: manager.getEditableConfig()
        });
        return;
      }

      if (method === "POST" && pathname === "/api/start") {
        const result = await manager.start();
        json(res, 200, { ok: true, data: result });
        return;
      }

      if (method === "POST" && pathname === "/api/pause") {
        const result = await manager.pause();
        json(res, 200, { ok: true, data: result });
        return;
      }

      if (method === "POST" && pathname === "/api/run-once") {
        const result = await manager.runOnce();
        json(res, 200, { ok: true, data: result });
        return;
      }

      if (method === "POST" && pathname === "/api/reload") {
        const result = await manager.reloadConfigFromDisk();
        json(res, 200, { ok: true, data: result });
        return;
      }

      if (method === "PUT" && pathname === "/api/config") {
        const payload = await readRequestJson(req);
        const result = await manager.saveConfig(payload);
        json(res, 200, { ok: true, data: result });
        return;
      }

      if (method === "GET" && pathname === "/") {
        const found = await serveStaticFile(res, path.join(staticDir, "index.html"));
        if (!found) {
          text(res, 404, "Dashboard file not found.");
        }
        return;
      }

      if (method === "GET" && (pathname === "/app.js" || pathname === "/style.css")) {
        const found = await serveStaticFile(res, path.join(staticDir, pathname.slice(1)));
        if (!found) {
          text(res, 404, "Static file not found.");
        }
        return;
      }

      json(res, 404, {
        ok: false,
        error: "NOT_FOUND"
      });
    } catch (error) {
      logger.error("Control server request failed.", error.stack ?? error.message);
      json(res, 500, {
        ok: false,
        error: "INTERNAL_ERROR",
        message: error.message
      });
    }
  });

  return {
    async start() {
      await new Promise((resolve) => {
        server.listen(port, host, resolve);
      });

      logger.info("Control server started.", {
        host,
        port,
        hasToken: Boolean(authToken)
      });
    },
    async stop() {
      await new Promise((resolve) => {
        server.close(() => resolve());
      });

      logger.info("Control server stopped.");
    }
  };
}
