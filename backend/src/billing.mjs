import crypto from "node:crypto";
import path from "node:path";

import { DocStore } from "./store.mjs";

const METHODS = new Set(["platega", "lava", "xrocket"]);
const ORDER_TTL_MS = 60 * 60 * 1000;

export class BillingStore {
  constructor({ config, plans, auth, accounts, log }) {
    this.config = config;
    this.plans = plans;
    this.auth = auth;
    this.accounts = accounts;
    this.log = log;
    this.docs = new DocStore(path.join(config.dataDir, "orders"));
    this.timer = null;
  }

  start() {
    if (this.timer) return;
    this.timer = setInterval(() => this.#sweep().catch(() => {}), 60_000);
    this.timer.unref?.();
  }

  #sweep() {
    const now = Date.now();
    for (const order of this.docs.list()) {
      if (order.status === "pending" && order.expiresAt < now) {
        order.status = "expired";
        this.docs.put(order.id, order);
      }
    }
  }

  async checkout(user, { plan, method, autoRenew }) {
    const id = String(plan || "").trim();
    const selected = this.plans[id];
    if (!selected || !selected.paid) {
      throw httpError(400, "Неизвестный тариф");
    }
    const payment = String(method || "").trim();
    if (!METHODS.has(payment)) throw httpError(400, "Неизвестный способ оплаты");
    const provider = this.config.billing[payment];
    if (!provider || !isConfigured(payment, provider)) {
      throw httpError(503, "Оплата временно недоступна. Напишите в поддержку.");
    }

    const amount = Number(selected.price) || 0;
    const order = {
      id: `ord_${crypto.randomBytes(8).toString("hex")}`,
      userId: user.id,
      email: user.email,
      plan: id,
      method: payment,
      amount,
      currency: payment === "platega" ? "RUB" : "USD",
      chargeAmount: payment === "platega" ? rub(amount, this.config.billing.rate) : amount,
      autoRenew: Boolean(autoRenew && payment === "platega"),
      status: "pending",
      createdAt: Date.now(),
      expiresAt: Date.now() + ORDER_TTL_MS,
      providerId: "",
      url: "",
      renewalNote: "",
      events: [{ type: "created", at: Date.now() }],
    };
    this.docs.put(order.id, order);
    try {
      await this.#createInvoice(order);
    } catch (err) {
      this.log(`billing: checkout ${order.id} failed: ${err.message}`);
      order.status = "failed";
      order.error = err.message;
      order.events.push({ type: "failed", at: Date.now() });
      this.docs.put(order.id, order);
      throw httpError(502, "Не удалось создать счёт. Попробуйте другой способ оплаты.");
    }
    this.docs.put(order.id, order);
    return publicOrder(order);
  }

