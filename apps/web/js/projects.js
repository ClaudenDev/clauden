import {
  $, el, clear, icon, timeAgo, bytes, toast, openModal, menuAt, confirmDialog,
  promptDialog, relTimeShort, uid,
} from "./util.js";
import { api, fileToBase64 } from "./api.js";
import { store } from "./store.js";

export function renderProjects(view, ctx) {
  const { state } = ctx;
  const body = el("div", { class: "screen-inner" });
  const page = el("div", { class: "page" },
    el("div", { class: "page-head" },
      el("div", null,
        el("h1", { class: "page-title", text: "Проекты" }),
        el("p", { class: "page-sub", text: "Инструкции и файлы, которые Clauden учитывает в каждом чате проекта" }),
      ),
      el("div", { class: "row" },
        el("button", { class: "icon-btn", title: "Поиск", onclick: () => ctx.openSearch(), html: icon("search") }),
        el("button", { class: "icon-btn", title: "Сортировать по дате", onclick: () => sortProjects(ctx), html: icon("sort") }),
        el("button", { class: "btn btn-secondary", onclick: () => createProject(ctx) }, el("span", { html: icon("plus") }), "Новый проект"),
      ),
    ),
    el("div", { id: "projects-grid" }),
  );
  body.append(page);
  view.append(body);

  const grid = $("#projects-grid");
  if (!state.projects.length) {
    grid.append(el("div", { class: "empty-block" },
      el("div", { html: icon("folder") }),
      el("h3", { text: "Проектов пока нет" }),
      el("p", { text: "Соберите знания и правила в одном месте — например, для работы, учёбы или клиента." }),
      el("button", { class: "btn btn-primary", onclick: () => createProject(ctx) }, "Создать проект"),
    ));
    return;
  }
  const cards = el("div", { class: "project-list" });
  for (const project of state.projects) {
    const chats = state.chats.filter((c) => c.projectId === project.id);
    cards.append(el("div", {
      class: "project-item",
      onclick: () => ctx.openProject(project.id),
    },
      el("span", { class: "project-item-icon", html: icon("folder") }),
      el("span", { class: "project-item-body" },
        el("span", { class: "project-item-title", text: project.title }),
        el("span", { class: "project-item-meta", text: relTimeShort(project.updatedAt) }),
      ),
      el("span", { class: "project-item-count", text: `${chats.length} ${plural(chats.length, "чат", "чата", "чатов")}` }),
    ));
  }
  grid.append(cards);
}

function sortProjects(ctx) {
  ctx.state.projects = ctx.state.projects.slice().sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  ctx.refreshSidebar();
  ctx.renderView();
}

