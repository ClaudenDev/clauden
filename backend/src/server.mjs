import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";

import { AccountsProvider, SUPPLIER_ENDPOINT } from "./accounts.mjs";
import { AuthStore, httpError, publicUser } from "./auth.mjs";
import { BillingStore, publicOrder } from "./billing.mjs";
import { ChatBackupStore, MemoryStore, ProjectBackupStore } from "./chats.mjs";
import { CodeManager } from "./code.mjs";
import { config, ensureDataDir, hasUpstream } from "./config.mjs";
import { bearer, readJsonBody, sendError, sendJson, SseStream, clientIp } from "./http.mjs";
import { ApiKeyStore } from "./keys.mjs";
import { ModelRegistry } from "./models.mjs";
import { buildPlans, planFor, publicPlans } from "./plans.mjs";
import { readJson } from "./store.mjs";
import { TurnStore } from "./turns.mjs";
import { UpstreamError, InferencePool } from "./upstream.mjs";
import { UsageStore } from "./usage.mjs";

const VERSION = "1.0.0";
const STARTED = Date.now();

function log(message) {
  process.stdout.write(`${new Date().toISOString()} ${message}\n`);
}

ensureDataDir();

const planOverrides = readJson(path.join(config.root, "config", "plans.json"), null) || {};
const plans = buildPlans(planOverrides);
const auth = new AuthStore({ config, plans, log });
const registry = new ModelRegistry({ config, log });
const usage = new UsageStore({ config, plans, log });
const turns = new TurnStore({ config, log });
turns.warm();
const chats = new ChatBackupStore({ config, log });
const projects = new ProjectBackupStore({ config, log });
const memory = new MemoryStore({ config, log });
const keys = new ApiKeyStore({ config, log });
const accounts = new AccountsProvider({ config, log });
const pool = new InferencePool({ config, log });
const billing = new BillingStore({ config, plans, auth, accounts, log });
const code = new CodeManager({ config, pool, registry, log });
billing.start();

const running = new Set();

function requester(req) {
  const token = bearer(req);
  const user = auth.userForToken(token);
  if (user) {
    const plan = planFor(plans, user.plan);
    return { user, plan, token, key: null };
  }
  const key = keys.resolve(token);
  if (key) {
    const owner = auth.byId(key.userId);
    if (owner) return { user: owner, plan: planFor(plans, owner.plan), token, key };
  }
  return null;
}

function requireUser(req, res) {
  const found = requester(req);
  if (!found) {
    sendError(res, 401, "Нужно войти в аккаунт", "unauthorized");
    return null;
  }
  return found;
}

function requireAdmin(req, res) {
  const provided = String(req.headers["x-admin-key"] || "");
  if (!config.adminKey || provided !== config.adminKey) {
    sendError(res, 401, "Нет доступа", "unauthorized");
    return false;
  }
  return true;
}

function planPayload(found) {
  const plan = found.plan;
  return {
    user: publicUser(found.user, plans),
    plan: {
      id: plan.id,
      label: plan.label,
      paid: plan.paid,
      limits: plan.limits,
    },
  };
}

function meta(req) {
  return {
    device: String(req.headers["x-device-name"] || ""),
    platform: String(req.headers["x-device-platform"] || ""),
    ip: clientIp(req),
  };
}

