import {
  $, $$, el, clear, icon, iconNode, initials, timeAgo, titleFromText,
  toast, confirmDialog, promptDialog, menuAt, openModal, isMobile, copyText,
  downloadText, debounce, relTimeShort, claudeMark,
} from "./util.js";
import {
  store, loadSettings, saveSettings, loadSession, saveSession,
  loadMemory, saveMemory, loadTasks, loadScheduled,
} from "./store.js";
import { api, auth as authApi, setToken, getToken, ApiError, request, isDesktopApp, claudeDesktop } from "./api.js";
import { renderChat } from "./chat.js";
import { renderProjects, renderProject } from "./projects.js";
import { renderArtifacts, collectArtifacts } from "./artifacts.js";
import { renderCode } from "./code.js";
import { renderRemote } from "./remote.js";
import { renderSettings } from "./settings.js";
import { renderMarket } from "./market.js";
import { renderLanding } from "./landing.js";
import { startRoutines } from "./routines.js";

const state = {
  user: null,
  chats: [],
  projects: [],
  models: [],
  plans: [],
  authConfig: null,
  claude: null,
  claudeModels: [],
  settings: loadSettings(),
  route: { name: "new", id: "" },
  booted: false,
  dataReady: false,
  offline: !navigator.onLine,
};

let deferredInstall = null;
let backupTimer = 0;
let renderToken = 0;
let accountSyncPromise = null;
let lastAccountSync = 0;
let backgroundSyncTimer = 0;

const CHAT_TOMBSTONE_TTL = 180 * 24 * 60 * 60 * 1000;
const CHAT_TOMBSTONE_MAX = 2000;

function chatTombstoneKey() {
  return `clauden.deletedChats.${state.user?.id || "anon"}`;
}

function loadChatTombstones() {
  const now = Date.now();
  let raw = null;
  try {
    raw = JSON.parse(localStorage.getItem(chatTombstoneKey()) || "{}");
  } catch {
    raw = null;
  }
  const out = {};
  for (const [id, value] of Object.entries(raw && typeof raw === "object" ? raw : {})) {
    const ts = Number(value);
    if (!id || !Number.isFinite(ts) || ts <= 0 || now - ts > CHAT_TOMBSTONE_TTL) continue;
    out[id] = ts;
  }
  return out;
}

function saveChatTombstones(map) {
  const entries = Object.entries(map)
    .sort((a, b) => b[1] - a[1])
    .slice(0, CHAT_TOMBSTONE_MAX);
  try {
    localStorage.setItem(chatTombstoneKey(), JSON.stringify(Object.fromEntries(entries)));
  } catch {

  }
}

function markChatDeleted(id, ts = Date.now()) {
  if (!id) return;
  const map = loadChatTombstones();
  map[id] = ts;
  saveChatTombstones(map);
}

const ctx = {
  state,
  navigate,
  render,
  renderView,
  refreshSidebar,
  loadChats,
  saveChat,
  deleteChat,
  clearAllChats,
  backupSoon,
  setUser,
  signOut,
  openChat,
  newChat,
  openProject,
  openSettings,
  renderSettings: refreshSettings,
  closeSettings,
  openSearch: openGlobalSearch,
  syncClaudeChats,
  upgrade,
  applyTheme,
  toast,
  confirm: confirmDialog,
  prompt: promptDialog,
  api,
  store,
  memory: () => loadMemory(state.user?.id),
  saveMemory: (items) => saveMemory(state.user?.id, items),
};

function systemDark() {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? true;
}

function clampNumber(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(max, Math.max(min, number));
}

function normalizeHex(value) {
  const hex = String(value || "").trim();
  return /^#[0-9a-f]{6}$/i.test(hex) ? hex.toLowerCase() : "#d97757";
}

function hexToRgb(hex) {
  const color = normalizeHex(hex).slice(1);
  return {
    r: parseInt(color.slice(0, 2), 16),
    g: parseInt(color.slice(2, 4), 16),
    b: parseInt(color.slice(4, 6), 16),
  };
}

function colorWithAlpha(hex, alpha) {
  const { r, g, b } = hexToRgb(hex);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function shadeColor(hex, amount) {
  const { r, g, b } = hexToRgb(hex);
  const shift = (channel) => {
    const value = amount < 0
      ? Math.round(channel * (1 + amount))
      : Math.round(channel + (255 - channel) * amount);
    return Math.min(255, Math.max(0, value));
  };
  const toHex = (channel) => channel.toString(16).padStart(2, "0");
  return `#${toHex(shift(r))}${toHex(shift(g))}${toHex(shift(b))}`;
}

function applyAppearance(dark) {
  const settings = state.settings || {};
  const root = document.documentElement;
  const style = ["classic", "glass", "noir"].includes(settings.style) ? settings.style : "classic";
  const accent = normalizeHex(settings.accent);
  const radius = clampNumber(settings.radius, 6, 28, 12);
  const glass = clampNumber(settings.glass, 0, 100, 65);
  const scale = clampNumber(settings.textSize, 85, 125, 100) / 100;
  const buttonStyle = ["soft", "pill", "square", "glass"].includes(settings.buttonStyle) ? settings.buttonStyle : "soft";
  const toggleStyle = ["classic", "ios", "compact"].includes(settings.toggleStyle) ? settings.toggleStyle : "classic";
  const motion = ["full", "soft", "off"].includes(settings.motion) ? settings.motion : "full";
  const density = ["compact", "comfortable", "spacious"].includes(settings.density) ? settings.density : "comfortable";

  root.dataset.style = style;
  root.dataset.button = buttonStyle;
  root.dataset.toggle = toggleStyle;
  root.dataset.motion = motion;
  root.dataset.density = density;
  const accentRgb = hexToRgb(accent);
  root.style.setProperty("--accent-rgb", `${accentRgb.r}, ${accentRgb.g}, ${accentRgb.b}`);
  root.style.setProperty("--clay", accent);
  root.style.setProperty("--clay-strong", shadeColor(accent, -0.16));
  root.style.setProperty("--clay-soft", colorWithAlpha(accent, 0.16));
  root.style.setProperty("--accent-glow", colorWithAlpha(accent, 0.38));
  root.style.setProperty("--radius-ui", `${radius}px`);
  root.style.setProperty("--radius", `${radius}px`);
  root.style.setProperty("--glass-blur", `${Math.round(6 + glass * 0.34)}px`);
  root.style.setProperty("--glass-alpha", (0.03 + (glass / 100) * 0.17).toFixed(3));
  root.style.setProperty("--font-scale", scale.toFixed(3));

  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    if (style === "glass") meta.content = dark ? "#0b0f19" : "#eef1f7";
    else if (style === "noir") meta.content = dark ? "#050506" : "#f7f7f8";
    else meta.content = dark ? "#191919" : "#f9f9f7";
  }
}

function applyTheme() {
  const mode = state.settings.theme || "system";
  const dark = mode === "dark" || (mode === "system" && systemDark());
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  document.body.dataset.font = state.settings.font === "system" ? "system" : "default";
  document.body.classList.toggle("is-desktop", isDesktopApp());
  applyAppearance(dark);
}

window.matchMedia?.("(prefers-color-scheme: dark)")?.addEventListener?.("change", () => {
  if (state.settings.theme === "system") applyTheme();
});

const APP_ROUTES = new Set(["chat", "chats", "projects", "project", "artifacts", "code", "market", "settings", "plans"]);
const PAGE_TITLES = {
  landing: "Clauden — Claude в браузере и приложении",
  login: "Вход в Clauden",
  auth: "Вход в Clauden",
  chat: "Чат — Clauden",
  chats: "Чаты — Clauden",
  projects: "Проекты — Clauden",
  project: "Проект — Clauden",
  artifacts: "Артефакты — Clauden",
  code: "Code — Clauden",
  r: "Remote Control — Clauden",
  market: "Маркетплейс — Clauden",
  settings: "Настройки — Clauden",
  plans: "Тарифы — Clauden",
};

