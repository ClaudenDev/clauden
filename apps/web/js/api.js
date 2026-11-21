let token = "";

export function setToken(value) {
  token = String(value || "");
}
export function getToken() {
  return token;
}

export class ApiError extends Error {
  constructor(message, status = 0, type = "", extra = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.type = type;
    this.extra = extra;
  }
}

const NETWORK_MESSAGE =
  "Нет соединения с сервером. Проверьте интернет и повторите. " +
  "Если не помогает — смените сеть или включите/выключите VPN.";

export function isNetworkError(err) {
  if (!err) return false;
  if (err.type === "network") return true;
  return /failed to fetch|networkerror|network error|load failed|fetch failed|econnreset|socket hang up|other side closed|connection reset|err_network/i.test(
    String(err.message || ""),
  );
}

async function networkFetch(input, init) {
  try {
    return await fetch(input, init);
  } catch (err) {
    if (err?.name === "AbortError" || init?.signal?.aborted) throw err;
    throw new ApiError(NETWORK_MESSAGE, 0, "network", { cause: err });
  }
}

export function clientMeta() {
  const ua = String(globalThis.navigator?.userAgent || "");
  const desktop = isDesktopApp() || /electron/i.test(ua);
  const mobile = /Android|iPhone|iPad|iPod|Mobile/i.test(ua);
  let os = "";
  if (/Windows/i.test(ua)) os = "Windows";
  else if (/Android/i.test(ua)) os = "Android";
  else if (/iPhone|iPad|iPod/i.test(ua)) os = "iOS";
  else if (/Mac OS X|Macintosh/i.test(ua)) os = "macOS";
  else if (/Linux/i.test(ua)) os = "Linux";
  let browser = "";
  if (/Edg\//i.test(ua)) browser = "Edge";
  else if (/OPR\/|Opera/i.test(ua)) browser = "Opera";
  else if (/YaBrowser/i.test(ua)) browser = "Яндекс Браузер";
  else if (/Firefox\//i.test(ua)) browser = "Firefox";
  else if (/Chrome\//i.test(ua)) browser = "Chrome";
  else if (/Safari\//i.test(ua)) browser = "Safari";
  const device = desktop
    ? [os || "ПК", "Clauden"].filter(Boolean).join(" · ")
    : [os || (mobile ? "Телефон" : "Компьютер"), browser].filter(Boolean).join(" · ");
  const platform = desktop ? "desktop" : "browser";
  return { device, platform, mobile };
}

function authHeaders(extra = {}) {
  const { device, platform } = clientMeta();
  const headers = {
    "x-device-name": device,
    "x-device-platform": platform,
    ...extra,
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
    headers["x-api-key"] = token;
  }
  return headers;
}

async function parseError(res) {
  let payload = null;
  try {
    payload = await res.json();
  } catch {

  }
  const error = payload?.error || {};
  return new ApiError(
    error.message || `HTTP ${res.status}`,
    res.status,
    error.type || "",
    { resets_at: error.resets_at, ...payload },
  );
}

export async function request(path, { method = "GET", body, headers = {}, signal } = {}) {
  const options = {
    method,
    headers: authHeaders({ ...headers }),
    signal,
  };
  if (body !== undefined) {
    if (body instanceof FormData || body instanceof Blob) {
      options.body = body;
    } else {
      options.headers["content-type"] = "application/json";
      options.body = JSON.stringify(body);
    }
  }
  const res = await networkFetch(path, options);
  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return null;
  const type = res.headers.get("content-type") || "";
  if (!type.includes("application/json")) return res.text();
  return res.json();
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function codeAlreadySent(err) {
  return (
    err?.status === 429 &&
    /код уже отправлен|подождите минуту/i.test(String(err.message || ""))
  );
}

export const auth = {

  requestCode: async (email) => {
    try {
      return await request("/v1/auth/email/code", { method: "POST", body: { email } });
    } catch (err) {
      if (!isNetworkError(err)) throw err;
      await sleep(900);
      try {
        return await request("/v1/auth/email/code", { method: "POST", body: { email } });
      } catch (retryErr) {
        if (codeAlreadySent(retryErr)) return { ok: true, expiresIn: 600 };
        throw retryErr;
      }
    }
  },

  register: async (email, code, password) => {
    const payload = { email, code, password };
    try {
      return await request("/v1/auth/email/register", { method: "POST", body: payload });
    } catch (err) {
      if (!isNetworkError(err)) throw err;
      await sleep(900);
      try {
        return await request("/v1/auth/email/register", { method: "POST", body: payload });
      } catch (retryErr) {
        if (
          retryErr.status === 400 &&
          /код не найден|код истёк|неверный код/i.test(String(retryErr.message || ""))
        ) {
          try {
            return await request("/v1/auth/email", {
              method: "POST",
              body: { email, password },
            });
          } catch {

          }
        }
        throw retryErr;
      }
    }
  },
  login: (email, password) =>
    request("/v1/auth/email", { method: "POST", body: { email, password } }),
  google: (idToken) =>
    request("/v1/auth/google", { method: "POST", body: { id_token: idToken } }),
  claudeLink: (payload) =>
    request("/v1/auth/claude/link", { method: "POST", body: payload }),
  me: () => request("/v1/auth/me"),
  update: (patch) => request("/v1/auth/me", { method: "PATCH", body: patch }),
  setPassword: (password) => request("/v1/auth/password", { method: "POST", body: { password } }),
  logout: () => request("/v1/auth/logout", { method: "POST", body: {} }),
  sessions: () => request("/v1/auth/sessions"),
  revoke: (id) => request("/v1/auth/sessions/revoke", { method: "POST", body: { id } }),
  logoutAll: () => request("/v1/auth/logout-all", { method: "POST", body: {} }),
  deleteAccount: () => request("/v1/auth/account", { method: "DELETE" }),
};

export const api = {
  models: () => request("/v1/models"),
  authConfig: () => request("/v1/auth/config"),
  plans: () => request("/v1/plans"),
  usage: () => request("/v1/usage"),
  keys: () => request("/v1/keys"),
  createKey: () => request("/v1/keys", { method: "POST", body: {} }),
  resetKey: () => request("/v1/keys/reset", { method: "POST", body: {} }),
  revokeKey: (id) => request(`/v1/keys/${encodeURIComponent(id)}`, { method: "DELETE" }),
  health: () => request("/health"),
  extract: (name, mime, base64) =>
    request("/v1/extract", { method: "POST", body: { name, mime, data: base64 } }),
  memory: () => request("/v1/memory"),
  addMemory: (text) => request("/v1/memory", { method: "POST", body: { text } }),
  importMemory: (items) => request("/v1/memory", { method: "POST", body: { items } }),
  updateMemory: (id, text) => request(`/v1/memory/${encodeURIComponent(id)}`, { method: "PATCH", body: { text } }),
  deleteMemory: (id) => request(`/v1/memory/${encodeURIComponent(id)}`, { method: "DELETE" }),
  clearMemory: () => request("/v1/memory", { method: "DELETE" }),
  transcribe: (blob, language = "ru") =>
    request(`/v1/transcribe?language=${encodeURIComponent(language)}`, {
      method: "POST",
      body: blob,
      headers: { "content-type": blob?.type || "audio/webm" },
    }),
  chatOnce: (payload) =>
    request("/v1/chat/completions", { method: "POST", body: { ...payload, stream: false } }),
  turns: (conversation) =>
    request(`/v1/turns?conversation=${encodeURIComponent(`c:${conversation}`)}`),
  activeTurn: (conversation) =>
    request(`/v1/turn/active?conversation=${encodeURIComponent(`c:${conversation}`)}`),
  stopTurn: (conversation, turnId) =>
    request("/v1/turns/stop", {
      method: "POST",
      body: { turnId },
      headers: { "X-Conversation-Id": conversation, "X-Turn-Id": turnId },
    }),
  chats: () => request("/v1/chats"),
  backupChats: (conversations) =>
    request("/v1/chats/backup", { method: "POST", body: { conversations } }),
  deleteChat: (id) => request(`/v1/chats/${encodeURIComponent(id)}`, { method: "DELETE" }),
  banRisk: (ip) => request("/v1/security/ban-risk", { method: "POST", body: { ip } }),
  clearChats: () => request("/v1/chats", { method: "DELETE" }),
  projects: () => request("/v1/projects"),
  backupProjects: (projects) =>
    request("/v1/projects/backup", { method: "POST", body: { projects } }),
  deleteProject: (id) => request(`/v1/projects/${encodeURIComponent(id)}`, { method: "DELETE" }),
  checkout: (plan, method, autoRenew = true) =>
    request("/v1/billing/checkout", { method: "POST", body: { plan, method, autoRenew } }),
  cancelRenewal: () => request("/v1/billing/cancel", { method: "POST", body: {} }),
  order: (id) => request(`/v1/billing/order/${encodeURIComponent(id)}`),
  connectorCheck: (url, headers) =>
    request("/v1/connectors/check", { method: "POST", body: { url, headers } }),
  connectorOAuthStart: (payload) =>
    request("/v1/connectors/oauth/start", { method: "POST", body: payload }),
  connectorOAuthFinish: (attemptId, callbackUrl) =>
    request("/v1/connectors/oauth/finish", { method: "POST", body: { attemptId, callbackUrl } }),
  connectorOAuthResult: (attemptId) =>
    request(`/v1/connectors/oauth/result?attemptId=${encodeURIComponent(attemptId)}`),
  connectorCatalog: ({ q = "", category = "", limit = 24, offset = 0 } = {}) => {
    const params = new URLSearchParams({ limit: String(limit), offset: String(offset), connectable: "1" });
    if (q) params.set("q", q);
    if (category) params.set("category", category);
    return request(`/v1/connectors/catalog?${params}`);
  },
  market: (kind, { q = "", limit = 24, offset = 0, source } = {}) => {
    const params = new URLSearchParams({ q, limit: String(limit), offset: String(offset) });
    if (source) params.set("source", source);
    return request(`/v1/market/${kind}?${params}`);
  },
  marketInstall: (kind, id, preview = false) =>
    request(`/v1/market/${preview ? "preview" : "install"}`, {
      method: "POST",
      body: { kind, id },
    }),
};

export const codeApi = {
  repos: () => request("/v1/code/repos"),
  github: () => request("/v1/code/github"),
  saveGithub: (value) => request("/v1/code/github", { method: "POST", body: { token: value } }),
  removeGithub: () => request("/v1/code/github", { method: "DELETE" }),
  githubRepos: () => request("/v1/code/github/repos"),
  models: () => request("/v1/code/models"),
  sessions: () => request("/v1/code/sessions"),
  create: (payload) => request("/v1/code/sessions", { method: "POST", body: payload }),
  session: (id) => request(`/v1/code/sessions/${id}`),
  send: (id, text, files) =>
    request(`/v1/code/sessions/${id}/messages`, {
      method: "POST",
      body: files?.length ? { text, files } : { text },
    }),
  stop: (id) => request(`/v1/code/sessions/${id}/stop`, { method: "POST", body: {} }),
  remove: (id) => request(`/v1/code/sessions/${id}`, { method: "DELETE" }),
  usage: (id) => request(`/v1/code/sessions/${id}/usage`),
  sessionModels: (id) => request(`/v1/code/sessions/${id}/models`),
  setModel: (id, model) =>
    request(`/v1/code/sessions/${id}/model`, {
      method: "POST",
      body: { providerID: model.providerID || "clauden", id: model.id || model.modelID || model, variant: model.variant || "" },
    }),
  undo: (id) => request(`/v1/code/sessions/${id}/undo`, { method: "POST", body: {} }),
  redo: (id) => request(`/v1/code/sessions/${id}/redo`, { method: "POST", body: {} }),
  upload: (id, name, data) =>
    request(`/v1/code/sessions/${id}/files/upload`, { method: "POST", body: { name, data } }),
  rename: (id, title) =>
    request(`/v1/code/sessions/${id}/rename`, { method: "POST", body: { title } }),
  diff: (id) => request(`/v1/code/sessions/${id}/diff`),
  commit: (id, message) =>
    request(`/v1/code/sessions/${id}/commit`, { method: "POST", body: { message } }),
  pull: (id, title, body) =>
    request(`/v1/code/sessions/${id}/pr`, { method: "POST", body: { title, body } }),
  exec: (id, command) =>
    request(`/v1/code/sessions/${id}/exec`, { method: "POST", body: { command } }),
  files: (id, path = "") =>
    request(`/v1/code/sessions/${id}/files?path=${encodeURIComponent(path)}`),
  fileRead: (id, path) =>
    request(`/v1/code/sessions/${id}/files/content?path=${encodeURIComponent(path)}`),
  fileWrite: (id, path, content) =>
    request(`/v1/code/sessions/${id}/files/content`, { method: "POST", body: { path, content } }),
  jobResult: (id, jobId, result) =>
    request(`/v1/code/sessions/${id}/device/jobs/${jobId}`, { method: "POST", body: result }),
  download: async (id, path) => {
    const res = await networkFetch(
      `/v1/code/sessions/${id}/download?path=${encodeURIComponent(path)}`,
      { headers: authHeaders() },
    );
    if (!res.ok) throw await parseError(res);
    return res.blob();
  },
  commands: (id) => request(`/v1/code/sessions/${id}/commands`),
  runCommand: (id, name, text) =>
    request(`/v1/code/sessions/${id}/command`, { method: "POST", body: { name, text } }),
  answerForm: (id, formId, answer) =>
    request(`/v1/code/sessions/${id}/forms/${formId}`, { method: "POST", body: { answer } }),
  answerPermission: (id, permissionId, decision, message) =>
    request(`/v1/code/sessions/${id}/permissions/${permissionId}`, {
      method: "POST",
      body: { decision, message },
    }),
};

export const remoteApi = {
  create: (id, payload = {}) =>
    request(`/v1/code/sessions/${id}/link`, { method: "POST", body: payload }),
  current: (id) => request(`/v1/code/sessions/${id}/link`),
  revoke: (id) => request(`/v1/code/sessions/${id}/link`, { method: "DELETE" }),
};

export const remoteClient = {
  snapshot: (token) => request(`/v1/remote/${token}`),
  send: (token, text) =>
    request(`/v1/remote/${token}/messages`, { method: "POST", body: { text } }),
  stop: (token) => request(`/v1/remote/${token}/stop`, { method: "POST", body: {} }),
  permission: (token, id, decision, message) =>
    request(`/v1/remote/${token}/permissions/${id}`, {
      method: "POST",
      body: { decision, message },
    }),
  form: (token, id, answer) =>
    request(`/v1/remote/${token}/forms/${id}`, { method: "POST", body: { answer } }),
};

export async function streamRemote(token, { signal, handlers }) {
  const res = await networkFetch(`/v1/remote/${token}/events`, {
    headers: { accept: "text/event-stream" },
    signal,
  });
  if (!res.ok) throw await parseError(res);
  await readSse(res, {
    signal,
    onEvent: (payload, event) => handlers.onEvent?.(payload, event),
  });
}

async function readSse(res, { onEvent, signal, idleMs = 0 }) {
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      if (signal?.aborted) return;
      let chunk;
      if (idleMs > 0) {
        let timer = null;
        try {
          chunk = await Promise.race([
            reader.read(),
            new Promise((_, reject) => {
              timer = setTimeout(
                () => reject(new ApiError("Соединение с сервером зависло", 0, "network")),
                idleMs,
              );
            }),
          ]);
        } finally {
          if (timer) clearTimeout(timer);
        }
      } else {
        chunk = await reader.read();
      }
      const { done, value } = chunk;
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replaceAll("\r\n", "\n");
      let index;
      while ((index = buffer.indexOf("\n\n")) !== -1) {
        const block = buffer.slice(0, index);
        buffer = buffer.slice(index + 2);
        let event = "";
        const dataLines = [];
        for (const line of block.split("\n")) {
          if (line.startsWith("event:")) event = line.slice(6).trim();
          else if (line.startsWith("data:")) dataLines.push(line.slice(5).replace(/^ /, ""));
        }
        if (!dataLines.length) continue;
        const raw = dataLines.join("\n");
        let payload = raw;
        try { payload = JSON.parse(raw); } catch {  }
        await onEvent(payload, event, raw);
      }
    }
  } finally {
    try { await reader.cancel(); } catch {  }
  }
}

export async function streamChat(payload, { conversationId, turnId, signal, handlers }) {
  const res = await networkFetch("/v1/chat/completions", {
    method: "POST",
    headers: authHeaders({
      "content-type": "application/json",
      accept: "text/event-stream",
      "x-conversation-id": conversationId,
      "x-turn-id": turnId,
      "x-timezone": Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
      "x-locale": navigator.language || "ru-RU",
      "x-clauden-client": "web",
    }),
    body: JSON.stringify(payload),
    signal,
  });

  if (!res.ok) throw await parseError(res);
  const type = res.headers.get("content-type") || "";
  if (!type.includes("text/event-stream")) {
    const data = await res.json().catch(() => null);
    const text = data?.choices?.[0]?.message?.content || data?.content || "";
    if (!String(text).trim()) {
      throw new ApiError("empty answer", 502, "empty_answer");
    }
    handlers.onDelta?.(text);
    handlers.onDone?.(text);
    return;
  }

  let finished = false;
  let full = "";
  let sawFinishReason = false;
  const finish = (text) => {
    if (finished) return;
    finished = true;
    handlers.onDone?.(text ?? full);
  };

  await readSse(res, {
    signal,

    idleMs: 60_000,
    onEvent: (event) => {
      if (event === "[DONE]") {
        if (!full.trim()) throw new ApiError("empty answer", 502, "empty_answer");
        finish(full);
        return;
      }
      if (typeof event === "string") return;
      if (event.error) {
        const message = event.error.message || event.error || "Ошибка ответа";
        throw new ApiError(message, 502, "stream_error");
      }
      const choice = event.choices?.[0];
      const delta = choice?.delta || choice?.message || {};
      if (typeof delta.content === "string" && delta.content) {
        full += delta.content;
        handlers.onDelta?.(delta.content);
      }
      const reasoning = delta.clauden_thinking ?? delta.reasoning_content;
      if (typeof reasoning === "string" && reasoning) handlers.onThinking?.(reasoning);
      if (delta.clauden_thinking_ms != null) handlers.onThinkingMs?.(Number(delta.clauden_thinking_ms) || 0);
      if (delta.clauden_step) handlers.onStep?.(delta.clauden_step);
      if (delta.clauden_file) handlers.onFile?.(delta.clauden_file);
      if (delta.clauden_action) handlers.onAction?.(delta.clauden_action);
      if (delta.clauden_sources) handlers.onSources?.(delta.clauden_sources);
      if (choice?.finish_reason) sawFinishReason = true;
    },
  });
  if (finished) return;

  if (!sawFinishReason) {
    throw new ApiError("stream ended without finish_reason", 502, "incomplete_stream");
  }
  if (!full.trim()) throw new ApiError("empty answer", 502, "empty_answer");
  finish(full);
}

export async function streamCodeSession(id, { signal, handlers }) {
  const res = await networkFetch(`/v1/code/sessions/${id}/events`, {
    headers: authHeaders({ accept: "text/event-stream" }),
    signal,
  });
  if (!res.ok) throw await parseError(res);
  await readSse(res, {
    signal,
    onEvent: (payload, event) => handlers.onEvent?.(payload, event),
  });
}

export function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

export function dataUrlToBase64(dataUrl) {
  const comma = String(dataUrl).indexOf(",");
  return comma >= 0 ? String(dataUrl).slice(comma + 1) : String(dataUrl);
}

export function desktopBridge() {
  return typeof window !== "undefined" && window.claudenDesktop?.isDesktop ? window.claudenDesktop : null;
}

export function isDesktopApp() {
  return Boolean(desktopBridge());
}

export function isCustomApiSupported() {
  return typeof desktopBridge()?.customApi?.start === "function";
}

function unwrapDesktop(result) {
  if (!result || result.ok === false) {
    const error = result?.error || {};
    throw new ApiError(error.message || "Claude недоступен", 0, error.kind || "claude_error", {
      retryAt: error.retryAt || 0,
    });
  }
  return result.data;
}

export const proxyDesktop = {
  available: () => typeof desktopBridge()?.proxy?.state === "function",
  state: async () => unwrapDesktop(await desktopBridge().proxy.state()),
  ownExit: async () => {
    const bridge = desktopBridge();
    if (typeof bridge?.proxy?.ownExit !== "function") {
      throw new ApiError("Обновите приложение Clauden для проверки выхода", 0, "desktop_only");
    }
    return unwrapDesktop(await bridge.proxy.ownExit());
  },
  refresh: async () => unwrapDesktop(await desktopBridge().proxy.refresh()),
  subscribe: async (input) => unwrapDesktop(await desktopBridge().proxy.subscribe({ input })),
  select: async (id) => unwrapDesktop(await desktopBridge().proxy.select(id)),
  connect: async (id) => unwrapDesktop(await desktopBridge().proxy.connect(id)),
  disconnect: async () => unwrapDesktop(await desktopBridge().proxy.disconnect()),
  ping: async (id) => unwrapDesktop(await desktopBridge().proxy.ping(id)),
  autostart: async (value) => unwrapDesktop(await desktopBridge().proxy.autostart(value)),
  onEvent: (listener) => desktopBridge()?.proxy?.onEvent(listener) || (() => {}),
};

export const claudeDesktop = {
  status: async (options) => unwrapDesktop(await desktopBridge().claude.status(options)),
  login: async () => unwrapDesktop(await desktopBridge().claude.login()),
  logout: async () => unwrapDesktop(await desktopBridge().claude.logout()),
  refresh: async () => unwrapDesktop(await desktopBridge().claude.refresh()),
  models: async () => unwrapDesktop(await desktopBridge().claude.models()),
  exitIp: async () => {
    const bridge = desktopBridge();
    if (typeof bridge?.claude?.exitIp !== "function") {
      throw new ApiError("Обновите приложение Clauden для проверки риска", 0, "desktop_only");
    }
    return unwrapDesktop(await bridge.claude.exitIp());
  },
  download: async (file) => unwrapDesktop(await desktopBridge().claude.download(file)),
  importList: async (options) => unwrapDesktop(await desktopBridge().claude.importList(options)),
  importOne: async (summary) => unwrapDesktop(await desktopBridge().claude.importOne(summary)),
  desktopStatus: async () => {
    const bridge = desktopBridge();
    if (typeof bridge?.claude?.desktop !== "function") return { supported: false };
    return unwrapDesktop(await bridge.claude.desktop());
  },
  importDesktop: async (options) => {
    const bridge = desktopBridge();
    if (typeof bridge?.claude?.importDesktop !== "function") {
      throw new ApiError("Обновите приложение Clauden для импорта сессии Claude", 0, "desktop_only");
    }
    return unwrapDesktop(await bridge.claude.importDesktop(options));
  },
};

export const customApi = {
  list: async () => unwrapDesktop(await desktopBridge().customApi.list()),
  get: async (id) => unwrapDesktop(await desktopBridge().customApi.get(id)),
  save: async (payload) => unwrapDesktop(await desktopBridge().customApi.save(payload)),
  remove: async (id) => unwrapDesktop(await desktopBridge().customApi.remove(id)),
  test: async (payload) => unwrapDesktop(await desktopBridge().customApi.test(payload)),
};

export function isUpdateSupported() {
  return typeof desktopBridge()?.updates?.status === "function";
}

export const desktopUpdates = {
  status: async () => unwrapDesktop(await desktopBridge().updates.status()),
  check: async () => unwrapDesktop(await desktopBridge().updates.check()),
  install: async () => unwrapDesktop(await desktopBridge().updates.install()),
  onEvent: (listener) => {
    const bridge = desktopBridge();
    if (typeof bridge?.updates?.onEvent !== "function") return () => {};
    return bridge.updates.onEvent(listener);
  },
};

export async function streamCustomApi(payload, { signal, handlers = {} } = {}) {
  const bridge = desktopBridge();
  if (typeof bridge?.customApi?.start !== "function") {
    throw new ApiError("Свой API доступен только в приложении Clauden для ПК", 0, "desktop_only");
  }
  const streamId = `custom_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
  let full = "";
  let closed = false;
  let done = false;
  let idleTimer = null;
  let resolveStream;
  let rejectStream;
  const promise = new Promise((resolve, reject) => {
    resolveStream = resolve;
    rejectStream = reject;
  });

  const cleanup = () => {
    if (closed) return;
    closed = true;
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = null;
    off();
    signal?.removeEventListener("abort", onAbort);
  };

  const finish = (text) => {
    if (done) return;
    done = true;
    cleanup();
    handlers.onDone?.(text ?? full);
    resolveStream(text ?? full);
  };

  const fail = (error) => {
    if (done) return;
    done = true;
    cleanup();
    handlers.onError?.(error);
    rejectStream(error);
  };

  const stop = () => {
    bridge.customApi.stop(streamId).catch(() => {});
  };

  const onAbort = () => {
    stop();
    finish(full);
  };

  const bump = () => {
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      stop();
      fail(new ApiError("API не отвечает. Проверьте соединение.", 0, "timeout"));
    }, 180000);
  };

  const off = bridge.customApi.onEvent((event) => {
    if (!event || event.streamId !== streamId) return;
    bump();
    if (event.type === "delta") {
      const piece = event.text || "";
      full += piece;
      handlers.onDelta?.(piece);
    } else if (event.type === "done") {
      if (typeof event.text === "string" && event.text) full = event.text;
      finish(full);
    } else if (event.type === "error") {
      fail(new ApiError(event.error?.message || "API вернул ошибку", 0, event.error?.kind || "custom_error"));
    }
  });

  if (signal) {
    if (signal.aborted) onAbort();
    else signal.addEventListener("abort", onAbort, { once: true });
  }

  try {
    const started = await bridge.customApi.start({ streamId, ...payload });
    if (!started?.ok) {
      throw new ApiError(started?.error?.message || "Не удалось запустить API", 0, started?.error?.kind || "custom_error");
    }
    bump();
  } catch (err) {
    cleanup();
    throw err;
  }
  return promise;
}

export async function streamClaudeDesktop(payload, { signal, handlers = {} } = {}) {
  const bridge = desktopBridge();
  if (!bridge) {
    throw new ApiError("Claude-режим доступен только в приложении Clauden для ПК", 0, "desktop_only");
  }
  const streamId = `claude_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
  let closed = false;
  let done = false;
  let idleTimer = null;
  let resolveStream;
  let rejectStream;
  const promise = new Promise((resolve, reject) => {
    resolveStream = resolve;
    rejectStream = reject;
  });

  const onAbort = () => {
    bridge.claude.stop(streamId).catch(() => {});
  };

  const cleanup = () => {
    if (closed) return;
    closed = true;
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = null;
    off();
    signal?.removeEventListener("abort", onAbort);
  };

  const bump = () => {
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      onAbort();
      fail(new ApiError("Claude не отвечает. Проверьте соединение.", 0, "timeout"));
    }, 600000);
  };

  const finish = (text) => {
    if (done) return;
    done = true;
    cleanup();
    handlers.onDone?.(text);
    resolveStream(text);
  };

  const fail = (error) => {
    if (done) return;
    done = true;
    cleanup();
    handlers.onError?.(error);
    rejectStream(error);
  };

  const off = bridge.claude.onEvent((event) => {
    if (!event || event.streamId !== streamId) return;
    bump();
    if (event.type === "conversation") {
      handlers.onConversation?.(event.conversationId);
    } else if (event.type === "delta") {
      handlers.onDelta?.(event.text || "");
    } else if (event.type === "step") {
      handlers.onStep?.(event.step);
    } else if (event.type === "done") {
      const text = String(event.text || "");
      if (!text.trim()) {

        fail(new ApiError(
          "Claude вернул пустой ответ. Проверьте вход в Claude и повторите запрос.",
          0,
          "empty_answer",
        ));
        return;
      }
      finish(text);
    } else if (event.type === "error") {
      fail(new ApiError(event.error?.message || "Claude вернул ошибку", 0, event.error?.kind || "claude_error", {
        retryAt: event.error?.retryAt || 0,
      }));
    }
  });

  if (signal) {
    if (signal.aborted) onAbort();
    else signal.addEventListener("abort", onAbort, { once: true });
  }

  try {
    const started = await bridge.claude.start({ streamId, ...payload });
    if (!started?.ok) {
      throw new ApiError(started?.error?.message || "Не удалось запустить Claude", 0, started?.error?.kind || "claude_error", {
        retryAt: started?.error?.retryAt || 0,
      });
    }
    bump();
  } catch (err) {
    cleanup();
    throw err;
  }
  return promise;
}