function promptText(body) {
  return (body.messages || [])
    .filter((message) => message.role === "user")
    .map((message) =>
      typeof message.content === "string"
        ? message.content
        : (message.content || [])
            .map((part) => (part?.type === "text" ? part.text : ""))
            .join(" "),
    )
    .join("\n");
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  const route = `${req.method} ${url.pathname.replace(/\/+$/, "") || "/"}`;
  if (config.logRequests) log(`${route} ${clientIp(req)}`);

  try {

    if (route === "GET /" || route === "GET /health") {
      return sendJson(res, 200, {
        ok: true,
        service: config.serviceName,
        version: VERSION,
        uptime: Math.round((Date.now() - STARTED) / 1000),
        pool: hasUpstream() ? "configured" : "missing",
        accounts: accounts.describe(),
      });
    }

    if (route === "POST /v1/auth/email/code") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, await auth.requestEmailCode(body.email));
    }
    if (route === "POST /v1/auth/email/register") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, await authResult(auth.registerEmail(body, meta(req))));
    }
    if (route === "POST /v1/auth/email") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, await authResult(auth.loginEmailPassword(body, meta(req))));
    }
    if (route === "POST /v1/auth/google") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, await authResult(auth.loginGoogle(body, meta(req))));
    }
    if (route === "POST /v1/auth/bridge") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, await authResult(auth.loginBridge(body, meta(req))));
    }
    if (route === "GET /v1/auth/config") {
      return sendJson(res, 200, {
        googleEnabled: Boolean(config.auth.googleClientIds.length || config.auth.bridgeSecret),
        emailEnabled: true,
      });
    }
    if (route === "GET /v1/auth/me") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, planPayload(found));
    }
    if (route === "PATCH /v1/auth/me") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      auth.updateProfile(found.user, body);
      return sendJson(res, 200, planPayload(found));
    }
    if (route === "POST /v1/auth/password") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      await auth.changePassword(found.user, body.password);
      return sendJson(res, 200, { ok: true });
    }
    if (route === "GET /v1/auth/sessions") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, { data: auth.sessionList(found.user.id, found.token) });
    }
    if (route === "POST /v1/auth/sessions/revoke") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      return sendJson(res, 200, { ok: auth.revokeSession(found.user.id, String(body.id || "")) });
    }
    if (route === "POST /v1/auth/logout") {
      auth.logout(bearer(req));
      return sendJson(res, 200, { ok: true });
    }
    if (route === "POST /v1/auth/logout-all") {
      const found = requireUser(req, res);
      if (!found) return;
      auth.logoutAll(found.user.id);
      return sendJson(res, 200, { ok: true });
    }
    if (route === "DELETE /v1/auth/account") {
      const found = requireUser(req, res);
      if (!found) return;
      auth.deleteUser(found.user);
      return sendJson(res, 200, { ok: true });
    }
    if (route === "POST /v1/auth/claude/link") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      auth.linkClaude(found.user, body);
      return sendJson(res, 200, planPayload(found));
    }

    if (route === "GET /v1/models") {
      const found = requester(req);
      return sendJson(res, 200, {
        object: "list",
        data: registry.list(found?.plan?.id || "Free"),
      });
    }
    if (route === "GET /v1/plans") {
      return sendJson(res, 200, { data: publicPlans(plans) });
    }
    if (route === "GET /v1/usage") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, { usage: usage.summary(found.user.id, found.plan) });
    }

    if (route === "GET /v1/keys") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, { data: keys.forUser(found.user.id) });
    }
    if (route === "POST /v1/keys") {
      const found = requireUser(req, res);
      if (!found) return;
      if (!found.plan.paid) return sendError(res, 403, "API-ключи доступны на платном тарифе");
      return sendJson(res, 200, keys.create(found.user.id));
    }
    if (route === "POST /v1/keys/reset") {
      const found = requireUser(req, res);
      if (!found) return;
      if (!found.plan.paid) return sendError(res, 403, "API-ключи доступны на платном тарифе");
      return sendJson(res, 200, keys.reset(found.user.id));
    }
    if (req.method === "DELETE" && url.pathname.startsWith("/v1/keys/")) {
      const found = requireUser(req, res);
      if (!found) return;
      const id = url.pathname.split("/").pop();
      return sendJson(res, 200, { ok: keys.revoke(found.user.id, id) });
    }

    if (route === "POST /v1/chat/completions") {
      return await handleChat(req, res);
    }
    if (route === "POST /v1/messages") {
      return await handleAnthropicChat(req, res);
    }
    if (route === "POST /v1/responses") {
      return await handleResponses(req, res);
    }
    if (route === "GET /v1/turns") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, {
        data: turns.list(url.searchParams.get("conversation") || ""),
      });
    }
    if (route === "GET /v1/turn/active") {
      const found = requireUser(req, res);
      if (!found) return;
      const turn = turns.latest(url.searchParams.get("conversation") || "");
      return sendJson(res, 200, { turn });
    }
    if (route === "POST /v1/turns/stop") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      const turn = splitters.get(String(body.turnId || ""));
      if (turn) turn.abort();
      return sendJson(res, 200, { ok: true });
    }

    if (route === "GET /v1/chats") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, chats.list(found.user.id));
    }
    if (route === "POST /v1/chats/backup") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      return sendJson(res, 200, chats.put(found.user.id, body.conversations));
    }
    if (route === "DELETE /v1/chats") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, { ok: chats.clear(found.user.id) });
    }
    if (req.method === "DELETE" && url.pathname.startsWith("/v1/chats/")) {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, { ok: chats.remove(found.user.id, decodeURIComponent(url.pathname.split("/").pop())) });
    }
    if (route === "GET /v1/projects") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, projects.list(found.user.id));
    }
    if (route === "POST /v1/projects/backup") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      return sendJson(res, 200, projects.put(found.user.id, body.projects));
    }
    if (req.method === "DELETE" && url.pathname.startsWith("/v1/projects/")) {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, {
        ok: projects.remove(found.user.id, decodeURIComponent(url.pathname.split("/").pop())),
      });
    }

    if (url.pathname === "/v1/memory") {
      const found = requireUser(req, res);
      if (!found) return;
      if (req.method === "GET") return sendJson(res, 200, { data: memory.list(found.user.id) });
      if (req.method === "POST") {
        const body = await readJsonBody(req);
        const items = body.items ? memory.add(found.user.id, body.items) : memory.add(found.user.id, body.text);
        return sendJson(res, 200, { data: items });
      }
      if (req.method === "DELETE") {
        memory.clear(found.user.id);
        return sendJson(res, 200, { ok: true });
      }
    }
    if (url.pathname.startsWith("/v1/memory/")) {
      const found = requireUser(req, res);
      if (!found) return;
      const id = decodeURIComponent(url.pathname.split("/").pop());
      if (req.method === "PATCH") {
        const body = await readJsonBody(req);
        return sendJson(res, 200, { data: memory.update(found.user.id, id, body.text) });
      }
      if (req.method === "DELETE") {
        return sendJson(res, 200, { ok: memory.remove(found.user.id, id) });
      }
    }

    if (route === "GET /v1/accounts/me") {
      const found = requireUser(req, res);
      if (!found) return;
      const orders = billing.listForUser(found.user.id);
      const active = orders.find((order) => order.status === "paid") || null;
      return sendJson(res, 200, {
        data: {
          plan: found.plan.id,
          paid: found.plan.paid,
          provider: "external",
          supplier: SUPPLIER_ENDPOINT,
          account: active ? active.account : { provider: "external", status: "none" },
        },
      });
    }
    if (route === "POST /v1/billing/checkout") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req);
      return sendJson(res, 200, { order: billing.checkout(found.user, body) });
    }
    if (route === "POST /v1/billing/cancel") {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, { ok: billing.cancelRenewal(found.user) });
    }
    if (req.method === "GET" && url.pathname.startsWith("/v1/billing/order/")) {
      const found = requireUser(req, res);
      if (!found) return;
      const id = url.pathname.split("/").pop();
      const order = billing.get(id);
      if (!order || order.userId !== found.user.id) return sendError(res, 404, "Счёт не найден");
      await billing.refresh(order);
      return sendJson(res, 200, { order: publicOrder(order) });
    }
    if (route === "GET /v1/billing/return") {
      const ok = url.searchParams.get("paid") !== "0";
      return sendHtml(res, 200, returnPage(ok));
    }

    if (url.pathname.startsWith("/v1/code/")) {
      return await handleCode(req, res, url);
    }

    if (route === "POST /v1/extract") {
      const found = requireUser(req, res);
      if (!found) return;
      const body = await readJsonBody(req, config.limits.maxBodyBytes);
      return sendJson(res, 200, extractText(body));
    }
    if (route === "POST /v1/transcribe") {
      return sendError(
        res,
        501,
        "Распознавание речи не настроено на этом сервере",
        "not_configured",
      );
    }

    if (route === "POST /v1/security/ban-risk") {
      return sendJson(res, 200, { score: 0, level: "low", reasons: [] });
    }

    if (route === "POST /v1/connectors/oauth/start") {
      return sendError(res, 501, "OAuth-коннекторы не настроены на этом сервере", "not_configured");
    }
    if (route === "POST /v1/connectors/oauth/finish") {
      return sendError(res, 501, "OAuth-коннекторы не настроены на этом сервере", "not_configured");
    }
    if (route === "GET /v1/connectors/oauth/result") {
      return sendJson(res, 200, { attempt: null });
    }

    if (url.pathname.startsWith("/v1/remote/")) {
      const token = url.pathname.split("/")[3] || "";
      const link = remoteLinks.get(token);
      if (!link) {
        return sendError(res, 404, "Ссылка недействительна или истекла", "link_expired");
      }
      if (req.method === "GET" && url.pathname.endsWith("/events")) {
        const stream = new SseStream(res);
        stream.send(code.full(link.userId, link.sessionId));
        const unsubscribe = code.subscribe(link.sessionId, (event) => stream.send(event.data));
        req.on("close", () => {
          unsubscribe();
          stream.close();
        });
        return;
      }
      if (req.method === "GET") {
        return sendJson(res, 200, { data: code.full(link.userId, link.sessionId) });
      }
      if (req.method === "POST" && url.pathname.endsWith("/messages")) {
        const body = await readJsonBody(req);
        return sendJson(res, 200, { data: await code.send(link.userId, link.sessionId, body) });
      }
      if (req.method === "POST" && url.pathname.endsWith("/stop")) {
        return sendJson(res, 200, { ok: code.stop(link.userId, link.sessionId) });
      }
      return sendError(res, 404, "Не найдено", "not_found");
    }

    if (url.pathname.startsWith("/v1/market/")) {
      const found = requireUser(req, res);
      if (!found) return;
      return sendJson(res, 200, { data: [] });
    }
    if (url.pathname.startsWith("/v1/connectors/catalog")) {
      return sendJson(res, 200, { data: [] });
    }
    if (route === "POST /v1/connectors/check") {
      return sendJson(res, 200, { ok: true });
    }

    if (url.pathname.startsWith("/v1/admin/")) {
      if (!requireAdmin(req, res)) return;
      if (route === "GET /v1/admin/users") {
        return sendJson(res, 200, {
          data: Object.values(auth.users).map((user) => publicUser(user, plans)),
        });
      }
      if (route === "POST /v1/admin/plan") {
        const body = await readJsonBody(req);
        const plan = planFor(plans, body.plan);
        const user = auth.adminGrant(body.user, plan.id);
        return sendJson(res, 200, { user: publicUser(user, plans) });
      }
      return sendError(res, 404, "Неизвестный метод");
    }

    return sendError(res, 404, "Не найдено", "not_found");
  } catch (err) {
    const status = err.status || (err instanceof UpstreamError ? err.status : 500);
    if (status >= 500) log(`error ${route}: ${err.stack || err.message}`);
    if (res.headersSent) {
      try {
        res.end();
      } catch {

      }
      return;
    }
    return sendError(res, status, err.message || "Внутренняя ошибка", err.type || "server_error");
  }
});

