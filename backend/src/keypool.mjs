import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const COOLDOWN_RATE_LIMIT_MS = 60_000;
const COOLDOWN_SERVER_MS = 30_000;
const DEAD_AFTER_FAILURES = 3;

export class PoolExhausted extends Error {
  constructor(message = "Нет доступных ключей пула") {
    super(message);
    this.name = "PoolExhausted";
    this.status = 503;
    this.type = "pool_exhausted";
  }
}

function deriveKey(secret, salt) {
  return crypto.scryptSync(secret, salt, 32);
}

export function encryptPool(entries, secret, file) {
  const salt = crypto.randomBytes(16);
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", deriveKey(secret, salt), iv);
  const data = Buffer.concat([
    cipher.update(JSON.stringify(entries), "utf8"),
    cipher.final(),
  ]);
  const payload = {
    version: 1,
    alg: "aes-256-gcm",
    salt: salt.toString("base64"),
    iv: iv.toString("base64"),
    tag: cipher.getAuthTag().toString("base64"),
    data: data.toString("base64"),
  };
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(payload, null, 2), { mode: 0o600 });
  return file;
}

export function decryptPool(file, secret) {
  const raw = JSON.parse(fs.readFileSync(file, "utf8"));
  if (raw.version !== 1 || raw.alg !== "aes-256-gcm") {
    throw new Error("Неподдерживаемый формат файла пула");
  }
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    deriveKey(secret, Buffer.from(raw.salt, "base64")),
    Buffer.from(raw.iv, "base64"),
  );
  decipher.setAuthTag(Buffer.from(raw.tag, "base64"));
  const plain = Buffer.concat([
    decipher.update(Buffer.from(raw.data, "base64")),
    decipher.final(),
  ]);
  return JSON.parse(plain.toString("utf8"));
}

export class AnthropicKeyPool {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
    this.file = path.join(config.dataDir, "pool", "anthropic.json");
    this.entries = [];
    this.cursor = 0;
    this.load();
  }

  get enabled() {
    return this.entries.length > 0;
  }

  load() {
    if (!this.config.pool.secret) {
      this.entries = [];
      return;
    }
    if (!fs.existsSync(this.file)) {
      this.entries = [];
      return;
    }
    try {
      this.entries = decryptPool(this.file, this.config.pool.secret).map(normalize);
    } catch (err) {
      this.log(`pool: load failed: ${err.message}`);
      this.entries = [];
    }
  }

  reload() {
    this.load();
    return this.entries.length;
  }

  importKeys(keys, { label = "" } = {}) {
    if (!this.config.pool.secret) throw new Error("CLAUDEN_POOL_SECRET не задан");
    const now = Date.now();
    const fresh = keys
      .map((value) => String(value || "").trim())
      .filter((value) => value.startsWith("sk-ant-"))
      .map((key, index) => ({
        id: `key_${crypto.createHash("sha256").update(key).digest("hex").slice(0, 10)}`,
        key,
        label: label || `pool-${index + 1}`,
        addedAt: now,
        lastUsedAt: 0,
        failures: 0,
        state: "active",
        cooldownUntil: 0,
        lastError: "",
      }));
    const merged = new Map(this.entries.map((entry) => [entry.id, entry]));
    for (const entry of fresh) merged.set(entry.id, entry);
    this.entries = [...merged.values()];
    encryptPool(this.entries, this.config.pool.secret, this.file);
    return this.entries.length;
  }

  get(keyId) {
    return this.entries.find((entry) => entry.id === keyId) || null;
  }

  usable(now = Date.now()) {
    return this.entries.filter(
      (entry) => entry.state === "active" && entry.cooldownUntil <= now,
    );
  }

  pick() {
    const now = Date.now();
    const usable = this.usable(now).sort(
      (a, b) => (a.lastUsedAt || 0) - (b.lastUsedAt || 0),
    );
    if (!usable.length) {
      const cooling = this.entries.filter(
        (entry) => entry.state === "active" && entry.cooldownUntil > now,
      );
      if (cooling.length) {
        const soonest = cooling.sort((a, b) => a.cooldownUntil - b.cooldownUntil)[0];
        throw new PoolExhausted(
          `Все ключи на паузе, ближайший вернётся через ${Math.max(
            1,
            Math.ceil((soonest.cooldownUntil - now) / 1000),
          )} с`,
        );
      }
      throw new PoolExhausted();
    }
    const entry = usable[this.cursor % usable.length];
    this.cursor += 1;
    entry.lastUsedAt = now;
    return entry;
  }

  reportSuccess(entry) {
    entry.failures = 0;
    entry.lastError = "";
    if (entry.state !== "active") {
      entry.state = "active";
    }
  }

  reportFailure(entry, { status = 0, message = "" } = {}) {
    entry.failures += 1;
    entry.lastError = String(message || `http ${status}`).slice(0, 300);
    entry.lastUsedAt = Date.now();
    if (status === 401 || status === 403) {
      entry.state = "invalid";
      this.log(`pool: key ${entry.id} ${entry.label} rejected (${status})`);
      return "invalid";
    }
    if (status === 429) {
      entry.cooldownUntil = Date.now() + COOLDOWN_RATE_LIMIT_MS;
      return "cooldown";
    }
    if (status >= 500) {
      entry.cooldownUntil = Date.now() + COOLDOWN_SERVER_MS;
      return entry.failures >= DEAD_AFTER_FAILURES ? "degraded" : "cooldown";
    }
    return "failure";
  }

  revive(entry) {
    entry.state = "active";
    entry.failures = 0;
    entry.cooldownUntil = 0;
    entry.lastError = "";
  }

  status() {
    const now = Date.now();
    return {
      enabled: this.enabled,
      total: this.entries.length,
      active: this.entries.filter((entry) => entry.state === "active").length,
      cooling: this.entries.filter(
        (entry) => entry.state === "active" && entry.cooldownUntil > now,
      ).length,
      invalid: this.entries.filter((entry) => entry.state === "invalid").length,
      keys: this.entries.map((entry) => ({
        id: entry.id,
        label: entry.label,
        state: entry.state,
        failures: entry.failures,
        cooldownMs: Math.max(0, entry.cooldownUntil - now),
        lastUsedAt: entry.lastUsedAt,
        fingerprint: fingerprint(entry.key),
      })),
    };
  }
}

function normalize(entry) {
  return {
    id: String(entry.id),
    key: String(entry.key),
    label: String(entry.label || ""),
    addedAt: Number(entry.addedAt) || 0,
    lastUsedAt: Number(entry.lastUsedAt) || 0,
    failures: Number(entry.failures) || 0,
    state: entry.state === "invalid" ? "invalid" : "active",
    cooldownUntil: Number(entry.cooldownUntil) || 0,
    lastError: String(entry.lastError || ""),
  };
}

function fingerprint(key) {
  const value = String(key || "");
  if (value.length < 8) return "";
  return `…${value.slice(-4)}`;
}
