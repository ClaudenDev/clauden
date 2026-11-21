import { h, icon, rpc, toast, modal, clear, relativeTime, mascotNode } from "./ui.js";
import { state, resetSession, activeTab } from "./state.js";
import { renderLogin } from "./login.js";
import { renderSessions, openNewSession, openLocalSession, createLocalSession } from "./sessions.js";
import { renderExplorer, loadTree, loadDir } from "./explorer.js";
import * as editor from "./editor.js";
import { renderDiff, refreshDiff } from "./diff.js";
import * as chat from "./chat.js";
import { mountTerminal, focusTerminal } from "./terminal.js";
import {
  loadConnectors,
  saveConnectors,
  connectorPayload,
  newConnectorId,
  defaultEffort,
  setDefaultEffort,
} from "./connectors.js";
import { loadGithub, openGithubDialog } from "./github.js";

const root = document.getElementById("root");

let els = {};
let booted = false;
let limitsTimer = null;
let limitsFocusAt = 0;
let sessionsRequestSeq = 0;
let sessionsFocusAt = 0;

const ctx = {
  openSession,
  openFile: (path) => editor.openFile(path, ctx),
  openDiff: async () => {
    editor.openDiffTab(ctx);
    if (!state.diff) await refreshDiff(ctx);
  },
  openRemote,
  openModels: () => openModels(ctx),
  openEffort: () => openEffort(ctx),
  openSettings: () => openSettings(),
  openCommands: (helpOnly = false) => openCommands(ctx, helpOnly),
  attachFiles: () => attachFiles(ctx),
  refreshUsage: () => refreshUsage(),
  refreshLimits: () => refreshLimits(),
  refreshSessions,
  refreshDiff: (options) => refreshDiff(ctx, options),
  renderTabs: () => editor.renderTabs(els.tabs, ctx),
  renderWork,
  showChat,
  openLocal: () => openLocalSession(ctx),
  openBlank: () => openBlankSession(ctx),
  renderDiff: (host) => renderDiff(host, ctx),
  renderSide,
  setCursor: (line, col) => {
    if (els.cursor) els.cursor.textContent = `Ln ${line}, Col ${col}`;
  },
  stopAgent: async () => {
    if (!state.session) return;
    try {
      await rpc(window.clauden.code.stop(state.session.id));
      toast("Останавливаем агента…");
    } catch (err) {
      toast(err.message || "Не удалось остановить", "error");
    }
  },
  toggleTerminal,
  onSnapshot: (full) => {
    if (full?.title) els.title.textContent = full.title;
    renderStatus();
    loadTree()
      .then(() => {
        renderSide();
        refreshDiff(ctx, { silent: true });
      })
      .catch(() => {});
  },
  onStatus: (status) => {
    renderStatus();
    if (status === "idle") {
      refreshUsage();
      refreshLimits();
      refreshDiff(ctx, { silent: true });
      loadTree().then(renderSide).catch(() => {});
    }
  },
  onConnection: (status) => {
    renderStatus();
    if (status === "live") refreshLimits();
  },
};

async function boot() {
  if (!window.clauden) {
    root.append(
      h("div", { class: "login" }, h("div", { class: "login-card", text: "Мост приложения не загрузился" })),
    );
    return;
  }

  const auth = await rpc(window.clauden.auth.state());
  state.auth = auth;

  if (!auth.authenticated) {
    renderLogin(root, {
      apiUrl: auth.apiUrl,
      email: auth.email,
      onDone: (next) => {
        state.auth = next;
        boot();
      },
    });
    return;
  }

  buildShell(auth);
  window.clauden?.local?.sandbox?.().then((info) => {
    state.sandboxInfo = info || null;
    renderStatus();
  }).catch(() => {});
}