const splitters = new Map();
const remoteLinks = new Map();

async function handleAnthropicChat(req, res) {
  const found = requireUser(req, res);
  if (!found) return;
  const body = await readJsonBody(req, config.limits.maxBodyBytes);
  const model = registry.normalize(body.model) || registry.list()[0].id;
  if (!registry.allows(found.plan.id, model)) {
    return sendError(res, 403, "Модель недоступна на вашем тарифе", "model_not_allowed");
  }
  if (!found.plan.paid && !found.key) {
    return sendJson(res, 402, {
      error: { message: "На Free-тарифе моделей Clauden нет. Оформите подписку.", type: "plan_required" },
    });
  }
  const resetsAt = usage.exceeded(found.user.id, found.plan);
  if (resetsAt && !found.key) {
    return sendJson(res, 429, {
      error: {
        message: "Лимит тарифа исчерпан. Попробуйте позже или смените тариф.",
        type: "rate_limit",
        resets_at: resetsAt,
      },
    });
  }
  const seat = billing.seatFor(found.user.id);
  const conversation = String(req.headers["x-conversation-id"] || "");
  const turn = turns.begin({
    conversation,
    userId: found.user.id,
    model,
    text: promptText(body),
  });
  const wantStream = body.stream === true;
  const messageId = `msg_${turn.id.slice(5)}`;

  try {
    if (!wantStream) {
      const result = await pool.complete(body, {
        model,
        system: stringSystem(body.system),
        account: seat?.token,
      });
      turns.append(turn.id, result.text);
      usage.record(found.user.id, { model, tokens: result.usage?.total_tokens || 0 });
      turns.finish(turn.id, { status: "done" });
      return sendJson(res, 200, {
        id: messageId,
        type: "message",
        role: "assistant",
        model,
        content: [{ type: "text", text: result.text }],
        stop_reason: "end_turn",
        stop_sequence: null,
        usage: {
          input_tokens: Number(result.usage?.prompt_tokens) || 0,
          output_tokens: Number(result.usage?.completion_tokens) || 0,
        },
      });
    }

    const stream = new SseStream(res);
    res.write(`event: message_start\ndata: ${JSON.stringify({
      type: "message_start",
      message: {
        id: messageId,
        type: "message",
        role: "assistant",
        model,
        content: [],
        stop_reason: null,
        stop_sequence: null,
        usage: { input_tokens: 0, output_tokens: 0 },
      },
    })}\n\n`);
    res.write(`event: content_block_start\ndata: ${JSON.stringify({
      type: "content_block_start",
      index: 0,
      content_block: { type: "text", text: "" },
    })}\n\n`);

    let full = "";
    for await (const chunk of pool.stream(body, {
      model,
      system: stringSystem(body.system),
      signal: undefined,
      account: seat?.token,
    })) {
      if (!chunk.delta) continue;
      full += chunk.delta;
      turns.append(turn.id, chunk.delta);
      res.write(`event: content_block_delta\ndata: ${JSON.stringify({
        type: "content_block_delta",
        index: 0,
        delta: { type: "text_delta", text: chunk.delta },
      })}\n\n`);
    }
    res.write(`event: content_block_stop\ndata: ${JSON.stringify({
      type: "content_block_stop",
      index: 0,
    })}\n\n`);
    res.write(`event: message_delta\ndata: ${JSON.stringify({
      type: "message_delta",
      delta: { stop_reason: "end_turn", stop_sequence: null },
      usage: { output_tokens: 0 },
    })}\n\n`);
    res.write(`event: message_stop\ndata: ${JSON.stringify({ type: "message_stop" })}\n\n`);
    stream.close();
    usage.record(found.user.id, { model });
    turns.finish(turn.id, { status: "done" });
  } catch (err) {
    turns.finish(turn.id, { status: "error", error: err.message });
    if (res.headersSent) {
      try {
        res.end();
      } catch {}
      return;
    }
    throw err;
  }
}

