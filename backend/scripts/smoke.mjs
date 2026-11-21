import { spawn } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const PORT = 18790;
const POOL_PORT = 18791;
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "clauden-smoke-"));
const adminKey = "smoke-admin-key";
const base = `http://127.0.0.1:${PORT}`;

const children = [];

function start(name, args, env) {
  const child = spawn(process.execPath, args, {
    cwd: ROOT,
    env: { ...process.env, ...env },
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.on("data", (data) => process.stdout.write(`[${name}] ${data}`));
  child.stderr.on("data", (data) => process.stderr.write(`[${name}] ${data}`));
  children.push(child);
  return child;
}

async function waitFor(url, attempts = 50) {
  for (let i = 0; i < attempts; i += 1) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {

    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`timeout waiting for ${url}`);
}

function assert(condition, message) {
  if (!condition) throw new Error(`assertion failed: ${message}`);
}

async function json(pathname, options = {}) {
  const res = await fetch(base + pathname, options);
  const text = await res.text();
  let payload = {};
  try {
    payload = JSON.parse(text);
  } catch {
    payload = { raw: text };
  }
  return { status: res.status, payload };
}

try {
  start("pool", ["scripts/mock-pool.mjs"], { MOCK_POOL_PORT: String(POOL_PORT) });
  start("gateway", ["src/server.mjs"], {
    CLAUDEN_PORT: String(PORT),
    CLAUDEN_DATA_DIR: dataDir,
    CLAUDEN_ADMIN_KEY: adminKey,
    CLAUDEN_DEV_CODES: "1",
    CLAUDEN_UPSTREAM_BASE: `http://127.0.0.1:${POOL_PORT}`,
    CLAUDEN_UPSTREAM_KEY: "smoke",
  });

  await waitFor(`http://127.0.0.1:${POOL_PORT}/health`);
  await waitFor(`${base}/health`);

  const models = await json("/v1/models");
  assert(models.status === 200 && models.payload.data.length > 0, "models list");

  const plans = await json("/v1/plans");
  assert(plans.payload.data.some((plan) => plan.id === "Pro"), "plans list");

  const email = `smoke-${Date.now()}@example.com`;
  const codeRes = await json("/v1/auth/email/code", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email }),
  });
  assert(codeRes.status === 200 && codeRes.payload.devCode, "dev code");

  const reg = await json("/v1/auth/email/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, code: codeRes.payload.devCode, password: "smoke-password" }),
  });
  assert(reg.status === 200 && reg.payload.token, "registration");
  const token = reg.payload.token;
  const authHeader = { authorization: `Bearer ${token}`, "content-type": "application/json" };

  const unauthorized = await json("/v1/auth/me");
  assert(unauthorized.status === 401, "auth required");

  const me = await json("/v1/auth/me", { headers: authHeader });
  assert(me.payload.user.plan === "Free", "default plan");

  const grant = await json("/v1/admin/plan", {
    method: "POST",
    headers: { "content-type": "application/json", "x-admin-key": adminKey },
    body: JSON.stringify({ user: email, plan: "Pro" }),
  });
  assert(grant.status === 200, "admin grant");

  const once = await json("/v1/chat/completions", {
    method: "POST",
    headers: authHeader,
    body: JSON.stringify({
      model: "claude-sonnet-5-5",
      stream: false,
      messages: [{ role: "user", content: "ping" }],
    }),
  });
  assert(once.status === 200, "chat completion status");
  assert(String(once.payload.choices?.[0]?.message?.content).includes("echo"), "chat content");

  const conv = "smoke-conversation";
  const streamRes = await fetch(`${base}/v1/chat/completions`, {
    method: "POST",
    headers: { ...authHeader, "x-conversation-id": conv },
    body: JSON.stringify({
      model: "claude-sonnet-5-5",
      stream: true,
      messages: [{ role: "user", content: "stream" }],
    }),
  });
  assert(streamRes.status === 200, "stream status");
  const raw = await streamRes.text();
  assert(raw.includes("chat.completion.chunk"), "stream chunks");
  assert(raw.includes("[DONE]"), "stream done marker");

  const anthropic = await fetch(`${base}/v1/messages`, {
    method: "POST",
    headers: { ...authHeader, "anthropic-version": "2023-06-01" },
    body: JSON.stringify({
      model: "claude-sonnet-5-5",
      max_tokens: 256,
      messages: [{ role: "user", content: "ping anthropic" }],
    }),
  });
  assert(anthropic.status === 200, "anthropic messages status");
  const anthropicBody = await anthropic.json();
  assert(anthropicBody.type === "message", "anthropic message shape");
  assert(String(anthropicBody.content?.[0]?.text).includes("echo"), "anthropic content");

  const responses = await fetch(`${base}/v1/responses`, {
    method: "POST",
    headers: authHeader,
    body: JSON.stringify({ model: "claude-sonnet-5-5", input: "ping responses" }),
  });
  assert(responses.status === 200, "responses status");
  const responsesBody = await responses.json();
  assert(responsesBody.object === "response", "responses object");
  assert(
    String(responsesBody.output?.[0]?.content?.[0]?.text).includes("echo"),
    "responses content",
  );

  const active = await json(`/v1/turn/active?conversation=${conv}`, { headers: authHeader });
  assert(active.payload.turn && active.payload.turn.status === "done", "turn journal");
  assert(active.payload.turn.text.includes("echo"), "turn text");

  const usage = await json("/v1/usage", { headers: authHeader });
  assert(usage.payload.usage.plan === "Pro", "usage plan");
  assert(usage.payload.usage.window.used >= 2, "usage counted");

  const backup = await json("/v1/chats/backup", {
    method: "POST",
    headers: authHeader,
    body: JSON.stringify({
      conversations: [{ id: "chat-1", title: "Smoke", messages: [], updatedAt: Date.now() }],
    }),
  });
  assert(backup.payload.total === 1, "chat backup");
  const chatList = await json("/v1/chats", { headers: authHeader });
  assert(chatList.payload.data.length === 1, "chat list");

  const key = await json("/v1/keys", { method: "POST", headers: authHeader, body: "{}" });
  assert(key.status === 200 && key.payload.token.startsWith("sk-cld-ushw-"), "api key");

  const viaKey = await json("/v1/models", { headers: { authorization: `Bearer ${key.payload.token}` } });
  assert(viaKey.status === 200, "api key auth");

  const acc = await json("/v1/accounts/me", { headers: authHeader });
  assert(acc.status === 200, "accounts me status");
  assert(acc.payload.data.provider === "external", "accounts me provider");
  assert(acc.payload.data.supplier.includes("поставщик"), "accounts me placeholder");
  assert(acc.payload.data.account.status === "none", "no seat before payment");

  const health = await json("/health");
  assert(
    health.payload.accounts.endpoint.includes("поставщик не разрешил"),
    "supplier placeholder",
  );

  console.log("\nsmoke: all checks passed");
  process.exitCode = 0;
} catch (err) {
  console.error(`\nsmoke: FAILED — ${err.message}`);
  process.exitCode = 1;
} finally {
  for (const child of children) child.kill("SIGTERM");
  fs.rmSync(dataDir, { recursive: true, force: true });
}
