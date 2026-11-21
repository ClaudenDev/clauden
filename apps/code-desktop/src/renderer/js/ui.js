export function h(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "html") node.innerHTML = value;
    else if (key === "text") node.textContent = value;
    else if (key === "style" && typeof value === "object") Object.assign(node.style, value);
    else if (key === "dataset") Object.assign(node.dataset, value);
    else if (key.startsWith("on") && typeof value === "function") {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else node.setAttribute(key, value === true ? "" : value);
  }
  append(node, children);
  return node;
}

export function append(node, children) {
  for (const child of children.flat(4)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
}

export function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

const PATHS = {
  mark: '<path d="M12 3.2v17.6M4.4 7.6l15.2 8.8M4.4 16.4l15.2-8.8"/>',
  files: '<path d="M4 6.5A1.5 1.5 0 0 1 5.5 5h3.2l1.6 2H18a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 18 18H5.5A1.5 1.5 0 0 1 4 16.5z"/>',
  sessions:
    '<path d="M4 6h16M4 12h16M4 18h10"/><circle cx="19" cy="18" r="1.6" fill="currentColor" stroke="none"/>',
  diff: '<path d="M7 4v10M7 20v-2M4 7h6M17 20V10M17 4v2M14 17h6M12 9h-1M12 15h-1"/>',
  terminal: '<path d="M4 5h16v14H4z"/><path d="M7.5 10l2.2 2.2-2.2 2.2M12.5 14.5h4"/>',
  chat: '<path d="M20 12a7.5 7.5 0 0 1-7.5 7.5H6l-3 2v-9.5A7.5 7.5 0 0 1 10.5 4.5h2A7.5 7.5 0 0 1 20 12z"/>',
  settings:
    '<circle cx="12" cy="12" r="3"/><path d="M12 3v2.2M12 18.8V21M4.2 7.5l1.9 1.1M17.9 15.4l1.9 1.1M4.2 16.5l1.9-1.1M17.9 8.6l1.9-1.1"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.3 6.3"/><path d="M20 5v6h-6"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  chevron: '<path d="M9 6l6 6-6 6"/>',
  chevronDown: '<path d="M6 9l6 6 6-6"/>',
  folder:
    '<path d="M4 6.5A1.5 1.5 0 0 1 5.5 5h3.2l1.6 2H18a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 18 18H5.5A1.5 1.5 0 0 1 4 16.5z"/>',
  file: '<path d="M14 3H7a1.5 1.5 0 0 0-1.5 1.5v15A1.5 1.5 0 0 0 7 21h10a1.5 1.5 0 0 0 1.5-1.5V7.5z"/><path d="M14 3v4.5h4.5"/>',
  save: '<path d="M5 4h11l3 3v13H5z"/><path d="M8 4v6h7V4M8 20v-6h8v6"/>',
  link: '<path d="M10.5 13.5a3.5 3.5 0 0 0 5 0l2.5-2.5a3.5 3.5 0 0 0-5-5L11.8 7"/><path d="M13.5 10.5a3.5 3.5 0 0 0-5 0L6 13a3.5 3.5 0 0 0 5 5l1.2-1.2"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M15 9V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h3"/>',
  check: '<path d="M4 12.5l5 5L20 6.5"/>',
  alert: '<path d="M12 4l9 16H3z"/><path d="M12 10v4M12 17h.01"/>',
  trash: '<path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13"/>',
  edit: '<path d="M4 20h4l10-10-4-4L4 16z"/><path d="M14 6l4 4"/>',
  play: '<path d="M7 4l13 8-13 8z"/>',
  stop: '<rect x="6" y="6" width="12" height="12" rx="2"/>',
  send: '<path d="M5 12h13M12 5l7 7-7 7"/>',
  logout: '<path d="M15 5H6a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h9"/><path d="M17 8l4 4-4 4M21 12h-9"/>',
  minimize: '<path d="M6 12h12"/>',
  maximize: '<rect x="5.5" y="5.5" width="13" height="13" rx="1.5"/>',
  restore: '<rect x="4.5" y="7.5" width="11" height="11" rx="1.5"/><path d="M8 7.5V5.5h10v10h-2"/>',
  branch: '<circle cx="7" cy="6" r="2"/><circle cx="7" cy="18" r="2"/><circle cx="17" cy="9" r="2"/><path d="M7 8v8M9 6.6h4a4 4 0 0 1 4 4v3"/>',
  server: '<rect x="3.5" y="4" width="17" height="6" rx="1.6"/><rect x="3.5" y="14" width="17" height="6" rx="1.6"/><path d="M7 7h.01M7 17h.01"/>',
  laptop: '<rect x="4" y="5" width="16" height="10" rx="1.6"/><path d="M2.5 19h19"/>',
  folderPlus: '<path d="M4 6.5A1.5 1.5 0 0 1 5.5 5h3.2l1.6 2H18a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 18 18H5.5A1.5 1.5 0 0 1 4 16.5z"/><path d="M12 11v4M10 13h4"/>',
  clock: '<circle cx="12" cy="12" r="8"/><path d="M12 8v4.2l2.6 1.6"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4 4"/>',
  external: '<path d="M14 5h5v5M19 5l-8 8"/><path d="M18 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h4"/>',
  dots: '<circle cx="6" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="18" cy="12" r="1.6" fill="currentColor" stroke="none"/>',
  shield: '<path d="M12 3l7 3v6c0 4.2-2.9 7.4-7 9-4.1-1.6-7-4.8-7-9V6z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
  arrowUp: '<path d="M12 19V5M6 11l6-6 6 6"/>',
  arrowDown: '<path d="M12 5v14M6 13l6 6 6-6"/>',
  command: '<path d="M9 6.5A2.5 2.5 0 1 0 6.5 9H9zM15 17.5A2.5 2.5 0 1 0 17.5 15H15zM9 9h6v6H9zM9 15H6.5A2.5 2.5 0 1 0 9 17.5zM15 9h2.5A2.5 2.5 0 1 0 15 6.5z"/>',
};

export function icon(name, size = 15) {
  const path = PATHS[name] || PATHS.file;
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
}

export function iconNode(name, size = 15) {
  const span = h("span", { class: "ico", html: icon(name, size) });
  span.style.display = "flex";
  return span;
}

export function mascotNode(size = 16) {
  const badge = size <= 32;
  return h("img", {
    class: `mascot ${badge ? "mascot-badge" : "mascot-full"}`,
    src: badge ? "assets/mascot-badge.svg" : "assets/mascot-tight.svg",
    width: size,
    height: size,
    alt: "",
    draggable: "false",
  });
}

let toastHost = null;

export function toast(message, kind = "info", timeout = 2600) {
  if (!toastHost) {
    toastHost = h("div", { class: "toasts" });
    document.body.append(toastHost);
  }
  const node = h("div", { class: `toast ${kind === "error" ? "error" : ""}`, text: message });
  toastHost.append(node);
  setTimeout(() => {
    node.style.transition = "opacity .18s";
    node.style.opacity = "0";
    setTimeout(() => node.remove(), 200);
  }, timeout);
}

export function modal({ title, subtitle, body, actions, width, onClose }) {
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    scrim.remove();
    document.removeEventListener("keydown", onKey);
    onClose?.();
  };
  const footer = h("div", { class: "modal-actions" });
  const box = h(
    "div",
    { class: "modal", style: width ? { width: `${width}px` } : {} },
    title ? h("h2", { text: title }) : null,
    subtitle ? h("p", { class: "modal-sub", text: subtitle }) : null,
    body,
    footer,
  );
  const scrim = h(
    "div",
    {
      class: "scrim",
      onclick: (event) => {
        if (event.target === scrim) close();
      },
    },
    box,
  );

  for (const action of actions || []) {
    footer.append(
      h("button", {
        class: `btn ${action.kind === "primary" ? "btn-primary" : action.kind === "ghost" ? "btn-ghost" : "btn-secondary"}`,
        text: action.label,
        onclick: () => action.onClick?.(close),
      }),
    );
  }

  const onKey = (event) => {
    if (event.key === "Escape") {
      close();
      document.removeEventListener("keydown", onKey);
    }
  };
  document.addEventListener("keydown", onKey);
  document.body.append(scrim);
  return { close, el: box, footer };
}