function buildShell(auth) {
  if (typeof window.__ccCleanup === "function") {
    try {
      window.__ccCleanup();
    } catch {

    }
  }
  root.replaceChildren();
  booted = true;

  const title = h("span", { class: "tb-title", text: "Новая сессия" });
  const winControls =
    window.clauden.platform === "darwin"
      ? null
      : h(
          "div",
          { class: "win-controls" },
          h("button", {
            class: "win-btn",
            title: "Свернуть",
            html: icon("minimize", 14),
            onclick: () => window.clauden.win.minimize(),
          }),
          h("button", {
            class: "win-btn",
            title: "Развернуть",
            html: icon("maximize", 12),
            onclick: () => window.clauden.win.toggleMaximize(),
          }),
          h("button", {
            class: "win-btn close",
            title: "Закрыть",
            html: icon("close", 14),
            onclick: () => window.clauden.win.close(),
          }),
        );

  const titlebar = h(
    "div",
    { class: `titlebar ${window.clauden.platform === "darwin" ? "mac" : ""}` },
    h(
      "div",
      { class: "tb-brand" },
      mascotNode(17),
      h("span", { text: "Clauden Code" }),
    ),
    h("div", { class: "tb-sep" }),
    title,
    h("div", { class: "tb-spacer" }),
    h(
      "div",
      { class: "tb-actions" },
      h("button", {
        class: "icon-btn",
        title: "Clauden Runtime: компьютер, файлы, Computer Use",
        html: icon("laptop", 15),
        onclick: async () => {
          try {
            const root = await rpc(window.clauden.local.root());
            await rpc(window.clauden.runtime.open(root));
          } catch (error) {
            toast(error.message || "Не удалось открыть Clauden Runtime", "error");
          }
        },
      }),
      h("button", {
        class: "icon-btn",
        title: "Сохранить (Ctrl+S)",
        html: icon("save", 14),
        onclick: () => editor.saveActive(ctx),
      }),
      h("button", {
        class: "icon-btn",
        title: "Терминал (Ctrl+`)",
        html: icon("terminal", 14),
        onclick: () => toggleTerminal(),
      }),
    ),
    winControls,
  );

  const rail = h(
    "div",
    { class: "rail" },
    railButton("sessions", "Сессии", "sessions", () => setView("sessions")),
    railButton("files", "Файлы", "files", () => setView("files")),
    h("button", {
      class: "rail-btn",
      title: "Открыть папку на этом компьютере",
      html: icon("folderPlus", 16),
      onclick: () => openLocalSession(ctx),
    }),
    h("div", { class: "rail-spacer" }),
    h("button", {
      class: "rail-btn",
      title: "Терминал",
      html: icon("terminal", 16),
      onclick: () => toggleTerminal(),
    }),
    h("button", {
      class: "rail-btn",
      title: "Настройки",
      html: icon("settings", 16),
      onclick: openSettings,
    }),
    h("button", {
      class: "rail-avatar",
      title: auth.email || "Аккаунт",
      text: (auth.email || "?").slice(0, 1).toUpperCase(),
      onclick: openAccount,
    }),
  );

  const sideTitle = h("span", { class: "side-title", text: "Сессии" });
  const sideActions = h("div", { style: { display: "flex", gap: "2px" } });
  const sideBody = h("div", { class: "side-body" });
  const side = h(
    "div",
    { class: "side" },
    h("div", { class: "side-head" }, sideTitle, h("span", { class: "spacer" }), sideActions),
    sideBody,
  );

  const tabs = h("div", { class: "tabs" });
  const workMain = h("div", { class: "work-main" });
  const editorHost = h("div", { class: "editor-host hidden" });
  const chatHost = h("div", { class: "chat" });
  workMain.append(chatHost, editorHost);
  const panelResize = h("div", { class: "panel-resize", title: "Потяните, чтобы изменить высоту" });
  const panel = h("div", { class: "panel hidden" }, panelResize);
  const work = h("div", { class: "work" }, workMain, panel);
  const main = h("div", { class: "main" }, tabs, work);

  let dragging = false;
  panelResize.addEventListener("mousedown", (event) => {
    dragging = true;
    event.preventDefault();
    document.body.style.cursor = "ns-resize";
  });
  const onDragMove = (event) => {
    if (!dragging) return;
    const rect = work.getBoundingClientRect();
    const height = Math.min(Math.max(rect.bottom - event.clientY, 90), rect.height - 140);
    panel.style.height = `${height}px`;
  };
  const onDragEnd = () => {
    if (!dragging) return;
    dragging = false;
    document.body.style.cursor = "";
  };
  document.addEventListener("mousemove", onDragMove);
  document.addEventListener("mouseup", onDragEnd);

  const modeItem = h("span", { class: "sb-item sb-mode", title: "" });
  const branchItem = h("span", { class: "sb-item" });
  const statusItem = h("span", { class: "sb-item" });
  const connItem = h("span", { class: "sb-item" });
  const cursor = h("span", { class: "sb-item" });
  const version = h("span", { class: "sb-item", text: `Clauden Code ${auth.version || "0.1.0"}` });

  const statusbar = h(
    "div",
    { class: "statusbar" },
    modeItem,
    branchItem,
    statusItem,
    connItem,
    h("span", { class: "spacer" }),
    cursor,
    h("span", { class: "sb-item", text: "UTF-8" }),
    h("span", { class: "sb-item", text: "Пробелы: 2" }),
    version,
  );

  const shell = h("div", { class: "shell" }, rail, side, main);

  els = {
    titlebar,
    title,
    rail,
    side,
    sideTitle,
    sideActions,
    sideBody,
    tabs,
    workMain,
    editorHost,
    panel,
    work,
    chatHost,
    branchItem,
    modeItem,
    statusItem,
    connItem,
    cursor,
    statusbar,
    shell,
  };

  root.append(titlebar, shell, statusbar);

  editor.renderTabs(tabs, ctx);
  chat.mountChat(chatHost, ctx);
  refreshLimits();
  if (limitsTimer) clearInterval(limitsTimer);
  limitsTimer = setInterval(refreshLimits, 60_000);
  const onLimitsFocus = () => {
    if (Date.now() - limitsFocusAt < 15_000) return;
    limitsFocusAt = Date.now();
    refreshLimits();
  };

  const onSessionsFocus = () => {
    if (Date.now() - sessionsFocusAt < 30_000) return;
    sessionsFocusAt = Date.now();
    refreshSessions({ retries: 1, silent: true });
  };
  window.addEventListener("focus", onLimitsFocus);
  window.addEventListener("focus", onSessionsFocus);

  setView(state.view, { silent: true });
  renderWork();
  renderStatus();
  wireShortcuts();

  refreshSessions().then(() => {
    const last = localStorage.getItem("cc:lastSession");
    const candidate = state.sessions.find((session) => session.id === last) || state.sessions[0];
    if (candidate) openSession(candidate.id);
  });

  const offEvent = window.clauden.code.onEvent(({ id, event, payload }) => {
    if (!state.session || id !== state.session.id) return;
    chat.applyEvent(event, payload);
  });

  const offMaximize = window.clauden.win.onMaximize((maximized) => {
    const button = titlebar.querySelector(".win-controls .win-btn:nth-child(2)");
    if (button) button.innerHTML = icon(maximized ? "restore" : "maximize", 12);
  });

  const offUpdate = window.clauden.updates?.onEvent?.((status) => {
    if (status?.status === "available") {
      toast(`Доступно обновление ${status.version || "Clauden Code"} — скачиваю…`, "info", 5000);
    } else if (status?.status === "downloaded") {
      toast(`Обновление ${status.version || ""} скачано — Настройки → Обновления`.trim(), "info", 7000);
    }
  });

  window.__ccCleanup = () => {
    offEvent?.();
    offMaximize?.();
    offUpdate?.();
    if (limitsTimer) {
      clearInterval(limitsTimer);
      limitsTimer = null;
    }
    window.removeEventListener("focus", onLimitsFocus);
    window.removeEventListener("focus", onSessionsFocus);
    document.removeEventListener("mousemove", onDragMove);
    document.removeEventListener("mouseup", onDragEnd);
  };
}

function railButton(id, label, iconName, onClick) {
  const button = h("button", {
    class: `rail-btn ${state.view === id ? "active" : ""}`,
    title: label,
    dataset: { view: id },
    html: icon(iconName, 16),
    onclick: onClick,
  });
  return button;
}