async function handleResponses(req, res) {
  const found = requireUser(req, res);
  if (!found) return;
  const body = await readJsonBody(req, config.limits.maxBodyBytes);
  const model = registry.normalize(body.model) || registry.list()[0].id;
  if (!registry.allows(found.plan.id, model)) {
    return sendError(res, 403, "Модель недоступна на вашем тарифе", "model_not_allowed");
  }
  if (!found.plan.paid && !found.key) {
    return sendJson(res, 402, {
      error: { message: "На Free-тарифе моделей Clauden нет. Оформите подписку.", type: "plan_required" },
    });
  }
  const seat = billing.seatFor(found.user.id);
  const input = body.input;
  const messages = Array.isArray(input)
    ? input.map((item) => ({
        role: item.role || "user",
        content: item.content ?? item.text ?? "",
      }))
    : [{ role: "user", content: String(input ?? "") }];
  const request = {
    messages,
    max_tokens: body.max_output_tokens || config.upstream.maxTokens,
    temperature: body.temperature,
  };
  const responseId = `resp_${crypto.randomBytes(12).toString("hex")}`;
  const turn = turns.begin({
    conversation: "",
    userId: found.user.id,
    model,
    text: String(input ?? ""),
  });

  try {
    if (!body.stream) {
      const result = await pool.complete(request, {
        model,
        system: stringSystem(body.instructions),
        account: seat?.token,
      });
      turns.append(turn.id, result.text);
      usage.record(found.user.id, { model });
      turns.finish(turn.id, { status: "done" });
      return sendJson(res, 200, {
        id: responseId,
        object: "response",
        created_at: Math.floor(Date.now() / 1000),
        status: "completed",
        model,
        output: [
          {
            type: "message",
            id: `msg_${responseId.slice(5)}`,
            status: "completed",
            role: "assistant",
            content: [{ type: "output_text", text: result.text, annotations: [] }],
          },
        ],
        usage: result.usage || undefined,
      });
    }

    const stream = new SseStream(res);
    res.write(`event: response.created\ndata: ${JSON.stringify({
      type: "response.created",
      response: { id: responseId, object: "response", status: "in_progress", model },
    })}\n\n`);
    let full = "";
    for await (const chunk of pool.stream(request, {
      model,
      system: stringSystem(body.instructions),
      account: seat?.token,
    })) {
      if (!chunk.delta) continue;
      full += chunk.delta;
      turns.append(turn.id, chunk.delta);
      res.write(`event: response.output_text.delta\ndata: ${JSON.stringify({
        type: "response.output_text.delta",
        item_id: `msg_${responseId.slice(5)}`,
        delta: chunk.delta,
      })}\n\n`);
    }
    res.write(`event: response.completed\ndata: ${JSON.stringify({
      type: "response.completed",
      response: {
        id: responseId,
        object: "response",
        status: "completed",
        model,
        output: [
          {
            type: "message",
            role: "assistant",
            content: [{ type: "output_text", text: full, annotations: [] }],
          },
        ],
      },
    })}\n\n`);
    stream.close();
    usage.record(found.user.id, { model });
    turns.finish(turn.id, { status: "done" });
  } catch (err) {
    turns.finish(turn.id, { status: "error", error: err.message });
    if (res.headersSent) {
      try {
        res.end();
      } catch {}
      return;
    }
    throw err;
  }
}

