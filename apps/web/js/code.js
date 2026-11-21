import {
  $, el, clear, icon, timeAgo, bytes, toast, openModal, menuAt, confirmDialog,
  promptDialog, uid, relTimeShort, downloadText, copyText,
} from "./util.js";
import { codeApi, streamCodeSession, remoteApi, ApiError, isDesktopApp, fileToBase64 } from "./api.js";
import { renderMarkdown, enhanceMarkdown } from "./markdown.js";

let cache = { repos: [], github: null, sessions: [], githubRepos: [], models: [] };
let streamAbort = null;
let activeSession = null;

const runningJobs = new Set();

function isLocalSession(session) {
  return session?.source === "ssh" && Boolean(session.remote?.local);
}

async function runLocalJob(job, sessionId, getSession) {
  if (!job?.id || (job.status && job.status !== "queued")) return;
  const bridge = window.claudenDesktop?.local;
  const session = getSession();
  const root = isLocalSession(session) ? session.remote.root : "";
  if (!bridge?.job || !root) return;
  const key = `${sessionId}:${job.id}`;
  if (runningJobs.has(key)) return;
  runningJobs.add(key);
  if (runningJobs.size > 500) runningJobs.delete(runningJobs.values().next().value);
  try {
    const result = await bridge.job(root, sessionId, job);
    if (!result?.ok) {
      throw new Error(result?.error?.message || "Не удалось выполнить задачу");
    }
    const data = result.data || {};
    await codeApi.jobResult(sessionId, job.id, {
      rejected: Boolean(data.rejected),
      stdout: data.stdout || "",
      stderr: data.stderr || "",
      code: Number.isFinite(Number(data.code)) ? Number(data.code) : 0,
      error: data.error || "",
    });
  } catch (err) {

    runningJobs.delete(key);
    toast(err.message || "Не удалось выполнить команду на этом ПК", "error");
  }
}

export async function renderCode(view, ctx, sessionId) {
  const user = ctx.state.user;
  if (!user?.codeAccess) {
    renderPaywall(view, ctx);
    return;
  }
  const layout = el("div", { class: "code-layout" });
  const side = el("div", { class: "code-side" });
  const main = el("div", { class: "code-main" });
  layout.append(side, main);
  view.append(layout);
  const shellTitle = document.querySelector("#topbar-title");
  if (shellTitle) {
    shellTitle.textContent = "Code";
    document.querySelector("#topbar-actions")?.prepend(
      el("button", { class: "btn btn-secondary btn-sm", onclick: () => newSession(ctx), text: "Новая сессия" }),
    );
  }

  side.append(el("div", { class: "code-side-head" },
    el("div", { class: "row" },
      el("span", { class: "setting-name", style: { flex: "1" }, text: "Сессии" }),
      el("button", { class: "icon-btn", title: "Обновить", onclick: () => loadAndDraw(), html: icon("refresh") }),
    ),
    el("button", { class: "btn btn-primary btn-sm", onclick: () => newSession(ctx), text: "Новая сессия" }),
  ));
  const sideScroll = el("div", { class: "code-side-scroll" });
  side.append(sideScroll);

  const drawSide = () => {
    clear(sideScroll);
    if (!cache.sessions.length) {
      sideScroll.append(el("p", { class: "muted small", style: { padding: "10px" }, text: "Пока нет сессий. Создайте первую — агент склонирует репозиторий и начнёт работу." }));
      return;
    }
    for (const session of cache.sessions) {
      sideScroll.append(el("div", {
        class: `code-session-card ${sessionId === session.id ? "active" : ""}`,
        onclick: () => ctx.navigate(`/code/${session.id}`),
      },
        el("div", { class: "code-session-title", text: session.title || session.repoName || "Сессия" }),
        el("div", { class: "code-session-sub", text: `${session.repoName || session.repo || ""} · ${statusLabel(session.status)}` }),
        el("div", { class: "code-session-sub", text: relTimeShort(session.updatedAt) }),
      ));
    }
  };

  const loadAndDraw = async () => {
    try {
      const [repos, github, sessions] = await Promise.all([
        codeApi.repos().catch(() => ({ data: [] })),
        codeApi.github().catch(() => ({ data: null })),
        codeApi.sessions().catch(() => ({ data: [] })),
      ]);
      cache.repos = repos.data || [];
      cache.github = github.data || null;
      cache.sessions = sessions.data || [];
      drawSide();
    } catch (err) {
      sideScroll.append(el("div", { class: "error-box", text: err.message || "Не удалось загрузить" }));
    }
  };

  drawSide();
  await loadAndDraw();

  if (!sessionId) {
    renderWelcome(main, ctx);
    return;
  }
  await openSession(main, ctx, sessionId, loadAndDraw);
}

function renderPaywall(view, ctx) {
  const body = el("div", { class: "screen-inner" });
  body.append(el("div", { class: "empty-block", style: { paddingTop: "12vh" } },
    el("div", { html: icon("code") }),
    el("h3", { text: "Code доступен по подписке" }),
    el("p", { text: "Агент в облаке: клонирует репозиторий, правит код, запускает проверки и делает коммиты. Входит в Pro, Max и Ultra." }),
    el("button", { class: "btn btn-primary", text: "Оформить подписку", onclick: () => ctx.openSettings("plans") }),
  ));
  view.append(body);
}

function renderWelcome(main, ctx) {
  clear(main);
  main.append(el("div", { class: "screen-inner", style: { padding: "24px" } },
    el("div", { style: { maxWidth: "680px", margin: "0 auto" } },
      el("h1", { class: "page-title", text: "Code" }),
      el("p", { class: "page-sub", text: isDesktopApp() && window.claudenDesktop?.local
        ? "Откройте папку проекта — агент будет читать и менять файлы прямо на этом компьютере."
        : "Агент работает в клоне репозитория: читает код, правит файлы, запускает тесты и коммитит изменения." }),
      el("div", { class: "cards-grid", style: { marginTop: "22px" } },
        isDesktopApp() && window.claudenDesktop?.local
          ? el("div", { class: "card clickable", onclick: () => openDesktopFolder(ctx) },
              el("div", { class: "card-icon", html: icon("folder") }),
              el("h3", { class: "card-title", text: "Открыть папку на этом ПК" }),
              el("p", { class: "card-sub", text: "Агент работает с файлами на вашем компьютере: создаёт, правит и запускает команды" }),
            )
          : null,
        el("div", { class: "card clickable", onclick: () => newSession(ctx) },
          el("div", { class: "card-icon", html: icon("plus") }),
          el("h3", { class: "card-title", text: isDesktopApp() ? "Сессия на сервере" : "Новая сессия" }),
          el("p", { class: "card-sub", text: "Репозиторий GitHub или пустая папка — на выбор" }),
        ),
        el("div", { class: "card clickable", onclick: () => openConnectRemote(ctx) },
          el("div", { class: "card-icon", html: icon("link") }),
          el("h3", { class: "card-title", text: "Remote Control" }),
          el("p", { class: "card-sub", text: "Открыть сессию по ссылке с другого устройства" }),
        ),
        el("div", { class: "card clickable", onclick: () => connectGithub(ctx) },
          el("div", { class: "card-icon", html: icon("github") }),
          el("h3", { class: "card-title", text: cache.github?.connected ? `GitHub: ${cache.github.login}` : "Подключить GitHub" }),
          el("p", { class: "card-sub", text: cache.github?.connected ? "Можно брать любые ваши репозитории" : "Токен с доступом к репозиториям" }),
        ),
      ),
      cache.repos.length ? el("div", null,
        el("h2", { class: "section-title", text: "Репозитории сервера" }),
        el("div", { class: "list" }, ...cache.repos.map((repo) => el("div", { class: "list-row" },
          el("span", { class: "list-row-icon", html: icon("folder") }),
          el("span", { class: "list-row-text" },
            el("span", { class: "list-row-title", text: repo.name }),
            el("span", { class: "list-row-sub", text: repo.description || repo.defaultBranch || "" }),
          ),
        ))),
      ) : null,
      el("p", { class: "field-hint", style: { marginTop: "20px" }, text: isDesktopApp() ? "В приложении Clauden можно открыть папку прямо на этом компьютере или начать с пустой сессии. На сервере Code работает в клоне репозитория." : "Code работает на сервере Clauden. Сессию можно начать без репозитория — с пустой папки." }),
    ),
  ));
}

