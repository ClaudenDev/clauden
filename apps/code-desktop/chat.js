import { h, icon, rpc, toast, clear, escapeHtml, relativeTime, throttle, mascotNode } from "./ui.js";
import { state } from "./state.js";
import { renderMarkdown, enhanceMarkdown } from "./markdown.js";

let els = null;
let ctxRef = null;
let live = null;

const ICON_FOR_STEP = (step) => {
  const name = String(step?.name || "").toLowerCase();
  if (step.status === "failed") return "alert";
  if (["bash", "shell", "exec"].includes(name)) return "terminal";
  if (["websearch", "web_search", "webfetch", "fetch"].includes(name)) return "search";
  if (["read", "write", "edit", "glob", "grep", "list"].includes(name)) return "file";
  if (step.status === "running") return "clock";
  return "check";
};

export function mountChat(host, ctx) {
  ctxRef = ctx;

  const col = h("div", { class: "chat-col" });
  const feed = h("div", { class: "chat-feed" }, col);
  const status = h("span", { class: "chat-status" });
  const stopBtn = h("button", {
    class: "icon-btn",
    title: "Остановить агента",
    html: icon("stop", 13),
    onclick: () => ctx.stopAgent(),
  });
  const remoteBtn = h("button", {
    class: "icon-btn",
    title: "Remote Control",
    html: icon("link", 14),
    onclick: () => ctx.openRemote(),
  });
  const modelBtn = h("button", {
    class: "pill-btn",
    title: "Модель сессии",
    text: "Модель",
    onclick: () => ctx.openModels(),
  });
  const usageChip = h("span", { class: "sb-item usage-chip", title: "Токены и контекст", text: "…" });
  const commandsBtn = h("button", {
    class: "icon-btn",
    title: "Команды: /compact, /undo, /model",
    html: icon("terminal", 14),
    onclick: () => ctx.openCommands(),
  });
  const attachBtn = h("button", {
    class: "icon-btn",
    title: "Прикрепить файлы",
    html: icon("file", 14),
    onclick: () => ctx.attachFiles(),
  });

  const head = h(
    "div",
    { class: "chat-head" },
    mascotNode(18),
    h("span", { class: "chat-title", text: "Агент" }),
    status,
    h("span", { class: "spacer" }),
    usageChip,
    modelBtn,
    commandsBtn,
    attachBtn,
    remoteBtn,
    stopBtn,
  );

  const textarea = h("textarea", {
    placeholder: "Напишите агенту… (/remote-control — ссылка на сессию)",
    rows: 1,
  });
  const sendBtn = h("button", {
    class: "send-btn",
    title: "Отправить (Enter)",
    html: icon("arrowUp", 14),
    onclick: () => send(),
  });

  textarea.addEventListener("input", () => {
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(textarea.scrollHeight, 180)}px`;
    sendBtn.disabled = !textarea.value.trim();
  });
  textarea.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      send();
    }
  });

  const composer = h(
    "div",
    { class: "composer" },
    h(
      "div",
      { class: "composer-box" },
      textarea,
      h(
        "div",
        { class: "composer-bar" },
        h("span", { class: "composer-hint", text: "Enter — отправить" }),
        h("span", { class: "spacer" }),
        sendBtn,
      ),
    ),
  );

  host.append(head, feed, composer);
  els = { host, head, feed, col, status, textarea, sendBtn, stopBtn, remoteBtn, modelBtn, usageChip, commandsBtn, attachBtn };
  renderChat();
}

export function focusComposer() {
  els?.textarea.focus();
}

export function renderUsage() {
  if (!els?.usageChip) return;
  const session = state.session;
  const tokens = state.usage?.tokens;

  const contextTokens = Number(state.usage?.contextTokens || 0);
  const sessionTokens = tokens
    ? Number(tokens.input || 0) +
      Number(tokens.output || 0) +
      Number(tokens.reasoning || 0) +
      Number(tokens.cache?.read || 0) +
      Number(tokens.cache?.write || 0)
    : 0;
  const model = (state.models || []).find(
    (item) => item.id === (session?.modelAlias || String(session?.model || "").replace(/^clauden\//, "")),
  );
  const limit = Number(model?.context || 0);
  const cost = Number(state.usage?.cost || 0);
  const parts = [];
  if (contextTokens) parts.push(`${formatTokens(contextTokens)} токенов`);
  if (limit) parts.push(`${Math.min(100, Math.round((contextTokens / limit) * 100))}% контекста`);
  if (cost > 0) parts.push(`$${cost.toFixed(2)}`);
  els.usageChip.textContent = parts.join(" · ") || "0 токенов";
  els.usageChip.title = [
    contextTokens
      ? `Контекст последнего шага: ${contextTokens.toLocaleString("ru-RU")}`
      : "",
    limit ? `Окно модели: ${limit.toLocaleString("ru-RU")}` : "",
    sessionTokens ? `Всего за сессию: ${sessionTokens.toLocaleString("ru-RU")}` : "",
  ].filter(Boolean).join("\n") || "Расход токенов сессии";
  if (els.modelBtn) {
    els.modelBtn.textContent = modelLabel(session?.modelAlias || "claude-sonnet-5-5");
    els.modelBtn.title = `Модель: ${els.modelBtn.textContent}`;
  }
}

export function modelLabel(id) {
  const key = String(id || "").replace(/^clauden\//, "");
  const labels = {
    "claude-fable-5-2": "Claude Fable 5.2",
    "claude-fable-5-1": "Claude Fable 5.1",
    "claude-opus-5-5": "Claude Opus 5.5",
    "claude-sonnet-5-5": "Claude Sonnet 5.5",
    "claude-haiku-5-5": "Claude Haiku 5.5",
    "claude-sonnet-4-6": "Claude Sonnet 4.6",
    "claude-sonnet-4-5": "Claude Sonnet 4.5",
    "claude-haiku-4-5": "Claude Haiku 4.5",
  };
  return labels[key] || key.replace(/^claude-/, "Claude ").replace(/-/g, " ");
}

function formatTokens(value) {
  const number = Number(value) || 0;
  if (number >= 1_000_000) return `${(number / 1_000_000).toFixed(1)}M`;
  if (number >= 1000) return `${(number / 1000).toFixed(number >= 10_000 ? 0 : 1)}k`;
  return String(number);
}

async function send() {
  const text = els.textarea.value.trim();
  if (!state.session) {
    if (text) toast("Сначала создайте или откройте сессию", "error");
    return;
  }
  if (!text) return;

  if (text.startsWith("/")) {
    if (await runSlashCommand(text)) {
      els.textarea.value = "";
      els.textarea.dispatchEvent(new Event("input"));
      return;
    }
  }
  if (/^\/remote-control\b/i.test(text)) {
    els.textarea.value = "";
    els.textarea.dispatchEvent(new Event("input"));
    ctxRef.openRemote();
    return;
  }
  if (text === "/diff") {
    els.textarea.value = "";
    els.textarea.dispatchEvent(new Event("input"));
    ctxRef.openDiff();
    return;
  }

  els.textarea.value = "";
  els.textarea.style.height = "auto";
  els.sendBtn.disabled = true;

  state.messages.push({ role: "user", text, at: Date.now() });
  state.status = "working";
  renderChat();

  try {
    await rpc(window.clauden.code.send(state.session.id, text));
  } catch (err) {
    state.status = "idle";
    state.error = err.message || "Не удалось отправить";
    renderChat();
  }
}

async function runSlashCommand(text) {
  const raw = String(text || "").trim();
  if (!raw.startsWith("/")) return false;
  const [head, ...rest] = raw.slice(1).split(/\s+/);
  const name = head.toLowerCase();
  const arg = rest.join(" ").trim();
  const id = state.session?.id;
  if (!id) return false;

  if (name === "help") {
    ctxRef.openCommands(true);
    return true;
  }
  if (name === "remote-control") {
    ctxRef.openRemote();
    return true;
  }
  if (name === "diff") {
    ctxRef.openDiff();
    return true;
  }
  if (name === "compact") {
    try {
      await rpc(window.clauden.code.compact(id));
      toast("Контекст сжат");
      ctxRef.refreshUsage?.();
    } catch (err) {
      toast(err.message || "Не удалось сжать контекст", "error");
    }
    return true;
  }
  if (name === "undo" || name === "redo") {
    try {
      const full = await rpc(window.clauden.code[name](id));
      applyFullSession(full);
      toast(name === "undo" ? "Ход отменён" : "Откат отменён");
    } catch (err) {
      toast(err.message || "Не удалось выполнить", "error");
    }
    return true;
  }
  if (name === "model") {
    if (!arg) {
      ctxRef.openModels();
      return true;
    }
    const model = (state.models || []).find((item) => item.id === arg || item.name === arg);
    if (!model) {
      toast(`Модель «${arg}» не найдена`, "error");
      return true;
    }
    try {
      await rpc(window.clauden.code.setModel(id, {
        providerID: model.providerID || "clauden",
        id: model.id,
      }));
      state.session.modelAlias = model.id;
      state.session.model = `clauden/${model.id}`;
      renderUsage();
      toast(`Модель: ${model.name || modelLabel(model.id)}`, "success");
    } catch (err) {
      toast(err.message || "Не удалось сменить модель", "error");
    }
    return true;
  }
  if (name === "clear") {
    ctxRef.stopAgent();
    return true;
  }

  try {
    const full = await rpc(window.clauden.code.runCommand(id, name, arg));
    if (full) applyFullSession(full);
    toast(`/${name} запущена`);
  } catch (err) {
    toast(err.message || `Не удалось выполнить /${name}`, "error");
  }
  return true;
}

export function applyFullSession(full) {
  if (!full) return;
  state.session = { ...state.session, ...full };
  state.messages = (full.messages || []).map(normalizeMessage);
  state.permissions = full.permissions || [];
  state.forms = full.forms || [];
  state.status = full.status || "idle";
  state.error = full.error || "";
  state.streaming = false;
  state.streamText = "";
  renderChat();
  ctxRef?.onSnapshot?.(full);
}

export function renderChat() {
  if (!els) return;

  renderUsage();
  const working = state.status === "working";
  els.status.textContent = working ? "работает" : state.connection === "live" ? "на связи" : "офлайн";
  els.status.style.color = working ? "var(--clay)" : "var(--ink-3)";
  els.stopBtn.classList.toggle("hidden", !working);
  els.remoteBtn.classList.toggle("hidden", !state.session);

  clear(els.col);

  if (!state.session) {
    els.col.append(
      h(
        "div",
        { class: "chat-empty" },
        h("div", { text: "Сессия не выбрана." }),
        h("div", { text: "Выберите сессию слева или создайте новую.", style: { marginTop: "4px" } }),
        h(
          "div",
          { style: { display: "flex", gap: "8px", marginTop: "12px", flexWrap: "wrap" } },
          ctxRef?.openLocal
            ? h("button", {
                class: "btn btn-secondary btn-sm",
                text: "Открыть папку на этом ПК",
                onclick: () => ctxRef.openLocal(),
              })
            : null,
          ctxRef?.openBlank
            ? h("button", {
                class: "btn btn-ghost btn-sm",
                text: "Пустая сессия",
                onclick: () => ctxRef.openBlank(),
              })
            : null,
        ),
      ),
    );
    return;
  }

  if (!state.messages.length) {
    els.col.append(
      h("div", { class: "chat-empty", text: "Сессия готова. Опишите задачу — агент начнёт с чтения репозитория." }),
    );
  }

  for (const message of state.messages) els.col.append(messageNode(message));

  for (const permission of state.permissions) els.col.append(permissionCard(permission));
  for (const form of state.forms) els.col.append(formCard(form));

  if (state.streaming) {
    live = h("div", { class: "msg-agent" }, h("div", { class: "md" }));
    live.querySelector(".md").innerHTML = renderMarkdown(state.streamText);
    els.col.append(live);
  } else if (working) {
    live = h(
      "div",
      { class: "working" },
      h("span", { class: "dots" }, h("i"), h("i"), h("i")),
      h("span", { text: "Агент работает…" }),
    );
    els.col.append(live);
  } else {
    live = null;
  }

  if (state.error) els.col.append(h("div", { class: "chat-error", text: state.error }));

  els.feed.scrollTop = els.feed.scrollHeight;
}

function messageNode(message) {
  if (message.role === "user") {
    return h("div", { class: "msg-user", text: message.text || "" });
  }

  const md = h("div", { class: "md", html: renderMarkdown(message.text || "") });
  enhanceMarkdown(md);

  const node = h(
    "div",
    { class: "msg-agent" },
    h(
      "div",
      { class: "who" },
      mascotNode(14),
      h("span", { text: "Агент" }),
      message.at ? h("span", { text: `· ${relativeTime(message.at)}` }) : null,
    ),
    md,
  );

  if (message.steps?.length) node.append(stepsBlock(message.steps));
  return node;
}

function stepsBlock(steps) {
  const done = steps.filter((step) => step.status === "done").length;
  const failed = steps.some((step) => step.status === "failed");
  const list = h("div", { class: "steps-list hidden" });

  for (const step of steps) {
    list.append(
      h(
        "div",
        { class: `step ${step.status || ""}` },
        h("span", { class: "ico", html: icon(ICON_FOR_STEP(step), 12) }),
        h("span", { text: step.title || step.name || "Шаг" }),
        step.detail ? h("span", { class: "detail", text: step.detail }) : null,
      ),
    );
  }

  const toggle = h(
    "button",
    {
      class: `steps-toggle ${failed ? "failed" : ""}`,
      onclick: () => {
        list.classList.toggle("hidden");
        toggle.classList.toggle("open");
      },
    },
    h("span", {
      html: icon(failed ? "alert" : "check", 12),
      style: { display: "flex", color: failed ? "var(--red)" : "var(--green)" },
    }),
    h("span", { text: `Действия · ${done}/${steps.length}` }),
    h("span", { class: "chev", html: icon("chevronDown", 12) }),
  );

  return h("div", { class: "steps" }, toggle, list);
}

function permissionCard(permission) {
  const title = permission.title || permission.action || "Инструмент";
  const body = permission.detail || permission.summary || permission.command || "";

  const answer = async (decision) => {
    try {
      await rpc(window.clauden.code.permission(state.session.id, permission.id, decision));
      state.permissions = state.permissions.filter((item) => item.id !== permission.id);
      renderChat();
      toast(decision === "reject" ? "Отклонено" : "Разрешено");
    } catch (err) {
      toast(err.message || "Не удалось ответить", "error");
    }
  };

  return h(
    "div",
    { class: "ask" },
    h(
      "div",
      { class: "ask-title" },
      h("span", { html: icon("shield", 13), style: { display: "flex" } }),
      h("span", { text: "Нужно разрешение" }),
    ),
    h("div", { class: "ask-code", text: String(title) }),
    body ? h("div", { class: "ask-code", text: String(body) }) : null,
    h(
      "div",
      { class: "ask-actions" },
      h("button", { class: "btn btn-ghost btn-sm", text: "Отклонить", onclick: () => answer("reject") }),
      h("button", { class: "btn btn-secondary btn-sm", text: "Всегда", onclick: () => answer("always") }),
      h("button", { class: "btn btn-primary btn-sm", text: "Разрешить", onclick: () => answer("once") }),
    ),
  );
}

function formCard(form) {
  const controls = new Map();
  const fields = form.fields || form.questions || [];

  const rows = fields.map((field) => {
    const id = field.id || field.name || field.key;
    const label = field.label || field.title || field.question || id;
    let control;
    if (Array.isArray(field.options) && field.options.length) {
      control = h(
        "select",
        {},
        ...field.options.map((option) => {
          const value = typeof option === "string" ? option : option.value ?? option.label;
          const text = typeof option === "string" ? option : option.label ?? option.value;
          return h("option", { value, text });
        }),
      );
    } else {
      control = h("input", { placeholder: field.placeholder || "" });
    }
    controls.set(id, control);
    return h("label", { class: "ask-field" }, h("span", { text: label }), control);
  });

  const submit = h("button", {
    class: "btn btn-primary btn-sm",
    text: "Ответить",
    onclick: async () => {
      const answer = {};
      for (const [key, control] of controls) answer[key] = control.value;
      try {
        await rpc(window.clauden.code.form(state.session.id, form.id, answer));
        state.forms = state.forms.filter((item) => item.id !== form.id);
        renderChat();
        toast("Ответ отправлен");
      } catch (err) {
        toast(err.message || "Не удалось ответить", "error");
      }
    },
  });

  return h(
    "div",
    { class: "ask" },
    h(
      "div",
      { class: "ask-title" },
      h("span", { html: icon("chat", 13), style: { display: "flex" } }),
      h("span", { text: form.title || "Агент ждёт ответа" }),
    ),
    ...rows,
    h("div", { class: "ask-actions" }, submit),
  );
}

export function applyEvent(event, payload) {
  if (event === "connection") {
    state.connection = payload?.status || "offline";
    if (els) renderChat();
    ctxRef?.onConnection?.(state.connection);
    return;
  }

  if (event === "snapshot") {
    const full = payload || {};
    state.session = { ...state.session, ...full };
    state.messages = Array.isArray(full.messages) ? full.messages.map(normalizeMessage) : [];
    state.permissions = full.permissions || [];
    state.forms = full.forms || [];
    state.status = full.status || "idle";
    state.error = full.error || "";
    state.streaming = false;
    state.streamText = "";
    renderChat();
    ctxRef?.onSnapshot?.(full);
    return;
  }

  if (event === "delta") {
    const text = typeof payload?.text === "string" ? payload.text : "";
    if (!text) return;
    state.streaming = true;
    state.streamText += text;
    appendDelta(text);
    return;
  }

  if (event === "status") {
    const status = payload?.status || "idle";
    state.status = status;
    if (status !== "working") {
      state.streaming = false;
      state.streamText = "";
    }
    renderChat();
    ctxRef?.onStatus?.(status, payload?.error || "");
    return;
  }

  if (event === "step") {
    const last = state.messages[state.messages.length - 1];
    if (!last || last.role === "user") return;
    last.steps = last.steps || [];
    const index = last.steps.findIndex((step) => step.id === payload.id);
    if (index >= 0) last.steps[index] = { ...last.steps[index], ...payload };
    else last.steps.push(payload);
    renderChat();
    return;
  }

  if (event === "permissions") {
    state.permissions = Array.isArray(payload) ? payload : [];
    renderChat();
    return;
  }

  if (event === "forms") {
    state.forms = Array.isArray(payload) ? payload : [];
    renderChat();
    return;
  }
}

function normalizeMessage(message) {
  return {
    role: message.role || "assistant",
    text: message.text || "",
    steps: message.steps || [],
    at: message.at || message.createdAt || null,
  };
}

const paintDelta = throttle(() => {
  if (!live) {
    renderChat();
    return;
  }
  const md = live.querySelector(".md");
  if (!md) return;
  md.innerHTML = renderMarkdown(state.streamText);
  els.feed.scrollTop = els.feed.scrollHeight;
}, 90);

function appendDelta(text) {
  if (!live || !live.classList.contains("msg-agent")) {
    renderChat();
    return;
  }
  paintDelta(text);
}