export async function renderProject(view, ctx, id) {
  const { state } = ctx;
  const project = state.projects.find((p) => p.id === id) || await store.getProject(id);
  if (!project) {
    view.append(el("div", { class: "empty-block" }, el("h3", { text: "Проект не найден" }), el("button", { class: "btn btn-secondary", text: "К проектам", onclick: () => ctx.navigate("/projects") })));
    return;
  }
  project.files = project.files || [];
  const chats = state.chats.filter((c) => c.projectId === project.id);

  const top = el("div", { class: "row between", style: { marginBottom: "18px" } },
    el("button", { class: "btn btn-ghost", onclick: () => ctx.navigate("/projects") },
      el("span", { html: icon("arrowLeft") }), "Проекты"),
    el("div", { class: "row" },
      el("button", { class: "icon-btn", title: "Ещё", onclick: (e) => menuAt(e.currentTarget, [
        { icon: "edit", label: "Переименовать", onClick: () => renameProject(project, ctx) },
        { icon: "share", label: "Экспорт", onClick: () => exportProject(project, ctx) },
        "-",
        { icon: "trash", label: "Удалить проект", danger: true, onClick: () => removeProject(project, ctx) },
      ]), html: icon("more") }),
    ),
  );

  const body = el("div", { class: "screen-inner" });
  const page = el("div", { class: "page" });
  const head = el("div", { class: "page-head", style: { alignItems: "flex-start" } },
    el("div", { style: { minWidth: "0" } },
      el("h1", { class: "page-title", text: project.title }),
      el("div", { class: "row", style: { marginTop: "8px" } },
        el("span", { class: "badge", html: `${icon("lock")} Приватный` }),
        el("span", { class: "muted small", text: `создан ${new Date(project.createdAt || Date.now()).toLocaleDateString("ru-RU")}` }),
      ),
    ),
    el("button", { class: "btn btn-primary", onclick: () => ctx.newChat(project.id) }, el("span", { html: icon("plus") }), "Новый чат"),
  );
  page.append(top, head);

  const descInput = el("input", { class: "input", value: project.description || "", placeholder: "О чём проект? Одно предложение" });
  const instructions = el("textarea", { class: "textarea", rows: "7", placeholder: "Например: пиши на русском, используй наш глоссарий, не предлагай внешние сервисы…" });
  instructions.value = project.instructions || "";
  const saveBtn = el("button", { class: "btn btn-secondary btn-sm", text: "Сохранить" });
  const save = async () => {
    project.description = descInput.value.trim();
    project.instructions = instructions.value;
    project.updatedAt = Date.now();
    await store.putProject(project);
    api.backupProjects([project]).catch(() => {});
    syncProjects(ctx);
    toast("Проект сохранён");
  };
  saveBtn.addEventListener("click", save);
  instructions.addEventListener("blur", save);
  descInput.addEventListener("blur", save);

  const contextCard = el("div", { class: "card", style: { marginBottom: "14px" } },
    el("div", { class: "row between", style: { marginBottom: "10px" } },
      el("h2", { class: "section-title", style: { margin: "0" }, text: "Контекст проекта" }),
      saveBtn,
    ),
    el("div", { class: "field" }, el("label", { text: "Описание" }), descInput),
    el("div", { class: "field" }, el("label", { text: "Пользовательские инструкции" }), instructions,
      el("span", { class: "field-hint", text: "Попадают в системный промпт каждого чата проекта (до 6000 символов)." })),
  );
  page.append(contextCard);

  const fileInput = el("input", { type: "file", multiple: true, hidden: true, accept: ".pdf,.docx,.txt,.md,.csv,.json,.html" });
  const filesCard = el("div", { class: "card", style: { marginBottom: "14px" } },
    el("div", { class: "row between", style: { marginBottom: "10px" } },
      el("h2", { class: "section-title", style: { margin: "0" }, text: "Знания" }),
      el("button", { class: "btn btn-secondary btn-sm", onclick: () => fileInput.click() }, el("span", { html: icon("plus") }), "Добавить файл"),
    ),
    el("div", { id: "project-files" }),
    fileInput,
  );
  page.append(filesCard);

  const renderFiles = () => {
    const list = $("#project-files");
    clear(list);
    if (!project.files.length) {
      list.append(el("p", { class: "muted small", style: { margin: "4px 0" }, text: "Файлов пока нет. PDF, DOCX и текст извлекаются на сервере." }));
      return;
    }
    for (const file of project.files) {
      list.append(el("div", { class: "setting-row", style: { padding: "8px 0" } },
        el("span", { class: "list-row-icon", html: icon("file") }),
        el("span", { class: "setting-text" },
          el("div", { class: "setting-name", text: file.name }),
          el("div", { class: "setting-hint", text: `${bytes(file.size)} · ${String(file.text || "").length.toLocaleString("ru-RU")} символов` }),
        ),
        el("button", { class: "icon-btn", title: "Удалить", onclick: async () => {
          project.files = project.files.filter((f) => f.id !== file.id);
          project.updatedAt = Date.now();
          await store.putProject(project);
          api.backupProjects([project]).catch(() => {});
          renderFiles();
        }, html: icon("trash") }),
      ));
    }
  };

  fileInput.addEventListener("change", async () => {
    const files = [...fileInput.files];
    fileInput.value = "";
    for (const file of files) {
      if (file.size > 25 * 1024 * 1024) { toast(`«${file.name}» больше 25 МБ`, "error"); continue; }
      try {
        toast(`Читаю «${file.name}»…`);
        const base64 = await fileToBase64(file);
        const result = await api.extract(file.name, file.type || "", base64);
        project.files.push({
          id: uid("file"),
          name: file.name,
          mime: file.type || "",
          size: file.size,
          text: String(result.text || "").slice(0, 200000),
        });
        project.updatedAt = Date.now();
        await store.putProject(project);
        api.backupProjects([project]).catch(() => {});
        renderFiles();
        toast(`«${file.name}» добавлен`, "success");
      } catch (err) {
        toast(err.message || `Не удалось прочитать «${file.name}»`, "error");
      }
    }
  });

  const chatsCard = el("div", { class: "card" },
    el("div", { class: "row between", style: { marginBottom: "8px" } },
      el("h2", { class: "section-title", style: { margin: "0" }, text: "Чаты проекта" }),
      el("button", { class: "btn btn-primary btn-sm", onclick: () => ctx.newChat(project.id) }, el("span", { html: icon("plus") }), "Новый чат"),
    ),
    el("div", { id: "project-chats" }),
  );
  page.append(chatsCard);
  body.append(page);
  view.append(body);
  renderFiles();

  const chatList = $("#project-chats");
  if (!chats.length) {
    chatList.append(el("p", { class: "muted small", text: "Ещё нет чатов в этом проекте." }));
  } else {
    chats.forEach((chat) => {
      chatList.append(el("button", { class: "list-row", onclick: () => ctx.openChat(chat.id) },
        el("span", { class: "list-row-icon", html: icon("chat") }),
        el("span", { class: "list-row-text" },
          el("span", { class: "list-row-title", text: chat.title || "Новый чат" }),
          el("span", { class: "list-row-sub", text: (chat.messages || []).slice(-1)[0]?.content?.slice(0, 110) || "" }),
        ),
        el("span", { class: "list-row-meta", text: relTimeShort(chat.updatedAt) }),
      ));
    });
  }
}