function parseRoute() {
  const hash = location.hash.replace(/^#/, "");
  const legacy = hash.startsWith("/");
  const [path, queryString] = (legacy ? hash : location.pathname || "/").split("?");
  const params = new URLSearchParams(queryString || (legacy ? "" : location.search || ""));
  const parts = path.split("/").filter(Boolean);
  if (!parts.length) return { name: "landing", id: "", params };
  let [name, id] = parts;
  if (name === "new") name = "chat";
  return { name, id: id || "", params };
}

function migrateHashRoute() {
  const raw = location.hash.replace(/^#/, "");
  if (!raw.startsWith("/")) return;
  history.replaceState(null, "", raw);
}

function safeNext(value) {
  const next = String(value || "");
  if (!next.startsWith("/") || next.startsWith("//")) return "";
  if (next.startsWith("/login") || next.startsWith("/auth")) return "";
  return next;
}

function resolveRoute(route = state.route) {

  if (route.name === "r") return route;
  if (route.name === "new") {
    history.replaceState(null, "", `/chat${route.id ? `/${route.id}` : ""}`);
    route = { ...route, name: "chat" };
  }
  if (!state.user) {
    if (APP_ROUTES.has(route.name)) {
      const next = safeNext(`${location.pathname}${location.search}`) || "/chat";
      history.replaceState(null, "", `/login?next=${encodeURIComponent(next)}`);
      return { name: "login", id: "", params: new URLSearchParams(location.search) };
    }
    if (route.name === "login" || route.name === "auth" || route.name === "landing") return route;
    history.replaceState(null, "", "/");
    return { name: "landing", id: "", params: new URLSearchParams() };
  }
  if (route.name === "login") {
    const next = safeNext(route.params.get("next")) || "/chat";
    history.replaceState(null, "", next);
    return parseRoute();
  }
  if (route.name === "landing" || route.name === "auth") return route;
  if (!APP_ROUTES.has(route.name)) {
    history.replaceState(null, "", "/chat");
    return { name: "chat", id: "", params: new URLSearchParams() };
  }
  return route;
}

function navigate(path, { replace = false } = {}) {
  const target = path.startsWith("/") ? path : `/${path}`;
  if (target === `${location.pathname}${location.search}`) {
    state.route = resolveRoute(parseRoute());
    render();
    return;
  }
  const wasLanding = state.route.name === "landing";
  if (replace) history.replaceState(null, "", target);
  else history.pushState(null, "", target);
  state.route = resolveRoute(parseRoute());
  if (state.user && wasLanding && APP_ROUTES.has(state.route.name) && !state.dataReady) {
    render();
    ensureAccountData().then(() => {
      refreshSidebar();
      renderView();
    }).catch(() => {});
    window.scrollTo(0, 0);
    return;
  }
  render();
  window.scrollTo(0, 0);
}

function render() {
  state.route = resolveRoute(state.route);
  if (state.route.name !== "settings" && state.route.name !== "plans") closeSettings();
  const app = $("#app");
  app.hidden = false;
  $("#boot")?.remove();
  clear(app);
  if (state.route.name === "r") {
    app.classList.remove("landing-mode", "sidebar-collapsed");
    document.body.classList.remove("on-landing");
    app.append(el("div", { id: "view", class: "remote-view" }));
    renderView();
    return;
  }
  const landing = state.route.name === "landing";

  const welcome = landing && isDesktopApp();
  app.classList.toggle("landing-mode", landing && !welcome);
  document.body.classList.toggle("on-landing", landing && !welcome);
  if (landing || !state.user) {
    app.append(el("div", { id: "view", class: landing && !welcome ? "landing-view" : "screen" }));
    renderView();
    return;
  }
  app.append(renderSidebar(), el("div", { class: "side-backdrop", onclick: closeSidebar }));
  app.append(el("main", { class: "main" }, renderTopbar(), el("div", { id: "view", class: "screen" })));
  if (localStorage.getItem("clauden.sidebarCollapsed") === "1") app.classList.add("sidebar-collapsed");
  if (state.offline) {
    app.prepend(el("div", { class: "offline-banner", text: "Нет подключения — изменения сохранятся локально" }));
  }
  renderView();
  renderSidebarRefresh();
}

function renderTopbar() {
  const bar = el("div", { class: "topbar" },
    el("button", { class: "icon-btn mobile-only", onclick: toggleSidebar, html: icon("menu") }),
    el("button", { class: "icon-btn show-panel-btn", title: "Показать панель", onclick: toggleSidebarCollapsed, html: icon("panelLeft") }),
    el("span", { class: "topbar-title", id: "topbar-title" }),
    el("span", { class: "topbar-spacer" }),
    el("div", { class: "topbar-actions", id: "topbar-actions" }),
  );
  if (state.route.name !== "chat" && state.route.name !== "settings") {
    bar.querySelector("#topbar-actions")?.append(
      el("button", { class: "icon-btn", title: "Настройки", onclick: () => openSettings("account"), html: icon("settings") }),
    );
  }
  return bar;
}

function renderView() {
  state.route = resolveRoute(state.route);
  const token = ++renderToken;
  const view = $("#view");
  if (!view) return;
  const route = state.route;
  clear(view);
  const landing = route.name === "landing";
  view.className =
    route.name === "r"
      ? "remote-view"
      : landing && !isDesktopApp()
        ? "landing-view"
        : "screen";
  document.title = PAGE_TITLES[route.name] || "Clauden";
  closeSidebar();

  if (landing) {
    if (isDesktopApp()) {
      renderWelcome(view);
      return;
    }
    renderLanding(view, ctx);
    return;
  }

  if (route.name === "r") {
    if (!route.id) {
      navigate("/chat", { replace: true });
      return;
    }
    renderRemote(view, ctx, route.id);
    return;
  }

  if (!state.user) {
    renderAuth(view, token);
    return;
  }

  if (route.name === "chat") {
    renderChat(view, ctx, { chatId: route.id, token });
    return;
  }
  if (route.name === "chats") {
    renderChatsIndex(view);
    return;
  }
  if (route.name === "projects") {
    renderProjects(view, ctx);
    return;
  }
  if (route.name === "project") {
    renderProject(view, ctx, route.id);
    return;
  }
  if (route.name === "artifacts") {
    renderArtifacts(view, ctx);
    return;
  }
  if (route.name === "code") {
    renderCode(view, ctx, route.id);
    return;
  }
  if (route.name === "market") {
    renderMarket(view, ctx, route.id || "skills");
    return;
  }
  if (route.name === "settings" || route.name === "plans") {
    const tab = route.name === "plans" ? "plans" : (route.id || "account");
    history.replaceState(null, "", "/chat");
    state.route = resolveRoute(parseRoute());

    renderChat(view, ctx, { chatId: "", token });
    openSettings(tab);
    return;
  }
  closeSettings();
  navigate("/chat", { replace: true });
}

function chatRow(chat) {
  const active = state.route.name === "chat" && state.route.id === chat.id;
  const row = el("div", {
    class: `chat-row ${active ? "active" : ""}`,
    title: chat.title,
    onclick: () => openChat(chat.id),
  },
    el("span", { class: "chat-row-dot" }),
    el("span", { class: "chat-row-title", text: chat.title || "Новый чат" }),
    el("button", {
      class: "row-menu",
      "aria-label": "Меню чата",
      onclick: (e) => { e.stopPropagation(); openChatMenu(e.currentTarget, chat); },
      html: icon("more"),
    }),
  );
  return row;
}

function openChatMenu(anchor, chat) {
  menuAt(anchor, [
    { icon: "pin", label: chat.pinned ? "Открепить" : "Закрепить", onClick: () => togglePin(chat) },
    { icon: "edit", label: "Переименовать", onClick: () => renameChat(chat) },
    { icon: "folder", label: "В проект…", onClick: () => moveToProject(chat) },
    { icon: "share", label: "Экспорт .md", onClick: () => exportChat(chat) },
    "-",
    { icon: "trash", label: "Удалить", danger: true, onClick: () => removeChat(chat) },
  ]);
}

function sideSection(title, { collapsed = false, onToggle, action } = {}) {
  const body = el("div", { class: "side-section-body" });
  const head = el("div", { class: "side-label" },
    el("button", {
      class: "side-caret",
      title: collapsed ? "Развернуть" : "Свернуть",
      onclick: () => {
        const next = body.hidden;
        body.hidden = !next;
        const caret = head.querySelector(".side-caret svg");
        if (caret) caret.replaceWith(iconNode(next ? "chevronDown" : "chevronRight"));
      },
      html: icon(collapsed ? "chevronRight" : "chevronDown"),
    }),
    el("span", { text: title }),
    el("span", { class: "label-spacer" }),
    action || null,
  );
  body.hidden = collapsed;
  return { head, body };
}

function sidebarContent() {
  const query = (state.sideQuery || "").trim().toLowerCase();
  const chats = query
    ? state.chats.filter((c) => (c.title || "").toLowerCase().includes(query) || (c.messages || []).some((m) => (m.content || "").toLowerCase().includes(query)))
    : state.chats;
  const pinned = chats.filter((c) => c.pinned);
  const recent = chats.filter((c) => !c.pinned);

  const fragment = document.createDocumentFragment();
  if (pinned.length) {
    const section = sideSection("Закреплённые");
    pinned.forEach((chat) => section.body.append(chatRow(chat)));
    fragment.append(section.head, section.body);
  }
  const chatsSection = sideSection("Чаты", {
    action: el("div", { style: { display: "flex", gap: "2px" } },
      isDesktopApp()
        ? el("button", {
            class: "icon-btn sm",
            title: "Синхронизировать чаты Claude",
            onclick: (event) => {
              const button = event.currentTarget;
              button.disabled = true;
              syncClaudeChats({ manual: true }).finally(() => { button.disabled = false; });
            },
            html: icon("refresh"),
          })
        : null,
      el("button", {
        class: "icon-btn sm",
        title: "Все чаты",
        onclick: () => navigate("/chats"),
        html: icon("filter"),
      }),
    ),
  });
  if (!recent.length && !pinned.length) {
    chatsSection.body.append(el("div", { class: "side-empty", text: query ? "Ничего не найдено" : "Пока нет чатов" }));
    if (!query && isDesktopApp()) {
      chatsSection.body.append(el("button", {
        class: "side-empty-action",
        text: "Загрузить чаты из Claude",
        onclick: (event) => {
          const button = event.currentTarget;
          button.disabled = true;
          syncClaudeChats({ manual: true }).finally(() => { button.disabled = false; });
        },
      }));
    }
  }
  recent.slice(0, 60).forEach((chat) => chatsSection.body.append(chatRow(chat)));
  fragment.append(chatsSection.head, chatsSection.body);
  return fragment;
}

function renderChrome() {
  const codeMode = state.route.name === "code";
  return el("div", { class: "chrome" },
    el("button", { class: "chrome-btn", title: "Меню", onclick: openAppMenu, html: icon("menu") }),
    el("button", { class: "chrome-btn", title: "Свернуть панель", onclick: toggleSidebarCollapsed, html: icon("panelLeft") }),
    el("button", { class: "chrome-btn", title: "Назад", onclick: () => history.back(), html: icon("chevronLeft") }),
    el("button", { class: "chrome-btn", title: "Вперёд", onclick: () => history.forward(), html: icon("chevronRight") }),
    el("span", { class: "chrome-spacer" }),
    el("div", { class: "chrome-toggle", title: "Режим" },
      el("button", {
        class: codeMode ? "" : "active",
        title: "Чат",
        onclick: () => { if (codeMode) navigate("/chat"); },
        html: claudeMark(15),
      }),
      el("button", {
        class: codeMode ? "active" : "",
        title: "Code",
        onclick: () => { if (!codeMode) navigate("/code"); },
        html: icon("code"),
      }),
    ),
  );
}

function renderSidebar() {
  const user = state.user;
  const claude = state.claude;
  const claudeActive = state.settings?.provider === "claude" && claude?.linked;
  const plan = claudeActive
    ? `Claude ${String(claude.plan || "free").toUpperCase()}`
    : `Clauden ${user?.plan || "Free"}`;
  const pro = !plan.toLowerCase().includes("free");
  const sidebar = el("aside", { class: "sidebar" });

  const nav = el("nav", { class: "side-nav" },
    navItem("new", "Новый", "plus", () => newChat()),
    navItem("search", "Поиск", "search", () => openGlobalSearch()),
    navItem("projects", "Проекты", "folder", () => navigate("/projects"), state.projects.length),
    navItem("artifacts", "Артефакты", "layers", () => navigate("/artifacts")),
    navItem("skills", "Навыки", "sparkles", () => navigate("/market/skills")),
  );

  const scroll = el("div", { class: "side-scroll" });
  scroll.append(sidebarContent());

  const openInBrowserButton = isDesktopApp()
    ? el("button", { class: "icon-btn", title: "Открыть в браузере", onclick: openInBrowser, html: icon("popout") })
    : null;
  const foot = el("div", { class: "side-foot" },
    el("button", { class: "user-chip", onclick: () => openSettings("account") },
      el("span", { class: "avatar", text: initials(user?.name, user?.email) }),
      el("span", { class: "user-chip-text" },
        el("div", { class: "user-chip-name", text: user?.name || user?.email || "Аккаунт" }),
        el("div", { class: "user-chip-plan" }, el("span", { class: `plan-badge ${pro ? "pro" : ""}`, text: plan })),
      ),
      el("span", { html: icon("chevronRight"), style: { width: "15px", height: "15px", color: "var(--text-3)" } }),
    ),
    openInBrowserButton,
  );

  sidebar.append(renderChrome(), nav, scroll, foot);
  return sidebar;
}

function openInBrowser() {
  const url = `${location.origin}/chat`;
  const opened = window.open(url, "_blank");
  if (!opened) toast("Браузер заблокировал новое окно", "error");
}

function openAppMenu(anchor) {
  menuAt(anchor, [
    { icon: "plus", label: "Новый чат", onClick: () => newChat() },
    { icon: "search", label: "Поиск (Ctrl+K)", onClick: () => openGlobalSearch() },
    { icon: "folder", label: "Проекты", onClick: () => navigate("/projects") },
    { icon: "layers", label: "Артефакты", onClick: () => navigate("/artifacts") },
    { icon: "sparkles", label: "Навыки и плагины", onClick: () => navigate("/market/skills") },
    { icon: "code", label: "Code", onClick: () => navigate("/code") },
    "-",
    { icon: "refresh", label: "Синхронизировать чаты Claude", onClick: () => syncClaudeChats({ manual: true }) },
    { icon: "settings", label: "Настройки", onClick: () => openSettings("account") },
    { icon: "logout", label: "Выйти", onClick: () => signOut() },
  ]);
}

function toggleSidebarCollapsed() {
  const app = $("#app");
  if (!app) return;
  const collapsed = app.classList.toggle("sidebar-collapsed");
  try { localStorage.setItem("clauden.sidebarCollapsed", collapsed ? "1" : ""); } catch {  }
}

function navItem(name, label, iconName, onClick, count) {
  const routes = { projects: "/projects", artifacts: "/artifacts", skills: "/market/skills" };
  const active = routes[name] ? state.route.name === routes[name].slice(1) : false;
  return el("button", {
    class: `nav-item ${active ? "active" : ""}`,
    onclick: onClick,
  },
    el("span", { html: icon(iconName) }),
    el("span", { text: label }),
    count ? el("span", { class: "nav-count", text: count }) : null,
  );
}

function renderSidebarRefresh() {
  const old = $(".sidebar");
  if (!old) return;
  const fresh = renderSidebar();
  old.replaceWith(fresh);
}

function refreshSidebar() {
  renderSidebarRefresh();
}

function closeSidebar() {
  $("#app")?.classList.remove("sidebar-open");
}

function toggleSidebar() {
  $("#app")?.classList.toggle("sidebar-open");
}

async function loadChats() {
  state.chats = await store.allChats();
  state.projects = await store.allProjects();
  refreshSidebar();
}

function chatForBackup(chat) {
  return {
    ...chat,
    messages: (chat.messages || []).map((message) => ({
      ...message,
      attachments: (message.attachments || []).map(({ data, ...rest }) => rest),
    })),
  };
}

function hasStreamingChat() {
  return state.chats.some((chat) =>
    (chat.messages || []).some((message) => message.streaming),
  );
}

async function ensureAccountData({ force = false } = {}) {
  if (!state.user) return;
  if (accountSyncPromise) return accountSyncPromise;

  accountSyncPromise = (async () => {
    await loadChats();
    if (force || Date.now() - lastAccountSync > 30_000 || !state.dataReady) {
      await syncChatsFromServer();
      await syncProjectsFromServer();
      await loadChats();
      lastAccountSync = Date.now();
    }
    state.dataReady = true;
    scheduleBackgroundSync();
  })();

  try {
    await accountSyncPromise;
  } finally {
    accountSyncPromise = null;
  }
}

function scheduleBackgroundSync() {
  clearInterval(backgroundSyncTimer);
  backgroundSyncTimer = setInterval(() => {
    if (!state.user || document.hidden || hasStreamingChat()) return;
    ensureAccountData({ force: true })
      .then(() => {
        if (["projects", "project", "chats"].includes(state.route.name)) renderView();
      })
      .catch(() => {});
  }, 60_000);
}

function backupSoon(delay = 1200) {
  clearTimeout(backupTimer);
  backupTimer = setTimeout(() => {
    if (!state.user) return;

    const deleted = loadChatTombstones();
    const conversations = state.chats
      .filter((chat) => chat.provider !== "claude" && !deleted[chat.id])
      .slice(0, 200)
      .map(chatForBackup);
    api.backupChats(conversations).catch(() => {});
  }, Math.max(0, Number(delay) || 0));
}

async function saveChat(chat) {
  chat.updatedAt = Date.now();
  const index = state.chats.findIndex((c) => c.id === chat.id);
  if (index >= 0) state.chats[index] = chat;
  else state.chats.unshift(chat);
  state.chats.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) || (b.updatedAt || 0) - (a.updatedAt || 0));
  await store.putChat(chat);
  refreshSidebar();
  backupSoon();
  return chat;
}

