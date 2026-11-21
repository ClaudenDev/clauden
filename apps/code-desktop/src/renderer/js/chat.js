import { h, icon, rpc, toast, clear, escapeHtml, relativeTime, throttle, mascotNode } from "./ui.js";
import { state } from "./state.js";
import { renderMarkdown, enhanceMarkdown } from "./markdown.js";

let els = null;
let ctxRef = null;
let live = null;

const ACTIVE_STATUSES = new Set(["working", "waiting_device"]);

function isActive(status = state.status) {
  return ACTIVE_STATUSES.has(status);
}

function normalizeText(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function lastAssistant() {
  for (let index = state.messages.length - 1; index >= 0; index -= 1) {
    if (state.messages[index]?.role === "assistant") return state.messages[index];
  }
  return null;
}

function messagesContain(text) {
  const needle = normalizeText(text);
  if (!needle) return true;
  const last = lastAssistant();
  return Boolean(last && normalizeText(last.text).includes(needle));
}

function commitStream() {
  const text = state.streamText;
  state.streamText = "";
  state.streaming = false;
  if (!normalizeText(text)) return;
  if (messagesContain(text)) return;

  const last = lastAssistant();
  if (last && !normalizeText(last.text)) {
    last.text = text;
    last.pending = true;
    return;
  }
  if (last && normalizeText(text).startsWith(normalizeText(last.text))) {

    last.text = text;
    last.pending = true;
    return;
  }
  state.messages.push({ role: "assistant", text, steps: [], at: Date.now(), pending: true });
}

function jobLabel(job) {
  if (!job) return "";
  if (job.fs) return `${job.fs.op || "fs"} ${job.fs.path || ""}`.trim();
  return String(job.command || "").trim();
}

function limitItem(label) {
  const fill = h("span", { class: "limit-fill" });
  const value = h("span", { class: "limit-value", text: "—" });
  const node = h(
    "span",
    { class: "limit-item" },
    h("span", { class: "limit-name", text: label }),
    h("span", { class: "limit-track" }, fill),
    value,
  );
  return { node, fill, value };
}

function setLimit(item, metric, label) {
  const value = Math.max(0, Math.min(100, Math.round(Number(metric?.percent) || 0)));
  const reset = formatReset(metric?.resetsAt);
  item.node.classList.remove("hidden");
  item.fill.style.width = `${value}%`;
  item.value.textContent = `${value}%`;
  item.node.classList.toggle("warn", value >= 70 && value < 90);
  item.node.classList.toggle("high", value >= 90);
  item.node.title = reset ? `${label}: ${value}% · сброс ${reset}` : `${label}: ${value}%`;
}

function formatReset(ts) {
  const date = new Date(Number(ts));
  if (!Number.isFinite(date.getTime())) return "";
  return date.toLocaleString("ru-RU", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

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
  const effortBtn = h("button", {
    class: "pill-btn",
    title: "Уровень усилий модели",
    text: "Усилия",
    onclick: () => ctx.openEffort(),
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
  const settingsBtn = h("button", {
    class: "icon-btn",
    title: "Настройки: модель, усилия, коннекторы",
    html: icon("settings", 14),
    onclick: () => ctx.openSettings?.(),
  });

  const limitsPlan = h("span", { class: "limits-plan", text: "Лимиты" });
  const sessionLimit = limitItem("5ч");
  const weeklyLimit = limitItem("нед.");
  const limitsRow = h(
    "div",
    {
      class: "composer-limits hidden",
      title: "Лимиты подписки Clauden · нажмите, чтобы обновить",
      onclick: () => ctx.refreshLimits?.(),
    },
    limitsPlan,
    sessionLimit.node,
    weeklyLimit.node,
  );

  const head = h(
    "div",
    { class: "chat-head" },
    mascotNode(18),
    h("span", { class: "chat-title", text: "Агент" }),
    status,
    h("span", { class: "spacer" }),
    usageChip,
    modelBtn,
    effortBtn,
    commandsBtn,
    attachBtn,
    remoteBtn,
    settingsBtn,
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
    limitsRow,
  );

  host.append(head, feed, composer);
  els = {
    host,
    head,
    feed,
    col,
    status,
    textarea,
    sendBtn,
    stopBtn,
    remoteBtn,
    modelBtn,
    effortBtn,
    usageChip,
    commandsBtn,
    attachBtn,
    limitsRow,
    limitsPlan,
    sessionLimit,
    weeklyLimit,
  };
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
    sessionTokens ? `Всего за сессию: ${sessionTokens.toLocaleString("ru-RU")}` : "",
  ].filter(Boolean).join("\n") || "Расход токенов сессии";
  if (els.modelBtn) {
    const label = modelLabel(session?.modelAlias || "claude-sonnet-5-5");
    els.modelBtn.textContent = label;
    els.modelBtn.title = `Модель: ${label}`;
  }
  if (els.effortBtn) {
    const active = effortLabel(session?.variant);
    els.effortBtn.textContent = `Усилия · ${active}`;
    els.effortBtn.title =
      `Сейчас: ${active}. Больше усилий — больше reasoning и расход лимита. ` +
      "Нажмите, чтобы изменить.";
  }
}

export function effortLabel(value) {
  const key = String(value || "").trim().toLowerCase();
  const labels = {
    "": "по умолчанию",
    none: "без reasoning",
    minimal: "минимальные",
    low: "низкие",
    medium: "средние",
    high: "высокие",
    xhigh: "очень высокие",
    "x-high": "очень высокие",
    max: "максимальные",
    thinking: "thinking",
  };
  return labels[key] || key;
}

export function effortHint(value) {
  const key = String(value || "").trim().toLowerCase();
  const hints = {
    "": "как настроено у модели",
    none: "ответ без длинных рассуждений",
    minimal: "минимум reasoning, самый быстрый ответ",
    low: "быстрее и дешевле по лимиту",
    medium: "баланс скорости и глубины reasoning",
    high: "глубже reasoning, больше расход лимита",
    xhigh: "очень глубокий reasoning, высокий расход",
    "x-high": "очень глубокий reasoning, высокий расход",
    max: "максимальная глубина, самый большой расход",
    thinking: "расширенное thinking у провайдера",
  };
  return hints[key] || "вариант модели";
}

export function renderLimits() {
  if (!els?.limitsRow) return;
  const usage = state.limits;
  if (!usage) {
    els.limitsRow.classList.toggle("hidden", !state.limitsError);
    els.limitsPlan.textContent = state.limitsError ? "лимиты недоступны" : "Лимиты";
    els.limitsPlan.classList.add("muted");
    els.sessionLimit.node.classList.add("hidden");
    els.weeklyLimit.node.classList.add("hidden");
    els.limitsRow.title = state.limitsError || "Лимиты подписки Clauden";
    return;
  }

  const plan = usage.planLabel || usage.plan || "";
  els.limitsRow.classList.remove("hidden");
  els.limitsPlan.textContent = plan || "Лимиты";
  els.limitsPlan.classList.remove("muted");
  setLimit(els.sessionLimit, usage.session, "5-часовое окно");
  setLimit(els.weeklyLimit, usage.weekly, "Недельный лимит");
  els.limitsRow.title = [
    plan ? `Тариф ${plan}` : "",
    "Обновляется вместе с Clauden",
    "Нажмите, чтобы обновить",
  ]
    .filter(Boolean)
    .join(" · ");
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
  const images = state.pendingImages || [];
  if (!state.session) {
    if (text) toast("Сначала создайте или откройте сессию", "error");
    return;
  }
  if (!text && !images.length) return;

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
  state.pendingImages = [];

  commitStream();
  state.job = null;
  const note = images.length
    ? `${images.map((img) => `🖼 ${img.name}`).join("\n")}${text ? "\n" : ""}${text}`
    : text;
  state.messages.push({ role: "user", text: note, at: Date.now() });
  state.status = "working";
  renderChat();

  try {
    await rpc(
      window.clauden.code.send(
        state.session.id,
        text,
        images.map(({ name, mime, data }) => ({ name, mime, data })),
      ),
    );
  } catch (err) {
    state.pendingImages = images;
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
      const keepVariant = Array.isArray(model.variants) && model.variants.includes(state.session.variant)
        ? state.session.variant
        : "";
      await rpc(window.clauden.code.setModel(id, {
        providerID: model.providerID || "clauden",
        id: model.id,
        variant: keepVariant,
      }));
      state.session.modelAlias = model.id;
      state.session.model = `clauden/${model.id}`;
      state.session.variant = keepVariant;
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
  if (state.streamText) commitStream();
  renderChat();
  ctxRef?.onSnapshot?.(full);
}

function statusLabel(status) {
  if (status === "working") return "работает";
  if (status === "waiting_device") return "выполняет на ПК";
  if (status === "stopped") return "остановлен";
  if (status === "failed") return "ошибка";
  return state.connection === "live" ? "на связи" : "офлайн";
}

export function renderChat() {
  if (!els) return;

  renderUsage();
  renderLimits();
  const active = isActive();
  els.status.textContent = statusLabel(state.status);
  els.status.style.color =
    active ? "var(--clay)" : state.status === "failed" ? "var(--red)" : "var(--ink-3)";
  els.status.title = active && state.job ? jobLabel(state.job) : "";
  els.stopBtn.classList.toggle("hidden", !active);
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

  for (const message of state.messages) {
    if (
      message.role === "assistant" &&
      !String(message.text || "").trim() &&
      !message.steps?.length
    ) {
      continue;
    }
    els.col.append(messageNode(message));
  }

  for (const permission of state.permissions) els.col.append(permissionCard(permission));
  for (const form of state.forms) {
    if (!isPlaceholderForm(form)) els.col.append(formCard(form));
  }

  if (state.streaming && state.streamText) {
    live = h("div", { class: "msg-agent" }, h("div", { class: "md" }));
    live.querySelector(".md").innerHTML = renderMarkdown(state.streamText);
    els.col.append(live);
  } else if (active) {
    const detail = state.job ? jobLabel(state.job) : "";
    const label =
      state.status === "waiting_device"
        ? "Выполняю команду на вашем ПК…"
        : "Агент работает…";
    live = h(
      "div",
      { class: "working", title: detail || label },
      h(
        "div",
        { class: "working-line" },
        h("span", { class: "dots" }, h("i"), h("i"), h("i")),
        h("span", { text: label }),
      ),
      detail ? h("div", { class: "working-detail", text: detail }) : null,
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

function isPlaceholderForm(form) {
  const fields = Array.isArray(form?.fields)
    ? form.fields
    : Array.isArray(form?.questions)
      ? form.questions
      : [];
  if (!fields.length) return true;
  const placeholder = /^(?:no action|nothing|none|noop|n\/?a|x|y|z|test|question|questions|вопрос|ответ|option|choice|todo|tbd|\.{0,3}|[-—–])$/i;

  const ack = /^(?:ok|okay|okey|ок|окей|понял[а]?|понятно|хорошо|ладно|принято|продолжай(?:те)?|продолжить|дальше|вперёд|вперед|continue|proceed|go(?:\s+ahead)?|done|готово)[.!…\s]*$/i;
  const values = [];
  const push = (value) => {
    const text = String(value || "").trim();
    if (text) values.push(text);
  };
  push(form?.title);
  for (const field of fields) {
    push(field?.label);
    push(field?.title);
    push(field?.question);
    push(field?.description);
    push(field?.placeholder);
    for (const option of Array.isArray(field?.options) ? field.options : []) {
      push(typeof option === "string" ? option : option?.label ?? option?.value);
    }
  }
  if (!values.some((text) => text.length > 2 && !placeholder.test(text))) return true;
  let sawOption = false;
  for (const field of fields) {
    const options = Array.isArray(field?.options) ? field.options : [];
    if (!options.length) return false;
    for (const option of options) {
      const text = String(
        typeof option === "string" ? option : option?.label ?? option?.value ?? "",
      ).trim();
      if (!text) continue;
      sawOption = true;
      if (!ack.test(text)) return false;
    }
  }
  return sawOption;
}

function formCard(form) {
  const controls = new Map();
  const fields = form.fields || form.questions || [];

  const formTitle = /^questions?$/i.test(String(form.title || "").trim())
    ? "Вопрос агента"
    : form.title || "Агент ждёт ответа";

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
      h("span", { text: formTitle }),
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
    const liveText = state.streamText;
    const incoming = Array.isArray(full.messages) ? full.messages.map(normalizeMessage) : [];
    state.session = { ...state.session, ...full };
    state.messages = incoming;
    state.permissions = full.permissions || [];
    state.forms = full.forms || [];
    state.status = full.status || "idle";
    state.error = full.error || "";

    if (liveText) {
      const last = lastAssistant();
      const lastText = normalizeText(last?.text);
      if (!lastText && last && isActive(state.status)) {

        state.streamText = "";
        state.streaming = false;
      } else if (messagesContain(liveText)) {
        state.streamText = "";
        state.streaming = false;
      } else if (last && lastText && normalizeText(liveText).startsWith(lastText)) {

        last.text = liveText;
        state.streamText = "";
        state.streaming = false;
      } else if (!isActive(state.status)) {
        state.streamText = liveText;
        commitStream();
      }
    } else {
      state.streaming = false;
    }
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
    const previous = state.status;
    const status = payload?.status || "idle";
    state.status = status;
    state.error = payload?.error || "";
    if (status === "working") {

      if (previous !== "working") commitStream();
      state.job = null;
    } else {

      commitStream();
      if (status !== "waiting_device") state.job = null;
    }
    renderChat();
    ctxRef?.onStatus?.(status, payload?.error || "");
    return;
  }

  if (event === "job") {
    state.job = payload || null;
    if (state.job && ["queued", "running"].includes(state.job.status)) {
      state.status = "waiting_device";
    }
    renderChat();
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

export function normalizeMessage(message) {
  return {
    id: message.id || message.messageID || "",
    role: message.role || "assistant",
    text: message.text || "",
    steps: message.steps || [],
    status: message.status || "",
    at: message.at || message.ts || message.createdAt || null,
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
