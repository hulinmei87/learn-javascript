import path from "node:path";
import { mkdir, readFile, writeFile } from "node:fs/promises";

export class ProcessedStore {
  constructor(filePath, logger) {
    this.filePath = filePath;
    this.logger = logger;
    this.records = {};
  }

  async init() {
    await mkdir(path.dirname(this.filePath), { recursive: true });

    try {
      const raw = await readFile(this.filePath, "utf8");
      this.records = JSON.parse(raw);
    } catch (error) {
      if (error.code === "ENOENT") {
        this.records = {};
        await this.persist();
        return;
      }

      this.logger.warn("Failed to read store file, fallback to empty store.", error.message);
      this.records = {};
    }
  }

  has(key) {
    return Object.prototype.hasOwnProperty.call(this.records, key);
  }

  async mark(key, payload = {}) {
    this.records[key] = {
      ...payload,
      processedAt: new Date().toISOString()
    };

    await this.persist();
  }

  size() {
    return Object.keys(this.records).length;
  }

  async persist() {
    await writeFile(this.filePath, `${JSON.stringify(this.records, null, 2)}\n`, "utf8");
  }
}