async function deleteChat(id) {

  markChatDeleted(id);
  state.chats = state.chats.filter((c) => c.id !== id);
  await store.deleteChat(id);
  api.deleteChat(id).catch(() => {});
  refreshSidebar();
  backupSoon();
}

async function clearAllChats() {
  const now = Date.now();
  const tombstones = loadChatTombstones();
  for (const chat of state.chats) {
    if (chat.id) tombstones[chat.id] = now;
  }
  saveChatTombstones(tombstones);
  state.chats = [];
  await store.clearChats();
  api.clearChats().catch(() => {});
  refreshSidebar();
}

function openChat(id) {
  if (!id) return;
  navigate(`/chat/${id}`);
}

function newChat(projectId = "") {
  const query = projectId ? `?project=${encodeURIComponent(projectId)}` : "";
  navigate(`/chat${query}`);
}

function openProject(id) {
  navigate(`/project/${id}`);
}

let settingsModal = null;

function openSettings(tab = "account") {
  closeSettings();
  settingsModal = renderSettings(document.body, ctx, tab);
  return settingsModal;
}

function refreshSettings() {
  settingsModal?.refresh?.();
}

function closeSettings() {
  settingsModal?.close?.();
  settingsModal = null;
  document.querySelector(".settings-overlay")?.remove();
}

