import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");

export function loadEnvFile(file) {
  let raw;
  try {
    raw = fs.readFileSync(file, "utf8");
  } catch {
    return;
  }
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
}

loadEnvFile(path.join(ROOT, ".env"));

function env(name, fallback = "") {
  const value = process.env[name];
  return value === undefined || value === "" ? fallback : value;
}

function bool(name, fallback) {
  const value = process.env[name];
  if (value === undefined || value === "") return fallback;
  return /^(1|true|yes|on)$/i.test(String(value));
}

function int(name, fallback) {
  const n = Number.parseInt(String(process.env[name] ?? ""), 10);
  return Number.isFinite(n) ? n : fallback;
}

function list(name) {
  return env(name)
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

export const config = {
  root: ROOT,
  host: env("CLAUDEN_HOST", "127.0.0.1"),
  port: int("CLAUDEN_PORT", 8788),

  publicBase: env("CLAUDEN_PUBLIC_BASE", "http://127.0.0.1:8788").replace(/\/+$/, ""),

  dataDir: path.resolve(ROOT, env("CLAUDEN_DATA_DIR", "data")),

  serviceName: env("CLAUDEN_SERVICE_NAME", "clauden-gateway"),

  upstream: {
    base: env("CLAUDEN_UPSTREAM_BASE", "").replace(/\/+$/, ""),
    key: env("CLAUDEN_UPSTREAM_KEY", ""),
    model: env("CLAUDEN_UPSTREAM_MODEL", "clauden-chat"),
    timeoutMs: int("CLAUDEN_UPSTREAM_TIMEOUT_MS", 10 * 60_000),
    maxTokens: int("CLAUDEN_UPSTREAM_MAX_TOKENS", 64_000),
  },

  accounts: {
    relay: env("CLAUDEN_ACCOUNTS_RELAY", "https://clauden.best/partner/accounts"),
    secret: env("CLAUDEN_ACCOUNTS_SECRET", ""),
    timeoutMs: int("CLAUDEN_ACCOUNTS_TIMEOUT_MS", 30_000),
  },

  auth: {
    tokenTtlMs: int("CLAUDEN_TOKEN_TTL_MS", 30 * 24 * 60 * 60 * 1000),
    codeTtlMs: int("CLAUDEN_CODE_TTL_MS", 10 * 60 * 1000),
    googleClientIds: list("CLAUDEN_GOOGLE_CLIENT_IDS"),
    bridgeSecret: env("CLAUDEN_BRIDGE_SECRET", ""),

    devCodes: bool("CLAUDEN_DEV_CODES", true),
  },

  mail: {
    provider: env("CLAUDEN_MAIL_PROVIDER", ""),
    apiKey: env("CLAUDEN_MAIL_API_KEY", ""),
    from: env("CLAUDEN_MAIL_FROM", "Clauden <no-reply@clauden.best>"),
  },

  billing: {
    currency: env("CLAUDEN_CURRENCY", "USD"),
    rate: Number(env("CLAUDEN_USD_RUB_RATE", "0")) || 0,
    platega: {
      base: env("CLAUDEN_PLATEGA_BASE", "https://app.platega.io"),
      merchant: env("CLAUDEN_PLATEGA_MERCHANT", ""),
      secret: env("CLAUDEN_PLATEGA_SECRET", ""),
      subscriptions: bool("CLAUDEN_PLATEGA_SUBSCRIPTIONS", true),
    },
    lava: {
      base: env("CLAUDEN_LAVA_BASE", "https://gate.lava.top"),
      apiKey: env("CLAUDEN_LAVA_API_KEY", ""),
      offerId: env("CLAUDEN_LAVA_OFFER_ID", ""),
    },
    xrocket: {
      base: env("CLAUDEN_XROCKET_BASE", "https://pay.xrocket.tg"),
      token: env("CLAUDEN_XROCKET_TOKEN", ""),
    },
  },

  adminKey: env("CLAUDEN_ADMIN_KEY", ""),

  code: {
    exec: bool("CLAUDEN_CODE_EXEC", false),
  },

  limits: {
    maxConcurrentTurns: int("CLAUDEN_MAX_CONCURRENT", 8),
    maxBodyBytes: int("CLAUDEN_MAX_BODY_BYTES", 32 * 1024 * 1024),
    turnTimeoutMs: int("CLAUDEN_TURN_TIMEOUT_MS", 15 * 60_000),
  },

  turnTtlMs: int("CLAUDEN_TURN_TTL_MS", 3 * 24 * 60 * 60 * 1000),

  logRequests: bool("CLAUDEN_LOG_REQUESTS", false),
};

export function ensureDataDir() {
  fs.mkdirSync(config.dataDir, { recursive: true });
  return config.dataDir;
}

export function hasUpstream() {
  return Boolean(config.upstream.base);
}
