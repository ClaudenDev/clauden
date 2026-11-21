import { h, icon, rpc, toast, confirmDialog, bytes } from "./ui.js";
import { state } from "./state.js";
import { ws } from "./ws.js";

const EXT_COLOR = {
  js: "#d3ac5c",
  mjs: "#d3ac5c",
  cjs: "#d3ac5c",
  ts: "#6f9df5",
  tsx: "#6f9df5",
  jsx: "#6f9df5",
  json: "#d3ac5c",
  md: "#8fb3d9",
  css: "#a78bd8",
  scss: "#a78bd8",
  html: "#d97757",
  vue: "#5aa87e",
  py: "#5aa87e",
  sh: "#5aa87e",
  yml: "#c98cd8",
  yaml: "#c98cd8",
  toml: "#c98cd8",
  lock: "#74706c",
  png: "#c98cd8",
  jpg: "#c98cd8",
  svg: "#d3ac5c",
};

function fileColor(name) {
  const ext = name.split(".").pop()?.toLowerCase();
  return EXT_COLOR[ext] || "#74706c";
}

export async function loadDir(dir = "") {
  if (!state.session) return;
  const data = await ws.list(dir);
  state.tree.set(dir, data?.entries || []);
  return data;
}

export async function loadTree() {
  if (!state.session) return;
  try {
    await loadDir("");
    state.expanded = new Set([""]);
  } catch (err) {
    state.tree = new Map();
    throw err;
  }
}

export function renderExplorer(host, ctx) {
  host.replaceChildren();

  if (!state.session) {
    host.append(h("div", { class: "side-empty", text: "Сначала откройте сессию" }));
    return;
  }

  const root = state.tree.get("");
  if (!root) {
    for (let i = 0; i < 6; i += 1) host.append(h("div", { class: "skeleton" }));
    return;
  }

  const tree = h("div", { class: "tree" });
  renderLevel(tree, "", 0, ctx);
  host.append(tree);
}

function renderLevel(host, dir, depth, ctx) {
  const entries = state.tree.get(dir) || [];
  for (const entry of entries) {
    const isDir = entry.type === "dir";
    const expanded = state.expanded.has(entry.path);
    const changed = state.changed.has(entry.path);
    const dirty = state.tabs.some((tab) => tab.path === entry.path && tab.dirty);

    const row = h(
      "div",
      {
        class: `tree-row ${isDir ? "dir" : "file"} ${state.activeTabId && state.tabs.find((t) => t.id === state.activeTabId)?.path === entry.path ? "active" : ""} ${changed ? "changed" : ""}`,
        style: { paddingLeft: `${6 + depth * 12}px` },
        title: entry.path,
        onclick: async () => {
          if (isDir) {
            if (expanded) {
              state.expanded.delete(entry.path);
              ctx.renderSide();
              return;
            }
            state.expanded.add(entry.path);
            if (!state.tree.has(entry.path)) {
              try {
                await loadDir(entry.path);
              } catch (err) {
                toast(err.message || "Не удалось открыть папку", "error");
                state.expanded.delete(entry.path);
              }
            }
            ctx.renderSide();
          } else {
            ctx.openFile(entry.path);
          }
        },
        oncontextmenu: (event) => {
          event.preventDefault();
          contextMenu(event, entry, isDir, ctx);
        },
      },
      h("span", {
        class: `chev ${expanded ? "rot" : ""}`,
        html: isDir ? icon("chevron", 12) : "",
      }),
      h("span", {
        class: "fico",
        html: icon(isDir ? (expanded ? "folder" : "folder") : "file", 13),
        style: isDir ? {} : { color: fileColor(entry.name) },
      }),
      h("span", { class: "tree-name", text: entry.name }),
      dirty ? h("span", { class: "dirty", text: "•" }) : null,
    );

    host.append(row);

    if (isDir && expanded) renderLevel(host, entry.path, depth + 1, ctx);
  }
}

function contextMenu(event, entry, isDir, ctx) {
  document.querySelectorAll(".ctx-menu").forEach((node) => node.remove());

  const close = () => menu.remove();
  const items = [];

  if (isDir) {
    items.push({
      label: "Новый файл",
      run: async () => {
        const name = prompt("Имя файла", "new-file.ts");
        if (!name) return;
        try {
          await ws.create(entry.path, name, "file");
          state.tree.delete(entry.path);
          await loadDir(entry.path);
          ctx.renderSide();
          toast("Файл создан");
        } catch (err) {
          toast(err.message || "Не удалось создать", "error");
        }
      },
    });
    items.push({
      label: "Новая папка",
      run: async () => {
        const name = prompt("Имя папки", "src");
        if (!name) return;
        try {
          await ws.create(entry.path, name, "dir");
          state.tree.delete(entry.path);
          await loadDir(entry.path);
          ctx.renderSide();
        } catch (err) {
          toast(err.message || "Не удалось создать", "error");
        }
      },
    });
  } else {
    items.push({
      label: "Открыть",
      run: () => ctx.openFile(entry.path),
    });
    items.push({
      label: "Переименовать",
      run: async () => {
        const name = prompt("Новое имя", entry.name);
        if (!name || name === entry.name) return;
        try {
          await ws.rename(entry.path, name);
          state.tree.clear();
          await loadTree();
          ctx.renderSide();
          toast("Переименовано");
        } catch (err) {
          toast(err.message || "Не удалось переименовать", "error");
        }
      },
    });
    items.push({
      label: "Удалить",
      run: async () => {
        const ok = await confirmDialog("Удалить файл?", entry.path);
        if (!ok) return;
        try {
          await ws.remove(entry.path);
          state.tree.clear();
          await loadTree();
          ctx.renderSide();
          toast("Удалено");
        } catch (err) {
          toast(err.message || "Не удалось удалить", "error");
        }
      },
    });
  }

  const menu = h(
    "div",
    {
      class: "ctx-menu",
      style: {
        position: "fixed",
        left: `${event.clientX}px`,
        top: `${event.clientY}px`,
        zIndex: 80,
        background: "var(--bg-3)",
        border: "1px solid var(--line-2)",
        borderRadius: "8px",
        padding: "4px",
        minWidth: "150px",
        boxShadow: "0 12px 30px rgba(0,0,0,.45)",
      },
    },
    ...items.map((item) =>
      h("div", {
        class: "ctx-item",
        text: item.label,
        style: { padding: "5px 9px", borderRadius: "5px", cursor: "pointer", fontSize: "12px" },
        onmouseenter: (e) => (e.currentTarget.style.background = "var(--bg-4)"),
        onmouseleave: (e) => (e.currentTarget.style.background = "transparent"),
        onclick: () => {
          close();
          item.run();
        },
      }),
    ),
  );

  document.body.append(menu);
  const rect = menu.getBoundingClientRect();
  if (rect.right > window.innerWidth) menu.style.left = `${window.innerWidth - rect.width - 8}px`;
  if (rect.bottom > window.innerHeight) menu.style.top = `${window.innerHeight - rect.height - 8}px`;
  setTimeout(() => document.addEventListener("mousedown", close, { once: true }), 0);
}