function upgrade(planId = "") {
  openSettings("plans");
}

function setUser(user) {
  if (state.user?.id !== user?.id) state.dataReady = false;
  state.user = user;
  if (user) {
    state.settings = saveSettings({
      nickname: state.settings.nickname || user.nickname || user.name || "",
      fullName: state.settings.fullName || user.name || "",

      work: user.work !== undefined ? user.work : (state.settings.work || ""),
      preferenceNote: state.settings.preferenceNote || user.instructions || "",
      lastPlan: user.plan || "Free",
    });
  }
  applyTheme();
}

async function signOut() {
  try { await authApi.logout(); } catch {  }
  saveSession(null);
  setToken("");
  state.user = null;
  state.chats = [];
  state.projects = [];
  state.dataReady = false;
  lastAccountSync = 0;
  navigate("/login", { replace: true });
}

async function togglePin(chat) {
  chat.pinned = !chat.pinned;
  await saveChat(chat);
}

async function renameChat(chat) {
  const title = await promptDialog({ title: "Название чата", value: chat.title, label: "Как назвать чат?" });
  if (!title) return;
  chat.title = title.slice(0, 120);
  await saveChat(chat);
  if (state.route.id === chat.id) renderView();
}

async function moveToProject(chat) {
  const projects = state.projects;
  const content = el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Добавить в проект" })),
    el("div", { class: "modal-body" },
      el("div", { class: "list" },
        el("button", {
          class: `list-row ${!chat.projectId ? "active" : ""}`,
          onclick: async () => { chat.projectId = ""; await saveChat(chat); modal.close(); toast("Чат вне проекта"); },
        }, el("span", { class: "list-row-icon", html: icon("inbox") }), el("span", { class: "list-row-text" }, el("span", { class: "list-row-title", text: "Без проекта" }))),
        ...projects.map((project) => el("button", {
          class: `list-row ${chat.projectId === project.id ? "active" : ""}`,
          onclick: async () => { chat.projectId = project.id; await saveChat(chat); modal.close(); toast(`Чат в проекте «${project.title}»`); },
        }, el("span", { class: "list-row-icon", html: icon("folder") }), el("span", { class: "list-row-text" }, el("span", { class: "list-row-title", text: project.title })))),
      ),
      projects.length ? null : el("p", { class: "muted small", style: { marginTop: "12px" }, text: "Проектов пока нет. Создайте его на вкладке «Проекты»." }),
    ),
  );
  const modal = openModal(content);
}

function exportChat(chat) {
  const lines = [`# ${chat.title}`, ""];
  for (const message of chat.messages || []) {
    if (message.role === "user") lines.push(`## Вы`, "", message.content, "");
    else if (message.content) lines.push(`## Clauden`, "", message.content, "");
  }
  downloadText(lines.join("\n"), `${(chat.title || "chat").replace(/[\\/:*?"<>|]+/g, "-").slice(0, 80)}.md`, "text/markdown;charset=utf-8");
}

async function removeChat(chat) {
  const ok = await confirmDialog({ title: "Удалить чат?", text: `«${chat.title}» будет удалён с этого устройства и из резервной копии.`, confirm: "Удалить" });
  if (!ok) return;
  await deleteChat(chat.id);
  if (state.route.id === chat.id) navigate("/chat", { replace: true });
  toast("Чат удалён");
}

function renderChatsIndex(view) {
  const body = el("div", { class: "screen-inner" });
  const page = el("div", { class: "page" },
    el("div", { class: "page-head" },
      el("div", null,
        el("h1", { class: "page-title", text: "Чаты" }),
        el("p", { class: "page-sub", text: `${state.chats.length} диалогов на этом устройстве` }),
      ),
      el("div", { class: "row" },
        el("button", { class: "icon-btn", title: "Поиск", onclick: () => openGlobalSearch(), html: icon("search") }),
        el("button", { class: "btn btn-secondary", onclick: () => newChat() }, el("span", { html: icon("plus") }), "Новый чат"),
      ),
    ),
    el("div", { class: "segmented", id: "chats-filter", style: { marginBottom: "18px" } },
      el("button", { class: "active", text: "Все", onclick: () => filterChats("all") }),
      el("button", { text: "Закреплённые", onclick: () => filterChats("pinned") }),
    ),
    el("div", { id: "chats-list" }),
  );
  body.append(page);
  view.append(body);
  renderChatsList("all");
}

function renderChatsList(filter = "all") {
  const list = $("#chats-list");
  if (!list) return;
  clear(list);
  const chats = state.chats.filter((c) => filter === "all" || c.pinned);
  if (!chats.length) {
    list.append(el("div", { class: "empty-block" },
      el("div", { html: icon("chat") }),
      el("h3", { text: "Пока пусто" }),
      el("p", { text: "Начните диалог — он появится здесь и синхронизируется с приложением." }),
      el("button", { class: "btn btn-primary", onclick: () => newChat() }, "Начать чат"),
    ));
    return;
  }
  const wrapper = el("div", { class: "list" });
  chats.forEach((chat) => {
    wrapper.append(el("button", {
      class: "list-row",
      onclick: () => openChat(chat.id),
    },
      el("span", { class: "list-row-icon", html: icon(chat.pinned ? "pin" : "chat") }),
      el("span", { class: "list-row-text" },
        el("span", { class: "list-row-title", text: chat.title || "Новый чат" }),
        el("span", { class: "list-row-sub", text: (chat.messages || []).slice(-1)[0]?.content?.replace(/\s+/g, " ").slice(0, 120) || "Пустой чат" }),
      ),
      chat.projectId ? el("span", { class: "badge", text: "проект" }) : null,
      el("span", { class: "list-row-meta", text: relTimeShort(chat.updatedAt) }),
      el("span", { class: "row-menu", html: icon("more"), onclick: (e) => { e.stopPropagation(); openChatMenu(e.currentTarget, chat); } }),
    ));
  });
  list.append(wrapper);
}

function filterChats(filter) {
  $$("#chats-filter button").forEach((b) => b.classList.toggle("active", b.textContent === (filter === "all" ? "Все" : "Закреплённые")));
  renderChatsList(filter);
}

async function renderAuth(view, token) {
  view.className = "screen";
  const container = el("div", { class: "auth" });
  view.append(container);
  renderAuthStep(container, "login", token);
}

