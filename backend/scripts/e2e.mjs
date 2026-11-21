import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";

import { encryptPool } from "../src/keypool.mjs";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "clauden-e2e-"));
const GW_PORT = 18820;
const PLATEGA_PORT = 18821;
const SUPPLIER_PORT = 18822;
const UPSTREAM_PORT = 18823;
const ANTHROPIC_PORT = 18824;
const base = `http://127.0.0.1:${GW_PORT}`;
const children = [];
const seen = { anthropic: [], upstream: [], supplier: [], platega: [] };

function listen(handler, port) {
  const server = http.createServer(handler);
  return new Promise((resolve) => server.listen(port, "127.0.0.1", () => resolve(server)));
}

function collectText(raw) {
  const out = [];
  for (const line of raw.split("\n")) {
    if (!line.startsWith("data: ")) continue;
    try {
      const event = JSON.parse(line.slice(6));
      const delta = event.choices?.[0]?.delta;
      if (delta?.content) out.push(delta.content);
    } catch {}
  }
  return out.join("");
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
  });
}

function sse(res, events, delay = 0) {
  res.writeHead(200, { "content-type": "text/event-stream" });
  let i = 0;
  const push = () => {
    if (i >= events.length) return res.end();
    res.write(events[i]);
    i += 1;
    setTimeout(push, delay);
  };
  push();
}

function pieces(text, size = 4) {
  const out = [];
  for (let i = 0; i < text.length; i += size) out.push(text.slice(i, i + size));
  return out;
}

function openaiChunks(text) {
  return [
    ...pieces(text).map((piece) =>
      `data: ${JSON.stringify({ choices: [{ index: 0, delta: { content: piece } }] })}\n\n`),
    `data: ${JSON.stringify({ choices: [{ index: 0, delta: {}, finish_reason: "stop" }] })}\n\n`,
    "data: [DONE]\n\n",
  ];
}

function anthropicEvents(text) {
  return [
    `event: message_start\ndata: ${JSON.stringify({ type: "message_start" })}\n\n`,
    ...pieces(text).map((piece) =>
      `event: content_block_delta\ndata: ${JSON.stringify({
        type: "content_block_delta",
        delta: { type: "text_delta", text: piece },
      })}\n\n`),
    `event: message_delta\ndata: ${JSON.stringify({ type: "message_delta", delta: { stop_reason: "end_turn" } })}\n\n`,
    `event: message_stop\ndata: ${JSON.stringify({ type: "message_stop" })}\n\n`,
  ];
}

const platega = await listen(async (req, res) => {
  const body = await readBody(req);
  seen.platega.push({ url: req.url, body });
  if (req.url === "/transaction/process") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ transactionId: "tx-1", redirectUrl: "https://pay.example.com/tx-1" }));
  }
  if (req.url.startsWith("/transaction/tx-")) {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ status: "CONFIRMED" }));
  }
  res.writeHead(404).end();
}, PLATEGA_PORT);

const supplier = await listen(async (req, res) => {
  const body = await readBody(req);
  seen.supplier.push({ url: req.url, body, relay: req.headers["x-clauden-relay"] });
  if (req.url === "/v1/accounts/status") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ ok: true, stock: 5 }));
  }
  if (req.url === "/v1/accounts/issue") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({
      login: "claude-seat@example.com",
      password: "seat-password",
      token: "seat-token-live",
      plan: "Pro",
      expiresAt: Date.now() + 30 * 86400000,
    }));
  }
  res.writeHead(404).end();
}, SUPPLIER_PORT);

const upstream = await listen(async (req, res) => {
  const body = await readBody(req);
  seen.upstream.push({ account: req.headers["x-clauden-account"], body });
  if (req.headers["x-clauden-account"]) {
    res.writeHead(401, { "content-type": "application/json" });
    return res.end(JSON.stringify({ error: { message: "seat revoked" } }));
  }
  sse(res, openaiChunks("upstream answer"));
}, UPSTREAM_PORT);

const anthropic = await listen(async (req, res) => {
  const body = await readBody(req);
  const key = req.headers["x-api-key"];
  seen.anthropic.push({ key, body });
  if (key !== "sk-ant-good-key-0001") {
    res.writeHead(401, { "content-type": "application/json" });
    return res.end(JSON.stringify({ type: "error", error: { message: "invalid x-api-key" } }));
  }
  sse(res, anthropicEvents("pool answer"), 0);
}, ANTHROPIC_PORT);

