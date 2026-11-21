import { h, icon, rpc, toast, clear } from "./ui.js";
import { state, activeTab, newTabId } from "./state.js";
import { ws } from "./ws.js";

const LANG = {
  js: "javascript",
  mjs: "javascript",
  cjs: "javascript",
  jsx: "javascript",
  ts: "typescript",
  tsx: "typescript",
  json: "json",
  css: "css",
  scss: "scss",
  less: "less",
  html: "xml",
  htm: "xml",
  vue: "xml",
  svg: "xml",
  xml: "xml",
  md: "markdown",
  markdown: "markdown",
  py: "python",
  rb: "ruby",
  go: "go",
  rs: "rust",
  java: "java",
  kt: "kotlin",
  swift: "swift",
  php: "php",
  sh: "bash",
  bash: "bash",
  zsh: "bash",
  yml: "yaml",
  yaml: "yaml",
  toml: "ini",
  ini: "ini",
  sql: "sql",
  c: "c",
  h: "c",
  cpp: "cpp",
  hpp: "cpp",
  cs: "csharp",
};

function languageOf(path) {
  const ext = String(path || "").split(".").pop()?.toLowerCase();
  return LANG[ext] || "";
}

function basename(path) {
  return String(path || "").split("/").pop() || path;
}

export function renderTabs(host, ctx) {
  clear(host);

  host.append(
    h(
      "div",
      {
        class: `tab chat-tab ${state.activeTabId ? "" : "active"}`,
        title: "Чат с агентом (Ctrl+J)",
        onclick: () => ctx.showChat?.(),
      },
      h("span", {
        html: icon("chat", 12),
        style: { display: "flex", color: state.activeTabId ? "var(--ink-3)" : "var(--clay)" },
      }),
      h("span", { class: "tab-name", text: "Claude Code" }),
    ),
  );

  for (const tab of state.tabs) {
    const active = tab.id === state.activeTabId;
    host.append(
      h(
        "div",
        {
          class: `tab ${active ? "active" : ""}`,
          onclick: () => {
            state.activeTabId = tab.id;
            ctx.renderTabs();
            ctx.renderWork();
          },
          onauxclick: (event) => {
            if (event.button === 1) closeTab(tab.id, ctx);
          },
          title: tab.path || tab.title,
        },
        h("span", {
          html: icon(tab.kind === "diff" ? "diff" : "file", 12),
          style: { display: "flex", color: tab.kind === "diff" ? "var(--clay)" : "var(--ink-3)" },
        }),
        h("span", { class: "tab-name", text: tab.title }),
        tab.dirty ? h("span", { class: "dirty-dot" }) : null,
        h("span", {
          class: "tab-close",
          html: icon("close", 11),
          onclick: (event) => {
            event.stopPropagation();
            closeTab(tab.id, ctx);
          },
        }),
      ),
    );
  }

  host.append(h("div", { class: "tabs-fill" }));

  const changedCount = state.diff?.files?.length || 0;
  host.append(
    h(
      "div",
      { class: "tabs-tools" },
      h(
        "button",
        {
          class: "pill-btn",
          title: "Показать изменения (Ctrl+G)",
          onclick: () => ctx.openDiff(),
        },
        h("span", { html: icon("diff", 13), style: { display: "flex" } }),
        h("span", { text: "Изменения" }),
        changedCount ? h("span", { class: "count", text: String(changedCount) }) : null,
      ),
      h(
        "button",
        {
          class: "pill-btn",
          title: "Remote Control",
          onclick: () => ctx.openRemote(),
        },
        h("span", { html: icon("link", 13), style: { display: "flex" } }),
        h("span", { text: "Remote" }),
      ),
    ),
  );
}

export function closeTab(id, ctx) {
  const index = state.tabs.findIndex((tab) => tab.id === id);
  if (index < 0) return;
  const tab = state.tabs[index];
  if (tab.dirty) {
    const ok = confirm(`Закрыть «${tab.title}» без сохранения?`);
    if (!ok) return;
  }
  state.tabs.splice(index, 1);
  if (state.activeTabId === id) {
    const next = state.tabs[index] || state.tabs[index - 1] || null;
    state.activeTabId = next ? next.id : null;
  }
  ctx.renderTabs();
  ctx.renderWork();
  ctx.renderSide();
}