function setView(view, { silent = false } = {}) {
  state.view = view;
  if (view === "files") state.sideOpen = true;
  for (const button of els.rail.querySelectorAll(".rail-btn[data-view]")) {
    button.classList.toggle("active", button.dataset.view === view);
  }
  applyLayout();
  renderSide({ silent });
}

function applyLayout() {
  els.shell.classList.toggle("no-side", !state.sideOpen);
  localStorage.setItem("cc:side", state.sideOpen ? "1" : "0");
}

function renderSide() {
  clear(els.sideActions);
  els.sideTitle.textContent = state.view === "sessions" ? "Сессии" : "Файлы";

  if (state.view === "sessions") {
    els.sideActions.append(
      h("button", {
        class: "icon-btn",
        title: "Новая сессия (Ctrl+N)",
        html: icon("plus", 14),
        onclick: () => openNewSession(ctx),
      }),
      h("button", {
        class: "icon-btn",
        title: "Обновить",
        html: icon("refresh", 13),
        onclick: () => refreshSessions(),
      }),
    );
    renderSessions(els.sideBody, ctx);
  } else {
    els.sideActions.append(
      h("button", {
        class: "icon-btn",
        title: "Обновить дерево",
        html: icon("refresh", 13),
        onclick: async () => {
          state.tree.clear();
          try {
            await loadTree();
            renderSide();
          } catch (err) {
            toast(err.message || "Не удалось обновить", "error");
          }
        },
      }),
    );
    renderExplorer(els.sideBody, ctx);
  }
}

function renderWork() {
  const tab = activeTab();
  els.chatHost.classList.toggle("hidden", Boolean(tab));
  els.editorHost.classList.toggle("hidden", !tab);
  clear(els.editorHost);
  if (tab) editor.renderEditor(els.editorHost, ctx);
  renderStatus();
}

function showChat() {
  if (!state.activeTabId) {
    chat.focusComposer();
    return;
  }
  state.activeTabId = null;
  ctx.renderTabs();
  renderWork();
  chat.focusComposer();
}

function baseName(dir) {
  const parts = String(dir || "").split(/[\\/]/).filter(Boolean);
  return parts[parts.length - 1] || "";
}

function renderStatus() {
  if (!els.branchItem) return;
  const session = state.session;
  const local = session?.source === "ssh";
  const root = session?.remote?.root || "";

  clear(els.modeItem);
  els.modeItem.title = "";
  if (session) {
    els.modeItem.classList.toggle("local", local);
    els.modeItem.append(
      h("span", { html: icon(local ? "laptop" : "server", 12), style: { display: "flex" } }),
      h("span", { text: local ? "этот ПК" : "сервер" }),
    );
    els.modeItem.title = local
      ? `Локальная сессия — файлы и команды на этом компьютере: ${root}`
      : `Сессия на сервере Clauden: ${session.repoName || session.repo || ""}`;
  }

  clear(els.branchItem);
  els.branchItem.title = "";
  if (local) {
    els.branchItem.title = root;
    els.branchItem.append(
      h("span", { html: icon("folder", 12), style: { display: "flex" } }),
      h("span", { text: baseName(root) || "папка" }),
    );
  } else {
    els.branchItem.append(
      h("span", { html: icon("branch", 12), style: { display: "flex" } }),
      h("span", { text: session?.branch || "—" }),
    );
  }

  clear(els.statusItem);
  const status = state.status || "idle";
  const statusParts = [h("span", { text: session ? `${session.id}` : "нет сессии" })];
  if (local && state.sandboxInfo) {
    const info = state.sandboxInfo;
    statusParts.push(
      h("span", {
        text: info.available ? ` · песочница: ${info.kind}` : " · без песочницы",
        title: info.detail || "",
        style: { color: info.available ? "var(--green, #7fb069)" : "var(--red)" },
      }),
    );
  }
  if (status === "working") {
    statusParts.push(h("span", { text: " · работает", style: { color: "var(--clay)" } }));
  } else if (session?.error) {
    statusParts.push(h("span", { text: " · ошибка", style: { color: "var(--red)" } }));
  }
  els.statusItem.append(...statusParts);

  clear(els.connItem);
  els.connItem.append(
    h("span", { class: `conn-dot ${state.connection}` }),
    h("span", { text: state.connection === "live" ? "SSE" : state.connection === "connecting" ? "связь…" : "офлайн" }),
  );

  els.title.textContent = session?.title || "Новая сессия";
}

async function refreshSessions({ retries = 2, silent = false } = {}) {
  const seq = (sessionsRequestSeq += 1);
  let lastError = null;
  for (let attempt = 0; attempt <= retries; attempt += 1) {
    try {
      const sessions = await rpc(window.clauden.code.sessions());
      if (seq !== sessionsRequestSeq) return state.sessions;
      state.sessions = Array.isArray(sessions) ? sessions : [];
      state.sessionsError = "";
      renderSide();
      return state.sessions;
    } catch (err) {
      lastError = err;
      if (attempt < retries) {
        await new Promise((resolve) => setTimeout(resolve, 1000 + attempt * 1500));
      }
    }
  }
  if (seq === sessionsRequestSeq) {

    state.sessionsError = lastError?.message || "Не удалось загрузить сессии";
    renderSide();
    if (!silent) toast(state.sessionsError, "error");
  }
  return [];
}

async function openSession(id) {
  if (!id) return;
  if (state.session?.id === id) return;

  if (state.session) await rpc(window.clauden.code.unsubscribe(state.session.id)).catch(() => {});
  resetSession();
  renderSide();
  editor.renderTabs(els.tabs, ctx);
  renderWork();
  chat.renderChat();

  try {
    const full = await rpc(window.clauden.code.session(id));
    state.session = full;
    state.messages = (full.messages || []).map(chat.normalizeMessage);
    state.permissions = full.permissions || [];
    state.forms = full.forms || [];
    state.status = full.status || "idle";
    localStorage.setItem("cc:lastSession", id);

    if (!state.models?.length) {
      state.models = await rpc(window.clauden.code.models(id)).catch(() => []) || [];
    }
    refreshUsage();

    if (full.source === "ssh" && full.remote?.root) {
      localStorage.setItem("cc:lastRoot", full.remote.root);
      await rpc(window.clauden.local.setRoot(full.remote.root)).catch(() => {});
      state.cwd = full.remote.root;
    }

    renderSide();
    renderStatus();
    chat.renderChat();

    state.connection = "connecting";
    renderStatus();
    await rpc(window.clauden.code.subscribe(id, full.source === "ssh" ? full.remote?.root || "" : ""));
    await loadTree();
    renderSide();
    await refreshDiff(ctx, { silent: true });
  } catch (err) {
    toast(err.message || "Не удалось открыть сессию", "error");
  }
}

