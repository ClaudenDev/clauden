import { el, clear, icon, toast, copyText } from "./util.js";

const isMac = /Mac|iPhone|iPad/.test(navigator.platform || "");
const mod = isMac ? "⌘" : "Ctrl+";

let menuEl = null;
let subEl = null;
let outsideHandler = null;

export function closePlusMenu() {
  subEl?.remove();
  menuEl?.remove();
  subEl = null;
  menuEl = null;
  if (outsideHandler) {
    document.removeEventListener("mousedown", outsideHandler, true);
    document.removeEventListener("keydown", outsideHandler, true);
    outsideHandler = null;
  }
}

export function openPlusMenu(anchor, opts) {
  closePlusMenu();
  const items = [
    { id: "upload", icon: "paperclip", label: "Загрузить файлы", hint: `${mod}U`, run: () => opts.onOpenPicker?.("artifacts") },
    { id: "project", icon: "folder", label: "Добавить в проект", submenu: () => projectSubmenu(opts) },
    { id: "github", icon: "github", label: "Добавить из GitHub", run: () => opts.onAddFromGithub?.() },
    { id: "skills", icon: "sparkles", label: "Навыки", submenu: () => skillsSubmenu(opts) },
    { id: "connectors", icon: "plug", label: "Коннекторы", submenu: () => connectorsSubmenu(opts) },
    { id: "design", icon: "palette", label: "Дизайн-система", submenu: () => designSubmenu(opts) },
    { id: "plugins", icon: "layers", label: "Добавить плагины", submenu: () => pluginsSubmenu(opts) },
    "-",
    { id: "web", icon: "globe", label: "Веб-поиск", checked: () => Boolean(opts.webSearch), run: () => opts.onToggleWebSearch?.(!opts.webSearch) },
  ];

  const menu = el("div", { class: "plus-menu", role: "menu" });
  menuEl = menu;

  for (const item of items) {
    if (item === "-") {
      menu.append(el("div", { class: "pm-sep" }));
      continue;
    }
    const row = el("button", {
      class: `pm-item ${item.submenu ? "has-sub" : ""}`,
      role: item.checked ? "menuitemcheckbox" : "menuitem",
      onclick: (e) => {
        e.stopPropagation();
        if (item.submenu) {
          openSub(row, item.submenu(), true);
          return;
        }
        if (item.checked) {

          const next = !item.checked();
          item.run?.();
          row.querySelector(".pm-tick")?.classList.toggle("on", next);
          return;
        }
        closePlusMenu();
        item.run?.();
      },
      onmouseenter: () => {
        menu.querySelectorAll(".pm-item.open").forEach((n) => n.classList.remove("open"));
        if (item.submenu) {
          row.classList.add("open");
          openSub(row, item.submenu(), false);
        } else {
          closeSub();
        }
      },
    },
    el("span", { class: "pm-ico", html: icon(item.icon) }),
    el("span", { class: "pm-label", text: item.label }),
    item.hint ? el("span", { class: "pm-hint", text: item.hint }) : null,
    item.checked ? el("span", { class: `pm-tick ${item.checked() ? "on" : ""}`, html: icon("check") }) : null,
    item.submenu ? el("span", { class: "pm-chev", html: icon("chevronRight") }) : null,
    );

    menu.append(row);
  }

  document.body.append(menu);

  const rect = anchor.getBoundingClientRect();
  const width = menu.offsetWidth;
  const height = menu.offsetHeight;
  let left = Math.min(rect.left, window.innerWidth - width - 12);
  left = Math.max(12, left);
  let top = rect.top - height - 10;
  if (top < 12) top = Math.min(rect.bottom + 8, window.innerHeight - height - 12);
  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;

  outsideHandler = (e) => {
    if (e.type === "keydown") {
      if (e.key === "Escape") { e.preventDefault(); closePlusMenu(); }
      return;
    }
    if (menu.contains(e.target) || subEl?.contains(e.target)) return;
    closePlusMenu();
  };
  setTimeout(() => {
    document.addEventListener("mousedown", outsideHandler, true);
    document.addEventListener("keydown", outsideHandler, true);
  }, 0);

  function openSub(row, subItems, fromClick) {
    closeSub();
    if (!subItems?.length) return;
    const sub = el("div", { class: "plus-menu pm-sub" });
    for (const entry of subItems) {
      if (entry === "-") { sub.append(el("div", { class: "pm-sep" })); continue; }
      sub.append(el("button", {
        class: `pm-item ${entry.disabled ? "disabled" : ""} ${entry.checked ? "is-checked" : ""}`,
        onclick: (e) => {
          e.stopPropagation();
          if (entry.disabled) return;
          closePlusMenu();
          entry.run?.();
        },
      },
      el("span", { class: "pm-ico", html: icon(entry.icon || "circle") }),
      el("span", { class: "pm-label", text: entry.label }),
      entry.hint ? el("span", { class: "pm-hint", text: entry.hint }) : null,
      entry.checked ? el("span", { class: "pm-tick on", html: icon("check") }) : null,
      ));
    }
    document.body.append(sub);
    const rowRect = row.getBoundingClientRect();
    const subWidth = sub.offsetWidth;
    const subHeight = sub.offsetHeight;
    let left = rowRect.right + 6;
    if (left + subWidth > window.innerWidth - 10) left = Math.max(10, rowRect.left - subWidth - 6);
    let top = rowRect.top - 6;
    if (top + subHeight > window.innerHeight - 10) top = Math.max(10, window.innerHeight - subHeight - 10);
    sub.style.left = `${left}px`;
    sub.style.top = `${top}px`;
    subEl = sub;
    if (fromClick) subEl.dataset.sticky = "1";
  }

  function closeSub() {
    if (subEl?.dataset.sticky) return;
    subEl?.remove();
    subEl = null;
  }

  return menu;
}