export function confirmDialog(title, text, confirmLabel = "Удалить") {
  return new Promise((resolve) => {
    const box = modal({
      title,
      subtitle: text,
      body: h("div"),
      actions: [
        { label: "Отмена", kind: "ghost", onClick: (close) => (close(), resolve(false)) },
        { label: confirmLabel, kind: "primary", onClick: (close) => (close(), resolve(true)) },
      ],
    });
    box.el.querySelector(".modal-actions .btn-primary").style.background = "#c0392b";
  });
}

export function relativeTime(ms) {
  if (!ms) return "";
  const diff = Date.now() - Number(ms);
  if (diff < 60_000) return "только что";
  if (diff < 3_600_000) return `${Math.round(diff / 60_000)} мин`;
  if (diff < 86_400_000) return `${Math.round(diff / 3_600_000)} ч`;
  if (diff < 7 * 86_400_000) return `${Math.round(diff / 86_400_000)} дн`;
  const date = new Date(Number(ms));
  return `${date.getDate()} ${MONTHS[date.getMonth()]}`;
}

export function groupLabel(ms) {
  const date = new Date(Number(ms) || Date.now());
  const today = new Date();
  const yesterday = new Date(Date.now() - 86_400_000);
  const same = (a, b) =>
    a.getDate() === b.getDate() && a.getMonth() === b.getMonth() && a.getFullYear() === b.getFullYear();
  if (same(date, today)) return "Сегодня";
  if (same(date, yesterday)) return "Вчера";
  if (Date.now() - Number(ms) < 7 * 86_400_000) return "На этой неделе";
  return "Раньше";
}

const MONTHS = [
  "янв",
  "фев",
  "мар",
  "апр",
  "мая",
  "июн",
  "июл",
  "авг",
  "сен",
  "окт",
  "ноя",
  "дек",
];

export function debounce(fn, wait = 150) {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
}

export function throttle(fn, wait = 120) {
  let last = 0;
  let timer = null;
  return (...args) => {
    const now = Date.now();
    const left = wait - (now - last);
    if (left <= 0) {
      last = now;
      fn(...args);
    } else if (!timer) {
      timer = setTimeout(() => {
        timer = null;
        last = Date.now();
        fn(...args);
      }, left);
    }
  };
}

export async function rpc(promise) {
  const result = await promise;
  if (!result) throw new Error("Нет ответа от приложения");
  if (result.ok) return result.data;
  const error = new Error(result.error?.message || "Ошибка");
  error.status = result.error?.status || 0;
  error.code = result.error?.code || "";
  throw error;
}

export function bytes(size) {
  if (!size && size !== 0) return "";
  if (size < 1024) return `${size} Б`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} КБ`;
  return `${(size / 1024 / 1024).toFixed(1)} МБ`;
}