function renderAuthStep(container, mode, token, data = {}) {
  clear(container);
  const card = el("div", { class: "auth-card" });
  const error = el("div", { class: "error-box", hidden: true });
  const showError = (message) => {
    error.textContent = message;
    error.hidden = false;
  };

  if (mode === "login") {
    const email = el("input", { class: "input", type: "email", placeholder: "Введите электронную почту", autocomplete: "email", value: data.email || "" });
    const next = el("button", { class: "btn btn-invert btn-block", text: "Продолжить через почту" });
    const go = () => {
      const mail = email.value.trim();
      if (!mail) { showError("Введите email"); email.focus(); return; }
      error.hidden = true;
      renderAuthStep(container, "password", token, { email: mail });
    };
    next.addEventListener("click", go);
    email.addEventListener("keydown", (e) => { if (e.key === "Enter") go(); });

    container.append(
      el("h1", { class: "auth-title", text: "Войти" }),
      el("div", { class: "auth-panel" },
        state.authConfig?.googleEnabled
          ? el("button", { class: "btn btn-block btn-google-dark", onclick: startGoogle }, googleIcon(), "Продолжить через Google")
          : null,
        state.authConfig?.googleEnabled ? el("div", { class: "auth-sep", text: "или" }) : null,
        email,
        next,
        error,
        el("p", { class: "auth-note" },
          "Продолжая, вы принимаете ",
          el("a", { href: "/terms", target: "_blank", rel: "noopener" }, "условия"),
          " и ",
          el("a", { href: "/privacy", target: "_blank", rel: "noopener" }, "политику конфиденциальности"),
          " Clauden.",
        ),
      ),
      el("button", { class: "auth-alt", onclick: () => renderAuthStep(container, "account", token, { email: data.email }) },
        "Войти другим способом ", el("span", { html: icon("chevronRight") })),
      el("p", { class: "auth-foot" },
        el("a", { href: "/", onclick: (e) => { e.preventDefault(); navigate("/"); } }, "Что такое Clauden?"),
        " · ",
        el("a", { href: "https://t.me/ClaudenApp", target: "_blank", rel: "noopener" }, "Telegram"),
      ),
    );
    setTimeout(() => email.focus(), 40);
    return;
  }

  if (mode === "password" || mode === "account") {
    const email = el("input", { class: "input", type: "email", placeholder: "Введите электронную почту", autocomplete: "email", value: data.email || "" });
    const password = el("input", { class: "input", type: "password", placeholder: "Пароль", autocomplete: "current-password" });
    const submit = el("button", { class: "btn btn-invert btn-block", text: "Войти" });

    const run = async () => {
      const mail = email.value.trim();
      if (!mail) { showError("Введите email"); email.focus(); return; }
      if (!password.value) { showError("Введите пароль"); password.focus(); return; }
      error.hidden = true;
      submit.disabled = true;
      submit.innerHTML = '<span class="spinner"></span>';
      try {
        const session = await authApi.login(mail, password.value);
        await afterLogin(session, token);
      } catch (err) {
        showError(err.message || "Не получилось");
        submit.disabled = false;
        submit.textContent = "Войти";
      }
    };
    submit.addEventListener("click", run);
    [email, password].forEach((field) => field.addEventListener("keydown", (e) => { if (e.key === "Enter") run(); }));

    container.append(
      el("h1", { class: "auth-title", text: "Войти" }),
      el("div", { class: "auth-panel" },
        email,
        password,
        submit,
        error,
      ),
      el("button", { class: "auth-alt", onclick: () => renderAuthStep(container, "login", token, { email: email.value }) },
        "Войти другим способом ", el("span", { html: icon("chevronRight") })),
      el("div", { class: "auth-foot" },
        el("button", { class: "auth-link", text: "Создать аккаунт", onclick: () => renderAuthStep(container, "register-email", token, { email: email.value }) }),
        " · ",
        el("a", { href: "/", onclick: (e) => { e.preventDefault(); navigate("/"); } }, "Что такое Clauden?"),
      ),
    );
    setTimeout(() => (mode === "password" ? password : email).focus(), 40);
    return;
  }

  if (mode === "register-email") {
    const email = el("input", { class: "input", type: "email", placeholder: "Введите электронную почту", autocomplete: "email", value: data.email || "" });
    const password = el("input", { class: "input", type: "password", placeholder: "Придумайте пароль (от 8 символов)", autocomplete: "new-password" });
    const submit = el("button", { class: "btn btn-invert btn-block", text: "Получить код" });
    const run = async () => {
      const mail = email.value.trim();
      if (!mail) { showError("Введите email"); email.focus(); return; }
      if (password.value.length < 8) { showError("Пароль должен быть не короче 8 символов"); password.focus(); return; }
      error.hidden = true;
      submit.disabled = true;
      submit.innerHTML = '<span class="spinner"></span>';
      try {
        await authApi.requestCode(mail);
        renderAuthStep(container, "register-code", token, { email: mail, password: password.value });
      } catch (err) {
        showError(err.message || "Не получилось");
        submit.disabled = false;
        submit.textContent = "Получить код";
      }
    };
    submit.addEventListener("click", run);
    [email, password].forEach((field) => field.addEventListener("keydown", (e) => { if (e.key === "Enter") run(); }));

    container.append(
      el("h1", { class: "auth-title", text: "Создать аккаунт" }),
      el("div", { class: "auth-panel" },
        email,
        password,
        submit,
        error,
        el("p", { class: "auth-note" },
          "Продолжая, вы принимаете ",
          el("a", { href: "/terms", target: "_blank", rel: "noopener" }, "условия"),
          " и ",
          el("a", { href: "/privacy", target: "_blank", rel: "noopener" }, "политику конфиденциальности"),
          " Clauden.",
        ),
      ),
      el("button", { class: "auth-alt", onclick: () => renderAuthStep(container, "login", token, { email: email.value }) },
        "Войти другим способом ", el("span", { html: icon("chevronRight") })),
    );
    setTimeout(() => email.focus(), 40);
    return;
  }

  if (mode === "register-code") {
    const code = el("input", { class: "input code-input", inputmode: "numeric", maxlength: "6", placeholder: "000000", autocomplete: "one-time-code" });
    const submit = el("button", { class: "btn btn-invert btn-block", text: "Подтвердить электронную почту" });
    const resend = el("button", { class: "auth-alt", type: "button", text: "Отправить код ещё раз" });
    let resendLeft = 0;
    let resendTimer = null;
    const tickResend = () => {
      if (!resend.isConnected) {
        clearInterval(resendTimer);
        resendTimer = null;
        return;
      }
      if (resendLeft > 0) {
        resend.disabled = true;
        resend.textContent = `Отправить снова через ${resendLeft} с`;
        resendLeft -= 1;
        return;
      }
      resend.disabled = false;
      resend.textContent = "Отправить код ещё раз";
      clearInterval(resendTimer);
      resendTimer = null;
    };
    const startResendCooldown = () => {
      resendLeft = 60;
      tickResend();
      if (!resendTimer) resendTimer = setInterval(tickResend, 1000);
    };
    resend.addEventListener("click", async () => {
      resend.disabled = true;
      resend.textContent = "Отправляю…";
      error.hidden = true;
      try {
        await authApi.requestCode(data.email);
        resend.textContent = "Код отправлен";
        setTimeout(startResendCooldown, 900);
      } catch (err) {
        showError(err.message || "Не удалось отправить код");
        resend.disabled = false;
        resend.textContent = "Отправить код ещё раз";
      }
    });
    const run = async () => {
      if (!code.value.trim()) { showError("Введите код из письма"); code.focus(); return; }
      error.hidden = true;
      submit.disabled = true;
      submit.innerHTML = '<span class="spinner"></span>';
      try {
        const session = await authApi.register(data.email, code.value.trim(), data.password);
        await afterLogin(session, token);
      } catch (err) {
        showError(err.message || "Не получилось");
        submit.disabled = false;
        submit.textContent = "Подтвердить электронную почту";
      }
    };
    submit.addEventListener("click", run);
    code.addEventListener("keydown", (e) => { if (e.key === "Enter") run(); });

    container.append(
      el("h1", { class: "auth-title", text: "Проверьте почту" }),
      el("div", { class: "auth-panel" },
        el("p", { class: "auth-lead", text: `Мы отправили код на ${data.email}. Введите его ниже.` }),
        el("p", { class: "auth-lead", text: "Письмо не пришло? Проверьте папку «Спам» или отправьте код ещё раз." }),
        code,
        submit,
        resend,
        error,
      ),
      el("button", { class: "auth-alt", onclick: () => renderAuthStep(container, "register-email", token, { email: data.email }) },
        "Изменить адрес электронной почты ", el("span", { html: icon("chevronRight") })),
    );
    startResendCooldown();
    setTimeout(() => code.focus(), 40);
  }

  container.append(card);
}

function googleIcon() {
  return el("span", {
    html: '<svg viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.3 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.1 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.3-.4-3.5z"/><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 18.9 12 24 12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.1 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.3 0-9.7-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C36.9 39.2 44 34 44 24c0-1.3-.1-2.3-.4-3.5z"/></svg>',
  });
}

function startGoogle() {

  if (isDesktopApp() && window.claudenDesktop?.app?.openExternal) {
    window.claudenDesktop.app.openExternal(
      "https://clauden.best/webapi/auth/google/start?app=clauden",
    );
    toast("Завершите вход в браузере — Clauden откроется автоматически", "info", 6000);
    return;
  }
  const popup = window.open(
    "https://clauden.best/webapi/auth/google/start?app=clauden&web=1",
    "clauden-google",
    "width=520,height=680,menubar=no,toolbar=no",
  );
  if (!popup) {
    toast("Браузер заблокировал окно. Разрешите всплывающие окна для clauden.best.", "error", 4000);
    return;
  }
  toast("Завершите вход в открывшемся окне Google");
}

