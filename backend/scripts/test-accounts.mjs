import assert from "node:assert/strict";
import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";

import { AccountsProvider, SUPPLIER_ENDPOINT } from "../src/accounts.mjs";
import { BillingStore, publicOrder } from "../src/billing.mjs";
import { buildPlans } from "../src/plans.mjs";

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "clauden-accounts-"));
const plans = buildPlans();
let issued = null;

const relay = http.createServer(async (req, res) => {
  if (req.url === "/v1/accounts/status") {
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(JSON.stringify({ ok: true, stock: 3 }));
  }
  if (req.url === "/v1/accounts/issue") {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    issued = { body, relay: req.headers["x-clauden-relay"], sig: req.headers["x-clauden-signature"] };
    res.writeHead(200, { "content-type": "application/json" });
    return res.end(
      JSON.stringify({
        login: "supplied-account@example.com",
        password: "secret",
        token: "seat-token-1",
        plan: body.plan,
        expiresAt: Date.now() + 86400000,
      }),
    );
  }
  res.writeHead(404).end();
});

await new Promise((resolve) => relay.listen(0, "127.0.0.1", resolve));
const port = relay.address().port;

const config = {
  dataDir: dir,
  root: path.resolve("."),
  accounts: { relay: `http://127.0.0.1:${port}`, secret: "relay-secret", timeoutMs: 5000 },
  publicBase: "http://127.0.0.1",
  billing: {
    currency: "USD",
    rate: 100,
    platega: {},
    lava: {},
    xrocket: {},
  },
};

const accounts = new AccountsProvider({ config, log: () => {} });

const status = await accounts.status();
assert.equal(status.ok, true, "relay status");
assert.equal(status.stock, 3, "stock reported");

const seat = await accounts.issue({ plan: "Pro", email: "buyer@example.com", orderId: "ord_1" });
assert.equal(seat.login, "supplied-account@example.com", "login");
assert.equal(seat.token, "seat-token-1", "token");
assert.equal(seat.plan, "Pro", "plan");
assert.equal(issued.relay, "relay-secret", "relay header");
assert.equal(issued.body.orderId, "ord_1", "order id forwarded");
assert.equal(typeof issued.sig, "string", "signature sent");
assert.ok(issued.sig.length === 64, "signature is sha256 hex");

const cold = new AccountsProvider({
  config: { ...config, accounts: { relay: "", secret: "", timeoutMs: 1000 } },
  log: () => {},
});
assert.equal(cold.describe().configured, false, "unconfigured reported");
assert.equal(cold.describe().endpoint, SUPPLIER_ENDPOINT, "placeholder exposed");
await assert.rejects(() => cold.issue({ plan: "Pro", email: "x@y.z", orderId: "o" }), /не настроена/);

const auth = { byId: () => null, setPlan: () => {}, store: { flush: () => {} } };
const billing = new BillingStore({ config, plans, auth, accounts, log: () => {} });
billing.docs.put("ord_1", {
  id: "ord_1",
  userId: "u1",
  email: "buyer@example.com",
  plan: "Pro",
  method: "platega",
  amount: 20,
  chargeAmount: 2000,
  currency: "RUB",
  status: "paid",
  createdAt: Date.now(),
  fulfilledAt: Date.now(),
  seat,
  events: [
    { type: "created", at: 1 },
    { type: "paid", at: 2 },
    { type: "account_issued", at: 3 },
  ],
});
const view = publicOrder(billing.get("ord_1"));
assert.equal(view.account.status, "active", "account active");
assert.equal(view.account.login, "su***@example.com", "login masked");
assert.equal(view.events.length, 3, "timeline exposed");
assert.equal(billing.seatFor("u1").token, "seat-token-1", "seat lookup");

relay.close();
fs.rmSync(dir, { recursive: true, force: true });
console.log("accounts: ok");