encryptPool(
  [
    { id: "key_bad", key: "sk-ant-bad-key-0001", label: "dead", addedAt: 1, lastUsedAt: 0, failures: 0, state: "active", cooldownUntil: 0, lastError: "" },
    { id: "key_good", key: "sk-ant-good-key-0001", label: "live", addedAt: 1, lastUsedAt: 0, failures: 0, state: "active", cooldownUntil: 0, lastError: "" },
  ],
  "e2e-pool-secret",
  path.join(dataDir, "pool", "anthropic.json"),
);

const gateway = spawn(process.execPath, ["src/server.mjs"], {
  cwd: ROOT,
  env: {
    ...process.env,
    CLAUDEN_PORT: String(GW_PORT),
    CLAUDEN_DATA_DIR: dataDir,
    CLAUDEN_DEV_CODES: "1",
    CLAUDEN_ADMIN_KEY: "admin-e2e",
    CLAUDEN_UPSTREAM_BASE: `http://127.0.0.1:${UPSTREAM_PORT}`,
    CLAUDEN_UPSTREAM_KEY: "upstream-key",
    CLAUDEN_ACCOUNTS_RELAY: `http://127.0.0.1:${SUPPLIER_PORT}`,
    CLAUDEN_ACCOUNTS_SECRET: "relay-secret",
    CLAUDEN_POOL_SECRET: "e2e-pool-secret",
    CLAUDEN_POOL_BASE: `http://127.0.0.1:${ANTHROPIC_PORT}`,
    CLAUDEN_PLATEGA_BASE: `http://127.0.0.1:${PLATEGA_PORT}`,
    CLAUDEN_PLATEGA_MERCHANT: "merchant-e2e",
    CLAUDEN_PLATEGA_SECRET: "platega-secret",
  },
  stdio: ["ignore", "pipe", "pipe"],
});
children.push(gateway);
gateway.stdout.on("data", (data) => process.stdout.write(`[gw] ${data}`));
gateway.stderr.on("data", (data) => process.stderr.write(`[gw] ${data}`));

async function waitFor(url) {
  for (let i = 0; i < 80; i += 1) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`timeout ${url}`);
}

async function json(pathname, options = {}) {
  const res = await fetch(base + pathname, options);
  const text = await res.text();
  let payload = null;
  try {
    payload = JSON.parse(text);
  } catch {
    payload = { raw: text };
  }
  return { status: res.status, payload, text, headers: res.headers };
}

