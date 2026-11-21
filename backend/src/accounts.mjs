import crypto from "node:crypto";

export const SUPPLIER_ENDPOINT = "https://<поставщик не разрешил публиковать домен>";

export class SupplierUnavailable extends Error {
  constructor(message = "Поставщик подписок временно недоступен") {
    super(message);
    this.name = "SupplierUnavailable";
    this.status = 503;
    this.type = "supplier_unavailable";
  }
}

export class AccountsProvider {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
  }

  get configured() {
    return Boolean(this.config.accounts.relay);
  }

  describe() {
    return {
      provider: "external",
      relay: this.config.accounts.relay,
      endpoint: SUPPLIER_ENDPOINT,
      configured: this.configured,
      note: "Поставщик не разрешает публиковать домен и API-адрес.",
    };
  }

  async issue({ plan, email, orderId }) {
    if (!this.configured) {
      throw new SupplierUnavailable(
        "Выдача подписок не настроена: задайте CLAUDEN_ACCOUNTS_RELAY и CLAUDEN_ACCOUNTS_SECRET",
      );
    }
    const body = JSON.stringify({ plan, email, orderId });
    const signature = crypto
      .createHmac("sha256", this.config.accounts.secret)
      .update(body)
      .digest("hex");

    const controller = new AbortController();
    const timer = setTimeout(
      () => controller.abort(new Error("supplier timeout")),
      this.config.accounts.timeoutMs,
    );
    try {
      const res = await fetch(`${this.config.accounts.relay}/v1/accounts/issue`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-clauden-relay": this.config.accounts.secret,
          "x-clauden-signature": signature,
        },
        body,
        signal: controller.signal,
      });
      if (!res.ok) {
        const text = await res.text().catch(() => "");
        this.log(`accounts: supplier ${res.status} ${text.slice(0, 300)}`);
        if (res.status === 409) {
          throw new SupplierUnavailable("На складе поставщика нет свободных аккаунтов этого тарифа");
        }
        throw new SupplierUnavailable();
      }
      const data = await res.json();
      if (!data?.login || !data?.password) {
        throw new SupplierUnavailable("Поставщик вернул неполные данные аккаунта");
      }
      return {
        login: String(data.login),
        password: String(data.password),
        token: String(data.token || data.accessToken || ""),
        plan: String(data.plan || plan),
        expiresAt: Number(data.expiresAt) || Date.now() + 30 * 24 * 60 * 60 * 1000,
      };
    } catch (err) {
      if (err instanceof SupplierUnavailable) throw err;
      this.log(`accounts: supplier request failed: ${err.message}`);
      throw new SupplierUnavailable();
    } finally {
      clearTimeout(timer);
    }
  }

  async status() {
    if (!this.configured) return { ok: false, reason: "not_configured" };
    try {
      const res = await fetch(`${this.config.accounts.relay}/v1/accounts/status`, {
        headers: { "x-clauden-relay": this.config.accounts.secret },
        signal: AbortSignal.timeout(5000),
      });
      if (!res.ok) return { ok: false, reason: `http_${res.status}` };
      const data = await res.json().catch(() => ({}));
      return { ok: true, stock: Number(data.stock) || 0 };
    } catch (err) {
      return { ok: false, reason: err.message };
    }
  }
}