function stringSystem(value) {
  if (!value) return "";
  if (typeof value === "string") return value;
  if (Array.isArray(value)) {
    return value
      .map((part) => (typeof part === "string" ? part : part?.text || ""))
      .filter(Boolean)
      .join("\n");
  }
  return String(value);
}

async function handleChat(req, res) {
  const found = requireUser(req, res);
  if (!found) return;
  const body = await readJsonBody(req, config.limits.maxBodyBytes);
  const model = registry.normalize(body.model) || registry.list()[0].id;
  if (!registry.allows(found.plan.id, model)) {
    return sendError(res, 403, "Модель недоступна на вашем тарифе", "model_not_allowed");
  }
  const resetsAt = usage.exceeded(found.user.id, found.plan);
  if (!found.plan.paid && !found.key) {
    return sendJson(res, 402, {
      error: {
        message: "На Free-тарифе моделей Clauden нет. Оформите подписку, чтобы продолжить.",
        type: "plan_required",
      },
    });
  }
  if (resetsAt && !found.key) {
    return sendJson(res, 429, {
      error: {
        message: "Лимит тарифа исчерпан. Попробуйте позже или смените тариф.",
        type: "rate_limit",
        resets_at: resetsAt,
      },
    });
  }
  if (running.size >= config.limits.maxConcurrentTurns) {
    return sendError(res, 429, "Сервер занят, попробуйте через минуту", "busy");
  }

  const conversation = String(req.headers["x-conversation-id"] || body.conversation || "");
  const prompt = promptText(body);
  const seat = billing.seatFor(found.user.id);
  const wantStream = body.stream !== false;
  const turn = turns.begin({ conversation, userId: found.user.id, model, text: prompt });
  const abort = new AbortController();
  splitters.set(turn.id, abort);
  running.add(turn.id);

  const finishTurn = (status, error = "") => {
    running.delete(turn.id);
    splitters.delete(turn.id);
    turns.finish(turn.id, { status, error });
  };

  try {
    if (!wantStream) {
      const result = await pool.complete(body, {
        model,
        system: body.system,
        account: seat?.token,
      });
      turns.append(turn.id, result.text);
      usage.record(found.user.id, { model, tokens: result.usage?.total_tokens || 0 });
      finishTurn("done");
      return sendJson(res, 200, {
        id: turn.id,
        object: "chat.completion",
        model,
        choices: [
          { index: 0, message: { role: "assistant", content: result.text }, finish_reason: "stop" },
        ],
        usage: result.usage || undefined,
      });
    }

    const stream = new SseStream(res);
    let full = "";
    const reasoningMs = Date.now();
    for await (const chunk of pool.stream(body, {
      model,
      system: body.system,
      signal: abort.signal,
      account: seat?.token,
    })) {
      if (chunk.reasoning) {
        stream.send({
          id: turn.id,
          object: "chat.completion.chunk",
          model,
          choices: [
            {
              index: 0,
              delta: { clauden_thinking: chunk.reasoning, clauden_thinking_ms: Date.now() - reasoningMs },
            },
          ],
        });
      }
      if (chunk.delta) {
        full += chunk.delta;
        turns.append(turn.id, chunk.delta);
        stream.send({
          id: turn.id,
          object: "chat.completion.chunk",
          model,
          choices: [{ index: 0, delta: { content: chunk.delta } }],
        });
      }
      if (chunk.finish) {
        stream.send({
          id: turn.id,
          object: "chat.completion.chunk",
          model,
          choices: [{ index: 0, delta: {}, finish_reason: chunk.finish }],
        });
      }
    }
    stream.done();
    usage.record(found.user.id, { model });
    finishTurn("done");
  } catch (err) {
    if (err?.name === "AbortError" || abort.signal.aborted) {
      finishTurn("stopped");
      if (!res.headersSent) return sendError(res, 499, "Запрос отменён", "cancelled");
      return;
    }
    finishTurn("error", err.message);
    if (res.headersSent) {
      try {
        res.write(`data: ${JSON.stringify({ error: { message: err.message } })}\n\n`);
        res.end();
      } catch {

      }
      return;
    }
    throw err;
  }
}