function projectSubmenu(opts) {
  const projects = opts.projects || [];
  const items = projects.map((p) => ({
    icon: "folder",

    label: p.title || p.name || "Без названия",
    checked: opts.currentProjectId === p.id,
    run: () => opts.onAddToProject?.(p),
  }));
  if (items.length) items.push("-");
  items.push({ icon: "plus", label: "Новый проект", run: () => opts.onNewProject?.() });
  return items;
}

function skillsSubmenu(opts) {
  return [
    { icon: "layers", label: "Артефакты", checked: Boolean(opts.skills?.artifacts), run: () => opts.onToggleSkill?.("artifacts") },
    { icon: "globe", label: "Веб-поиск", checked: Boolean(opts.skills?.webSearch), run: () => opts.onToggleSkill?.("webSearch") },
    { icon: "terminal", label: "Выполнение кода", checked: Boolean(opts.skills?.codeExecution), run: () => opts.onToggleSkill?.("codeExecution") },
    { icon: "chart", label: "Визуализации", checked: Boolean(opts.skills?.visualizations), run: () => opts.onToggleSkill?.("visualizations") },
    { icon: "brain", label: "Память", checked: Boolean(opts.skills?.memory), run: () => opts.onToggleSkill?.("memory") },
    "-",
    { icon: "sliders", label: "Все возможности", run: () => opts.onOpenSettings?.("features") },
  ];
}

function connectorsSubmenu(opts) {
  const list = opts.connectors || [];
  const items = list.map((c) => ({
    icon: c.icon || "plug",
    label: c.label,
    hint: c.status || "",
    checked: Boolean(c.connected),
    run: () => c.run?.(),
  }));
  if (items.length) items.push("-");
  items.push({ icon: "plus", label: "Добавить коннектор", run: () => opts.onOpenSettings?.("connectors") });
  return items;
}

function designSubmenu(opts) {
  return [
    { icon: "moon", label: "Тёмная тема", checked: opts.theme === "dark", run: () => opts.onTheme?.("dark") },
    { icon: "sun", label: "Светлая тема", checked: opts.theme === "light", run: () => opts.onTheme?.("light") },
    { icon: "monitor", label: "Как в системе", checked: opts.theme === "system", run: () => opts.onTheme?.("system") },
    "-",
    { icon: "sparkles", label: "iOS Glass", checked: opts.style === "glass", run: () => opts.onStyle?.("glass") },
    { icon: "moon", label: "Noir", checked: opts.style === "noir", run: () => opts.onStyle?.("noir") },
    { icon: "palette", label: "Классика", checked: !opts.style || opts.style === "classic", run: () => opts.onStyle?.("classic") },
    "-",
    { icon: "sliders", label: "Настроить оформление", run: () => opts.onOpenSettings?.("appearance") },
  ];
}

function pluginsSubmenu(opts) {
  return [
    { icon: "plug", label: "Свой API", run: () => opts.onOpenSettings?.("custom-api") },
    { icon: "github", label: "GitHub", run: () => opts.onOpenSettings?.("connectors") },
    { icon: "terminal", label: "Терминал и SSH", run: () => opts.onOpenSettings?.("connectors") },
    { icon: "shield", label: "Прокси и VPN", run: () => opts.onOpenSettings?.("proxy") },
    "-",
    { icon: "layers", label: "Все плагины", run: () => opts.onOpenSettings?.("customize") },
  ];
}