async function openSession(main, ctx, id, reloadList) {
  clear(main);
  main.append(el("div", { class: "loading-block" }, el("span", { class: "spinner" }), "Открываю сессию…"));
  let session;
  try {
    const result = await codeApi.session(id);
    session = result.data;
  } catch (err) {
    clear(main);
    main.append(el("div", { class: "error-box", style: { margin: "20px" }, text: err.message || "Сессия не найдена" }));
    return;
  }
  activeSession = session;

  clear(main);
  const modelBtn = el("button", {
    class: "btn btn-secondary btn-sm",
    title: "Модель сессии",
    text: modelLabel(session.modelAlias || "claude-sonnet-5-5"),
  });
  const usageChip = el("span", { class: "badge", title: "Токены и контекст", text: "…" });
  const head = el("div", { class: "topbar bordered", style: { flex: "none" } },
    el("button", { class: "icon-btn mobile-only", onclick: () => ctx.navigate("/code"), html: icon("arrowLeft") }),
    el("div", { style: { minWidth: "0", flex: "1" } },
      el("div", { class: "topbar-title", text: session.title || session.repoName || "Сессия" }),
      el("div", { class: "code-status", id: "code-status" },
        el("span", { class: "dot" }),
        el("span", { text: `${statusLabel(session.status)} · ${session.repoName || session.repo || ""}${session.branch ? ` · ${session.branch}` : ""}` }),
      ),
    ),
    usageChip,
    modelBtn,
    el("button", { class: "icon-btn", title: "Команды (/compact, /undo, /model)", onclick: (e) => openCommandsMenu(e.currentTarget, session, ctx, runCommandText), html: icon("terminal") }),
    el("button", { class: "icon-btn", title: "Remote Control", onclick: () => openRemoteSheet(session, ctx), html: icon("share") }),
    el("button", { class: "icon-btn", title: "Ещё", onclick: (e) => sessionMenu(e.currentTarget, ctx, session, reloadList), html: icon("more") }),
  );
  const messages = el("div", { class: "code-messages" });
  const waiting = el("div", { id: "code-waiting" });
  const composer = el("div", { class: "code-composer" });
  const textarea = el("textarea", { rows: "1", placeholder: session.status === "working" ? "Агент работает…" : "Что сделать дальше?" });
  const sendBtn = el("button", { class: "btn btn-primary btn-sm", text: "Отправить" });
  const stopBtn = el("button", { class: "btn btn-secondary btn-sm", text: "Стоп", hidden: session.status !== "working" });
  const attachRow = el("div", { class: "composer-attach", hidden: true });
  const fileInput = el("input", {
    type: "file",
    multiple: true,
    hidden: true,
    accept: "image/*,.pdf,.docx,.txt,.md,.csv,.json,.html,.svg,.zip,.tar,.gz,.7z,.rar,.xlsx,.pptx,.log,.yml,.yaml,.ts,.tsx,.js,.jsx,.py,.go,.rs",
  });
  const attachBtn = el("button", {
    class: "btn btn-ghost btn-sm",
    title: session.source === "ssh" ? "Прикрепить файлы на сервер" : "Прикрепить файлы",
    text: "+ файл",
  });
  const attachments = [];
  const status = head.querySelector("#code-status");
  composer.append(attachRow, textarea, el("div", { class: "code-composer-bar" },
    el("span", { class: "muted small", text: `${session.messages?.length || 0} сообщений` }),
    el("span", { class: "spacer" }),
    attachBtn, stopBtn, sendBtn,
  ));
  composer.append(fileInput);
  main.append(head);

  if (isDesktopApp() && isLocalSession(session) && !window.claudenDesktop?.local?.job) {
    main.append(el("div", { class: "error-box", style: { margin: "10px 16px 0", flex: "none" },
      text: "Обновите приложение Clauden: эта версия не выполняет команды агента на компьютере. Скачайте свежую сборку на clauden.best/downloads." }));
  }

  if (isDesktopApp() && window.claudenDesktop?.local && !isLocalSession(session)) {
    main.append(el("div", { class: "notice-box", style: { margin: "10px 16px 0", flex: "none" } },
      el("div", { class: "row between" },
        el("div", { style: { minWidth: "0" } },
          el("div", { class: "setting-name", text: "Это облачная сессия" }),
          el("p", { class: "small", style: { margin: "4px 0 0" }, text: "Агент работает с копией репозитория на сервере. Чтобы он видел файлы на этом компьютере и мог их менять, откройте папку проекта." }),
        ),
        el("button", { class: "btn btn-primary btn-sm", text: "Открыть папку", onclick: () => openDesktopFolder(ctx) }),
      ),
    ));
  }
  main.append(messages, waiting, composer);

  const render = () => {
    clear(messages);
    for (const message of session.messages || []) messages.append(messageNode(message));
    renderWaiting();
    updateStatus();
    scrollBottom();
  };

  const renderWaiting = () => {
    clear(waiting);
    if (session.error) {
      waiting.append(el("div", { class: "error-box", text: friendlyCodeError(session.error) }));
    }
    for (const form of session.forms || []) {
      if (!isTrivialForm(form)) waiting.append(formCard(session, form, ctx));
    }
    for (const permission of session.permissions || []) waiting.append(permissionCard(session, permission, ctx));
    if (session.todos?.length) {
      const list = el("div", { class: "notice-box" });
      list.append(el("div", { class: "setting-name", text: "План" }));
      for (const todo of session.todos) list.append(el("div", { class: "small", text: `${todo.status === "completed" ? "✓" : "•"} ${todo.content || todo.title || ""}` }));
      waiting.append(list);
    }
  };

  const updateStatus = () => {
    status.className = `code-status ${session.status === "working" ? "working" : session.status === "failed" ? "failed" : ""}`;
    const label = session.status === "waiting_device" && isLocalSession(session)
      ? "выполняю на вашем ПК"
      : statusLabel(session.status);
    status.lastElementChild.textContent = `${label} · ${session.repoName || session.repo || ""}${session.branch ? ` · ${session.branch}` : ""}`;
    stopBtn.hidden = session.status !== "working";
    textarea.placeholder = session.status === "working" ? "Агент работает…" : "Что сделать дальше?";
    textarea.disabled = session.status === "working";
    sendBtn.disabled = session.status === "working";
  };

  const scrollBottom = () => { messages.scrollTop = messages.scrollHeight; };

  let usageInfo = null;
  const refreshUsage = async () => {
    if (!document.contains(head)) {
      clearInterval(usageTimer);
      return;
    }
    try {
      const { data } = await codeApi.usage(id);
      usageInfo = data || null;
      drawUsage();
    } catch {
      usageChip.textContent = "usage —";
    }
  };
  const drawUsage = () => {
    const tokens = usageInfo?.tokens || null;

    const contextTokens = Number(usageInfo?.contextTokens || 0);
    const sessionTokens = tokens
      ? Number(tokens.input || 0) +
        Number(tokens.output || 0) +
        Number(tokens.reasoning || 0) +
        Number(tokens.cache?.read || 0) +
        Number(tokens.cache?.write || 0)
      : 0;
    const models = cache.models || [];
    const model = models.find((item) => item.id === (session.modelAlias || session.model?.replace(/^clauden\//, "")));
    const limit = Number(model?.context || 0);
    const cost = Number(usageInfo?.cost || 0);
    const parts = [];
    if (contextTokens) parts.push(`${formatTokens(contextTokens)} токенов`);
    if (limit) parts.push(`контекст ${Math.min(100, Math.round((contextTokens / limit) * 100))}%`);
    if (cost > 0) parts.push(`$${cost.toFixed(2)}`);
    usageChip.textContent = parts.join(" · ") || "0 токенов";
    usageChip.title = [
      contextTokens
        ? `Контекст последнего шага: ${contextTokens.toLocaleString("ru-RU")}`
        : "",
      sessionTokens ? `Всего за сессию: ${sessionTokens.toLocaleString("ru-RU")}` : "",
    ].filter(Boolean).join("\n") || "Расход токенов сессии";
  };
  codeApi.models().then(({ data }) => {
    cache.models = data || [];
    drawUsage();
  }).catch(() => {});
  setTimeout(refreshUsage, 400);
  const usageTimer = setInterval(refreshUsage, 12_000);

  const pickModel = async (model) => {
    try {
      await codeApi.setModel(id, model);
      session.modelAlias = model.id;
      session.model = `clauden/${model.id}`;
      modelBtn.textContent = modelLabel(model.id);
      drawUsage();
      toast(`Модель: ${model.name || modelLabel(model.id)}`, "success");
    } catch (err) {
      toast(err.message || "Не удалось сменить модель", "error");
    }
  };
  modelBtn.addEventListener("click", async () => {
    if (!cache.models.length) {
      try { cache.models = (await codeApi.models()).data || []; } catch {  }
    }
    const items = cache.models.map((model) => ({
      label: model.name || modelLabel(model.id),
      onClick: () => pickModel(model),
    }));
    if (!items.length) {
      toast("Каталог моделей недоступен", "error");
      return;
    }
    menuAt(modelBtn, items);
  });

  const drawAttachments = () => {
    clear(attachRow);
    attachRow.hidden = !attachments.length;
    for (const item of attachments) {
      const isImage = item.kind === "image" && item.data;
      const card = el("span", { class: `att-card${isImage ? " is-image" : ""}`, title: `${item.name} · ${bytes(item.size)}` });
      if (isImage) {
        card.append(el("img", {
          class: "att-card-img",
          src: `data:${item.mime || "image/png"};base64,${item.data}`,
          alt: item.name,
        }));
      } else {
        card.append(el("span", { class: "att-card-glyph", html: icon("file") }));
      }
      card.append(
        el("span", { class: "att-card-name", text: item.name }),
        el("button", { class: "att-card-x", title: "Убрать", onclick: () => {
          const index = attachments.indexOf(item);
          if (index >= 0) attachments.splice(index, 1);
          drawAttachments();
        }, html: icon("close") }),
      );
      attachRow.append(card);
    }
  };
  attachBtn.addEventListener("click", () => fileInput.click());
  const addFiles = async (files) => {
    for (const file of files) {
      if (file.size > 20 * 1024 * 1024) {
        toast(`«${file.name}» больше 20 МБ`, "error");
        continue;
      }
      try {
        const mime = file.type || guessMime(file.name);
        const inline = isInlineImage(mime);
        if (inline && file.size > 5 * 1024 * 1024) {
          toast(`«${file.name}» больше 5 МБ — такая картинка не отправится`, "error");
          continue;
        }
        if (inline && attachments.filter((item) => item.kind === "image").length >= 3) {
          toast("К сообщению можно приложить не больше 3 картинок", "error");
          continue;
        }
        const data = await fileToBase64(file);
        attachments.push({
          name: file.name,
          size: file.size,
          mime,
          data,
          kind: inline ? "image" : "file",
        });
      } catch {
        toast(`Не удалось прочитать «${file.name}»`, "error");
      }
    }
    drawAttachments();
  };
  fileInput.addEventListener("change", async () => {
    const files = [...fileInput.files];
    fileInput.value = "";
    await addFiles(files);
  });

  textarea.addEventListener("paste", async (e) => {
    const files = [...(e.clipboardData?.files || [])].filter((f) => String(f.type).startsWith("image/"));
    if (!files.length) return;
    e.preventDefault();
    await addFiles(files);
  });

  async function runCommandText(text) {
    const command = String(text || "").trim();
    if (!command.startsWith("/")) return false;
    const [name, ...rest] = command.slice(1).split(/\s+/);
    const arg = rest.join(" ").trim();
    try {
      if (name === "help") {
        openCommandsHelp(ctx);
        return true;
      }
      if (name === "remote-control") {
        openRemoteSheet(session, ctx);
        return true;
      }
      if (name === "compact") {
        await codeApi.compact(id);
        toast("Контекст сжат", "success");
        refreshUsage();
        return true;
      }
      if (name === "undo") {
        const result = await codeApi.undo(id);
        session = result.data;
        render();
        toast("Ход отменён");
        return true;
      }
      if (name === "redo") {
        const result = await codeApi.redo(id);
        session = result.data;
        render();
        toast("Откат отменён");
        return true;
      }
      if (name === "model") {
        if (!arg) {
          toast("Укажите модель: /model claude-sonnet-5-5");
          return true;
        }
        const model = (cache.models || []).find((item) => item.id === arg || item.name === arg);
        if (!model) {
          toast(`Модель «${arg}» не найдена`, "error");
          return true;
        }
        await pickModel(model);
        return true;
      }
      if (name === "clear") {
        await codeApi.stop(id);
        session = { ...session, status: "stopped" };
        render();
        toast("Агент остановлен");
        return true;
      }

      const result = await codeApi.runCommand(id, name, arg);
      if (result?.data) {
        session = result.data;
        render();
      } else {
        toast(`Команда /${name} запущена`);
      }
      return true;
    } catch (err) {
      toast(err.message || `Не удалось выполнить /${name}`, "error");
      return true;
    }
  }

  sendBtn.addEventListener("click", async () => {
    const text = textarea.value.trim();
    if ((!text && !attachments.length) || session.status === "working") return;
    if (await runCommandText(text)) {
      textarea.value = "";
      return;
    }
    const files = attachments.splice(0);
    drawAttachments();
    textarea.value = "";
    sendBtn.disabled = true;
    const images = files.filter((file) => file.kind === "image");
    const others = files.filter((file) => file.kind !== "image");
    if (others.length && session.source === "ssh") {
      sendBtn.textContent = "Загружаю…";
      toast("Загружаю файлы на сервер через телефон…", "info");
    }
    try {
      const uploaded = [];
      for (const file of others) {
        try {
          const { data } = await codeApi.upload(id, file.name, file.data);
          if (data?.path) uploaded.push(data.path);
        } catch (err) {
          toast(`Файл «${file.name}»: ${err.message || "не загрузился"}`, "error");
        }
      }
      const note = uploaded.length
        ? `[${uploaded.length === 1 ? "Файл" : "Файлы"}: ${uploaded.join(", ")}]`
        : "";
      const withFiles = [text, note].filter(Boolean).join("\n\n");

      const result = await codeApi.send(
        id,
        withFiles,
        images.map(({ name, mime, data }) => ({ name, mime, data })),
      );
      session = result.data;
      sendBtn.textContent = "Отправить";
      render();
      refreshUsage();
    } catch (err) {
      toast(err.message || "Не удалось отправить", "error");
      sendBtn.textContent = "Отправить";
      sendBtn.disabled = false;
    }
  });
  textarea.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendBtn.click(); }
  });
  stopBtn.addEventListener("click", async () => {
    try { const result = await codeApi.stop(id); session = result.data; render(); } catch (err) { toast(err.message, "error"); }
  });

  render();
  subscribe(ctx, id, (next) => { session = next; render(); refreshUsage(); });
  setTimeout(() => { if (!document.contains(head)) clearInterval(usageTimer); }, 1000);

  function messageNode(message) {
    const node = el("div", { class: `code-msg ${message.role}` });
    if (message.role === "user") {
      if (message.text) node.append(el("div", { text: message.text }));
      if (message.images?.length) {
        const row = el("div", { class: "composer-attach sent", style: { marginTop: "6px" } });
        for (const image of message.images) {
          row.append(el("span", { class: "att-card", title: `${image.name} · ${bytes(image.size)}` },
            el("span", { class: "att-card-glyph", html: icon("file") }),
            el("span", { class: "att-card-name", text: image.name }),
          ));
        }
        node.append(row);
      }
      return node;
    }
    const md = el("div", { class: "md", html: renderMarkdown(message.text || "") });
    enhanceMarkdown(md);
    node.append(md);
    if (message.steps?.length) {
      const steps = el("div", { class: "term-steps" });
      for (const step of message.steps) {
        steps.append(el("div", { class: `term-step ${step.status || ""}` },
          el("span", null, el("span", { text: step.title || step.name || "Шаг" }), step.detail ? el("span", { class: "detail", text: ` · ${step.detail}` }) : null)));
      }
      node.append(steps);
    }
    if (message.status === "working") node.append(el("div", { class: "code-status working" }, el("span", { class: "dot" }), el("span", { text: "работает…" })));
    return node;
  }
}

