import assert from "node:assert/strict";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";

import { AnthropicKeyPool, PoolExhausted, decryptPool, encryptPool } from "../src/keypool.mjs";
import { AnthropicPoolClient } from "../src/pool-client.mjs";

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "clauden-pool-"));
const secret = "pool-test-secret";

const anthropic = http.createServer(async (req, res) => {
  if (!req.url.endsWith("/v1/messages")) {
    res.writeHead(404).end();
    return;
  }
  const key = req.headers["x-api-key"];
  if (key === "sk-ant-bad") {
    res.writeHead(401, { "content-type": "application/json" });
    return res.end(JSON.stringify({ type: "error", error: { message: "invalid x-api-key" } }));
  }
  res.writeHead(200, { "content-type": "text/event-stream" });
  const pieces = ["hello", " from", " pool"];
  res.write(`event: message_start\ndata: ${JSON.stringify({ type: "message_start" })}\n\n`);
  for (const piece of pieces) {
    res.write(
      `event: content_block_delta\ndata: ${JSON.stringify({
        type: "content_block_delta",
        delta: { type: "text_delta", text: piece },
      })}\n\n`,
    );
  }
  res.write(
    `event: message_delta\ndata: ${JSON.stringify({
      type: "message_delta",
      delta: { stop_reason: "end_turn" },
    })}\n\n`,
  );
  res.write(`event: message_stop\ndata: ${JSON.stringify({ type: "message_stop" })}\n\n`);
  res.end();
});

await new Promise((resolve) => anthropic.listen(0, "127.0.0.1", resolve));
const port = anthropic.address().port;

const config = {
  dataDir: dir,
  pool: {
    secret,
    base: `http://127.0.0.1:${port}`,
    model: "claude-sonnet-4-5",
    maxTokens: 1024,
    timeoutMs: 5000,
  },
};

const pool = new AnthropicKeyPool({ config, log: () => {} });
assert.equal(pool.enabled, false, "empty pool disabled");
assert.equal(pool.importKeys(["sk-ant-bad", "sk-ant-good"]), 2, "two keys imported");
assert.equal(pool.enabled, true, "pool enabled");

const stored = decryptPool(path.join(dir, "pool", "anthropic.json"), secret);
assert.equal(stored.length, 2, "file holds both keys");
assert.ok(
  !fs.readFileSync(path.join(dir, "pool", "anthropic.json"), "utf8").includes("sk-ant-good"),
  "keys are encrypted at rest",
);

const client = new AnthropicPoolClient({ config, pool, log: () => {} });
let text = "";
let failovers = 0;
for await (const chunk of client.stream(
  { messages: [{ role: "user", content: "hi" }] },
  { model: "claude-sonnet-4-5" },
)) {
  if (chunk.delta) text += chunk.delta;
  if (chunk.failover) failovers += 1;
}
assert.equal(text, "hello from pool", "answer from healthy key");
assert.ok(failovers >= 1, "failover reported");
assert.equal(pool.status().invalid, 1, "dead key marked invalid");

pool.importKeys(["sk-ant-bad2", "sk-ant-bad3"]);
pool.entries.forEach((entry) => pool.revive(entry));
for (const entry of pool.entries) entry.key = "sk-ant-bad";

let exhausted = false;
try {
  for await (const chunk of client.stream({ messages: [{ role: "user", content: "x" }] })) {
    void chunk;
  }
} catch (err) {
  exhausted = err instanceof PoolExhausted || err.status === 401 || err.status === 429;
}
assert.equal(exhausted, true, "all keys failing ends with an error");

const fresh = new AnthropicKeyPool({ config, log: () => {} });
assert.equal(fresh.entries.length, pool.entries.length, "pool reloads from disk");

anthropic.close();
fs.rmSync(dir, { recursive: true, force: true });
console.log("pool: ok");