async function finishBridge(params) {
  document.title = "Вход — Clauden";
  const app = $("#app");
  $("#boot")?.remove();
  app.hidden = false;
  clear(app);
  const card = el("div", { class: "auth-card" },
    el("img", { class: "auth-logo", src: "/assets/logo.svg?v=9", alt: "" }),
    el("h1", { class: "auth-title", text: "Входим…" }),
    el("p", { class: "auth-sub", text: "Google подтвердил аккаунт, создаю сессию Clauden" }),
    el("div", { class: "center" }, el("span", { class: "spinner" })),
  );
  app.append(el("div", { class: "auth" }, card));
  try {
    const session = await request("/v1/auth/bridge", {
      method: "POST",
      body: { payload: params.get("payload") || "", sig: params.get("sig") || "" },
    });
    saveSession(session);
    setToken(session.token);
    setUser(session.user);
    await loadClaudeDesktop();
    await ensureAccountData({ force: true });
    syncClaudeChats().catch(() => {});
    if (!session.user.onboarded) {
      renderOnboarding();
      return;
    }
    if (window.opener && !window.opener.closed) {
      try { window.opener.location.href = "/chat"; } catch {  }
      window.close();
      return;
    }
    navigate("/chat", { replace: true });
  } catch (err) {
    clear(card);
    card.append(
      el("img", { class: "auth-logo", src: "/assets/logo.svg?v=9", alt: "" }),
      el("h1", { class: "auth-title", text: "Не удалось войти" }),
      el("p", { class: "auth-sub", text: err.message || "Google-вход истёк или не принят сервером" }),
      el("button", { class: "btn btn-primary btn-block", text: "На главную", onclick: () => { navigate("/", { replace: true }); } }),
    );
  }
}

async function loadAuthConfig() {
  try {
    state.authConfig = await api.authConfig();
  } catch {  }
}

async function afterLogin(session, token) {
  saveSession(session);
  setToken(session.token);
  setUser(session.user);
  await loadClaudeDesktop();
  await ensureAccountData({ force: true });
  await refreshProfile();
  syncClaudeChats().catch(() => {});
  if (!state.user.onboarded) {
    renderOnboarding();
    return;
  }
  const next = safeNext(new URLSearchParams(location.search).get("next"));
  navigate(next || "/chat", { replace: true });
}

async function syncChatsFromServer() {
  try {
    const data = await api.chats();
    const remote = Array.isArray(data?.data) ? data.data : [];
    const remoteDeleted = Array.isArray(data?.deleted) ? data.deleted : [];

    const tombstones = loadChatTombstones();
    for (const item of remoteDeleted) {
      if (!item?.id) continue;
      const ts = Number(item.deletedAt) || Date.now();
      if (!tombstones[item.id] || ts > tombstones[item.id]) tombstones[item.id] = ts;
    }
    saveChatTombstones(tombstones);
    const deletedIds = new Set(Object.keys(tombstones));

    const local = await store.allChats();
    const survivors = [];
    for (const chat of local) {

      if (deletedIds.has(chat.id) && chat.provider !== "claude") {
        await store.deleteChat(chat.id);
      } else {
        survivors.push(chat);
      }
    }

    const remoteIds = new Set(remote.map((chat) => chat?.id).filter(Boolean));
    const unconfirmed = [...deletedIds].filter((id) => remoteIds.has(id)).slice(0, 50);
    if (unconfirmed.length) {
      await Promise.allSettled(unconfirmed.map((id) => api.deleteChat(id)));
    }

    const map = new Map(survivors.map((chat) => [chat.id, chat]));
    for (const chat of remote) {
      if (!chat?.id || deletedIds.has(chat.id)) continue;
      const existing = map.get(chat.id);
      if (!existing || (chat.updatedAt || 0) > (existing.updatedAt || 0)) {
        map.set(chat.id, { ...chat, messages: chat.messages || [] });
      }
    }
    for (const chat of map.values()) await store.putChat(chat);

    const pending = survivors
      .filter((chat) => chat.provider !== "claude" && !deletedIds.has(chat.id))
      .filter((chat) => (chat.messages || []).length)
      .filter((chat) => {
        const seen = remote.find((item) => item.id === chat.id);
        return !seen || (chat.updatedAt || 0) > (seen.updatedAt || 0);
      })
      .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
      .slice(0, 200)
      .map(chatForBackup);
    if (pending.length) await api.backupChats(pending).catch(() => {});
  } catch {  }
}

async function syncProjectsFromServer() {
  try {
    const data = await api.projects();
    const remote = Array.isArray(data?.data) ? data.data : [];
    const local = await store.allProjects();
    const map = new Map(local.map((project) => [project.id, project]));
    for (const project of remote) {
      if (!project?.id) continue;
      const existing = map.get(project.id);
      if (!existing || (project.updatedAt || 0) > (existing.updatedAt || 0)) {
        map.set(project.id, project);
      }
    }
    for (const project of map.values()) await store.putProject(project);

    const pending = local.filter((project) => {
      const seen = remote.find((item) => item.id === project.id);
      return !seen || (project.updatedAt || 0) > (seen.updatedAt || 0);
    });
    if (pending.length) await api.backupProjects(pending).catch(() => {});
  } catch {  }
}

async function refreshProfile() {
  try {
    const { user } = await authApi.me();
    setUser(user);
    saveSession({ token: getToken(), user });
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      saveSession(null);
      setToken("");
      state.user = null;
    }
  }
}

function renderWelcome(view) {
  view.className = "screen";
  const wrap = el("div", { class: "welcome" },
    el("div", { class: "welcome-mark", html: claudeMark(52) }),
    el("h1", { class: "welcome-title", text: "Clauden для Windows" }),
    el("p", { class: "welcome-sub", text: "Самый быстрый способ общаться с Claude" }),
    el("button", { class: "btn btn-invert", style: { minWidth: "320px" }, text: "Начать", onclick: () => navigate("/login") }),
  );
  view.append(wrap);
}

function renderOnboarding() {
  const app = $("#app");
  $("#boot")?.remove();
  clear(app);
  app.hidden = false;
  const user = state.user || {};
  let step = 0;
  const data = {
    name: user.name || "",
    birthday: user.birthday || "",
  };

  const container = el("div", { class: "auth" });
  app.append(container);

  const draw = () => {
    clear(container);
    const card = el("div", { class: "onboarding" });
    card.append(
      el("img", { class: "auth-logo", src: "/assets/logo.svg?v=9", alt: "" }),
      el("div", { class: "steps-dots" }, [0, 1, 2].map((i) => el("i", { class: i <= step ? "on" : "" }))),
    );
    const error = el("div", { class: "error-box", hidden: true });

    if (step === 0) {
      const input = el("input", { class: "input", value: data.name, placeholder: "Как к вам обращаться?" });
      card.append(
        el("h1", { class: "auth-title", text: "Как вас зовут?" }),
        el("p", { class: "auth-sub", text: "Clauden будет обращаться к вам по имени" }),
        el("div", { class: "field" }, el("label", { text: "Имя" }), input),
        error,
        el("button", { class: "btn btn-primary btn-lg btn-block", text: "Продолжить", onclick: () => {
          data.name = input.value.trim();
          if (!data.name) { error.textContent = "Введите имя"; error.hidden = false; return; }
          step = 1; draw();
        }}),
      );
      setTimeout(() => input.focus(), 40);
    }
    if (step === 1) {
      const input = el("input", { class: "input", type: "date", value: data.birthday, max: new Date().toISOString().slice(0, 10) });
      card.append(
        el("h1", { class: "auth-title", text: "Дата рождения" }),
        el("p", { class: "auth-sub", text: "Нужна, чтобы показывать подходящие ответы" }),
        el("div", { class: "field" }, el("label", { text: "Дата" }), input),
        error,
        el("button", { class: "btn btn-primary btn-lg btn-block", text: "Продолжить", onclick: () => {
          if (!input.value) { error.textContent = "Выберите дату"; error.hidden = false; return; }
          data.birthday = input.value; step = 2; draw();
        }}),
        el("button", { class: "btn btn-ghost btn-block", style: { marginTop: "6px" }, text: "Пропустить", onclick: () => { step = 2; draw(); } }),
      );
    }
    if (step === 2) {
      card.append(
        el("h1", { class: "auth-title", text: "Добро пожаловать" }),
        el("p", { class: "auth-sub", text: "Осталось принять условия" }),
        el("div", { class: "notice-box", style: { maxHeight: "180px", overflow: "auto" },
          html: '<b>Условия использования Clauden</b><br><br>Clauden — ИИ-ассистент. Ответы могут содержать ошибки: проверяйте важную информацию. Не используйте сервис для незаконных действий. Аккаунт и история чатов синхронизируются между устройствами. Вы можете удалить аккаунт в настройках.' }),
        error,
        el("button", { class: "btn btn-primary btn-lg btn-block", text: "Принять и начать", onclick: async (e) => {
          e.currentTarget.disabled = true;
          try {
            const { user: updated } = await authApi.update({ name: data.name, birthday: data.birthday || "", terms: true, onboarded: true });
            setUser(updated);
            saveSession({ token: getToken(), user: updated });
            if (window.opener && !window.opener.closed) {
              try { window.opener.location.href = "/chat"; } catch {  }
              window.close();
              return;
            }
            navigate("/chat", { replace: true });
          } catch (err) {
            error.textContent = err.message || "Не получилось";
            error.hidden = false;
            e.currentTarget.disabled = false;
          }
        }}),
      );
    }
    container.append(card);
  };
  draw();
}