function subscribe(ctx, id, onUpdate) {
  streamAbort?.abort();
  streamAbort = new AbortController();
  let current = null;
  streamCodeSession(id, {
    signal: streamAbort.signal,
    handlers: {
      onEvent: (payload, event) => {
        if (!payload) return;
        if (event === "snapshot" || payload.id === id && payload.messages) {
          current = payload;
          onUpdate(payload);
          return;
        }
        if (!current) return;
        if (event === "delta" && payload.text) {
          const last = [...current.messages].reverse().find((m) => m.role === "assistant");
          if (last) last.text = (last.text || "") + payload.text;
          onUpdate({ ...current });
          return;
        }
        if (event === "status") {
          current.status = payload.status || current.status;
          current.error = payload.error || "";
          onUpdate({ ...current });
          return;
        }
        if (event === "step") {
          const last = [...current.messages].reverse().find((m) => m.role === "assistant");
          if (last) {
            last.steps = last.steps || [];
            const index = last.steps.findIndex((s) => s.id && s.id === payload.id);
            if (index >= 0) last.steps[index] = payload;
            else last.steps.push(payload);
          }
          onUpdate({ ...current });
          return;
        }
        if (event === "job") { runLocalJob(payload, id, () => current); return; }
        if (event === "todos") { current.todos = payload; onUpdate({ ...current }); return; }
        if (event === "forms") { current.forms = payload; onUpdate({ ...current }); return; }
        if (event === "permissions") { current.permissions = payload; onUpdate({ ...current }); return; }
        if (event === "actions") { current.actions = payload; onUpdate({ ...current }); return; }
      },
    },
  }).catch((err) => {
    if (err?.name === "AbortError") return;
    toast(err.message || "Соединение с сессией потеряно", "error");
  });
}

