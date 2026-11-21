import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

export function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return fallback;
  }
}

export function writeJson(file, value) {
  const dir = path.dirname(file);
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2), { mode: 0o600 });
  fs.renameSync(tmp, file);
}

export function newId(prefix, bytes = 12) {
  const raw = crypto.randomBytes(bytes).toString("hex");
  return prefix ? `${prefix}_${raw}` : raw;
}

export function hashToken(token) {
  return crypto.createHash("sha256").update(String(token)).digest("hex");
}

export class JsonStore {

  constructor(file, initial = {}) {
    this.file = file;
    this.data = readJson(file, null) || structuredClone(initial);
    this.chain = Promise.resolve();
  }

  get() {
    return this.data;
  }

  update(fn) {
    const run = async () => {
      const result = await fn(this.data);
      writeJson(this.file, this.data);
      return result;
    };
    const next = this.chain.then(run, run);
    this.chain = next.catch(() => {});
    return next;
  }

  flush() {
    writeJson(this.file, this.data);
  }
}

export class DocStore {
  constructor(dir) {
    this.dir = dir;
    fs.mkdirSync(dir, { recursive: true });
  }

  pathFor(id) {
    const safe = String(id).replace(/[^A-Za-z0-9_.:-]/g, "_");
    return path.join(this.dir, `${safe}.json`);
  }

  get(id) {
    return readJson(this.pathFor(id), null);
  }

  put(id, value) {
    writeJson(this.pathFor(id), value);
    return value;
  }

  delete(id) {
    try {
      fs.unlinkSync(this.pathFor(id));
      return true;
    } catch {
      return false;
    }
  }

  list() {
    let names = [];
    try {
      names = fs.readdirSync(this.dir);
    } catch {
      return [];
    }
    return names
      .filter((name) => name.endsWith(".json"))
      .map((name) => readJson(path.join(this.dir, name), null))
      .filter(Boolean);
  }
}