async function openBlankSession(ctx) {
  try {
    const root = await rpc(window.clauden.local.blankRoot());
    if (!root) throw new Error("Не удалось создать папку");
    const data = await createLocalSession(root, "");
    toast("Пустая сессия готова");
    await ctx.refreshSessions();
    if (data?.id) ctx.openSession(data.id);
  } catch (err) {
    toast(err.message || "Не удалось создать сессию", "error");
  }
}

async function toggleTerminal(force) {
  const next = typeof force === "boolean" ? force : !state.terminalOpen;
  state.terminalOpen = next;
  els.panel.classList.toggle("hidden", !next);
  if (next && !els.panel.dataset.mounted) {
    els.panel.dataset.mounted = "1";
    await mountTerminal(els.panel, ctx);
  }
  if (next) setTimeout(() => focusTerminal(), 40);
  localStorage.setItem("cc:term", next ? "1" : "0");
}

async function refreshLimits() {
  if (!window.clauden?.account?.limits) {
    chat.renderLimits();
    return;
  }
  try {
    const data = await rpc(window.clauden.account.limits());
    state.limits = data?.usage || null;
    state.limitsError = "";
  } catch (err) {
    state.limitsError = err.message || "Не удалось загрузить лимиты";
  }
  chat.renderLimits();
}

async function refreshUsage() {
  if (!state.session) {
    chat.renderUsage();
    return;
  }
  try {
    const [usage, models] = await Promise.all([
      rpc(window.clauden.code.usage(state.session.id)).catch(() => null),
      state.models?.length
        ? Promise.resolve(state.models)
        : rpc(window.clauden.code.models(state.session.id)).catch(() => []),
    ]);
    state.usage = usage || null;
    if (Array.isArray(models) && models.length) state.models = models;
    chat.renderUsage();
  } catch {
    chat.renderUsage();
  }
  renderStatus();
}

function openModels(ctx) {
  if (!state.session) {
    toast("Сначала откройте сессию", "error");
    return;
  }
  const body = h("div", { class: "list" });
  const paint = () => {
    body.replaceChildren();
    for (const model of state.models || []) {
      const active = model.id === (state.session?.modelAlias || "");
      body.append(h("button", {
        class: `list-row ${active ? "active" : ""}`,
        onclick: async () => {
          try {
            const keepVariant = Array.isArray(model.variants) && model.variants.includes(state.session.variant)
              ? state.session.variant
              : "";
            await rpc(window.clauden.code.setModel(state.session.id, {
              providerID: model.providerID || "clauden",
              id: model.id,
              variant: keepVariant,
            }));
            state.session.modelAlias = model.id;
            state.session.model = `clauden/${model.id}`;
            state.session.variant = keepVariant;
            chat.renderUsage();
            dialog.close();
            toast(`Модель: ${model.name || chat.modelLabel(model.id)}`, "success");
          } catch (err) {
            toast(err.message || "Не удалось сменить модель", "error");
          }
        },
      },
        h("span", { class: "list-row-icon", html: icon("mark", 14) }),
        h("span", { class: "list-row-text" },
          h("span", { class: "list-row-title", text: model.name || chat.modelLabel(model.id) }),
        ),
        active ? h("span", { html: icon("check", 14), style: { color: "var(--clay)" } }) : null,
      ));
    }
    if (!(state.models || []).length) {
      body.append(h("div", { class: "modal-sub", text: "Каталог моделей недоступен — проверьте связь." }));
    }
  };
  const dialog = modal({
    title: "Модель сессии",
    subtitle: "Выберите модель для сессии",
    body,
    actions: [
      {
        label: "Обновить",
        kind: "ghost",
        onClick: async () => {
          state.models = (await rpc(window.clauden.code.models(state.session.id)).catch(() => [])) || [];
          paint();
        },
      },
      { label: "Закрыть", kind: "secondary", onClick: (close) => close() },
    ],
  });
  paint();
}