function createProject(ctx) {
  const title = el("input", { class: "input", placeholder: "Название проекта" });
  const description = el("textarea", { class: "textarea", rows: "3", placeholder: "Короткое описание (необязательно)" });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Новый проект" })),
    el("div", { class: "modal-body" },
      el("div", { class: "field" }, el("label", { text: "Название" }), title),
      el("div", { class: "field" }, el("label", { text: "Описание" }), description),
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      el("button", { class: "btn btn-primary", text: "Создать", onclick: async (e) => {
        const name = title.value.trim();
        if (!name) {
          title.classList.add("invalid");
          title.focus();
          toast("Введите название проекта", "error");
          return;
        }
        title.classList.remove("invalid");
        const project = {
          id: uid("project"),
          title: name.slice(0, 120),
          description: description.value.trim().slice(0, 500),
          instructions: "",
          files: [],
          createdAt: Date.now(),
          updatedAt: Date.now(),
        };
        await store.putProject(project);
        api.backupProjects([project]).catch(() => {});
        syncProjects(ctx);
        modal.close();
        ctx.openProject(project.id);
      }}),
    ),
  ));
  setTimeout(() => title.focus(), 30);
}

function syncProjects(ctx) {
  store.allProjects().then((projects) => {
    ctx.state.projects = projects;
    ctx.refreshSidebar();
  });
}

async function renameProject(project, ctx) {
  const name = await promptDialog({ title: "Название проекта", value: project.title });
  if (!name) return;
  project.title = name.slice(0, 120);
  project.updatedAt = Date.now();
  await store.putProject(project);
  api.backupProjects([project]).catch(() => {});
  syncProjects(ctx);
  ctx.renderView();
}

async function removeProject(project, ctx) {
  const ok = await confirmDialog({ title: "Удалить проект?", text: `«${project.title}» и его файлы знаний будут удалены. Чаты останутся, но выйдут из проекта.`, confirm: "Удалить" });
  if (!ok) return;
  await store.deleteProject(project.id);
  api.deleteProject(project.id).catch(() => {});
  for (const chat of ctx.state.chats.filter((c) => c.projectId === project.id)) {
    chat.projectId = "";
    await store.putChat(chat);
  }
  ctx.state.chats = await store.allChats();
  syncProjects(ctx);
  ctx.navigate("/projects");
  toast("Проект удалён");
}

function exportProject(project, ctx) {
  const chats = ctx.state.chats.filter((c) => c.projectId === project.id);
  const lines = [`# ${project.title}`, "", project.description || "", "", "## Инструкции", "", project.instructions || "(нет)", "", "## Знания"];
  for (const file of project.files || []) lines.push(`### ${file.name}`, "", String(file.text || "").slice(0, 20000), "");
  lines.push("## Чаты");
  for (const chat of chats) {
    lines.push(`### ${chat.title}`, "");
    for (const message of chat.messages || []) {
      if (message.role === "user") lines.push("**Вы:**", message.content, "");
      else if (message.content) lines.push("**Clauden:**", message.content, "");
    }
  }
  const blob = new Blob([lines.join("\n")], { type: "text/markdown;charset=utf-8" });
  const a = el("a", { href: URL.createObjectURL(blob), download: `${project.title.replace(/[\\/:*?"<>|]+/g, "-")}.md` });
  document.body.append(a); a.click(); a.remove();
}

function plural(n, one, few, many) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
  return many;
}