function statusLabel(status) {
  return { working: "работает", idle: "готово", stopped: "остановлено", failed: "ошибка", waiting_device: "ждёт устройство" }[status] || status || "—";
}

const MODEL_LABELS = {
  "claude-fable-5-2": "Claude Fable 5.2",
  "claude-fable-5-1": "Claude Fable 5.1",
  "claude-opus-5-5": "Claude Opus 5.5",
  "claude-sonnet-5-5": "Claude Sonnet 5.5",
  "claude-haiku-5-5": "Claude Haiku 5.5",
  "claude-sonnet-4-6": "Claude Sonnet 4.6",
  "claude-sonnet-4-5": "Claude Sonnet 4.5",
  "claude-haiku-4-5": "Claude Haiku 4.5",
};

function modelLabel(id) {
  const key = String(id || "").replace(/^clauden\//, "");
  return MODEL_LABELS[key] || key.replace(/^claude-/, "Claude ").replace(/-/g, " ");
}

function friendlyCodeError(message) {
  const text = String(message || "").trim();
  if (/не успел начать|очеред|queue/i.test(text)) {
    return "Сервис был занят и не успел начать обработку. Запрос повторяется автоматически — если ошибка осталась, отправьте сообщение ещё раз через минуту.";
  }
  if (/stream ended without finish_reason|incomplete[- ]stream|invalid .*stream event|streaming response failed/i.test(text)) {
    return "Сервис оборвал ответ. Попробуйте ещё раз — сессия и уже сделанная работа сохранены.";
  }
  if (/tool call stream state is inconsistent|tool output parsing failed/i.test(text)) {
    return "Модель вернула повреждённый вызов инструмента. Попробуйте ещё раз.";
  }
  return text || "Агент не справился с задачей";
}

function formatTokens(value) {  const number = Number(value) || 0;
  if (number >= 1_000_000) return `${(number / 1_000_000).toFixed(1)}M`;
  if (number >= 1000) return `${(number / 1000).toFixed(number >= 10_000 ? 0 : 1)}k`;
  return String(number);
}

async function openCommandsMenu(anchor, session, ctx, run) {
  const items = [
    { icon: "layers", label: "Сжать контекст (/compact)", onClick: () => run("/compact") },
    { icon: "refresh", label: "Отменить последний ход (/undo)", onClick: () => run("/undo") },
    { icon: "refresh", label: "Вернуть отменённое (/redo)", onClick: () => run("/redo") },
    { icon: "sparkles", label: "Сменить модель (/model)", onClick: () => sessionModelMenu(anchor, session, ctx) },
    "-",
  ];
  try {
    const { data } = await codeApi.commands(session.id);
    for (const command of data || []) {
      items.push({
        icon: "terminal",
        label: `/${command.name}${command.description ? ` — ${command.description}` : ""}`,
        onClick: () => run(`/${command.name}`),
      });
    }
  } catch {

  }
  items.push({ icon: "info", label: "Все команды (/help)", onClick: () => run("/help") });
  menuAt(anchor, items);
}

function openCommandsHelp(ctx) {
  const rows = [
    ["/compact", "Сжать контекст сессии, чтобы освободить окно"],
    ["/undo", "Отменить последний ход и изменения файлов"],
    ["/redo", "Вернуть отменённый ход"],
    ["/model <id>", "Сменить модель, например /model claude-sonnet-5-5"],
    ["/clear", "Остановить работу агента"],
    ["/remote-control", "Ссылка на живую сессию"],
    ["/help", "Этот список"],
  ];
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Команды Code" })),
    el("div", { class: "modal-body" },
      el("div", { class: "list" }, ...rows.map(([name, text]) => el("div", { class: "list-row" },
        el("span", { class: "badge", text: name }),
        el("span", { class: "list-row-text" }, el("span", { class: "list-row-title", text }))))),
    ),
    el("div", { class: "modal-foot" }, el("button", { class: "btn btn-ghost", text: "Закрыть", onclick: () => modal.close() })),
  ));
}