  async #createInvoice(order) {
    if (order.method === "platega") return this.#platega(order);
    if (order.method === "lava") return this.#lava(order);
    return this.#xrocket(order);
  }

  async #platega(order) {
    const { base, merchant, secret } = this.config.billing.platega;
    const body = {
      paymentMethod: order.autoRenew ? 6 : 2,
      id: order.id,
      amount: order.chargeAmount,
      currency: "RUB",
      description: `Clauden ${order.plan}`,
      successUrl: `${this.config.publicBase}/v1/billing/return?paid=1`,
      failUrl: `${this.config.publicBase}/v1/billing/return?paid=0`,
      metadata: { plan: order.plan, email: order.email },
    };
    const res = await fetch(`${base}/transaction/process`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-merchantid": merchant,
        "x-secret": secret,
      },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`platega ${res.status} ${text.slice(0, 200)}`);
    }
    const data = await res.json();
    order.providerId = String(data.transactionId || data.id || order.id);
    order.url = String(data.redirectUrl || data.url || data.paymentUrl || "");
    if (!order.url) throw new Error("platega: no payment url");
    this.docs.put(order.id, order);
  }

  async #lava(order) {
    const { base, apiKey, offerId } = this.config.billing.lava;
    const res = await fetch(`${base}/api/v2/invoice`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        email: order.email,
        offerId: offerId || undefined,
        currency: "USD",
        amount: order.amount,
        periodicity: "ONE_TIME",
        buyerLanguage: "RU",
        customFields: { orderId: order.id, plan: order.plan },
      }),
    });
    if (!res.ok) throw new Error(`lava ${res.status}`);
    const data = await res.json();
    order.providerId = String(data.id || data.invoiceId || order.id);
    order.url = String(data.paymentUrl || data.url || "");
    if (!order.url) throw new Error("lava: no payment url");
    this.docs.put(order.id, order);
  }

  async #xrocket(order) {
    const { base, token } = this.config.billing.xrocket;
    const res = await fetch(`${base}/tg-invoices/api/v1/invoice/create`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "Rocket-Pay-Key": token,
      },
      body: JSON.stringify({
        amount: order.amount,
        currency: "USD",
        description: `Clauden ${order.plan}`,
        hiddenMessage: order.id,
        callbackUrl: `${this.config.publicBase}/v1/billing/webhook/xrocket`,
      }),
    });
    if (!res.ok) throw new Error(`xrocket ${res.status}`);
    const data = await res.json();
    order.providerId = String(data.id || order.id);
    order.url = String(data.link || data.url || "");
    if (!order.url) throw new Error("xrocket: no payment url");
    this.docs.put(order.id, order);
  }

  async refresh(order) {
    if (order.status !== "pending") return order;
    try {
      if (order.method === "platega") await this.#plategaStatus(order);
      else if (order.method === "lava") await this.#lavaStatus(order);
      else await this.#xrocketStatus(order);
    } catch (err) {
      this.log(`billing: status ${order.id} failed: ${err.message}`);
    }
    if (order.status === "paid") await this.#fulfil(order);
    this.docs.put(order.id, order);
    return order;
  }

  async #plategaStatus(order) {
    const { base, merchant, secret } = this.config.billing.platega;
    const res = await fetch(`${base}/transaction/${order.providerId}`, {
      headers: { "x-merchantid": merchant, "x-secret": secret },
    });
    if (!res.ok) return;
    const data = await res.json();
    if (data.status === "CONFIRMED") order.status = "paid";
    else if (["CANCELED", "EXPIRED"].includes(data.status)) order.status = "failed";
  }

  async #lavaStatus(order) {
    const { base, apiKey } = this.config.billing.lava;
    const res = await fetch(`${base}/api/v2/invoice/${order.providerId}`, {
      headers: { authorization: `Bearer ${apiKey}` },
    });
    if (!res.ok) return;
    const data = await res.json();
    if (["completed", "subscription-active"].includes(data.status)) order.status = "paid";
    else if (["failed", "cancelled"].includes(data.status)) order.status = "failed";
  }

  async #xrocketStatus(order) {
    const { base, token } = this.config.billing.xrocket;
    const res = await fetch(`${base}/tg-invoices/api/v1/invoice/${order.providerId}`, {
      headers: { "Rocket-Pay-Key": token },
    });
    if (!res.ok) return;
    const data = await res.json();
    if (data.status === "paid") order.status = "paid";
    else if (["expired", "cancelled"].includes(data.status)) order.status = "failed";
  }

  async #fulfil(order) {
    if (order.fulfilledAt) return;
    order.fulfilledAt = Date.now();
    order.events = [...(order.events || []), { type: "paid", at: order.fulfilledAt }];
    this.docs.put(order.id, order);
    const user = this.auth.byId(order.userId);
    if (!user) return;
    const plan = this.plans[order.plan];
    const until = order.autoRenew ? Date.now() + 30 * 24 * 60 * 60 * 1000 : 0;
    this.auth.setPlan(user, order.plan, {
      until,
      method: order.method,
      autoRenew: order.autoRenew,
    });
    if (plan?.seat) {
      try {
        const seat = await this.accounts.issue({
          plan: order.plan,
          email: order.email,
          orderId: order.id,
        });
        order.seat = { ...seat, issuedAt: Date.now() };
        order.seatError = "";
        order.events.push({ type: "account_issued", at: order.seat.issuedAt });
      } catch (err) {
        this.log(`billing: seat for ${order.id} failed: ${err.message}`);
        order.seatError = err.message;
        order.events.push({ type: "account_failed", at: Date.now() });
      }
    }
  }

  cancelRenewal(user) {
    if (!user.renewal?.autoRenew) return false;
    user.renewal.autoRenew = false;
    user.renewal.status = "cancelled";
    this.auth.store.flush();
    return true;
  }

  get(id) {
    return this.docs.get(id);
  }

  seatFor(userId) {
    const orders = this.docs
      .list()
      .filter((order) => order.userId === userId && order.seat && order.seat.token)
      .sort((a, b) => (b.fulfilledAt || 0) - (a.fulfilledAt || 0));
    const order = orders[0];
    if (!order) return null;
    if (order.seat.expiresAt && order.seat.expiresAt < Date.now()) return null;
    return { ...order.seat, orderId: order.id, plan: order.plan };
  }

  listForUser(userId) {
    return this.docs
      .list()
      .filter((order) => order.userId === userId)
      .sort((a, b) => b.createdAt - a.createdAt)
      .map(publicOrder);
  }
}

function isConfigured(method, provider) {
  if (method === "platega") return Boolean(provider.merchant && provider.secret);
  if (method === "lava") return Boolean(provider.apiKey);
  return Boolean(provider.token);
}

function rub(usd, rate) {
  const r = Number(rate) || 0;
  return r > 0 ? Math.round(usd * r) : Math.round(usd * 100);
}

export function publicOrder(order) {
  return {
    id: order.id,
    plan: order.plan,
    method: order.method,
    status: order.status,
    amount: order.amount,
    chargeAmount: order.chargeAmount,
    currency: order.currency,
    autoRenew: order.autoRenew,
    url: order.url,
    createdAt: order.createdAt,
    expiresAt: order.expiresAt,
    renewalNote: order.renewalNote,
    account: accountStatus(order),
    events: order.events || [],
  };
}

function accountStatus(order) {
  if (!order.seat && !order.seatError) {
    return { provider: "external", status: order.status === "paid" ? "provisioning" : "pending" };
  }
  if (order.seatError) {
    return { provider: "external", status: "failed", message: order.seatError };
  }
  return {
    provider: "external",
    status: "active",
    plan: order.seat.plan,
    login: maskLogin(order.seat.login),
    issuedAt: order.seat.issuedAt || order.fulfilledAt || 0,
    expiresAt: order.seat.expiresAt || 0,
  };
}

function maskLogin(login) {
  const value = String(login || "");
  const at = value.indexOf("@");
  if (at <= 1) return value.length ? `${value[0]}***` : "";
  return `${value.slice(0, 2)}***${value.slice(at)}`;
}

function httpError(status, message) {
  const err = new Error(message);
  err.status = status;
  return err;
}
