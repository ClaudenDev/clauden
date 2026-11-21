import crypto from "node:crypto";
import path from "node:path";

import { JsonStore, hashToken, newId } from "./store.mjs";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const MIN_PASSWORD = 8;

function cleanEmail(value) {
  return String(value || "").trim().toLowerCase();
}

function scrypt(password, salt) {
  return new Promise((resolve, reject) => {
    crypto.scrypt(String(password), salt, 64, (err, key) => {
      if (err) reject(err);
      else resolve(key.toString("hex"));
    });
  });
}

function sessionId(token) {
  return crypto.createHash("sha256").update(String(token)).digest("hex").slice(0, 12);
}

export function publicUser(user, plans) {
  const plan = user.plan || "Free";
  const paid = Boolean(plans?.[plan]?.paid);
  return {
    id: user.id,
    email: user.email || "",
    name: user.name || "",
    nickname: user.nickname || "",
    work: user.work || "",
    instructions: user.instructions || "",
    birthday: user.birthday || "",
    provider: user.provider || "email",
    plan,
    planUpdatedAt: user.planUpdatedAt || 0,
    planUntil: user.planUntil || 0,
    autoRenew: Boolean(user.renewal?.autoRenew),
    renewAt: Number(user.renewal?.nextChargeAt) || 0,
    renewProvider: user.renewal?.method || "",
    renewStatus: user.renewal?.status || "",
    organizationId: user.organizationId || "",
    createdAt: user.createdAt || 0,
    onboarded: Boolean(user.onboarded),
    authMode: user.authMode || "clauden",
    claudeEmail: user.claudeEmail || "",
    claudePlan: user.claudePlan || "",
    claudeLinkedAt: user.claudeLinkedAt || 0,
    codeAccess: Boolean(paid && user.codeAccess),
    hasPassword: Boolean(user.passwordHash),
  };
}

export class AuthStore {
  constructor({ config, plans, log }) {
    this.config = config;
    this.plans = plans;
    this.log = log;
    this.store = new JsonStore(path.join(config.dataDir, "auth.json"), {
      users: {},
      sessions: {},
      codes: {},
    });
    this.sessions = this.store.get().sessions;
    this.codes = this.store.get().codes;
    this.#prune();
  }

  get users() {
    return this.store.get().users;
  }