async function loadClaudeDesktop() {
  if (!isDesktopApp()) return;

  const pending = claudeDesktop.status();
  let status = null;
  try {
    status = await Promise.race([
      pending,
      new Promise((resolve) => setTimeout(() => resolve(null), 8000)),
    ]);
  } catch (err) {
    state.claude = { linked: false, error: err.message };
    state.claudeModels = [];
    return;
  }
  if (!status) {
    state.claude = state.claude || { linked: false, pending: true };
    pending
      .then((late) => {
        state.claude = late;
        refreshSidebar();
        if (late?.linked) {
          loadClaudeModels().catch(() => {});
          syncClaudeChats().catch(() => {});
        }
      })
      .catch(() => {});
    return;
  }
  state.claude = status;
  if (status.linked) await loadClaudeModels();
  else state.claudeModels = [];
}

async function loadClaudeModels() {
  try {
    const models = await Promise.race([
      claudeDesktop.models(),
      new Promise((resolve) => setTimeout(() => resolve(null), 12000)),
    ]);
    state.claudeModels = models?.data || [];
  } catch {
    state.claudeModels = [];
  }
}

let claudeSyncing = false;
async function syncClaudeChats({ manual = false } = {}) {
  if (!isDesktopApp()) {
    if (manual) toast("Синхронизация доступна только в приложении Clauden для ПК", "error");
    return { added: 0 };
  }
  if (claudeSyncing) return { added: 0 };

  if (!state.claude?.linked) {
    try {
      state.claude = await claudeDesktop.status();
    } catch {  }
  }
  if (!state.claude?.linked) {
    if (manual) toast("Сначала войдите в Claude", "error");
    return { added: 0 };
  }
  claudeSyncing = true;
  try {
    const seen = new Set();
    const summaries = [];
    for (let offset = 0; offset < 1000; offset += 100) {
      const page = await claudeDesktop.importList({ offset, limit: 100 });
      if (!Array.isArray(page) || !page.length) break;
      const fresh = page.filter((item) => item?.uuid && !seen.has(item.uuid));
      if (!fresh.length) break;
      for (const item of fresh) {
        seen.add(item.uuid);
        summaries.push(item);
      }
      if (page.length < 100) break;
    }
    const local = await store.allChats();
    const known = new Map(
      local.filter((chat) => chat.claudeConversationId).map((chat) => [chat.claudeConversationId, chat]),
    );
    let added = 0;
    for (const item of summaries) {
      const existing = known.get(item.uuid);
      if (existing) {

        const stamp = item.updatedAt || 0;
        const title = item.name || existing.title;
        if ((title && title !== existing.title) || stamp > (existing.updatedAt || 0)) {
          const next = {
            ...existing,
            title: title || existing.title,
            updatedAt: Math.max(existing.updatedAt || 0, stamp),
          };
          await store.putChat(next);
        }
        continue;
      }
      const chat = {
        id: `claude-${item.uuid}`,
        title: item.name || "Чат Claude",
        createdAt: item.updatedAt || Date.now(),
        updatedAt: item.updatedAt || Date.now(),
        pinned: false,
        provider: "claude",
        claudeConversationId: item.uuid,
        messages: [],
        remote: true,
      };
      await store.putChat(chat);
      added += 1;
    }
    if (added) await loadChats();
    else refreshSidebar();
    if (manual) {
      if (!summaries.length) toast("В аккаунте Claude не нашлось чатов", "error");
      else if (added) toast(`Добавлено чатов из Claude: ${added}`);
      else toast("Все чаты Claude уже на месте");
    }
    return { added, total: summaries.length };
  } catch (err) {
    if (manual) toast(err.message || "Не удалось синхронизировать чаты Claude", "error");
    return { added: 0, error: err.message };
  } finally {
    claudeSyncing = false;
  }
}

function onDocumentClick(event) {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  const link = event.target instanceof Element ? event.target.closest("a[href]") : null;
  if (!link || link.target || link.hasAttribute("download")) return;
  const raw = link.getAttribute("href");
  if (!raw || raw.startsWith("#")) return;
  const url = new URL(link.href, location.href);
  if (url.origin !== location.origin) return;
  if (/^\/(v1|files|app|downloads)\//.test(url.pathname)) return;

  if (/^\/(docs|pricing|terms|privacy|offer|legal)(\/|$)/.test(url.pathname)) return;
  event.preventDefault();
  navigate(`${url.pathname}${url.search}`);
}

function attachGlobalListeners() {
  window.addEventListener("popstate", () => {
    state.route = parseRoute();
    render();
    if (state.route.name !== "landing") window.scrollTo(0, 0);
  });
  window.addEventListener("storage", (event) => {
    if (event.key === "clauden.session" && event.newValue && !state.user) location.reload();
  });
  window.addEventListener("online", () => { state.offline = false; $(".offline-banner")?.remove(); });
  window.addEventListener("offline", () => { state.offline = true; });
  document.addEventListener("click", onDocumentClick);

  document.addEventListener("visibilitychange", () => {
    if (document.hidden || !state.user || hasStreamingChat()) return;
    if (Date.now() - lastAccountSync < 20_000) return;
    ensureAccountData({ force: true })
      .then(() => {
        if (["projects", "project", "chats"].includes(state.route.name)) renderView();
      })
      .catch(() => {});
  });
  window.addEventListener("focus", () => {
    if (!state.user || hasStreamingChat()) return;
    if (Date.now() - lastAccountSync < 60_000) return;
    ensureAccountData({ force: true }).catch(() => {});
  });

  window.addEventListener("keydown", (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
      if (!state.user) return;
      e.preventDefault();
      openGlobalSearch();
    }
  });

  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferredInstall = e;
    window.__claudenInstallPrompt = e;
  });
  window.addEventListener("appinstalled", () => { deferredInstall = null; window.__claudenInstallPrompt = null; toast("Clauden установлен", "success"); });

  window.addEventListener("clauden:artifact", (event) => {
    const detail = event.detail || {};
    if (!detail.code) return;
    import("./artifacts.js").then(({ openArtifact }) => {
      openArtifact({
        id: `code:${Date.now()}`,
        name: detail.name || `artifact.${detail.lang || "txt"}`,
        kind: "code",
        lang: detail.lang || "text",
        code: detail.code,
      });
    });
  });
}

function registerShell() {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/sw.js?v=12").catch(() => {});
  }

  try {
    navigator.storage?.persist?.().catch(() => {});
  } catch {

  }
  state.booted = true;
  window.__clauden = { state, ctx };
  startRoutines(ctx);
}

async function validateSession(token) {
  if (!token) return;
  try {
    const { user } = await authApi.me();
    setUser(user);
    saveSession({ token, user });
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      saveSession(null);
      setToken("");
      state.user = null;
    }
  }
}

async function boot() {
  applyTheme();
  migrateHashRoute();
  state.route = parseRoute();

  const session = loadSession();
  if (session?.token) {
    setToken(session.token);
    state.user = session.user || null;
    if (state.user) setUser(state.user);
  }

  state.route = resolveRoute(state.route);
  attachGlobalListeners();

  if (state.route.name === "auth" && state.route.id === "bridge") {
    await loadAuthConfig();
    await finishBridge(state.route.params);
    registerShell();
    return;
  }

  if (state.route.name === "landing") {

    if (isDesktopApp() && session?.token) {
      history.replaceState(null, "", "/chat");
      state.route = resolveRoute(parseRoute());
    } else {
      render();
      registerShell();
      loadAuthConfig().catch(() => {});
      if (session?.token) validateSession(session.token).catch(() => {});
      return;
    }
  }

  await loadAuthConfig();
  if (session?.token) await validateSession(session.token);
  state.route = resolveRoute(parseRoute());

  if (!state.user) {
    render();
    registerShell();
    return;
  }

  if (!state.user.onboarded) {
    renderOnboarding();
    registerShell();
    return;
  }

  await loadClaudeDesktop();
  await ensureAccountData({ force: true });
  loadModels().catch(() => {});
  loadPlans().catch(() => {});
  syncClaudeChats().catch(() => {});

  render();
  registerShell();
}