function sessionMenu(anchor, ctx, session, reloadList) {
  menuAt(anchor, [
    { icon: "code", label: "Изменения (diff)", onClick: () => showDiff(session) },
    { icon: "folder", label: "Файлы", onClick: () => showFiles(session, ctx) },
    { icon: "sparkles", label: "Сменить модель", onClick: () => sessionModelMenu(anchor, session, ctx) },
    { icon: "terminal", label: "Команды…", onClick: () => openCommandsMenu(anchor, session, ctx, async (text) => {
      try {
        if (text === "/help") { openCommandsHelp(ctx); return; }
        if (text === "/compact") { await codeApi.compact(session.id); toast("Контекст сжат", "success"); return; }
        if (text === "/undo") { await codeApi.undo(session.id); ctx.renderView(); return; }
        if (text === "/redo") { await codeApi.redo(session.id); ctx.renderView(); return; }
        if (text === "/model") { toast("Введите /model <id> в поле сообщения"); return; }
        await codeApi.runCommand(session.id, text.slice(1), "");
        ctx.renderView();
      } catch (err) { toast(err.message, "error"); }
    }) },
    { icon: "github", label: "Закоммитить", onClick: () => commitSession(session, ctx) },
    { icon: "external", label: "Создать PR", onClick: () => createPull(session) },
    { icon: "edit", label: "Переименовать", onClick: async () => {
      const title = await promptDialog({ title: "Название сессии", value: session.title });
      if (!title) return;
      try { await codeApi.rename(session.id, title); ctx.renderView(); } catch (err) { toast(err.message, "error"); }
    }},
    { icon: "download", label: "Экспорт .md", onClick: () => exportSession(session) },
    "-",
    { icon: "trash", label: "Остановить", danger: true, onClick: async () => {
      const ok = await confirmDialog({ title: "Остановить агента?", text: "Текущий ход будет прерван.", confirm: "Остановить" });
      if (!ok) return;
      try { await codeApi.stop(session.id); ctx.renderView(); } catch (err) { toast(err.message, "error"); }
    }},
    { icon: "trash", label: "Удалить сессию", danger: true, onClick: async () => {
      const ok = await confirmDialog({ title: "Удалить сессию?", text: "Папка сессии и вся переписка будут удалены. Это действие нельзя отменить.", confirm: "Удалить" });
      if (!ok) return;
      try {
        await codeApi.remove(session.id);
        cache.sessions = cache.sessions.filter((item) => item.id !== session.id);
        toast("Сессия удалена");
        ctx.navigate("/code");
        reloadList?.();
      } catch (err) { toast(err.message || "Не удалось удалить", "error"); }
    }},
  ]);
}

function sessionModelMenu(anchor, session, ctx) {
  const show = (models) => {
    if (!models.length) {
      toast("Каталог моделей недоступен", "error");
      return;
    }
    menuAt(anchor, models.map((model) => ({
      icon: model.id === (session.modelAlias || "") ? "check" : "sparkles",
      label: model.name || modelLabel(model.id),
      onClick: async () => {
        try {
          await codeApi.setModel(session.id, model);
          toast(`Модель: ${model.name || modelLabel(model.id)}`, "success");
          ctx.renderView();
        } catch (err) {
          toast(err.message || "Не удалось сменить модель", "error");
        }
      },
    })));
  };
  if (cache.models?.length) {
    show(cache.models);
    return;
  }
  codeApi.models()
    .then(({ data }) => {
      cache.models = data || [];
      show(cache.models);
    })
    .catch(() => toast("Каталог моделей недоступен", "error"));
}

async function showDiff(session) {
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Изменения" })),
    el("div", { class: "modal-body" }, el("div", { class: "loading-block" }, el("span", { class: "spinner" }), "Считаю diff…")),
  ), { full: true });
  try {
    const { data } = await codeApi.diff(session.id);
    const body = modal.box.querySelector(".modal-body");
    clear(body);
    if (!data.files?.length) {
      body.append(el("p", { class: "muted", text: "Изменений нет." }));
      return;
    }
    const files = el("div", { class: "list" });
    for (const file of data.files) {
      files.append(el("div", { class: "list-row" },
        el("span", { class: "badge", text: file.status || "M" }),
        el("span", { class: "list-row-text" }, el("span", { class: "list-row-title mono", text: file.path })),
        el("span", { class: "list-row-meta", text: `+${file.additions || 0} −${file.deletions || 0}` }),
      ));
    }
    body.append(files, el("div", { class: "terminal", style: { marginTop: "12px", maxHeight: "50vh" } },
      el("pre", { style: { margin: "0", whiteSpace: "pre-wrap" }, text: data.patch || "" })));
    if (data.truncated) body.append(el("p", { class: "field-hint", text: "Diff обрезан из-за размера." }));
  } catch (err) {
    modal.box.querySelector(".modal-body").textContent = err.message || "Не удалось получить diff";
  }
}

async function downloadCodeFile(session, filePath) {
  try {
    const blob = await codeApi.download(session.id, filePath);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = String(filePath).split("/").filter(Boolean).pop() || "file";
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast("Скачивание началось", "success");
  } catch (err) {
    toast(err.message || "Не удалось скачать файл", "error");
  }
}