export function openFilePicker(opts = {}) {
  const artifacts = Array.isArray(opts.artifacts) ? opts.artifacts : [];
  let tab = opts.initialTab === "upload" ? "upload" : "artifacts";
  let filter = "all";
  let query = "";
  let sel = 0;
  let rows = [];
  let pickerInput = null;

  const root = document.getElementById("modal-root") || document.body;
  const scrim = el("div", { class: "pm-scrim" });
  const card = el("div", { class: "fp", role: "dialog", "aria-modal": "true" });

  const search = el("input", { class: "fp-search-input", placeholder: "поиск недавних файлов", "aria-label": "Поиск файлов" });
  const head = el("div", { class: "fp-head" },
    el("div", { class: "fp-search" },
      el("span", { class: "fp-search-ico", html: icon("search") }),
      search,
    ),
    el("span", { class: "fp-beta", text: "Beta" }),
    el("button", { class: "fp-x", title: "Закрыть", onclick: () => close(), html: icon("close") }),
  );

  const tabArtifacts = el("button", { class: "fp-tab", onclick: () => setTab("artifacts") },
    el("span", { html: icon("layers") }), el("span", { text: "Артефакты" }));
  const tabUpload = el("button", { class: "fp-tab", onclick: () => setTab("upload") },
    el("span", { html: icon("upload") }), el("span", { text: "Загрузить" }));
  const tabs = el("div", { class: "fp-tabs" }, tabArtifacts, tabUpload);

  const body = el("div", { class: "fp-body" });
  const foot = el("div", { class: "fp-foot" });
  card.append(head, tabs, body, foot);
  scrim.append(card);
  root.append(scrim);

  search.addEventListener("input", () => { query = search.value.trim().toLowerCase(); sel = 0; renderBody(); });

  scrim.addEventListener("mousedown", (e) => { if (e.target === scrim) close(); });
  const onKey = (e) => {
    if (e.key === "Escape") { e.preventDefault(); close(); return; }
    if (e.key === "Tab") return;
    if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
      e.preventDefault();
      setTab(tab === "artifacts" ? "upload" : "artifacts");
      return;
    }
    if (tab !== "artifacts") return;
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      if (!rows.length) return;
      sel = (sel + (e.key === "ArrowDown" ? 1 : -1) + rows.length) % rows.length;
      paintSelection();
      return;
    }
    if (e.key === "Enter") {
      e.preventDefault();
      const item = rows[sel];
      if (item) pick(item);
    }
  };
  document.addEventListener("keydown", onKey, true);

  let dragDepth = 0;
  card.addEventListener("dragenter", (e) => { e.preventDefault(); dragDepth += 1; card.classList.add("dragging"); });
  card.addEventListener("dragover", (e) => { e.preventDefault(); });
  card.addEventListener("dragleave", () => { dragDepth = Math.max(0, dragDepth - 1); if (!dragDepth) card.classList.remove("dragging"); });
  card.addEventListener("drop", (e) => {
    e.preventDefault();
    dragDepth = 0;
    card.classList.remove("dragging");
    const files = [...(e.dataTransfer?.files || [])];
    if (files.length) { close(); opts.onFiles?.(files); }
  });

  function setTab(next) {
    tab = next;
    sel = 0;
    renderBody();
  }

  function renderBody() {
    tabArtifacts.classList.toggle("active", tab === "artifacts");
    tabUpload.classList.toggle("active", tab === "upload");
    clear(body);
    rows = [];
    if (tab === "upload") renderUpload(); else renderArtifacts();
    renderFoot();
  }

  function renderFoot() {
    clear(foot);
    if (tab === "artifacts") {
      foot.append(
        hint("Навигация", ["↑", "↓"]),
        hint("Выбор", ["↵"]),
        hint("Сменить источник", ["←", "→"]),
      );
    } else {
      foot.append(hint("Сменить источник", ["←", "→"]));
    }
  }

  function hint(label, keys) {
    return el("span", { class: "fp-hint" },
      el("span", { text: label }),
      ...keys.map((k) => el("kbd", { text: k })),
    );
  }

  function renderUpload() {
    const choose = el("button", { class: "fp-choose", text: "Выбрать файлы", onclick: () => pickerInput?.click() });
    const zone = el("div", { class: "fp-drop" },
      el("div", { class: "fp-drop-ico", html: icon("upload") }),
      el("div", { class: "fp-drop-title", text: "Перетащите файлы или фото сюда" }),
      el("div", { class: "fp-drop-sub", text: "Документы, изображения, таблицы и файлы кода" }),
      choose,
    );
    zone.addEventListener("click", (e) => { if (e.target === zone) pickerInput?.click(); });
    body.append(zone);
  }

  function renderArtifacts() {
    const filters = el("div", { class: "fp-filters" });
    for (const [id, label] of [["all", "Все"], ["docs", "Документы"], ["apps", "Приложения"]]) {
      filters.append(el("button", {
        class: `fp-chip ${filter === id ? "active" : ""}`,
        text: label,
        onclick: () => { filter = id; sel = 0; renderBody(); },
      }));
    }
    body.append(filters, el("div", { class: "fp-label", text: "Действия" }));

    const uploadRow = el("div", {
      class: "fp-action",
      onclick: () => pickerInput?.click(),
    },
    el("span", { class: "fp-action-ico", html: icon("upload") }),
    el("span", { text: "Загрузить с компьютера" }),
    );
    body.append(uploadRow);

    const list = visibleArtifacts();
    if (!list.length) {
      body.append(el("div", { class: "fp-empty" },
        el("div", { class: "fp-empty-title", text: query ? "Ничего не нашлось" : "Пока нет артефактов" }),
        el("div", { class: "fp-empty-sub", text: query ? "Попробуйте другой запрос" : "Файлы и код из чатов появятся здесь" }),
      ));
      return;
    }

    let currentGroup = "";
    for (const item of list) {
      const group = groupLabel(item.ts);
      if (group !== currentGroup) {
        currentGroup = group;
        body.append(el("div", { class: "fp-label", text: group }));
      }
      const row = el("div", {
        class: "fp-row",
        role: "option",
        onclick: () => pick(item),
        onmouseenter: () => { sel = rows.indexOf(item); paintSelection(); },
      },
      el("span", { class: "fp-box", html: icon("check") }),
      el("span", { class: "fp-name", text: item.name }),
      el("span", { class: "fp-date", text: `· ${shortDate(item.ts)}` }),
      el("span", { class: "fp-tag", text: tagOf(item) }),
      el("button", {
        class: "fp-copy",
        title: "Скопировать",
        onclick: async (e) => {
          e.stopPropagation();
          try {
            const text = item.kind === "code" ? item.code || "" : await fetchText(item);
            await copyText(text);
            toast("Скопировано");
          } catch {
            toast("Не удалось скопировать", "error");
          }
        },
        html: icon("copy"),
      }),
      );
      rows.push(item);
      body.append(row);
    }
    paintSelection();
  }

  function visibleArtifacts() {
    const needle = query;
    return artifacts
      .filter((item) => {
        if (!needle) return true;
        return `${item.name} ${item.chatTitle || ""}`.toLowerCase().includes(needle);
      })
      .filter((item) => {
        if (filter === "all") return true;
        const lang = String(item.lang || "").toLowerCase();
        const ext = String(item.name || "").split(".").pop().toLowerCase();
        const appish = ["html", "htm", "svg", "js", "ts", "py", "css"].includes(lang) || ["html", "htm", "svg", "js", "ts", "py", "css"].includes(ext);
        return filter === "apps" ? appish : !appish;
      })
      .sort((a, b) => (b.ts || 0) - (a.ts || 0));
  }

  function paintSelection() {
    const nodes = body.querySelectorAll(".fp-row");
    nodes.forEach((node, index) => node.classList.toggle("sel", index === sel));
    nodes[sel]?.scrollIntoView({ block: "nearest" });
  }

  function pick(item) {
    close();
    opts.onArtifact?.(item);
  }

  function close() {
    document.removeEventListener("keydown", onKey, true);
    scrim.remove();
    opts.onClose?.();
  }

  pickerInput = el("input", { type: "file", multiple: true, hidden: true });
  pickerInput.addEventListener("change", () => {
    const files = [...pickerInput.files];
    pickerInput.value = "";
    if (!files.length) return;
    close();
    opts.onFiles?.(files);
  });
  card.append(pickerInput);

  renderBody();
  setTimeout(() => search.focus(), 40);
  return { close, setTab };
}

export function artifactToText(item) {
  return item.kind === "code" ? item.code || "" : fetchText(item);
}

async function fetchText(item) {
  if (item.kind === "code") return item.code || "";
  if (!item.url) return "";
  try {
    const res = await fetch(item.url);
    return await res.text();
  } catch {
    return "";
  }
}

function tagOf(item) {
  const lang = String(item.lang || "").toLowerCase();
  if (["html", "htm", "svg", "js", "ts", "py", "css"].includes(lang)) return "Приложение";
  return "Документ";
}

function shortDate(ts) {
  const d = new Date(Number(ts) || 0);
  if (!Number(ts)) return "";
  return d.toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
}

function groupLabel(ts) {
  const now = new Date();
  const date = new Date(Number(ts) || 0);
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const day = 24 * 60 * 60 * 1000;
  if (date.getTime() >= startOfToday) return "Сегодня";
  if (date.getTime() >= startOfToday - 7 * day) return "Ранее на этой неделе";
  if (date.getTime() >= startOfToday - 14 * day) return "На прошлой неделе";
  return "Ранее";
}
