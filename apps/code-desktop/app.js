import { h, icon, rpc, toast, modal, clear, relativeTime, mascotNode } from "./ui.js";
import { state, resetSession, activeTab } from "./state.js";
import { renderLogin } from "./login.js";
import { renderSessions, openNewSession, openLocalSession } from "./sessions.js";
import { renderExplorer, loadTree, loadDir } from "./explorer.js";
import * as editor from "./editor.js";
import { renderDiff, refreshDiff } from "./diff.js";
import * as chat from "./chat.js";
import { mountTerminal, focusTerminal } from "./terminal.js";

const root = document.getElementById("root");

let els = {};
let booted = false;

const ctx = {
  openSession,
  openFile: (path) => editor.openFile(path, ctx),
  openDiff: async () => {
    editor.openDiffTab(ctx);
    if (!state.diff) await refreshDiff(ctx);
  },
  openRemote,
  refreshSessions,
  refreshDiff: (options) => refreshDiff(ctx, options),
  renderTabs: () => editor.renderTabs(els.tabs, ctx),
  renderWork,
  showChat,
  openLocal: () => openLocalSession(ctx),
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
      refreshDiff(ctx, { silent: true });
      loadTree().then(renderSide).catch(() => {});
    }
  },
  onConnection: () => renderStatus(),
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

  window.__ccCleanup = () => {
    offEvent?.();
    offMaximize?.();
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

async function refreshSessions() {
  try {
    const sessions = await rpc(window.clauden.code.sessions());
    state.sessions = Array.isArray(sessions) ? sessions : [];
    renderSide();
    return state.sessions;
  } catch (err) {
    toast(err.message || "Не удалось загрузить сессии", "error");
    return [];
  }
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
    state.messages = (full.messages || []).map((message) => ({
      role: message.role || "assistant",
      text: message.text || "",
      steps: message.steps || [],
      at: message.at || message.createdAt || null,
    }));
    state.permissions = full.permissions || [];
    state.forms = full.forms || [];
    state.status = full.status || "idle";
    localStorage.setItem("cc:lastSession", id);

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
    body: h("div", { class: "modal-sub", text: "Выход закроет живые подписки и очистит токен на этом устройстве." }),
    actions: [
      { label: "Закрыть", kind: "ghost", onClick: (close) => close() },
      {
        label: "Выйти",
        kind: "secondary",
        onClick: async (close) => {
          close();
          await rpc(window.clauden.auth.logout());
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
  modal({
    title: "Настройки",
    body: h(
      "div",
      {},
      h("div", { class: "field" }, h("label", { text: "Адрес сервера" }), url),
      h("div", { class: "modal-sub", text: "По умолчанию https://clauden.best" }),
    ),
    actions: [
      { label: "Закрыть", kind: "ghost", onClick: (close) => close() },
      {
        label: "Сохранить",
        kind: "primary",
        onClick: async (close) => {
          await rpc(window.clauden.auth.setApiUrl(url.value.trim()));
          close();
          toast("Адрес сохранён — перезайдите, если сервер другой");
        },
      },
    ],
  });
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
state.chatOpen = localStorage.getItem("cc:chat") !== "0";
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