async function showFiles(session, ctx) {
  let path = "";
  const localBridge = window.claudenDesktop?.local;
  const localRoot = session.source === "ssh" && session.remote?.local ? session.remote.root : "";
  const canLocal = Boolean(localRoot && localBridge);
  const localNoBridge = session.source === "ssh" && Boolean(session.remote?.local) && !canLocal;

  const listBox = el("div", { class: "list" });
  const contentBox = el("div", { style: { flex: "1", minHeight: "0", overflow: "auto" } });
  const crumb = el("div", { class: "muted small", style: { marginBottom: "8px" }, text: "." });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: canLocal ? "Файлы · этот компьютер" : "Файлы" }), crumb),
    el("div", { class: "modal-body", style: { display: "flex", gap: "12px", minHeight: "50vh", maxHeight: "70vh" } },
      el("div", { style: { width: "260px", overflow: "auto", flex: "none" } }, listBox),
      el("div", { style: { flex: "1", minWidth: "0", display: "flex", flexDirection: "column" } }, contentBox),
    ),
  ), { full: true });

  const localCall = async (method, ...args) => {
    const result = await localBridge[method](...args);
    if (!result?.ok) throw new Error(result?.error?.message || "Не удалось выполнить");
    return result.data;
  };

  const load = async (next) => {
    path = next;
    clear(listBox);
    crumb.textContent = path || ".";
    listBox.append(el("div", { class: "loading-block" }, el("span", { class: "spinner" })));
    try {
      const data = canLocal
        ? await localCall("list", localRoot, path)
        : (await codeApi.files(session.id, path)).data;
      clear(listBox);
      if (localNoBridge) {
        listBox.append(el("div", { class: "empty-block", style: { padding: "16px" } },
          el("div", { html: icon("laptop") }),
          el("h3", { text: "Файлы на этом компьютере" }),
          el("p", { text: "Откройте эту сессию в приложении Clauden на ПК — оно покажет файлы и даст их редактировать. В браузере доступ к диску закрыт." }),
        ));
        return;
      }
      if (data.parent || path) {
        listBox.append(el("button", { class: "list-row", onclick: () => load(data.parent || "") },
          el("span", { class: "list-row-icon", html: icon("arrowLeft") }), el("span", { class: "list-row-text" }, el("span", { class: "list-row-title", text: ".." }))));
      }
      for (const entry of data.entries || []) {
        listBox.append(el("button", {
          class: "list-row",
          onclick: () => entry.type === "dir" ? load(entry.path) : openFile(entry.path),
        },
          el("span", { class: "list-row-icon", html: icon(entry.type === "dir" ? "folder" : "file") }),
          el("span", { class: "list-row-text" },
            el("span", { class: "list-row-title", text: entry.name }),
            el("span", { class: "list-row-sub", text: entry.type === "dir" ? "папка" : bytes(entry.size) }),
          ),
        ));
      }
      if (!data.entries?.length) {
        listBox.append(el("div", { class: "muted small", style: { padding: "10px" }, text: "Папка пуста" }));
      }
    } catch (err) {
      clear(listBox);
      listBox.append(el("div", { class: "error-box", text: err.message || "Не удалось" }));
    }
  };

  const openFile = async (filePath) => {
    clear(contentBox);
    contentBox.append(el("div", { class: "loading-block" }, el("span", { class: "spinner" }), "Читаю…"));
    try {
      const data = canLocal
        ? await localCall("read", localRoot, filePath)
        : (await codeApi.fileRead(session.id, filePath)).data;
      clear(contentBox);
      const sshRemote = session.source === "ssh" && !canLocal;
      const head = el("div", { class: "row between", style: { marginBottom: "6px" } },
        el("span", { class: "mono small", text: filePath }));
      if (sshRemote) {
        head.append(el("button", {
          class: "btn btn-secondary btn-sm",
          text: "Скачать",
          onclick: () => downloadCodeFile(session, filePath),
        }));
      }
      if (data.binary) {
        contentBox.append(head,
          el("p", { class: "muted", text: "Бинарный файл — редактирование недоступно. Нажмите «Скачать», чтобы получить его." }));
        return;
      }
      const area = el("textarea", { class: "textarea mono", style: { flex: "1", minHeight: "300px", fontFamily: "var(--font-mono)", fontSize: "12.5px" } });
      area.value = data.content || "";
      const save = el("button", { class: "btn btn-primary btn-sm", text: "Сохранить", onclick: async () => {
        try {
          if (canLocal) await localCall("write", localRoot, filePath, area.value);
          else await codeApi.fileWrite(session.id, filePath, area.value);
          toast("Файл сохранён", "success");
        } catch (err) { toast(err.message, "error"); }
      }});
      head.append(save);
      contentBox.append(head, area);
    } catch (err) {
      clear(contentBox);
      contentBox.append(el("div", { class: "error-box", text: err.message || "Не удалось прочитать" }));
    }
  };

  load("");
}

async function commitSession(session, ctx) {
  const message = await promptDialog({ title: "Коммит", label: "Сообщение коммита", value: "", confirm: "Закоммитить" });
  if (!message) return;
  try {
    const { data } = await codeApi.commit(session.id, message);
    toast(`Коммит создан${data?.sha ? ` · ${String(data.sha).slice(0, 7)}` : ""}`, "success");
  } catch (err) {
    toast(err.message || "Не удалось закоммитить", "error");
  }
}

async function createPull(session) {
  const title = await promptDialog({ title: "Pull Request", label: "Заголовок", value: session.title || "" });
  if (!title) return;
  const body = await promptDialog({ title: "Pull Request", label: "Описание", multiline: true, confirm: "Создать" });
  if (body === null) return;
  try {
    const { data } = await codeApi.pull(session.id, title, body);
    if (data?.url) {
      toast("Pull Request создан", "success");
      window.open(data.url, "_blank", "noopener");
    } else {
      toast(data?.message || "PR создан", "success");
    }
  } catch (err) {
    toast(err.message || "Не удалось создать PR", "error");
  }
}

function exportSession(session) {
  const lines = [`# ${session.title}`, "", `${session.repoName || session.repo} · ${session.branch || ""}`, ""];
  for (const message of session.messages || []) {
    lines.push(message.role === "user" ? "## Вы" : "## Clauden", "", message.text || "", "");
  }
  downloadText(lines.join("\n"), `code-${session.id}.md`, "text/markdown;charset=utf-8");
}