  #prune() {
    const now = Date.now();
    const data = this.store.get();
    let changed = false;
    for (const [token, session] of Object.entries(data.sessions)) {
      if (!session || session.expiresAt < now) {
        delete data.sessions[token];
        changed = true;
      }
    }
    for (const [email, code] of Object.entries(data.codes)) {
      if (!code || code.expiresAt < now) {
        delete data.codes[email];
        changed = true;
      }
    }
    if (changed) this.store.flush();
  }

  findByEmail(email) {
    const clean = cleanEmail(email);
    return Object.values(this.users).find((user) => user.email === clean) || null;
  }

  byId(id) {
    return this.users[id] || null;
  }

  #newUser({ email, name, provider }) {
    const user = {
      id: crypto.randomUUID(),
      email: cleanEmail(email),
      name: String(name || "").trim() || cleanEmail(email).split("@")[0],
      nickname: "",
      work: "",
      instructions: "",
      organizationId: crypto.randomUUID(),
      birthday: "",
      provider,
      authMode: "clauden",
      claudeEmail: "",
      claudePlan: "",
      claudeLinkedAt: 0,
      codeAccess: false,
      plan: "Free",
      planUntil: 0,
      planUpdatedAt: Date.now(),
      onboarded: false,
      termsAt: 0,
      createdAt: Date.now(),
    };
    this.users[user.id] = user;
    return user;
  }

  #issueSession(user, meta = {}) {
    const token = crypto.randomBytes(32).toString("base64url");
    const session = {
      token,
      userId: user.id,
      id: sessionId(token),
      createdAt: Date.now(),
      expiresAt: Date.now() + this.config.auth.tokenTtlMs,
      device: String(meta.device || ""),
      platform: String(meta.platform || ""),
      ip: String(meta.ip || ""),
      lastSeenAt: Date.now(),
    };
    this.sessions[token] = session;
    return tokened(user, session);
  }

  async requestEmailCode(email) {
    const clean = cleanEmail(email);
    if (!EMAIL_RE.test(clean)) throw httpError(400, "Введите корректный email");
    const existing = this.codes[clean];
    if (existing && existing.sentAt + 60_000 > Date.now()) {
      throw httpError(429, "Код уже отправлен, подождите минуту");
    }
    const code = String(crypto.randomInt(100000, 1000000));
    this.codes[clean] = {
      code,
      sentAt: Date.now(),
      expiresAt: Date.now() + this.config.auth.codeTtlMs,
      attempts: 0,
    };
    this.store.flush();
    await this.#sendCode(clean, code);
    const payload = { ok: true, expiresIn: Math.round(this.config.auth.codeTtlMs / 1000) };
    if (this.config.auth.devCodes) payload.devCode = code;
    return payload;
  }

  async #sendCode(email, code) {
    const { provider, apiKey, from } = this.config.mail;
    if (!provider || !apiKey) {
      this.log(`auth: mail provider is not configured, code for ${email}: ${code}`);
      return;
    }
    const subject = "Код подтверждения Clauden";
    const text = `Ваш код подтверждения: ${code}\n\nКод действует 10 минут.`;
    const endpoints = {
      resend: {
        url: "https://api.resend.com/emails",
        headers: { authorization: `Bearer ${apiKey}` },
        body: { from, to: [email], subject, text },
      },
      mailjet: {
        url: "https://api.mailjet.com/v3.1/send",
        headers: { authorization: `Basic ${apiKey}` },
        body: {
          Messages: [
            {
              From: { Email: from.replace(/.*<|>.*/g, "") },
              To: [{ Email: email }],
              Subject: subject,
              TextPart: text,
            },
          ],
        },
      },
    };
    const endpoint = endpoints[provider];
    if (!endpoint) {
      this.log(`auth: unknown mail provider ${provider}`);
      return;
    }
    try {
      const res = await fetch(endpoint.url, {
        method: "POST",
        headers: { "content-type": "application/json", ...endpoint.headers },
        body: JSON.stringify(endpoint.body),
      });
      if (!res.ok) this.log(`auth: mail ${provider} failed with ${res.status}`);
    } catch (err) {
      this.log(`auth: mail ${provider} error: ${err.message}`);
    }
  }

  #checkCode(email, code) {
    const clean = cleanEmail(email);
    const record = this.codes[clean];
    if (!record) throw httpError(400, "Код не найден, запросите новый");
    if (record.expiresAt < Date.now()) throw httpError(400, "Код истёк, запросите новый");
    record.attempts += 1;
    if (record.attempts > 8) {
      delete this.codes[clean];
      this.store.flush();
      throw httpError(429, "Слишком много попыток, запросите новый код");
    }
    if (String(code).trim() !== record.code) {
      this.store.flush();
      throw httpError(400, "Неверный код");
    }
    delete this.codes[clean];
    return clean;
  }

  async registerEmail({ email, code, password }, meta = {}) {
    const clean = this.#checkCode(email, code);
    if (String(password || "").length < MIN_PASSWORD) {
      throw httpError(400, `Пароль должен быть не короче ${MIN_PASSWORD} символов`);
    }
    let user = this.findByEmail(clean);
    if (user?.passwordHash) throw httpError(409, "Аккаунт с этим email уже есть");
    const salt = crypto.randomBytes(16).toString("hex");
    const passwordHash = await scrypt(password, salt);
    if (!user) user = this.#newUser({ email: clean, provider: "email" });
    user.passwordHash = passwordHash;
    user.passwordSalt = salt;
    user.provider = user.provider || "email";
    this.store.flush();
    return this.#issueSession(user, meta);
  }

  async loginEmailPassword({ email, password }, meta = {}) {
    const user = this.findByEmail(email);
    if (!user || !user.passwordHash) throw httpError(401, "Неверный email или пароль");
    const hash = await scrypt(password, user.passwordSalt);
    if (!timingSafe(hash, user.passwordHash)) throw httpError(401, "Неверный email или пароль");
    return this.#issueSession(user, meta);
  }

  async loginGoogle({ idToken }, meta = {}) {
    const payload = decodeJwt(idToken);
    if (!payload) throw httpError(400, "Некорректный Google-токен");
    const audiences = this.config.auth.googleClientIds;
    if (audiences.length && !audiences.includes(payload.aud)) {
      throw httpError(400, "Токен выписан для другого приложения");
    }
    if (payload.exp && payload.exp * 1000 < Date.now()) {
      throw httpError(400, "Google-токен истёк");
    }
    const email = cleanEmail(payload.email);
    if (!EMAIL_RE.test(email)) throw httpError(400, "Google не вернул email");
    let user = this.findByEmail(email);
    if (!user) user = this.#newUser({ email, name: payload.name, provider: "google" });
    if (payload.name && !user.name) user.name = payload.name;
    this.store.flush();
    return this.#issueSession(user, meta);
  }

  loginBridge({ payload, sig }, meta = {}) {
    const secret = this.config.auth.bridgeSecret;
    if (!secret) throw httpError(501, "Google-вход не настроен на этом сервере");
    const expected = crypto.createHmac("sha256", secret).update(payload).digest("hex");
    if (!timingSafe(expected, sig)) throw httpError(401, "Подпись не совпала");
    let data;
    try {
      data = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    } catch {
      throw httpError(400, "Некорректный payload");
    }
    const email = cleanEmail(data.email);
    let user = this.findByEmail(email);
    if (!user) user = this.#newUser({ email, name: data.name, provider: "google" });
    this.store.flush();
    return this.#issueSession(user, meta);
  }

  userForToken(token) {
    if (!token) return null;
    const session = this.sessions[token];
    if (!session) return null;
    if (session.expiresAt < Date.now()) {
      delete this.sessions[token];
      this.store.flush();
      return null;
    }
    session.lastSeenAt = Date.now();
    return this.byId(session.userId);
  }

  sessionForToken(token) {
    return token ? this.sessions[token] || null : null;
  }

  sessionList(userId, currentToken) {
    return Object.values(this.sessions)
      .filter((session) => session.userId === userId && session.expiresAt > Date.now())
      .sort((a, b) => (b.lastSeenAt || 0) - (a.lastSeenAt || 0))
      .map((session) => ({
        id: session.id,
        device: session.device,
        platform: session.platform,
        ip: session.ip,
        createdAt: session.createdAt,
        lastSeenAt: session.lastSeenAt,
        current: session.token === currentToken,
      }));
  }

  revokeSession(userId, id) {
    let removed = false;
    for (const [token, session] of Object.entries(this.sessions)) {
      if (session.userId === userId && session.id === id) {
        delete this.sessions[token];
        removed = true;
      }
    }
    this.store.flush();
    return removed;
  }

  logout(token) {
    if (token && this.sessions[token]) {
      delete this.sessions[token];
      this.store.flush();
      return true;
    }
    return false;
  }

  logoutAll(userId) {
    for (const [token, session] of Object.entries(this.sessions)) {
      if (session.userId === userId) delete this.sessions[token];
    }
    this.store.flush();
  }

  async changePassword(user, password) {
    if (String(password || "").length < MIN_PASSWORD) {
      throw httpError(400, `Пароль должен быть не короче ${MIN_PASSWORD} символов`);
    }
    user.passwordSalt = crypto.randomBytes(16).toString("hex");
    user.passwordHash = await scrypt(password, user.passwordSalt);
    this.store.flush();
  }

  updateProfile(user, patch = {}) {
    const allowed = ["name", "nickname", "work", "instructions", "birthday"];
    for (const key of allowed) {
      if (patch[key] !== undefined) user[key] = String(patch[key] || "").slice(0, 4000);
    }
    if (patch.onboarded !== undefined) user.onboarded = Boolean(patch.onboarded);
    if (patch.terms) user.termsAt = Date.now();
    this.store.flush();
    return user;
  }

  setPlan(user, plan, { until = 0, method = "", autoRenew = false } = {}) {
    user.plan = plan;
    user.planUpdatedAt = Date.now();
    user.planUntil = until;
    if (plan !== "Free") user.codeAccess = true;
    if (method) {
      user.renewal = {
        method,
        autoRenew,
        nextChargeAt: until || 0,
        status: autoRenew ? "active" : "",
      };
    }
    this.store.flush();
    return user;
  }

  deleteUser(user) {
    for (const [token, session] of Object.entries(this.sessions)) {
      if (session.userId === user.id) delete this.sessions[token];
    }
    delete this.users[user.id];
    this.store.flush();
  }

  linkClaude(user, { email, plan }) {
    if (!EMAIL_RE.test(cleanEmail(email))) throw httpError(400, "Введите email Claude-аккаунта");
    user.claudeEmail = cleanEmail(email);
    user.claudePlan = String(plan || "").slice(0, 40);
    user.claudeLinkedAt = Date.now();
    user.authMode = "claude";
    this.store.flush();
    return user;
  }

  createTokenFor(user) {
    return this.#issueSession(user, {});
  }

  adminGrant(email, plan) {
    let user = this.findByEmail(email);
    if (!user) user = this.#newUser({ email, provider: "admin" });
    this.setPlan(user, plan, { until: 0, method: "admin" });
    return user;
  }
}

function tokened(user, session) {
  return {
    token: session.token,
    user: { ...user, sessionId: session.id },
  };
}

function timingSafe(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

function decodeJwt(token) {
  try {
    const part = String(token).split(".")[1];
    if (!part) return null;
    return JSON.parse(Buffer.from(part, "base64url").toString("utf8"));
  } catch {
    return null;
  }
}

export function httpError(status, message) {
  const err = new Error(message);
  err.status = status;
  return err;
}

export { newId };