async function loadModels() {
  try {
    const data = await api.models();
    state.models = data?.data || [];
  } catch {  }
}

async function loadPlans() {

  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const data = await api.plans();
      state.plans = data?.data || [];
      if (state.plans.length) return;
    } catch {  }
    if (attempt < 2) {
      await new Promise((resolve) => setTimeout(resolve, 700 * (attempt + 1)));
    }
  }
}

const PALETTE_TABS = [
  { id: "all", label: "Все" },
  { id: "chats", label: "Чаты" },
  { id: "artifacts", label: "Артефакты" },
  { id: "projects", label: "Проекты" },
  { id: "tasks", label: "Задачи" },
  { id: "scheduled", label: "Отложенные" },
];

function paletteEntries(tab, query) {
  const q = query.trim().toLowerCase();
  const match = (...parts) => !q || parts.filter(Boolean).some((p) => String(p).toLowerCase().includes(q));
  const out = [];
  if (tab === "all" || tab === "chats") {
    for (const chat of state.chats) {
      if (!match(chat.title)) continue;
      out.push({
        group: "Недавние",
        icon: "chat",
        title: chat.title || "Новый чат",
        meta: relTimeShort(chat.updatedAt),
        onSelect: () => openChat(chat.id),
      });
    }
  }
  if (tab === "all" || tab === "projects") {
    for (const project of state.projects) {
      if (!match(project.title, project.description)) continue;
      out.push({
        group: "Проекты",
        icon: "folder",
        title: project.title,
        meta: `${(project.files || []).length} файлов`,
        onSelect: () => openProject(project.id),
      });
    }
  }
  if (tab === "all" || tab === "artifacts") {
    for (const artifact of collectArtifacts(state)) {
      if (!match(artifact.name, artifact.chatTitle)) continue;
      out.push({
        group: "Артефакты",
        icon: "file",
        title: artifact.name,
        meta: artifact.chatTitle || "",
        onSelect: () => {
          import("./artifacts.js").then(({ openArtifact }) => openArtifact(artifact));
        },
      });
    }
  }
  if (tab === "all" || tab === "tasks") {
    for (const task of loadTasks(state.user?.id)) {
      if (!match(task.title || task.text)) continue;
      out.push({
        group: "Задачи",
        icon: "check",
        title: task.title || task.text || "Задача",
        meta: task.done ? "выполнена" : "в работе",
        onSelect: () => { closeSettings(); openSettings("tasks"); },
      });
    }
  }
  if (tab === "all" || tab === "scheduled") {
    for (const item of loadScheduled(state.user?.id)) {
      if (!match(item.title || item.text)) continue;
      out.push({
        group: "Отложенные",
        icon: "clock",
        title: item.title || item.text || "Запрос",
        meta: new Date(item.at || Date.now()).toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }),
        onSelect: () => { closeSettings(); openSettings("scheduled"); },
      });
    }
  }
  return out;
}

function openGlobalSearch() {
  if (!state.user) return;
  let tab = "all";
  let query = "";
  let selected = 0;

  const backdrop = el("div", { class: "palette-backdrop", onclick: (e) => { if (e.target === backdrop) close(); } });
  const input = el("input", { placeholder: "Поиск по чатам, артефактам, проектам…", autofocus: true });
  const tabs = el("div", { class: "palette-tabs" });
  const list = el("div", { class: "palette-list" });
  const foot = el("div", { class: "palette-foot" },
    el("span", { class: "pf" }, el("kbd", { text: "Close" }), "Esc"),
    el("span", { class: "pf" }, el("kbd", { text: "↑↓" }), "Выбрать"),
    el("span", { class: "pf" }, el("kbd", { text: "↵" }), "Открыть"),
    el("span", { class: "pf" }, el("kbd", { text: "Tab" }), "Написать"),
  );
  const panel = el("div", { class: "palette" },
    el("div", { class: "palette-input" },
      el("span", { html: icon("search") }),
      input,
      el("button", { class: "icon-btn sm", onclick: close, html: icon("close") }),
    ),
    tabs,
    list,
    foot,
  );
  backdrop.append(panel);
  document.body.append(backdrop);

  function close() {
    backdrop.remove();
    document.removeEventListener("keydown", onKey, true);
  }

  function drawTabs() {
    clear(tabs);
    for (const item of PALETTE_TABS) {
      tabs.append(el("button", {
        class: tab === item.id ? "active" : "",
        text: item.label,
        onclick: () => { tab = item.id; selected = 0; draw(); },
      }));
    }
  }

  function draw() {
    drawTabs();
    clear(list);
    const entries = paletteEntries(tab, query);
    if (!query && tab === "all") {
      list.append(el("div", { class: "palette-group", text: "Действия" }));
      list.append(el("button", {
        class: "palette-item",
        onclick: () => { close(); newChat(); },
      }, el("span", { html: icon("plus") }), el("span", { class: "p-title", text: "Новый чат" })));
    }
    if (!entries.length) {
      list.append(el("div", { class: "pad muted small", text: query ? "Ничего не найдено" : "Пока пусто" }));
      return;
    }
    let group = "";
    entries.forEach((entry, index) => {
      if (entry.group !== group) {
        group = entry.group;
        list.append(el("div", { class: "palette-group", text: group }));
      }
      list.append(el("button", {
        class: `palette-item ${index === selected ? "sel" : ""}`,
        onclick: () => { close(); entry.onSelect(); },
      },
        el("span", { html: icon(entry.icon) }),
        el("span", { class: "p-title", text: entry.title }),
        el("span", { class: "p-meta", text: entry.meta || "" }),
      ));
    });
  }

  function onKey(event) {
    if (event.key === "Escape") { event.preventDefault(); close(); return; }
    const entries = paletteEntries(tab, query);
    const max = entries.length + (tab === "all" && !query ? 1 : 0);
    if (event.key === "ArrowDown") { event.preventDefault(); selected = Math.min(max - 1, selected + 1); draw(); }
    if (event.key === "ArrowUp") { event.preventDefault(); selected = Math.max(0, selected - 1); draw(); }
    if (event.key === "Enter") {
      event.preventDefault();
      if (tab === "all" && !query && selected === 0) { close(); newChat(); return; }
      const entry = entries[selected - (tab === "all" && !query ? 1 : 0)] || entries[selected];
      if (entry) { close(); entry.onSelect(); }
    }
  }

  input.addEventListener("input", () => { query = input.value; selected = 0; draw(); });
  document.addEventListener("keydown", onKey, true);
  draw();
  setTimeout(() => input.focus(), 30);
}

export { ctx, state, render, renderView, navigate, refreshSidebar, toggleSidebar, saveChat, backupSoon, exportChat, loadModels, loadPlans, deferredInstall };

function reportFatal(err) {
  const app = $("#app");
  if (app && !app.hidden && app.childElementCount > 0) return;
  const boot = $("#boot");
  if (!boot) return;
  boot.hidden = false;
  boot.replaceChildren(
    el("div", { style: "max-width:420px;text-align:center;padding:20px" },
      el("div", { style: "font-size:18px;margin-bottom:8px", text: "Не удалось запустить Clauden" }),
      el("div", { style: "color:var(--text-3);font-size:13px", text: String(err?.message || err || "неизвестная ошибка") }),
      el("button", { class: "btn btn-primary", style: "margin-top:16px", text: "Перезагрузить", onclick: () => location.reload() }),
      el("button", {
        class: "btn btn-ghost",
        style: "margin-top:10px",
        text: "Сбросить локальную базу",
        onclick: async () => {

          try {
            await new Promise((resolve) => {
              const req = indexedDB.deleteDatabase("clauden");
              req.onsuccess = req.onerror = req.onblocked = () => resolve();
            });
          } catch {

          }
          location.reload();
        },
      }),
    ),
  );
}

function reportFatalSoon(err) {
  setTimeout(() => reportFatal(err), 1200);
}

window.addEventListener("error", (event) => reportFatalSoon(event.error || event.message));
window.addEventListener("unhandledrejection", (event) => reportFatalSoon(event.reason));

boot().catch((err) => {
  console.error(err);
  reportFatal(err);
});