function isTrivialForm(form) {
  const fields = Array.isArray(form?.fields)
    ? form.fields
    : Array.isArray(form?.questions)
      ? form.questions
      : [];
  const ack = /^(?:ok|okay|okey|ок|окей|понял[а]?|понятно|хорошо|ладно|принято|продолжай(?:те)?|продолжить|дальше|вперёд|вперед|continue|proceed|go(?:\s+ahead)?|done|готово)[.!…\s]*$/i;
  if (!fields.length) return true;
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

function formCard(session, form, ctx) {
  const formTitle = /^questions?$/i.test(String(form.title || "").trim())
    ? "Вопрос агента"
    : form.title || "Агент ждёт ответа";
  const card = el("div", { class: "notice-box" },
    el("div", { class: "setting-name", text: formTitle }),
  );
  const message = String(form.metadata?.message || "").trim();
  if (message) card.append(el("p", { class: "small", text: message }));
  const inputs = {};
  for (const field of form.fields || []) {
    const id = field.key || field.id || field.name;
    if (!id) continue;
    const label = field.title || field.label || id;

    const question = String(field.description || "").trim();
    const box = el("div", { class: "field" });
    const placeholderHeader = /^(no action|nothing|question|questions|вопрос|ответ)$/i.test(label.trim());
    if (!(placeholderHeader && question)) {
      box.append(el("label", { text: label }));
    }
    if (question && question !== label) {
      box.append(el("div", { class: "field-hint", text: question }));
    }
    const slot = {};
    const hasOptions = Array.isArray(field.options) && field.options.length > 0;
    const optionText = (opt) => String(typeof opt === "string" ? opt : opt?.label ?? opt?.value ?? "").trim();
    const onlyNothing = hasOptions && field.options.every((opt) => /^(nothing|no action|none|нет|ничего)$/i.test(optionText(opt)));
    const showOptions = hasOptions && !onlyNothing;
    if (showOptions) {
      slot.select = el("select", { class: "select" }, ...field.options.map((opt) => {
        if (typeof opt === "string") return el("option", { value: opt, text: opt });
        return el("option", {
          value: opt.value ?? opt.label,
          text: opt.label ?? opt.value,
          title: opt.description || null,
        });
      }));
      box.append(slot.select);
    }
    if (!showOptions || field.custom) {
      slot.custom = el("input", {
        class: "input",
        placeholder: showOptions ? "Свой ответ" : field.placeholder || "Ответ",
      });
      box.append(slot.custom);
    }
    inputs[id] = slot;
    card.append(box);
  }
  card.append(el("button", { class: "btn btn-primary btn-sm", text: "Ответить", onclick: async () => {
    const answer = {};
    for (const [key, slot] of Object.entries(inputs)) {
      const custom = String(slot.custom?.value || "").trim();
      answer[key] = custom || slot.select?.value || "";
    }
    try {
      const { data } = await codeApi.answerForm(session.id, form.id, answer);

      session.forms = data?.forms || (session.forms || []).filter((item) => item.id !== form.id);
      card.remove();
      toast("Ответ отправлен");
    } catch (err) {
      toast(err.message, "error");
    }
  }}));
  return card;
}

function permissionCard(session, permission, ctx) {
  return el("div", { class: "notice-box" },
    el("div", { class: "setting-name", text: "Запрос разрешения" }),
    el("p", { class: "small", text: `${permission.action || permission.title || "Инструмент"} ${permission.resources ? JSON.stringify(permission.resources) : ""}` }),
    el("div", { class: "row", style: { marginTop: "8px" } },
      el("button", { class: "btn btn-primary btn-sm", text: "Разрешить", onclick: async () => {
        try { await codeApi.answerPermission(session.id, permission.id, "once"); } catch (err) { toast(err.message, "error"); }
      }}),
      el("button", { class: "btn btn-secondary btn-sm", text: "Всегда", onclick: async () => {
        try { await codeApi.answerPermission(session.id, permission.id, "always"); } catch (err) { toast(err.message, "error"); }
      }}),
      el("button", { class: "btn btn-ghost btn-sm", text: "Отклонить", onclick: async () => {
        try { await codeApi.answerPermission(session.id, permission.id, "reject"); } catch (err) { toast(err.message, "error"); }
      }}),
    ),
  );
}

async function openDesktopFolder(ctx) {
  try {
    const result = await window.claudenDesktop.local.pickFolder();
    if (!result?.ok) throw new Error(result?.error?.message || "Не удалось выбрать папку");
    const root = result.data;
    if (!root) return;
    const label = String(root).split(/[\\/]/).filter(Boolean).pop() || "Проект";
    const { data } = await codeApi.create({
      repo: "ssh",
      label,
      target: "Этот компьютер",
      root,
      local: true,
    });
    toast("Локальная сессия создана", "success");
    ctx.navigate(`/code/${data.id}`);
  } catch (err) {
    toast(err.message || "Не удалось открыть папку", "error");
  }
}

async function newSession(ctx) {
  if (!cache.github?.connected) {
    try { const { data } = await codeApi.github(); cache.github = data; } catch {  }
  }
  const repoSelect = el("select", { class: "select" });
  const repos = [
    { id: "blank", label: "Без репозитория · пустая папка" },
    ...cache.repos.map((repo) => ({ id: repo.id, label: `Сервер · ${repo.name}` })),
  ];
  for (const repo of repos) repoSelect.append(el("option", { value: repo.id, text: repo.label }));
  if (!repos.length) {
    repoSelect.append(el("option", { value: "", text: cache.github?.connected ? "Загружаю репозитории GitHub…" : "Нет репозиториев — подключите GitHub" }));
  }
  const branch = el("input", { class: "input", placeholder: "Ветка (необязательно)" });
  const prompt = el("textarea", { class: "textarea", rows: "5", placeholder: "Что сделать? Например: добавь страницу тарифов и тесты к ней" });
  const status = el("div", { class: "notice-box", hidden: true });
  const create = el("button", { class: "btn btn-primary", text: "Запустить" });

  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Новая сессия Code" })),
    el("div", { class: "modal-body" },
      isDesktopApp() && window.claudenDesktop?.local
        ? el("div", { class: "notice-box", style: { marginBottom: "14px" } },
            el("div", { class: "row between" },
              el("div", { style: { minWidth: "0" } },
                el("div", { class: "setting-name", text: "Работать на этом компьютере" }),
                el("p", { class: "small", style: { margin: "4px 0 0" }, text: "Агент откроет папку проекта на вашем ПК: сможет создавать, читать и менять файлы." }),
              ),
              el("button", { class: "btn btn-primary btn-sm", text: "Открыть папку", onclick: () => {
                modal.close();
                openDesktopFolder(ctx);
              } }),
            ),
          )
        : null,
      el("div", { class: "field" }, el("label", { text: "Репозиторий" }), repoSelect,
        el("div", { class: "field-hint" },
          el("button", { class: "btn btn-ghost btn-sm", style: { padding: "0" }, text: cache.github?.connected ? "Обновить список GitHub" : "Подключить GitHub", onclick: async () => {
            modal.close();
            await connectGithub(ctx, true);
          } }),
        ),
      ),
      el("div", { class: "field" }, el("label", { text: "Ветка" }), branch),
      el("div", { class: "field" }, el("label", { text: "Задача" }), prompt),
      status,
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      create,
    ),
  ));
  setTimeout(() => prompt.focus(), 30);

  if (cache.github?.connected) {
    try {
      const { data } = await codeApi.githubRepos();
      cache.githubRepos = data || [];
      repoSelect.innerHTML = "";
      repoSelect.append(el("option", { value: "blank", text: "Без репозитория · пустая папка" }));
      for (const repo of [...cache.repos, ...cache.githubRepos]) {
        repoSelect.append(el("option", { value: repo.id, text: `${String(repo.id).startsWith("gh:") ? "GitHub" : "Сервер"} · ${repo.name}` }));
      }
    } catch (err) {
      status.hidden = false;
      status.className = "error-box";
      status.textContent = err.message || "GitHub не отдал репозитории";
    }
  }

  create.addEventListener("click", async () => {
    const repo = repoSelect.value;
    const text = prompt.value.trim();
    if (!repo) { status.hidden = false; status.className = "error-box"; status.textContent = "Выберите репозиторий"; return; }
    if (!text && repo !== "blank" && repo !== "none") { prompt.focus(); return; }
    create.disabled = true;
    create.innerHTML = '<span class="spinner"></span> Клонирую…';
    status.hidden = false;
    status.className = "notice-box";
    status.textContent = "Клонирую репозиторий и запускаю агента. Это может занять до минуты.";
    try {
      const { data } = await codeApi.create({ repo, prompt: text, branch: branch.value.trim() || undefined });
      modal.close();
      ctx.navigate(`/code/${data.id}`);
    } catch (err) {
      status.className = "error-box";
      status.textContent = err.message || "Не удалось создать сессию";
      create.disabled = false;
      create.textContent = "Запустить";
    }
  });
}