async function handleCode(req, res, url) {
  const found = requireUser(req, res);
  if (!found) return;
  if (!found.plan.paid) {
    return sendError(res, 403, "Clauden Code доступен на платном тарифе", "plan_required");
  }
  const userId = found.user.id;

  if (url.pathname === "/v1/code/models") {
    return sendJson(res, 200, { data: registry.list(found.plan.id) });
  }
  if (url.pathname === "/v1/code/sessions") {
    if (req.method === "GET") return sendJson(res, 200, { data: code.list(userId) });
    if (req.method === "POST") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, { data: code.create(userId, body) });
    }
  }
  if (url.pathname === "/v1/code/repos" || url.pathname === "/v1/code/github") {
    return sendJson(res, 200, { data: [] });
  }

  const match = url.pathname.match(
    /^\/v1\/code\/sessions\/([A-Za-z0-9_-]+)(?:\/([a-z]+))?(?:\/([A-Za-z0-9_.-]+))?$/,
  );
  if (!match) return sendError(res, 404, "Не найдено", "not_found");
  const [, id, action, extra] = match;
  const session = code.get(userId, id);
  if (!session) return sendError(res, 404, "Сессия не найдена", "session_not_found");

  if (!action && req.method === "GET") return sendJson(res, 200, { data: code.full(userId, id) });
  if (!action && req.method === "DELETE") {
    return sendJson(res, 200, { ok: code.remove(userId, id) });
  }

  if (action === "events" && req.method === "GET") {
    const stream = new SseStream(res);
    stream.comment("snapshot");
    stream.send(code.full(userId, id));
    const unsubscribe = code.subscribe(id, (event) => stream.send(event.data));
    req.on("close", () => {
      unsubscribe();
      stream.close();
    });
    return;
  }

  if (action === "messages" && req.method === "POST") {
    const body = await readJsonBody(req, config.limits.maxBodyBytes);
    return sendJson(res, 200, { data: await code.send(userId, id, body) });
  }
  if (action === "stop" && req.method === "POST") {
    return sendJson(res, 200, { ok: code.stop(userId, id) });
  }
  if (action === "model" && req.method === "POST") {
    const body = await readJsonBody(req);
    return sendJson(res, 200, { data: code.setModel(userId, id, body.model) });
  }
  if (action === "models" && req.method === "GET") {
    return sendJson(res, 200, code.models(userId, id));
  }
  if (action === "rename" && req.method === "POST") {
    const body = await readJsonBody(req);
    return sendJson(res, 200, { data: code.rename(userId, id, body.title) });
  }
  if (action === "link") {
    if (req.method === "POST") {
      const body = await readJsonBody(req);
      const token = crypto.randomBytes(18).toString("hex");
      remoteLinks.set(token, {
        userId,
        sessionId: id,
        createdAt: Date.now(),
        expiresAt: Date.now() + (Number(body.ttlHours) || 24) * 60 * 60 * 1000,
      });
      return sendJson(res, 200, {
        data: { token, url: `${config.publicBase}/r/${token}`, expiresAt: Date.now() + 24 * 60 * 60 * 1000 },
      });
    }
    if (req.method === "GET") {
      for (const [token, link] of remoteLinks) {
        if (link.sessionId === id && link.userId === userId) {
          return sendJson(res, 200, {
            data: { token, url: `${config.publicBase}/r/${token}`, expiresAt: link.expiresAt },
          });
        }
      }
      return sendJson(res, 200, { data: null });
    }
    if (req.method === "DELETE") {
      for (const [token, link] of remoteLinks) {
        if (link.sessionId === id && link.userId === userId) remoteLinks.delete(token);
      }
      return sendJson(res, 200, { ok: true });
    }
  }
  if (action === "usage" && req.method === "GET") {
    return sendJson(res, 200, { data: code.usage(userId, id) });
  }
  if (action === "diff" && req.method === "GET") {
    return sendJson(res, 200, { data: await code.diff(userId, id) });
  }
  if (action === "commit" && req.method === "POST") {
    const body = await readJsonBody(req);
    return sendJson(res, 200, { data: await code.commit(userId, id, body.message) });
  }
  if (action === "exec" && req.method === "POST") {
    const body = await readJsonBody(req);
    return sendJson(res, 200, { data: await code.exec(userId, id, body.command) });
  }
  if (action === "files") {
    if (req.method === "GET" && extra === "content") {
      return sendJson(res, 200, { data: code.readFile(userId, id, url.searchParams.get("path") || "") });
    }
    if (req.method === "POST" && extra === "content") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, { data: code.writeFile(userId, id, body.path, body.content) });
    }
    if (req.method === "POST" && extra === "upload") {
      const body = await readJsonBody(req);
      return sendJson(res, 200, { data: code.upload(userId, id, body.name, body.data) });
    }
    return sendJson(res, 200, { data: code.files(userId, id, url.searchParams.get("path") || "") });
  }
  if (action === "download" && req.method === "GET") {
    const file = code.download(userId, id, url.searchParams.get("path") || "");
    if (!file) return sendError(res, 404, "Файл не найден");
    res.writeHead(200, {
      "content-type": "application/octet-stream",
      "content-disposition": `attachment; filename="${encodeURIComponent(file.name)}"`,
      "content-length": file.buffer.length,
    });
    return res.end(file.buffer);
  }

  return sendError(res, 404, "Неизвестное действие", "not_found");
}