async function openEffort(ctx) {
  if (!state.session) {
    toast("Сначала откройте сессию", "error");
    return;
  }

  if (!(state.models || []).length) {
    state.models = (await rpc(window.clauden.code.models(state.session.id)).catch(() => [])) || [];
  }

  const bareModel = String(state.session.model || "").replace(/^clauden\//, "");
  const currentModel = (state.models || []).find(
    (item) => item.id === (state.session?.modelAlias || bareModel),
  );
  const variants = Array.isArray(currentModel?.variants) ? currentModel.variants : [];
  const options = ["", ...variants.filter((item) => item && item !== "")];
  const body = h("div", { class: "list" });

  const choose = async (variant) => {
    const model = currentModel;
    if (!model) {
      toast("Сначала выберите модель", "error");
      return;
    }
    try {
      await rpc(window.clauden.code.setModel(state.session.id, {
        providerID: model.providerID || "clauden",
        id: model.id,
        variant: variant || "",
      }));
      state.session.variant = variant || "";
      chat.renderUsage();
      dialog.close();
      toast(`Усилия: ${chat.effortLabel(variant)}`, "success");
    } catch (err) {
      toast(err.message || "Не удалось сменить усилия", "error");
    }
  };

  for (const variant of options) {
    const active = (state.session.variant || "") === variant;
    body.append(h("button", {
      class: `list-row ${active ? "active" : ""}`,
      onclick: () => choose(variant),
    },
      h("span", { class: "list-row-icon", html: icon("sparkles", 14) }),
      h("span", { class: "list-row-text" },
        h("span", { class: "list-row-title", text: chat.effortLabel(variant) }),
        h("span", { class: "list-row-sub", text: chat.effortHint(variant) }),
      ),
      active ? h("span", { html: icon("check", 14), style: { color: "var(--clay)" } }) : null,
    ));
  }
  if (!variants.length) {
    body.append(h("div", {
      class: "modal-sub",
      style: { margin: "10px 0 0" },
      text: "У этой модели нет отдельных уровней усилий — используется значение провайдера.",
    }));
  }

  const percent = Math.round(Number(state.limits?.session?.percent) || 0);
  const dialog = modal({
    title: "Уровень усилий",
    subtitle:
      `Сейчас: ${chat.effortLabel(state.session.variant)}. ` +
      "Больше усилий — глубже reasoning и больше расход лимита." +
      (percent ? ` 5-часовое окно израсходовано на ${percent}%.` : ""),
    body,
    width: 520,
    actions: [{ label: "Закрыть", kind: "secondary", onClick: (close) => close() }],
  });
  return dialog;
}

async function openCommands(ctx, helpOnly = false) {
  if (!state.session) {
    toast("Сначала откройте сессию", "error");
    return;
  }
  const builtins = [
    ["/compact", "Сжать контекст сессии"],
    ["/undo", "Отменить последний ход"],
    ["/redo", "Вернуть отменённое"],
    ["/model", "Сменить модель"],
    ["/clear", "Остановить агента"],
    ["/diff", "Показать изменения"],
    ["/remote-control", "Ссылка на сессию"],
  ];
  let server = [];
  if (!helpOnly) {
    try {
      server = await rpc(window.clauden.code.commands(state.session.id)) || [];
    } catch {  }
  }
  const dialog = modal({
    title: "Команды",
    subtitle: "Введите команду в поле агента или нажмите на неё",
    body: h("div", { class: "list" },
      ...builtins.map(([name, description]) => h("button", {
        class: "list-row",
        onclick: async () => {
          dialog.close();
          if (name === "/compact") {
            try { await rpc(window.clauden.code.compact(state.session.id)); toast("Контекст сжат"); }
            catch (err) { toast(err.message, "error"); }
          } else if (name === "/undo" || name === "/redo") {
            try { chat.applyFullSession(await rpc(window.clauden.code[name.slice(1)](state.session.id))); }
            catch (err) { toast(err.message, "error"); }
          } else if (name === "/model") {
            openModels(ctx);
          } else if (name === "/clear") {
            ctx.stopAgent();
          } else if (name === "/diff") {
            ctx.openDiff();
          } else if (name === "/remote-control") {
            ctx.openRemote();
          }
        },
      },
        h("span", { class: "badge", text: name }),
        h("span", { class: "list-row-text" }, h("span", { class: "list-row-title", text: description })),
      )),
      ...server.map((command) => h("button", {
        class: "list-row",
        onclick: async () => {
          dialog.close();
          try {
            chat.applyFullSession(await rpc(window.clauden.code.runCommand(state.session.id, command.name, "")));
            toast(`/${command.name} запущена`);
          } catch (err) {
            toast(err.message, "error");
          }
        },
      },
        h("span", { class: "badge", text: `/${command.name}` }),
        h("span", { class: "list-row-text" }, h("span", { class: "list-row-title", text: command.description || "Серверная команда" })),
      )),
    ),
    actions: [{ label: "Закрыть", kind: "secondary", onClick: (close) => close() }],
  });
}

async function attachFiles(ctx) {
  if (!state.session) {
    toast("Сначала откройте сессию", "error");
    return;
  }
  let files = [];
  try {
    files = await rpc(window.clauden.local.pickFiles());
  } catch (err) {
    toast(err.message || "Не удалось выбрать файлы", "error");
    return;
  }
  if (!files?.length) return;
  const local = state.session.source === "ssh";
  const paths = [];
  const images = [];
  for (const file of files) {
    const name = String(file).split(/[\\/]/).pop() || "file";
    if (/\.(?:png|jpe?g|webp|gif)$/i.test(name)) {
      if (local) {

        if (images.length + state.pendingImages.length >= 3) {
          toast("К сообщению можно приложить не больше 3 картинок", "error");
          continue;
        }
        try {
          const read = await rpc(window.clauden.local.readBase64(file));
          const mime = /\.jpe?g$/i.test(name)
            ? "image/jpeg"
            : /\.gif$/i.test(name)
              ? "image/gif"
              : /\.webp$/i.test(name)
                ? "image/webp"
                : "image/png";
          images.push({ name: read.name || name, mime, data: read.data });
        } catch (err) {
          toast(`«${name}»: ${err.message || "не прочитался"}`, "error");
        }
        continue;
      }
    }
    if (local) {
      paths.push(file);
      continue;
    }
    try {
      const uploaded = await rpc(window.clauden.code.uploadLocal(state.session.id, file));
      if (uploaded?.path) paths.push(uploaded.path);
    } catch (err) {
      toast(`«${name}»: ${err.message || "не загрузился"}`, "error");
    }
  }
  if (images.length) {
    state.pendingImages.push(...images);
    toast(`Прикреплено картинок: ${images.length} — отправятся вместе с сообщением`);
  }
  if (!paths.length) {
    if (images.length) {
      const textarea = document.querySelector(".composer textarea");
      textarea?.focus();
    }
    return;
  }
  const prefix = paths.length === 1 ? "[Файл: " : "[Файлы: ";
  const note = `${prefix}${paths.join(", ")}]\n\n`;
  const textarea = document.querySelector(".composer textarea");
  if (textarea) {
    textarea.value = `${note}${textarea.value}`;
    textarea.dispatchEvent(new Event("input"));
    textarea.focus();
  }
  toast(`Прикреплено файлов: ${paths.length}`);
}

async function openRemote() {
  if (!state.session) {
    toast("Сначала откройте сессию", "error");
    return;
  }
  const ttl = h(
    "select",
    {},
    h("option", { value: "1", text: "1 час" }),
    h("option", { value: "24", text: "24 часа", selected: true }),
    h("option", { value: "168", text: "7 дней" }),
  );
  const linkInput = h("input", { readonly: true, placeholder: "Ссылка появится здесь" });
  const status = h("div", { class: "modal-sub", text: "" });
  const errorBox = h("div", { class: "modal-error hidden" });

  const dialog = modal({
    title: "Remote Control",
    subtitle: "Ссылка открывает эту сессию в браузере и на телефоне — с живой работой агента.",
    body: h(
      "div",
      {},
      h("div", { class: "field" }, h("label", { text: "Срок действия" }), ttl),
      h(
        "div",
        { class: "link-row" },
        linkInput,
        h("button", {
          class: "btn btn-secondary btn-sm",
          text: "Копировать",
          onclick: async () => {
            if (!linkInput.value) return;
            await window.clauden.sys.copy(linkInput.value);
            toast("Ссылка скопирована");
          },
        }),
        h("button", {
          class: "btn btn-secondary btn-sm",
          text: "Открыть",
          onclick: () => linkInput.value && window.clauden.sys.openExternal(linkInput.value),
        }),
      ),
      status,
      errorBox,
    ),
    actions: [
      {
        label: "Отозвать",
        kind: "ghost",
        onClick: async () => {
          try {
            await rpc(window.clauden.code.revokeLink(state.session.id));
            linkInput.value = "";
            status.textContent = "Ссылка отозвана";
          } catch (err) {
            errorBox.textContent = err.message || "Не удалось отозвать";
            errorBox.classList.remove("hidden");
          }
        },
      },
      {
        label: "Выпустить ссылку",
        kind: "primary",
        onClick: async () => {
          errorBox.classList.add("hidden");
          try {
            const data = await rpc(
              window.clauden.code.createLink(state.session.id, Number(ttl.value)),
            );
            const url = data?.url || (data?.token ? `https://clauden.best/r/${data.token}` : "");
            linkInput.value = url;
            if (url) await window.clauden.sys.copy(url);
            status.textContent = data?.expiresAt
              ? `Действует до ${new Date(data.expiresAt).toLocaleString("ru-RU")}`
              : "Ссылка скопирована в буфер";
          } catch (err) {
            errorBox.textContent = err.message || "Не удалось выпустить ссылку";
            errorBox.classList.remove("hidden");
          }
        },
      },
    ],
  });

  try {
    const current = await rpc(window.clauden.code.link(state.session.id));
    if (current?.url) {
      linkInput.value = current.url;
      status.textContent = current.expiresAt
        ? `Действует до ${new Date(current.expiresAt).toLocaleString("ru-RU")}`
        : "Ссылка активна";
    }
  } catch {

  }
  return dialog;
}

function openAccount() {
  const auth = state.auth || {};
  modal({
    title: auth.email || "Аккаунт",
    subtitle: `${auth.apiUrl || "https://clauden.best"} · ${state.sessions.length} сессий`,
    body: h("div", {},
      h("div", { class: "modal-sub", text: "Выход закроет живые подписки и очистит токен на этом устройстве." }),
      h("div", { class: "modal-sub", text: "Поддержка: support@clauden.best" }),
    ),
    actions: [
      { label: "Закрыть", kind: "ghost", onClick: (close) => close() },
      {
        label: "Выйти",
        kind: "secondary",
        onClick: async (close) => {
          close();
          await rpc(window.clauden.auth.logout());
          window.__ccCleanup?.();
          state.auth = null;
          state.limits = null;
          state.limitsError = "";
          resetSession();
          booted = false;
          localStorage.removeItem("cc:lastSession");
          boot();
        },
      },
    ],
  });
}

function openSettings() {
  const auth = state.auth || {};
  const url = h("input", { value: auth.apiUrl || "https://clauden.best", spellcheck: "false" });
  const updates = updatesSection(auth);

  const effort = h("select", {},
    ...["medium", "low", "high", ""].map((value) =>
      h("option", {
        value,
        text: value ? chat.effortLabel(value) : "по умолчанию (провайдер)",
      }),
    ),
  );
  effort.value = defaultEffort();
  effort.addEventListener("change", () => {
    setDefaultEffort(effort.value);
    toast(`Усилия для новых сессий: ${chat.effortLabel(effort.value)}`);
  });

  const githubStatus = h("div", { class: "update-hint", text: "Проверяю подключение…" });
  const githubBtn = h("button", {
    class: "btn btn-secondary btn-sm",
    text: "Подключить GitHub",
    onclick: () =>
      openGithubDialog({
        onChanged: (info) => paintGithub(info),
      }),
  });
  const paintGithub = (info) => {
    const connected = Boolean(info?.connected);
    state.github = connected ? info : { connected: false };
    githubStatus.textContent = connected
      ? `Подключён как @${info.login || "аккаунт"}. ` +
        "Репозитории можно выбрать в «Новой сессии» → «Сервер Clauden»."
      : "Не подключён. Нужен personal access token, чтобы выбирать репозитории GitHub " +
        "и работать с ними в облачной сессии.";
    githubBtn.textContent = connected ? "Управлять GitHub" : "Подключить GitHub";
  };
  paintGithub(state.github);
  loadGithub({ refresh: true }).then(paintGithub).catch(() => {
    githubStatus.textContent = "Не удалось проверить GitHub — проверьте связь с сервером.";
  });

  const connectors = loadConnectors();
  const connectorList = h("div");
  const connectorStatus = h("div", { class: "update-hint", text: "" });
  const connTitle = h("input", { placeholder: "Название, например Notion" });
  const connUrl = h("input", { placeholder: "https://mcp.example.com/mcp", spellcheck: "false" });
  const connHeaders = h("input", { placeholder: "Authorization: Bearer … (необязательно)" });

  const applyConnectors = async () => {
    if (!state.session) {
      toast("Коннекторы сохранены и подключатся при создании сессии");
      return;
    }
    try {
      const data = await rpc(
        window.clauden.code.setConnectors(state.session.id, connectorPayload()),
      );
      if (state.session) {
        state.session.connectors = data?.connectors || state.session.connectors || [];
        state.session.connectorState = data?.state || state.session.connectorState || null;
      }
      const connected = data?.state?.connected?.length || 0;
      const failed = data?.state?.failed?.length || 0;
      const failedNames = (data?.state?.failed || [])
        .map((item) => item.title || item.name || "")
        .filter(Boolean)
        .slice(0, 4)
        .join(", ");
      if (data?.ssh) {
        connectorStatus.textContent =
          "Локальная сессия: remote MCP-коннекторы работают только в облачных сессиях.";
        toast("Локальная сессия: MCP-коннекторы работают только в облачных сессиях", "error", 5000);
      } else {
        connectorStatus.textContent = [
          `Подключено: ${connected}`,
          failed ? `ошибок: ${failed}${failedNames ? ` (${failedNames})` : ""}` : "",
        ]
          .filter(Boolean)
          .join(" · ");
        toast(
          failed
            ? `Коннекторы: подключено ${connected}, ошибок ${failed}`
            : `Коннекторы: подключено ${connected}`,
          failed ? "error" : "info",
        );
      }
    } catch (err) {
      toast(err.message || "Не удалось применить коннекторы", "error");
    }
  };

  const drawConnectors = () => {
    connectorList.replaceChildren();
    if (!connectors.length) {
      connectorList.append(h("div", {
        class: "modal-sub",
        text: "Пока ничего не подключено. Добавьте remote MCP-сервер ниже.",
      }));
      return;
    }
    for (const item of connectors) {
      const enabled = h("input", { type: "checkbox", checked: item.enabled !== false });
      enabled.addEventListener("change", () => {
        item.enabled = enabled.checked;
        saveConnectors(connectors);
        applyConnectors();
      });
      connectorList.append(h("div", {
        style: {
          display: "flex",
          alignItems: "center",
          gap: "8px",
          padding: "7px 0",
          borderBottom: "1px solid var(--line)",
        },
      },
        h("div", { style: { flex: "1", minWidth: "0" } },
          h("div", { class: "update-name", text: item.title || item.url }),
          h("div", { class: "update-hint", text: item.url }),
        ),
        enabled,
        h("button", {
          class: "icon-btn",
          title: "Удалить",
          html: icon("trash", 13),
          onclick: () => {
            const index = connectors.indexOf(item);
            if (index >= 0) connectors.splice(index, 1);
            saveConnectors(connectors);
            drawConnectors();
            applyConnectors();
          },
        }),
      ));
    }
  };

  const addConnector = () => {
    const address = connUrl.value.trim();
    if (!address) {
      toast("Укажите URL MCP-сервера", "error");
      return;
    }
    try {
      const parsed = new URL(address);
      if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("bad protocol");
    } catch {
      toast("URL должен начинаться с http:// или https://", "error");
      return;
    }
    connectors.push({
      id: newConnectorId(),
      title: connTitle.value.trim() || address,
      url: address,
      headers: connHeaders.value.trim(),
      enabled: true,
    });
    saveConnectors(connectors);
    connTitle.value = "";
    connUrl.value = "";
    connHeaders.value = "";
    drawConnectors();
    applyConnectors();
  };

  const addBtn = h("button", { class: "btn btn-secondary btn-sm", text: "Добавить", onclick: addConnector });
  drawConnectors();

  modal({
    title: "Настройки",
    body: h(
      "div",
      {},
      h("div", { class: "settings-section" },
        h("div", { class: "settings-section-title", text: "Агент" }),
        h("div", { class: "field" },
          h("label", { text: "Усилия по умолчанию для новых сессий" }),
          effort,
        ),
        h("div", {
          class: "update-hint",
          text: "Средние не жгут 5-часовое окно как высокие. Уровень можно сменить в шапке агента.",
        }),
      ),
      h("div", { class: "settings-section" },
        h("div", { class: "settings-section-title", text: "GitHub" }),
        githubStatus,
        h("div", { style: { display: "flex", gap: "8px", flexWrap: "wrap", marginTop: "10px" } },
          githubBtn,
          h("button", {
            class: "btn btn-ghost btn-sm",
            text: "Создать токен",
            onclick: () => window.clauden.sys.openExternal(
              "https://github.com/settings/tokens/new?scopes=repo&description=Clauden%20Code",
            ),
          }),
        ),
      ),
      h("div", { class: "settings-section" },
        h("div", { class: "settings-section-title", text: "MCP-коннекторы" }),
        h("div", {
          class: "update-hint",
          text: "Remote MCP-серверы для облачных сессий Clauden. Локальные MCP на этом компьютере (например Blender) приложение не запускает.",
        }),
        connectorList,
        connectorStatus,
        h("div", { class: "field" }, h("label", { text: "Название" }), connTitle),
        h("div", { class: "field" }, h("label", { text: "URL MCP-сервера" }), connUrl),
        h("div", { class: "field" }, h("label", { text: "Заголовки" }), connHeaders),
        h("div", { style: { display: "flex", justifyContent: "flex-end", marginTop: "8px" } }, addBtn),
      ),
      h("div", { class: "settings-section" },
        h("div", { class: "settings-section-title", text: "Сервер" }),
        h("div", { class: "field" }, h("label", { text: "Адрес сервера" }), url),
        h("div", { class: "update-hint", text: "По умолчанию https://clauden.best" }),
      ),
      h("div", { class: "settings-section" },
        h("div", { class: "settings-section-title", text: "Поддержка" }),
        h("div", { class: "update-hint", text: "Почта: support@clauden.best" }),
      ),
      updates.node,
      h("div", {
        class: "settings-version",
        text: `Clauden Code ${auth.version || window.clauden.version || ""}`,
      }),
    ),
    actions: [
      { label: "Закрыть", kind: "ghost", onClick: (close) => close() },
      {
        label: "Сохранить",
        kind: "primary",
        onClick: async (close) => {
          await rpc(window.clauden.auth.setApiUrl(url.value.trim()));
          close();
          toast("Настройки сохранены");
        },
      },
    ],
    onClose: () => updates.dispose(),
  });
  updates.start();
}

function updatesSection(auth) {
  const supported = typeof window.clauden?.updates?.status === "function";
  const statusName = h("div", { class: "update-name", text: "Проверяю обновления…" });
  const statusHint = h("div", { class: "update-hint", text: "" });
  const progressFill = h("div", { class: "update-progress-fill" });
  const progressTrack = h("div", { class: "update-progress hidden" }, progressFill);
  const checkBtn = h("button", {
    class: "btn btn-secondary btn-sm",
    text: "Проверить обновления",
  });
  const installBtn = h("button", {
    class: "btn btn-primary btn-sm hidden",
    text: "Установить и перезапустить",
  });
  const node = h(
    "div",
    { class: "settings-section" },
    h("div", { class: "settings-section-title", text: "Обновления" }),
    statusName,
    statusHint,
    progressTrack,
    h("div", { class: "update-actions" }, checkBtn, installBtn),
  );

  let off = null;
  let disposed = false;
  const version = auth.version || window.clauden?.version || "";
  const current = version ? `Версия ${version}.` : "";
  const mb = (value) => `${(Number(value || 0) / 1024 / 1024).toFixed(1)} МБ`;

  const draw = (state = {}) => {
    if (disposed) return;
    const status = state.status || "idle";
    progressTrack.classList.toggle("hidden", status !== "downloading" && status !== "available");
    progressFill.style.width = `${Math.max(0, Math.min(100, Number(state.percent) || 0))}%`;
    installBtn.classList.toggle("hidden", status !== "downloaded");
    checkBtn.disabled = status === "checking" || status === "downloading";
    checkBtn.classList.toggle("hidden", status === "unsupported");
    statusHint.style.color = "";

    if (!supported || status === "unsupported") {
      statusName.textContent = "Автообновление недоступно";
      statusHint.textContent = "Эта платформа не поддерживает автоматическую установку.";
    } else if (status === "checking") {
      statusName.textContent = "Проверяю обновления…";
      statusHint.textContent = "Соединяюсь с clauden.best";
    } else if (status === "available") {
      statusName.textContent = `Доступна версия ${state.version || ""}`;
      statusHint.textContent = "Скачиваю обновление в фоне…";
    } else if (status === "downloading") {
      statusName.textContent = `Скачиваю ${state.version || "обновление"}… ${Number(state.percent) || 0}%`;
      statusHint.textContent = `${mb(state.transferred)} из ${mb(state.total)}${state.bytesPerSecond ? ` · ${mb(state.bytesPerSecond)}/с` : ""}`;

    } else if (status === "downloaded") {
      statusName.textContent = `Версия ${state.version || "обновление"} готова`;
      statusHint.textContent = "Установка перезапустит приложение. Сессии и настройки сохранятся.";
    } else if (status === "latest") {
      statusName.textContent = "У вас последняя версия";
      statusHint.textContent = [current, "Обновления проверяются при запуске и раз в 6 часов."]
        .filter(Boolean)
        .join(" ");
    } else if (status === "error") {
      statusName.textContent = "Не удалось проверить обновления";
      statusHint.textContent = state.error || "Проверьте соединение и попробуйте снова";
      statusHint.style.color = "var(--red)";
    } else if (status === "dev") {
      statusName.textContent = "Проверка доступна в установленной версии";
      statusHint.textContent = "В режиме разработки автообновление отключено.";
    } else {
      statusName.textContent = "Готово к проверке";
      statusHint.textContent = [current, "Обновления проверяются при запуске и раз в 6 часов."]
        .filter(Boolean)
        .join(" ");
    }
  };

  checkBtn.addEventListener("click", async () => {
    if (!supported) return;
    draw({ status: "checking" });
    try {
      draw(await rpc(window.clauden.updates.check()));
    } catch (err) {
      draw({ status: "error", error: err.message || "Не удалось проверить обновления" });
    }
  });

  installBtn.addEventListener("click", async () => {
    if (!supported) return;
    try {
      const result = await rpc(window.clauden.updates.install());
      if (result && result.ok === false) {
        draw({ status: "error", error: result.error || "Обновление ещё не скачано" });
      }
    } catch (err) {
      draw({ status: "error", error: err.message || "Не удалось установить обновление" });
    }
  });

  return {
    node,
    async start() {
      if (!supported) {
        draw({ status: "unsupported" });
        return;
      }
      try {
        draw(await rpc(window.clauden.updates.status()));
      } catch (err) {
        draw({ status: "error", error: err.message || "Не удалось получить статус" });
      }
      off = window.clauden.updates.onEvent?.(draw) || null;
    },
    dispose() {
      disposed = true;
      off?.();
      off = null;
    },
  };
}

function wireShortcuts() {
  document.addEventListener("keydown", (event) => {
    const mod = event.ctrlKey || event.metaKey;
    if (!mod) return;

    if (event.key.toLowerCase() === "s") {
      event.preventDefault();
      editor.saveActive(ctx);
    } else if (event.key.toLowerCase() === "b") {
      event.preventDefault();
      state.sideOpen = !state.sideOpen;
      applyLayout();
      renderSide();
    } else if (event.key.toLowerCase() === "j") {
      event.preventDefault();
      showChat();
    } else if (event.key === "`") {
      event.preventDefault();
      toggleTerminal();
    } else if (event.key.toLowerCase() === "g") {
      event.preventDefault();
      ctx.openDiff();
    } else if (event.key.toLowerCase() === "n") {
      event.preventDefault();
      openNewSession(ctx);
    } else if (event.key.toLowerCase() === "k") {
      event.preventDefault();
      chat.focusComposer();
    } else if (event.key.toLowerCase() === "p") {
      event.preventDefault();
      setView(state.view === "files" ? "sessions" : "files");
    }
  });
}

state.sideOpen = localStorage.getItem("cc:side") !== "0";
state.terminalOpen = localStorage.getItem("cc:term") === "1";

boot().catch((err) => {
  root.replaceChildren(
    h(
      "div",
      { class: "login" },
      h(
        "div",
        { class: "login-card" },
        h("h1", { text: "Не удалось запуститься" }),
        h("p", { class: "login-sub", text: err.message || String(err) }),
      ),
    ),
  );
});
