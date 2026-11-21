import { $, el, clear, icon, bytes, toast, openModal, copyText, downloadText, relTimeShort, go } from "./util.js";
import { store } from "./store.js";
import { renderMarkdown, enhanceMarkdown } from "./markdown.js";

const EXT_LANG = {
  html: "html",
  htm: "html",
  svg: "svg",
  md: "markdown",
  markdown: "markdown",
  txt: "text",
  json: "json",
  csv: "csv",
  py: "python",
  js: "javascript",
  ts: "typescript",
  css: "css",
};

export function collectArtifacts(state) {
  const items = [];
  const artifactTags = /<(?:ant)?artifact\b([^>]*)>([\s\S]*?)<\/(?:ant)?artifact>/gi;
  const attr = /([A-Za-z_][A-Za-z0-9_-]*)\s*=\s*"([^"]*)"/g;
  for (const chat of state.chats) {
    for (const message of chat.messages || []) {
      if (message.role !== "assistant") continue;
      for (const file of message.files || []) {
        if (!file?.name) continue;
        const ext = file.name.split(".").pop().toLowerCase();
        items.push({
          id: `file:${file.id || file.name}:${message.id}`,
          name: file.name,
          kind: "file",
          lang: EXT_LANG[ext] || "",
          mime: file.mime || "",
          url: file.url || "",
          size: file.size || 0,
          chatId: chat.id,
          chatTitle: chat.title,
          ts: message.ts || chat.updatedAt,
        });
      }
      const text = String(message.content || "");
      let tagMatch;
      artifactTags.lastIndex = 0;
      while ((tagMatch = artifactTags.exec(text)) !== null) {
        const attributes = {};
        let attrMatch;
        attr.lastIndex = 0;
        while ((attrMatch = attr.exec(tagMatch[1])) !== null) {
          attributes[attrMatch[1].toLowerCase()] = attrMatch[2];
        }
        const content = tagMatch[2].replace(/^\n+|\n+$/g, "");
        if (!content.trim()) continue;
        const title = attributes.title || attributes.identifier || "Артефакт";
        const type = attributes.type || "text/plain";
        items.push({
          id: `artifact:${chat.id}:${attributes.identifier || title}`,
          identifier: attributes.identifier || title,
          name: title,
          kind: "code",
          lang: artifactLang(type, title),
          code: content,
          chatId: chat.id,
          chatTitle: chat.title,
          ts: message.ts || chat.updatedAt,
        });
      }
      const blocks = extractBlocks(text);
      blocks.forEach((block, index) => {
        const interesting = ["html", "svg", "markdown", "md"].includes(block.lang) || block.code.length > 500;
        if (!interesting) return;
        items.push({
          id: `code:${chat.id}:${message.id}:${index}`,
          name: artifactName(block.lang, chat.title, index),
          kind: "code",
          lang: block.lang || "text",
          code: block.code,
          chatId: chat.id,
          chatTitle: chat.title,
          ts: message.ts || chat.updatedAt,
        });
      });
    }
  }

  const latest = new Map();
  for (const item of items) {
    const key = item.id.startsWith("artifact:") || item.kind === "code"
      ? `code:${item.chatId || ""}:${String(item.name || "").toLowerCase()}`
      : item.id;
    const previous = latest.get(key);
    if (!previous || (item.ts || 0) >= (previous.ts || 0)) latest.set(key, item);
  }
  return [...latest.values()].sort((a, b) => (b.ts || 0) - (a.ts || 0));
}

function extractBlocks(text) {
  const blocks = [];
  const re = /```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```/g;
  let match;
  while ((match = re.exec(String(text || ""))) !== null) {
    blocks.push({ lang: (match[1] || "").toLowerCase(), code: match[2] });
  }
  return blocks;
}