async function connectGithub(ctx, reopen = false) {
  const token = el("input", { class: "input", type: "password", placeholder: "ghp_… или github_pat_…" });
  const status = el("div", { class: "notice-box", hidden: true });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "GitHub" })),
    el("div", { class: "modal-body" },
      el("p", { class: "muted small", style: { marginTop: "0" }, text: "Токен с правами repo. Хранится на сервере Clauden в зашифрованном виде и используется только для клонирования и пуша. Подойдёт classic (ghp_…) или fine-grained (github_pat_…) с доступом к репозиториям." }),
      el("div", { class: "field" }, el("label", { text: "Personal access token" }), token),
      el("p", { class: "field-hint" }, el("a", { href: "https://github.com/settings/tokens", target: "_blank", rel: "noopener", text: "Создать токен на GitHub ↗" })),
      status,
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      el("button", { class: "btn btn-primary", text: "Подключить", onclick: async () => {
        status.hidden = false;
        status.className = "notice-box";
        status.textContent = "Проверяю токен…";
        try {
          const { data } = await codeApi.saveGithub(token.value.trim());
          cache.github = data;
          modal.close();
          toast(`GitHub подключён: ${data.login || ""}`, "success");
          ctx.renderView();
          if (reopen) setTimeout(() => newSession(ctx), 200);
        } catch (err) {
          status.className = "error-box";
          status.textContent = err.message || "GitHub не принял токен";
        }
      }}),
    ),
  ));
  setTimeout(() => token.focus(), 30);
}

const REMOTE_TTL = [
  { value: 60, label: "1 час" },
  { value: 24 * 60, label: "24 часа" },
  { value: 7 * 24 * 60, label: "7 дней" },
];

export function openRemoteSheet(session, ctx) {
  const linkInput = el("input", { class: "input", readonly: true, placeholder: "Ссылка появится здесь" });
  const copyBtn = el("button", { class: "btn btn-secondary btn-sm", text: "Копировать", onclick: async () => {
    if (!linkInput.value) return;
    await copyText(linkInput.value);
    toast("Ссылка скопирована");
  }});
  const openBtn = el("button", { class: "btn btn-ghost btn-sm", text: "Открыть", onclick: () => {
    if (linkInput.value) window.open(linkInput.value, "_blank", "noopener");
  }});
  const linkBox = el("div", { class: "remote-link-box", hidden: true },
    linkInput,
    el("div", { class: "row", style: { marginTop: "8px", gap: "8px" } }, copyBtn, openBtn),
  );

  const ttl = el("select", { class: "select" },
    ...REMOTE_TTL.map((item) => el("option", { value: String(item.value), text: item.label })),
  );
  ttl.value = String(24 * 60);

  const statusLine = el("div", { class: "field-hint" });
  const createBtn = el("button", { class: "btn btn-primary", text: "Создать ссылку" });
  const revokeBtn = el("button", { class: "btn btn-ghost", text: "Отозвать", hidden: true });

  const body = el("div", { class: "modal-body" },
    el("p", { class: "small", text: "Ссылка открывает эту сессию с любого устройства — без входа в аккаунт. Агент продолжает работу, вы видите шаги и подтверждаете разрешения. В чате сессии то же самое делает команда /remote-control." }),
    el("div", { class: "field" },
      el("label", { text: "Срок действия" }),
      ttl,
    ),
    linkBox,
    statusLine,
    el("div", { class: "row", style: { marginTop: "12px", gap: "8px" } }, createBtn, revokeBtn),
    el("div", { class: "notice-box", style: { marginTop: "14px" } },
      el("div", { class: "setting-name", text: "Что доступно по ссылке" }),
      el("div", { class: "small", text: "Переписка с агентом, его шаги и диффы, отправка новых задач, подтверждение разрешений и остановка работы." }),
      el("div", { class: "small", text: "Ссылка одноразовая на сессию: новая отзывает старую. Отозвать можно в любой момент." }),
    ),
  );

  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" },
      el("h2", { class: "modal-title", text: "Remote Control" }),
      el("span", { class: "badge", text: session.title || session.repoName || "Сессия" }),
    ),
    body,
  ));

  const paint = (link) => {
    const has = Boolean(link?.url);
    linkBox.hidden = !has;
    revokeBtn.hidden = !has;
    createBtn.textContent = has ? "Перевыпустить" : "Создать ссылку";
    if (has) {
      linkInput.value = link.url;
      const until = link.expiresAt ? new Date(link.expiresAt) : null;
      statusLine.textContent = until
        ? `Действует до ${until.toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })}`
        : "";
    } else {
      linkInput.value = "";
      statusLine.textContent = "Ссылки пока нет.";
    }
  };

  const load = async () => {
    try {
      const { data } = await remoteApi.current(session.id);
      paint(data);
    } catch (err) {
      statusLine.textContent = err.message || "";
    }
  };

  createBtn.addEventListener("click", async () => {
    createBtn.disabled = true;
    try {
      const { data } = await remoteApi.create(session.id, { ttlMinutes: Number(ttl.value) });
      paint(data);
      toast("Ссылка готова");
    } catch (err) {
      toast(err.message || "Не удалось создать ссылку", "error");
    } finally {
      createBtn.disabled = false;
    }
  });

  revokeBtn.addEventListener("click", async () => {
    try {
      await remoteApi.revoke(session.id);
      paint(null);
      toast("Ссылка отозвана");
    } catch (err) {
      toast(err.message || "Не удалось отозвать", "error");
    }
  });

  load();
  return modal;
}

export async function openConnectRemote(ctx) {
  const value = await promptDialog({
    title: "Remote Control",
    label: "Ссылка или код сессии",
    placeholder: "https://clauden.best/r/…",
    confirm: "Открыть",
  });
  if (!value) return;
  const token = String(value).trim().match(/([a-z0-9]{16,64})\s*$/i)?.[1];
  if (!token) {
    toast("Не похоже на ссылку Remote Control", "error");
    return;
  }
  ctx.navigate(`/r/${token}`);
}

const INLINE_IMAGE_MIME = {
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  gif: "image/gif",
  webp: "image/webp",
};

function isInlineImage(mime) {
  return Object.values(INLINE_IMAGE_MIME).includes(String(mime || "").toLowerCase());
}

function guessMime(name) {
  const ext = String(name || "").split(".").pop().toLowerCase();
  if (INLINE_IMAGE_MIME[ext]) return INLINE_IMAGE_MIME[ext];
  return {
    svg: "image/svg+xml",
    pdf: "application/pdf",
    docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    txt: "text/plain", md: "text/markdown", csv: "text/csv", json: "application/json",
    html: "text/html",
    zip: "application/zip", gz: "application/gzip", tar: "application/x-tar",
    "7z": "application/x-7z-compressed", rar: "application/vnd.rar",
    xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  }[ext] || "application/octet-stream";
}