export async function openFile(path, ctx) {
  if (!state.session) {
    toast("Сначала откройте сессию", "error");
    return;
  }
  const existing = state.tabs.find((tab) => tab.kind === "file" && tab.path === path);
  if (existing) {
    state.activeTabId = existing.id;
    ctx.renderTabs();
    ctx.renderWork();
    return;
  }

  const tab = {
    id: newTabId(),
    kind: "file",
    path,
    title: basename(path),
    content: "",
    original: "",
    dirty: false,
    binary: false,
    loading: true,
  };
  state.tabs.push(tab);
  state.activeTabId = tab.id;
  ctx.renderTabs();
  ctx.renderWork();

  try {
    const data = await ws.read(path);
    tab.content = data?.content || "";
    tab.original = tab.content;
    tab.binary = Boolean(data?.binary);
    tab.loading = false;
  } catch (err) {
    tab.loading = false;
    tab.error = err.message || "Не удалось открыть файл";
  }
  ctx.renderWork();
}

export function openDiffTab(ctx) {
  const existing = state.tabs.find((tab) => tab.kind === "diff");
  if (existing) {
    state.activeTabId = existing.id;
  } else {
    const tab = { id: newTabId(), kind: "diff", title: "Изменения", path: "" };
    state.tabs.push(tab);
    state.activeTabId = tab.id;
  }
  ctx.renderTabs();
  ctx.renderWork();
}

export function renderEditor(host, ctx) {
  const tab = activeTab();
  if (!tab) return false;

  if (tab.kind === "diff") {
    ctx.renderDiff(host);
    return true;
  }

  if (tab.loading) {
    host.append(h("div", { class: "ed-empty", text: "Открываем файл…" }));
    return true;
  }

  if (tab.error) {
    host.append(h("div", { class: "ed-empty", text: tab.error }));
    return true;
  }

  if (tab.binary) {
    host.append(h("div", { class: "ed-empty", text: "Двоичный файл — редактирование недоступно" }));
    return true;
  }

  const gutter = h("div", { class: "ed-gutter" });
  const code = h("code");
  const pre = h("pre", { class: "ed-hl", "aria-hidden": "true" }, code);
  const input = h("textarea", {
    class: "ed-input",
    spellcheck: "false",
    wrap: "off",
    autocapitalize: "off",
    autocomplete: "off",
  });
  input.value = tab.content;

  const lang = languageOf(tab.path);
  const big = tab.content.length > 300_000;

  function highlight() {
    const text = input.value;
    if (big || !lang || !window.hljs?.getLanguage?.(lang)) {
      code.textContent = text + "\n";
    } else {
      try {
        code.innerHTML = window.hljs.highlight(text, { language: lang, ignoreIllegals: true }).value + "\n";
      } catch {
        code.textContent = text + "\n";
      }
    }
  }

  function paintGutter() {
    const lines = input.value.split("\n").length;
    if (gutter.childElementCount === lines) return;
    const fragment = document.createDocumentFragment();
    for (let i = 1; i <= lines; i += 1) {
      const row = document.createElement("div");
      row.textContent = String(i);
      fragment.append(row);
    }
    clear(gutter);
    gutter.append(fragment);
  }

  function syncScroll() {
    code.style.transform = `translate(${-input.scrollLeft}px, ${-input.scrollTop}px)`;
    gutter.scrollTop = input.scrollTop;
  }

  function updateCursor() {
    const upto = input.value.slice(0, input.selectionStart);
    const line = upto.split("\n").length;
    const col = upto.length - upto.lastIndexOf("\n");
    ctx.setCursor(line, col);
    const rows = gutter.children;
    for (let i = 0; i < rows.length; i += 1) rows[i].classList.toggle("cur", i === line - 1);
  }

  input.addEventListener("input", () => {
    tab.content = input.value;
    tab.dirty = tab.content !== tab.original;
    highlight();
    paintGutter();
    syncScroll();
    updateCursor();
    ctx.renderTabs();
    ctx.renderSide();
  });
  input.addEventListener("scroll", syncScroll);
  input.addEventListener("keyup", updateCursor);
  input.addEventListener("click", updateCursor);
  input.addEventListener("keydown", (event) => {
    if (event.key === "Tab") {
      event.preventDefault();
      const start = input.selectionStart;
      const end = input.selectionEnd;
      input.setRangeText("  ", start, end, "end");
      input.dispatchEvent(new Event("input"));
    }
  });

  host.append(h("div", { class: "editor" }, gutter, h("div", { class: "ed-scroll" }, pre, input)));

  highlight();
  paintGutter();
  syncScroll();
  updateCursor();
  setTimeout(() => input.focus(), 0);
  return true;
}

export async function saveActive(ctx) {
  const tab = activeTab();
  if (!tab || tab.kind !== "file" || !tab.dirty) return false;
  if (!state.session) return false;
  try {
    await ws.write(tab.path, tab.content);
    tab.original = tab.content;
    tab.dirty = false;
    ctx.renderTabs();
    ctx.renderSide();
    toast(`Сохранено · ${tab.title}`);
    ctx.refreshDiff({ silent: true });
    return true;
  } catch (err) {
    toast(err.message || "Не удалось сохранить", "error");
    return false;
  }
}

export { languageOf, basename };