function artifactLang(type, name) {
  const value = String(type || "").toLowerCase();
  const title = String(name || "").toLowerCase();
  if (value.includes("html") || title.endsWith(".html")) return "html";
  if (value.includes("svg") || title.endsWith(".svg")) return "svg";
  if (value.includes("markdown") || title.endsWith(".md")) return "markdown";
  if (value.includes("python") || title.endsWith(".py")) return "python";
  if (value.includes("javascript") || title.endsWith(".js") || title.endsWith(".ts")) return "javascript";
  if (value.includes("css") || title.endsWith(".css")) return "css";
  if (value.includes("json") || title.endsWith(".json")) return "json";
  return "text";
}

function artifactName(lang, title, index) {
  const base = (title || "artifact").replace(/[\\/:*?"<>|]+/g, "-").slice(0, 40).trim() || "artifact";
  const ext = lang === "markdown" ? "md" : lang || "txt";
  return `${base}${index ? `-${index + 1}` : ""}.${ext}`;
}

export async function renderArtifacts(view, ctx) {
  const { state } = ctx;
  const body = el("div", { class: "screen-inner" });
  const saved = await store.allArtifacts();
  const items = [...saved, ...collectArtifacts(state)];

  let filter = "all";
  const page = el("div", { class: "page" });
  const head = el("div", { class: "page-head" },
    el("div", null,
      el("h1", { class: "page-title", text: "Артефакты" }),
      el("p", { class: "page-sub", text: "Документы, страницы и код из ваших чатов" }),
    ),
    el("div", { class: "row" },
      el("button", { class: "icon-btn", title: "Поиск", onclick: () => ctx.openSearch(), html: icon("search") }),
      el("button", { class: "icon-btn", title: "Фильтр", onclick: () => { filter = filter === "all" ? "mine" : "all"; draw(); }, html: icon("filter") }),
      el("button", { class: "icon-btn", title: "Сортировать", onclick: () => { items.reverse(); draw(); }, html: icon("sort") }),
      el("button", { class: "btn btn-secondary", onclick: () => ctx.newChat() }, el("span", { html: icon("plus") }), "Создать"),
    ),
  );
  const tabs = el("div", { class: "segmented", style: { marginBottom: "20px" } },
    el("button", { class: "active", text: "Все", onclick: (e) => setTab("all", e) }),
    el("button", { text: "Мои", onclick: (e) => setTab("mine", e) }),
    el("button", { text: "Из чатов", onclick: (e) => setTab("chats", e) }),
  );

  function setTab(next, event) {
    filter = next;
    tabs.querySelectorAll("button").forEach((b) => b.classList.toggle("active", b === event.currentTarget));
    draw();
  }

  const make = el("div", { class: "make-grid" },
    makeTile("blue", "file", "Документ", "Создать документ или отчёт", () => startWith("Создай документ: ")),
    makeTile("red", "layout", "Слайды", "Собрать презентацию", () => startWith("Собери презентацию: ")),
    makeTile("purple", "palette", "Дизайн", "Сверстать страницу или интерфейс", () => startWith("Сверстай дизайн: ")),
  );

  const banner = el("div", { class: "artifact-banner" },
    el("div", null,
      el("div", { class: "ab-title", text: "Артефакты Clauden" }),
      el("div", { class: "ab-sub", text: "Документы, слайды и страницы создаются прямо в чате и появляются здесь." }),
    ),
    el("button", { class: "btn btn-secondary", onclick: () => ctx.newChat(), text: "Создать в чате" }),
  );

  const grid = el("div", { class: "cards-grid" });
  page.append(head, tabs, banner, make, grid);
  body.append(page);
  view.append(body);

  function startWith(prefix) {
    sessionStorage.setItem("clauden.prefill", prefix);
    ctx.newChat();
  }

  function draw() {
    clear(grid);
    const visible = filter === "mine"
      ? items.filter((item) => saved.some((s) => s.id === item.id))
      : filter === "chats"
        ? items.filter((item) => item.chatId)
        : items;
    if (!visible.length) {
      grid.append(el("div", { class: "empty-block" },
        el("div", { html: icon("layers") }),
        el("h3", { text: "Пока нет артефактов" }),
        el("p", { text: "Попросите Clauden сделать страницу, документ или график — результат появится здесь." }),
        el("button", { class: "btn btn-primary", onclick: () => ctx.newChat() }, "Создать артефакт"),
      ));
      return;
    }
    for (const item of visible) grid.append(artifactCard(item, ctx));
  }

  draw();
}

function makeTile(color, iconName, title, sub, onClick) {
  return el("button", { class: `make-tile ${color}`, onclick: onClick },
    el("span", { html: icon(iconName) }),
    el("span", { class: "mt-title", text: title }),
    el("span", { class: "mt-sub", text: sub }),
  );
}

function artifactCard(item, ctx) {
  const card = el("div", { class: "card artifact-card", onclick: () => openArtifact(item) });
  const preview = el("div", { class: "artifact-preview" });
  if (item.kind === "file" && item.url && /^image\//.test(item.mime)) {
    preview.append(el("img", { src: item.url, style: { width: "100%", height: "100%", objectFit: "cover" }, loading: "lazy", alt: "" }));
  } else if (isPreviewable(item)) {
    preview.append(el("iframe", { sandbox: "allow-scripts", srcdoc: previewDoc(item), title: item.name, loading: "lazy" }));
  } else {
    preview.append(el("div", { class: "preview-fallback", html: icon(item.kind === "code" ? "code" : "file") }));
  }
  const body = el("div", { class: "artifact-body" },
    el("div", { class: "artifact-row" },
      el("span", { class: "artifact-copy", title: "Скопировать", onclick: (e) => {
        e.stopPropagation();
        const text = item.kind === "code" ? item.code : "";
        if (!text) { ctx.toast("Файл можно скачать из окна артефакта", "error"); return; }
        copyText(text).then(() => ctx.toast("Скопировано"));
      }, html: icon("copy") }),
      el("span", { class: "artifact-name", text: item.name }),
    ),
    el("div", { class: "artifact-meta" },
      el("span", { text: item.chatTitle || "Артефакт" }),
      el("span", { text: "·" }),
      el("span", { text: editedLabel(item.ts) }),
    ),
  );
  card.append(preview, body);
  return card;
}

function editedLabel(ts) {
  const date = new Date(Number(ts) || Date.now());
  return `Изменён ${date.toLocaleDateString("ru-RU", { day: "numeric", month: "short" })}`;
}

function isPreviewable(item) {
  if (item.kind === "code") return ["html", "svg", "markdown", "md"].includes(item.lang);
  return ["html", "htm", "svg", "md", "markdown"].includes(item.lang);
}

function previewDoc(item) {
  if (item.lang === "svg") {
    return `<!doctype html><html><head><style>html,body{margin:0;height:100%;display:grid;place-items:center;background:#fff}svg{max-width:92%;max-height:92%}</style></head><body>${item.code || `<img src="${item.url}" style="max-width:92%">`}</body></html>`;
  }
  if (item.lang === "markdown" || item.lang === "md") {
    const html = renderMarkdown(item.code || "");
    return `<!doctype html><html><head><meta charset="utf-8"><style>body{margin:0;padding:18px;font:15px/1.55 Georgia,serif;color:#191919;background:#fff}pre{background:#f0efec;padding:8px;border-radius:6px;overflow:auto}code{font-family:ui-monospace,monospace;font-size:12px}img{max-width:100%}</style></head><body>${html}</body></html>`;
  }
  return item.code || `<!doctype html><html><body style="margin:0"><iframe src="${item.url}" style="border:0;width:100%;height:100%"></iframe></body></html>`;
}

export function openArtifact(item, options = {}) {
  if (!item) return;
  let tab = options.tab || (isPreviewable(item) ? "preview" : "code");
  const body = el("div", { class: "artifact-view" });
  const toolbar = el("div", { class: "artifact-toolbar" },
    el("span", { style: { width: "18px", height: "18px", color: "var(--clay)" }, html: icon(item.kind === "code" ? "code" : "file") }),
    el("span", { class: "topbar-title", text: item.name, style: { flex: "1" } }),
    el("div", { class: "segmented" },
      el("button", { class: "active", text: "Превью", onclick: () => setTab("preview") }),
      el("button", { text: "Код", onclick: () => setTab("code") }),
    ),
    el("button", { class: "icon-btn", title: "Скопировать", onclick: async () => {
      const text = item.kind === "code" ? item.code : await fetchText(item);
      await copyText(text);
      toast("Скопировано");
    }, html: icon("copy") }),
    item.url && /^https?:/i.test(item.url) && window.claudenDesktop?.app?.openBrowser
      ? el("button", { class: "icon-btn", title: "Открыть в браузере Clauden", onclick: () => window.claudenDesktop.app.openBrowser(item.url), html: icon("globe") })
      : null,
    el("button", { class: "icon-btn", title: "Скачать", onclick: () => downloadArtifact(item), html: icon("download") }),
    el("button", { class: "icon-btn", title: "Закрыть", onclick: () => modal.close(), html: icon("close") }),
  );
  const content = el("div", { class: "artifact-frame-wrap" });
  const askInput = el("input", { class: "input", placeholder: "Что изменить в артефакте?" });
  const ask = el("div", { class: "artifact-ask" },
    askInput,
    el("button", { class: "btn btn-primary", text: "Спросить", onclick: () => {
      const text = askInput.value.trim();
      if (!text) { askInput.focus(); return; }
      const prompt = `Обнови артефакт «${item.name}»: ${text}\n\n` +
        `Не создавай новый артефакт. Верни обновлённую версию целиком в теге ` +
        `<artifact identifier="${item.identifier || item.name}" title="${item.name}">…</artifact> ` +
        `с тем же идентификатором, названием и типом.\n\n` +
        `${item.kind === "code" ? "```" + (item.lang || "") + "\n" + item.code + "\n```" : ""}`;
      sessionStorage.setItem("clauden.prefill", prompt);
      modal.close();
      go("/chat");
    }}),
  );
  body.append(toolbar, content, ask);

  function setTab(next) {
    tab = next;
    toolbar.querySelectorAll(".segmented button").forEach((b) => b.classList.toggle("active", b.textContent === (next === "preview" ? "Превью" : "Код")));
    renderContent();
  }

  async function renderContent() {
    clear(content);
    if (tab === "preview") {
      content.className = "artifact-frame-wrap";
      if (item.kind === "file" && item.url && /^image\//.test(item.mime)) {
        content.append(el("img", { src: item.url, style: { maxWidth: "100%", maxHeight: "100%", display: "block", margin: "auto" }, alt: "" }));
      } else if (isPreviewable(item)) {
        content.append(el("iframe", { sandbox: "allow-scripts", srcdoc: previewDoc(item), title: item.name }));
      } else if (item.kind === "file" && item.url) {
        content.append(el("iframe", { src: item.url, title: item.name }));
      } else {
        content.className = "artifact-code";
        content.append(el("div", { class: "empty-block", text: "Предпросмотр недоступен — откройте вкладку «Код»." }));
      }
    } else {
      content.className = "artifact-code";
      const text = item.kind === "code" ? item.code : (await fetchText(item)) || "";
      content.append(el("pre", null, el("code", { text })));
    }
  }

  const modal = openModal(body, { full: true });
  renderContent();
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

async function downloadArtifact(item) {
  if (item.kind === "code") {
    downloadText(item.code, item.name);
    return;
  }
  if (item.url) {
    const a = el("a", { href: item.url, download: item.name, target: "_blank", rel: "noopener" });
    document.body.append(a); a.click(); a.remove();
    return;
  }
  const text = await fetchText(item);
  downloadText(text, item.name);
}