async function authResult(result) {
  const { token, user } = await result;
  return { token, user: publicUser(user, plans) };
}

function extractText({ name, mime, data }) {
  const fileName = String(name || "file");
  const buffer = Buffer.from(String(data || ""), "base64");
  const isText = /^text\//.test(String(mime || "")) || /\.(txt|md|json|csv|log|js|ts|py|kt|java|html|css|ya?ml|xml|sh)$/i.test(fileName);
  if (!isText) {
    return {
      name: fileName,
      text: "",
      note: `Формат ${mime || "binary"} не разбирается на сервере: приложите файл как есть`,
    };
  }
  return { name: fileName, text: buffer.toString("utf8").slice(0, 200_000) };
}

function sendHtml(res, status, html) {
  res.writeHead(status, { "content-type": "text/html; charset=utf-8" });
  res.end(html);
}

function returnPage(ok) {
  const title = ok ? "Оплата принята" : "Оплата не завершена";
  const text = ok
    ? "Вернитесь в приложение Clauden — тариф включится автоматически после подтверждения платежа."
    : "Платёж отменён. Вернитесь в Clauden и попробуйте другой способ оплаты.";
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title><style>
body{margin:0;min-height:100vh;display:grid;place-items:center;
background:#131313;color:#f4f3ef;font:16px/1.5 system-ui,sans-serif}
main{max-width:420px;padding:32px;text-align:center}
h1{font-size:24px;font-weight:600;margin:0 0 12px}
p{color:#a7a5a0;margin:0 0 10px}</style></head>
<body><main><h1>${title}</h1><p>${text}</p><p><a href="/" style="color:#e39a80">Открыть Clauden</a></p></main></body></html>`;
}

server.listen(config.port, config.host, () => {
  log(`${config.serviceName} ${VERSION} listening on http://${config.host}:${config.port}`);
  log(`pool: ${hasUpstream() ? config.upstream.base : "not configured"}`);
  log(`accounts: ${SUPPLIER_ENDPOINT}`);
});

setInterval(() => turns.prune(), 60 * 60 * 1000).unref?.();

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    log(`${signal} received, shutting down`);
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 5000).unref();
  });
}

export { server, plans, registry };