try {
  await waitFor(`${base}/health`);

  const email = `e2e-${Date.now()}@example.com`;
  const code = await json("/v1/auth/email/code", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email }),
  });
  const reg = await json("/v1/auth/email/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, code: code.payload.devCode, password: "e2e-password" }),
  });
  assert.equal(reg.status, 200, "register");
  const auth = { authorization: `Bearer ${reg.payload.token}`, "content-type": "application/json" };
  assert.equal(reg.payload.user.plan, "Free", "free by default");

  const checkout = await json("/v1/billing/checkout", {
    method: "POST",
    headers: auth,
    body: JSON.stringify({ plan: "Pro", method: "platega", autoRenew: false }),
  });
  assert.equal(checkout.status, 200, "checkout created");
  const orderId = checkout.payload.order.id;
  assert.equal(checkout.payload.order.status, "pending", "order pending");
  assert.ok(checkout.payload.order.url.includes("pay.example.com"), "payment url");
  assert.equal(checkout.payload.order.account.status, "pending", "no seat yet");
  assert.deepEqual(checkout.payload.order.events.map((e) => e.type), ["created"], "created event");

  const paid = await json(`/v1/billing/order/${orderId}`, { headers: auth });
  assert.equal(paid.payload.order.status, "paid", "order paid after provider confirm");
  assert.equal(paid.payload.order.account.status, "active", "seat issued");
  assert.equal(paid.payload.order.account.login, "cl***@example.com", "login masked");
  assert.ok(
    paid.payload.order.events.some((e) => e.type === "account_issued"),
    "issue event present",
  );
  assert.equal(seen.supplier.length >= 1, true, "supplier was called");
  assert.equal(seen.supplier.at(-1).relay, "relay-secret", "relay auth header");
  const supplierPayload = JSON.parse(seen.supplier.at(-1).body);
  assert.equal(supplierPayload.orderId, orderId, "order forwarded to supplier");
  assert.deepEqual(Object.keys(supplierPayload).sort(), ["email", "orderId", "plan"], "only order data leaves");
  assert.equal(JSON.stringify(supplierPayload).includes("hello"), false, "no chat content to supplier");

  const plans = await json("/v1/plans");
  assert.ok(plans.payload.data.some((p) => p.id === "Pro"), "plans untouched");

  const me = await json("/v1/accounts/me", { headers: auth });
  assert.equal(me.payload.data.account.status, "active", "accounts me active");
  assert.equal(me.payload.data.supplier.includes("поставщик"), true, "supplier placeholder");
  assert.equal(me.text.includes("seat-password"), false, "password never returned");
  assert.equal(me.text.includes("seat-token-live"), false, "seat token never returned");
  assert.equal(me.text.includes("claude-seat@example.com"), false, "raw login never returned");

  const stream = await fetch(`${base}/v1/chat/completions`, {
    method: "POST",
    headers: { ...auth, "x-conversation-id": "e2e-chat" },
    body: JSON.stringify({
      model: "claude-sonnet-5-5",
      stream: true,
      messages: [{ role: "user", content: "hello" }],
    }),
  });
  assert.equal(stream.status, 200, "chat status");
  const raw = await stream.text();
  assert.equal(collectText(raw), "pool answer", "answer came from the key pool");
  assert.ok(raw.includes("clauden_failover"), "failover event visible");
  assert.ok(seen.upstream.some((entry) => entry.account === "seat-token-live"), "seat tried first");
  assert.ok(seen.anthropic.length >= 2, "pool tried a key");
  assert.ok(seen.anthropic.some((entry) => entry.key === "sk-ant-good-key-0001"), "good key used");
  assert.equal(
    seen.anthropic.every((entry) => entry.account === undefined),
    true,
    "no seat token sent to the key pool",
  );
  assert.equal(raw.includes("sk-ant-"), false, "no key leaked in stream");
  assert.equal(raw.includes("anthropic.com"), false, "no provider host leaked");
  assert.equal(raw.includes(`127.0.0.1:${SUPPLIER_PORT}`), false, "no supplier host leaked");

  const turn = await json("/v1/turn/active?conversation=e2e-chat", { headers: auth });
  assert.equal(turn.payload.turn.status, "done", "turn journal done");
  assert.ok(turn.payload.turn.text.includes("pool answer"), "turn journal keeps answer");

  const health = await json("/health");
  assert.equal(health.payload.keys.active >= 1, true, "pool summary");
  assert.equal(health.text.includes("sk-ant-"), false, "health hides keys");

  const poolAdmin = await json("/v1/admin/pool", { headers: { "x-admin-key": "admin-e2e" } });
  assert.equal(poolAdmin.status, 200, "admin pool");
  assert.equal(poolAdmin.text.includes("sk-ant-good-key-0001"), false, "admin hides raw keys");
  assert.equal(poolAdmin.payload.invalid >= 1, true, "bad key marked invalid");
  assert.ok(poolAdmin.payload.keys.some((key) => key.id === "key_bad"), "bad key listed");

  const orderFile = JSON.parse(
    fs.readFileSync(path.join(dataDir, "orders", `${orderId}.json`), "utf8"),
  );
  assert.equal(orderFile.seat.password, "seat-password", "seat stored for operator");
  const mode = fs.statSync(path.join(dataDir, "orders", `${orderId}.json`)).mode & 0o777;
  assert.ok(mode <= 0o600, "order file is private");

  const unauth = await json("/v1/admin/pool");
  assert.equal(unauth.status, 401, "admin requires key");

  assert.equal(health.payload.accounts.relay.startsWith("http://127.0.0.1:"), true, "relay is our endpoint");
  assert.equal(health.text.includes("claude-seat@example.com"), false, "health hides seat login");
  const bodies = [
    JSON.stringify(plans.payload),
    (await json("/v1/models")).text,
    JSON.stringify(paid.payload),
    JSON.stringify(me.payload),
    JSON.stringify(poolAdmin.payload),
  ];
  const forbidden = [
    "sk-ant-",
    "anthropic.com",
    "api.anthropic",
    "seat-password",
    "seat-token-live",
    "ocgo",
    "deepseek",
    "gwarden",
  ];
  for (const body of bodies) {
    for (const word of forbidden) {
      assert.equal(body.includes(word), false, `response leaks ${word}`);
    }
  }

  console.log("\ne2e: all checks passed");
  process.exitCode = 0;
} catch (err) {
  console.error(`\ne2e: FAILED — ${err.stack || err.message}`);
  process.exitCode = 1;
} finally {
  for (const child of children) child.kill("SIGTERM");
  await new Promise((resolve) => setTimeout(resolve, 200));
  platega.close();
  supplier.close();
  upstream.close();
  anthropic.close();
  fs.rmSync(dataDir, { recursive: true, force: true });
}
