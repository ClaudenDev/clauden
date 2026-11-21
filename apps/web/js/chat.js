import {
  $, el, clear, icon, timeAgo, formatTime, bytes, toast, openModal, menuAt,
  confirmDialog, copyText, downloadText, titleFromText, uid, debounce, isMobile, initials,
  throttleRaf, escapeHtml, plural, relTimeShort, claudeMark,
} from "./util.js";
import { api, streamChat, streamClaudeDesktop, claudeDesktop, customApi, streamCustomApi, isDesktopApp, isCustomApiSupported, fileToBase64, ApiError, isNetworkError } from "./api.js";
import { store, loadDraft, saveDraft, loadMemory, saveMemory, saveSettings, loadCustomize } from "./store.js";
import { renderMarkdown, enhanceMarkdown, stripMarkdown, extractCodeBlocks } from "./markdown.js";
import { openArtifact, collectArtifacts } from "./artifacts.js";
import { openPlusMenu, openFilePicker, closePlusMenu } from "./plusmenu.js";
import { addTask } from "./routines.js";

const MAIN_MODELS = {
  "claude-fable-5-2": { name: "Claude Fable 5.2", desc: "Новейшая модель для самых сложных задач", badge: "Max/Ultra" },
  "claude-fable-5-1": { name: "Claude Fable 5.1", desc: "Самая мощная для сложных исследований", badge: "Pro" },
  "claude-opus-5-5": { name: "Claude Opus 5.5", desc: "Глубокие рассуждения, код и анализ", badge: "Pro" },
  "claude-sonnet-5-5": { name: "Claude Sonnet 5.5", desc: "Быстрая и точная на каждый день", badge: "Pro" },
  "claude-haiku-5-5": { name: "Claude Haiku 5.5", desc: "Самая быстрая для простых задач", badge: "Pro" },
};

const MAX_ULTRA_PLANS = new Set(["max", "ultra"]);

function isMaxUltraPlan(plan) {
  return MAX_ULTRA_PLANS.has(String(plan || "").trim().toLowerCase());
}

const EFFORTS = [
  { label: "Низкий", value: 2048 },
  { label: "Средний", value: 4096 },
  { label: "Высокий", value: 8192 },
  { label: "Сверхвысокий", value: 12288 },
  { label: "Максимальный", value: 16384 },
];

const SKILL_LABELS = {
  artifacts: "Артефакты",
  webSearch: "Веб-поиск",
  codeExecution: "Выполнение кода",
  visualizations: "Визуализации",
  memory: "Память",
};

export async function renderChat(view, ctx, { chatId }) {
  const { state } = ctx;
  const routeProject = state.route.params?.get("project") || "";
  let chat = null;
  if (chatId) {
    chat = await store.getChat(chatId);
    if (!chat) chat = state.chats.find((c) => c.id === chatId);
  }
  const isNew = !chat;
  if (!chat) {
    chat = {
      id: uid("chat"),
      title: "",
      createdAt: Date.now(),
      updatedAt: Date.now(),
      pinned: false,
      projectId: routeProject,
      messages: [],
    };
  }
  let persisted = !isNew;

  if (
    chat &&
    chat.provider === "claude" &&
    chat.claudeConversationId &&
    !(chat.messages || []).length &&
    isDesktopApp() &&
    state.claude?.linked !== false
  ) {
    try {
      const full = await claudeDesktop.importOne({
        uuid: chat.claudeConversationId,
        name: chat.title || "",
        updatedAt: chat.updatedAt || 0,
      });
      if (full?.messages?.length) {
        chat.messages = full.messages;
        chat.title = chat.title || full.title || "";
        chat.updatedAt = Math.max(chat.updatedAt || 0, full.updatedAt || 0);
        chat.remote = false;
        await store.putChat(chat);
        const index = state.chats.findIndex((item) => item.id === chat.id);
        if (index >= 0) state.chats[index] = chat;
        else state.chats.unshift(chat);
        ctx.refreshSidebar?.();
      }
    } catch (err) {
      toast(err.message || "Не удалось загрузить чат из Claude", "error");
    }
  }

  let resumeMessage = null;
  const unfinished = (chat.messages || []).filter((message) => message.streaming);
  let staleStream = false;
  unfinished.forEach((message, index) => {
    if (index === unfinished.length - 1) {
      resumeMessage = message;
      return;
    }
    message.streaming = false;
    staleStream = true;
    if (!message.content) message.content = "_Ответ прерван._";
  });
  if (staleStream) {
    chat.updatedAt = Date.now();
    store.putChat(chat).catch(() => {});
  }
  let streaming = false;
  let controller = null;
  let turnId = "";
  let attachments = [];
  let pendingFiles = [];
  const project = chat.projectId ? state.projects.find((p) => p.id === chat.projectId) : null;
  if (isDesktopApp() && isCustomApiSupported() && !Array.isArray(state.customApis)) {
    try {
      state.customApis = await customApi.list();
    } catch {
      state.customApis = [];
    }
  }
  const preferredProvider = () => {
    if (!isDesktopApp()) return "clauden";
    const customReady = isCustomApiSupported() && Array.isArray(state.customApis) && state.customApis.length > 0;
    const paid = String(state.user?.plan || "Free").trim().toLowerCase() !== "free";
    if (state.settings.provider === "custom") {
      return customReady ? "custom" : state.claude?.linked ? "claude" : "clauden";
    }

    if (state.settings.provider === "claude") return "claude";
    if (!state.settings.provider) {

      if (paid) return "clauden";
      if (customReady && state.settings.customApiId) return "custom";
      if (state.claude?.linked) return "claude";
    }
    return "clauden";
  };
  let provider = chat.provider || preferredProvider();
  let pickedModel = "";

  const claudeModels = () => (state.claudeModels?.length ? state.claudeModels : [
    { id: "claude-sonnet-5-5", name: "Claude Sonnet 5.5" },
    { id: "claude-sonnet-4-6", name: "Claude Sonnet 4.6" },
    { id: "claude-sonnet-4-5", name: "Claude Sonnet 4.5" },
    { id: "claude-haiku-5-5", name: "Claude Haiku 5.5" },
    { id: "claude-haiku-4-5", name: "Claude Haiku 4.5" },
  ]);
  const customApis = () => (Array.isArray(state.customApis) ? state.customApis : []);
  const currentApi = () => customApis().find((item) => item.id === state.settings.customApiId) || customApis()[0] || null;
  const currentModel = () => {
    if (provider === "claude") return state.settings.claudeModel || claudeModels()[0]?.id || "";
    if (provider === "custom") {
      const api = currentApi();
      const saved = state.settings.customModel;
      if (saved && api?.models?.some((model) => model.id === saved)) return saved;
      return api?.models?.[0]?.id || saved || "";
    }
    return state.settings.model;
  };
  const claudeModelLabel = (id) => (claudeModels().find((m) => m.id === id)?.name || modelLabel(id)).replace(/^Claude\s+/i, "");
  const customModelLabel = (id) => {
    const api = currentApi();
    return api?.models?.find((m) => m.id === id)?.name || id || "модель";
  };
  const providerName = () => provider === "claude" ? "Claude" : provider === "custom" ? (currentApi()?.name || "Свой API") : "Clauden";
  const currentModelLabel = () => provider === "claude" ? claudeModelLabel(currentModel()) : provider === "custom" ? customModelLabel(currentModel()) : modelLabel(state.settings.model);
  function refreshModelPill() {
    if (!composer?.modelPill) return;
    composer.modelPill.firstChild.textContent = `${providerName()} · ${currentModelLabel()}`;
    refreshModelFoot();
    if (planNote) planNote.hidden = provider !== "clauden" || !freePlan;
  }

  async function applyProvider(next) {
    provider = next;
    chat.provider = next;
    state.settings = saveSettings({ provider: next });
    refreshModelPill();
    if (!persisted) return;
    try {
      await saveChatLocal();
    } catch {

    }
  }

  function syncShellTitle() {
    const host = $("#topbar-title");
    if (host) {
      clear(host);
      if (chat.title) {
        host.append(el("button", {
          class: "title-button",
          title: chat.title,
          onclick: (e) => openMenu(e.currentTarget),
        },
          el("span", { class: "title-text", text: chat.title }),
          el("span", { html: icon("chevronDown") }),
        ));
      }
    }
    const actions = $("#topbar-actions");
    if (actions) {
      clear(actions);
      const nodes = [
        project ? el("button", { class: "badge warn", style: { cursor: "pointer" }, text: project.title, onclick: () => ctx.openProject(project.id) }) : null,
        el("button", { class: "icon-btn", title: "Скопировать чат", onclick: shareChat, html: icon("share") }),
        el("button", { class: "icon-btn", title: "Ещё", onclick: (e) => openMenu(e.currentTarget), html: icon("more") }),
      ].filter(Boolean);
      for (const node of nodes) actions.append(node);
    }
  }

  const chatEl = el("div", { class: `chat ${chat.messages.length ? "" : "is-empty"}` });
  const scroll = el("div", { class: "chat-scroll" });
  const thread = el("div", { class: "thread" });
  scroll.append(thread);
  const displayName = String(state.user?.nickname || state.user?.name || "").trim().split(/\s+/)[0] || "";
  const planIsFree = () => String(state.user?.plan || "Free").trim().toLowerCase() === "free";
  const freePlan = planIsFree();
  const emptyState = el("div", { class: "empty-state" },
    el("div", { class: "plan-pill" },
      el("span", { text: freePlan ? "Бесплатный план" : `План ${state.user?.plan || "Free"}` }),
      el("span", { class: "pill-sep" }),
      el("a", { href: "/settings/plans", text: freePlan ? "Улучшить" : "Управление" }),
    ),
    el("div", { class: "greeting" },
      el("span", { html: claudeMark(26) }),
      el("span", { text: displayName ? `${greeting()}, ${displayName}` : greeting() }),
    ),
  );

  const composer = buildComposer();
  const planNote = el("div", { class: "plan-note" });
  if (freePlan) {
    const claudeLinked = Boolean(state.claude?.linked);
    planNote.append(
      el("a", { href: "/settings/plans", text: "Тарифы" }),
      el("span", { text: " · " }),
      isDesktopApp()
        ? el("a", { href: "/settings/claude", text: claudeLinked ? "Режим Claude" : "Привязать Claude" })
        : el("span", { class: "muted", text: "Claude-аккаунт доступен в приложении Clauden для ПК" }),
    );
  }
  planNote.hidden = provider !== "clauden" || !freePlan;
  const composerWrap = el("div", { class: "composer-wrap" }, emptyState, composer, planNote,
    el("div", { class: "composer-note" },
      el("span", { class: "composer-foot-left", text: "Clauden — ИИ, который может ошибаться. Проверяйте важное." }),
      el("span", { class: "composer-foot-model", text: `${providerName()} · ${currentModelLabel()}` }),
    ));
  chatEl.append(scroll, composerWrap);
  view.append(chatEl);
  syncShellTitle();

  const fileInput = el("input", { type: "file", multiple: true, hidden: true, accept: "image/*,.pdf,.docx,.txt,.md,.csv,.json,.html,.svg,.zip,.tar,.gz,.7z,.rar,.xlsx,.pptx" });
  view.append(fileInput);
  fileInput.addEventListener("change", async () => {
    const files = [...fileInput.files];
    fileInput.value = "";
    await addFiles(files);
  });

  const onShortcut = (e) => {
    if (!document.contains(composer)) {
      document.removeEventListener("keydown", onShortcut);
      return;
    }
    if ((e.ctrlKey || e.metaKey) && String(e.key).toLowerCase() === "u") {
      e.preventDefault();
      openFilePicker({
        artifacts: collectArtifacts(state),
        initialTab: "artifacts",
        onFiles: addFiles,
        onArtifact: attachArtifact,
      });
    }
  };
  document.addEventListener("keydown", onShortcut);

  renderAllMessages();
  updateEmpty();
  if (resumeMessage) {

    resumePendingTurn(resumeMessage).catch((err) => {
      console.error("chat resume failed", err);
    });
  }
  const prefill = sessionStorage.getItem("clauden.prefill");
  if (prefill) {
    sessionStorage.removeItem("clauden.prefill");
    composer.textarea.value = prefill;
    composer.sendBtn.disabled = false;
  }
  setTimeout(() => composer.textarea.focus(), 30);

  function buildComposer() {
    const attachRow = el("div", { class: "composer-attach", hidden: true });
    const textarea = el("textarea", {
      rows: "1",
      placeholder: "Чем могу помочь?",
      "aria-label": "Сообщение",
    });
    textarea.value = loadDraft(chat.id);
    textarea.addEventListener("input", () => {
      autoGrow();
      saveDraft(chat.id, textarea.value);
      sendBtn.disabled = !textarea.value.trim() && !attachments.length;
      refreshModelFoot();
    });
    textarea.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        send();
      }
    });
    textarea.addEventListener("paste", async (e) => {
      const files = [...(e.clipboardData?.files || [])];
      if (files.length) {
        e.preventDefault();
        await addFiles(files);
      }
    });

    textarea.addEventListener("keydown", async (e) => {
      if (!isDesktopApp() || !/Mac/i.test(navigator.platform || navigator.userAgent)) return;
      if (!e.ctrlKey || e.metaKey || e.altKey || e.shiftKey || e.key.toLowerCase() !== "v") return;
      if (!navigator.clipboard?.readText) return;
      e.preventDefault();
      try {
        const text = await navigator.clipboard.readText();
        if (!text) return;
        const start = textarea.selectionStart ?? textarea.value.length;
        const end = textarea.selectionEnd ?? start;
        textarea.setRangeText(text, start, end, "end");
        textarea.dispatchEvent(new Event("input", { bubbles: true }));
      } catch {

      }
    });
    const autoGrow = () => {
      textarea.style.height = "auto";
      textarea.style.height = `${Math.min(textarea.scrollHeight, 220)}px`;
    };

    const plus = el("button", {
      class: "composer-plus",
      title: "Добавить",
      "aria-haspopup": "menu",
      "aria-label": "Добавить файлы и инструменты",
      onclick: () => openAddMenu(plus),
      html: icon("plus"),
    });
    const modelPill = el("button", { class: "model-pill", title: "Выбор модели", onclick: openModelSheet },
      el("span", { text: `${providerName()} · ${currentModelLabel()}` }),
      el("span", { html: icon("chevronDown") }),
    );
    const sendBtn = el("button", { class: "send-btn", title: "Отправить", onclick: () => streaming ? stop() : send(), html: icon("arrowUp") });
    sendBtn.disabled = !textarea.value.trim() && !attachments.length;

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    const canRecord = Boolean(navigator.mediaDevices?.getUserMedia && window.MediaRecorder);
    const micBtn = canRecord || SpeechRecognition
      ? el("button", { class: "icon-btn", title: "Голосовой ввод", html: icon("mic") })
      : null;
    if (micBtn) {
      let recognition = null;
      let recorder = null;
      let stream = null;
      let chunks = [];
      let startedAt = 0;
      const baseText = () => textarea.value;
      const appendText = (text) => {
        const clean = String(text || "").trim();
        if (!clean) return;
        const base = baseText();
        textarea.value = `${base}${base ? " " : ""}${clean}`.trim();
        textarea.dispatchEvent(new Event("input"));
      };
      const finish = () => {
        micBtn.classList.remove("active");
        micBtn.title = "Голосовой ввод";
        if (stream) {
          for (const track of stream.getTracks()) track.stop();
          stream = null;
        }
        recorder = null;
      };
      const fallbackRecognition = () => {
        if (!SpeechRecognition) {
          toast("Браузер не поддерживает запись голоса", "error");
          return false;
        }
        try {
          recognition = new SpeechRecognition();
          recognition.lang = "ru-RU";
          recognition.interimResults = false;
          recognition.continuous = false;
          recognition.onresult = (event) => {
            appendText([...event.results].map((r) => r[0].transcript).join(" "));
          };
          recognition.onend = () => { recognition = null; micBtn.classList.remove("active"); };
          recognition.onerror = () => {
            recognition = null;
            micBtn.classList.remove("active");
            toast("Не удалось распознать речь. Проверьте микрофон и доступ к нему.", "error");
          };
          recognition.start();
          micBtn.classList.add("active");
          return true;
        } catch {
          toast("Не удалось включить микрофон", "error");
          return false;
        }
      };

      micBtn.addEventListener("click", async () => {
        if (recognition) {
          recognition.stop();
          recognition = null;
          micBtn.classList.remove("active");
          return;
        }
        if (recorder) {
          try { recorder.stop(); } catch {  }
          return;
        }
        if (!canRecord) {
          fallbackRecognition();
          return;
        }
        try {
          stream = await navigator.mediaDevices.getUserMedia({
            audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
          });
          const mime = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus", "audio/mp4"]
            .find((type) => window.MediaRecorder.isTypeSupported?.(type)) || "";
          recorder = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream);
          chunks = [];
          startedAt = Date.now();
          recorder.ondataavailable = (event) => { if (event.data?.size) chunks.push(event.data); };
          recorder.onstop = async () => {
            const stoppedMime = recorder?.mimeType || mime || "audio/webm";
            const blob = new Blob(chunks, { type: stoppedMime });
            finish();
            if (blob.size < 1200 || Date.now() - startedAt < 350) {
              toast("Слишком короткая запись — нажмите и говорите чуть дольше", "error");
              return;
            }
            micBtn.classList.add("active");
            micBtn.title = "Распознаю…";
            try {
              const result = await api.transcribe(blob);
              const text = String(result?.data?.text || result?.text || "").trim();
              if (text) appendText(text);
              else toast("Не расслышал — попробуйте ещё раз", "error");
            } catch (err) {

              if (!fallbackRecognition()) {
                toast(err.message || "Не удалось распознать речь", "error");
              }
            } finally {
              micBtn.classList.remove("active");
              micBtn.title = "Голосовой ввод";
            }
          };
          recorder.start();
          micBtn.classList.add("active");
          micBtn.title = "Идёт запись — нажмите, чтобы закончить";
        } catch (err) {
          finish();
          toast(
            err?.name === "NotAllowedError"
              ? "Разрешите доступ к микрофону в браузере или системе"
              : "Не удалось включить микрофон",
            "error",
          );
        }
      });
    }

    const bar = el("div", { class: "composer-bar" },
      plus,
      el("span", { class: "composer-spacer" }),
      modelPill,
      micBtn,
      sendBtn,
    );
    const box = el("div", { class: "composer" }, attachRow, textarea, bar);
    box.textarea = textarea;
    box.attachRow = attachRow;
    box.sendBtn = sendBtn;
    box.modelPill = modelPill;
    autoGrow();
    return box;
  }

  function refreshModelFoot() {
    const foot = $(".composer-foot-model");
    if (foot) foot.textContent = `${providerName()} · ${currentModelLabel()}`;
  }

  function openAddMenu(anchor) {
    const connectors = [{
      icon: "sparkles",
      label: "Claude-аккаунт",
      status: state.claude?.linked ? (state.claude.plan || "подключён") : "не подключён",
      connected: Boolean(state.claude?.linked),
      run: () => openModelSheet(),
    }];
    for (const item of Array.isArray(state.customApis) ? state.customApis : []) {
      connectors.push({
        icon: "plug",
        label: item.name || item.baseUrl || "Свой API",
        status: "свой API",
        connected: state.settings.customApiId === item.id,
        run: () => {
          state.settings = saveSettings({ provider: "custom", customApiId: item.id });
          refreshModelPill();
          ctx.toast(`Провайдер: ${item.name || "свой API"}`);
        },
      });
    }

    openPlusMenu(anchor, {
      projects: state.projects || [],
      currentProjectId: chat.projectId || "",
      webSearch: Boolean(state.settings.webSearch),
      theme: state.settings.theme,
      style: state.settings.style || "classic",
      skills: {
        artifacts: state.settings.artifacts,
        webSearch: state.settings.webSearch,
        codeExecution: state.settings.codeExecution,
        visualizations: state.settings.visualizations,
        memory: state.settings.memory,
      },
      connectors,
      onOpenPicker: (tab) => openFilePicker({
        artifacts: collectArtifacts(state),
        initialTab: tab,
        onFiles: addFiles,
        onArtifact: attachArtifact,
      }),
      onAddToProject: async (project) => {
        chat.projectId = project.id;
        await saveChatLocal();
        ctx.refreshSidebar?.();
        ctx.toast(`Чат в проекте «${project.title}»`);
      },
      onNewProject: async () => {
        const title = await ctx.prompt({ title: "Новый проект", placeholder: "Название проекта" });
        if (!title?.trim()) return;
        const project = { id: uid("prj"), title: title.trim(), createdAt: Date.now(), updatedAt: Date.now(), instructions: "" };
        await store.putProject(project);
        state.projects = [project, ...(state.projects || [])];
        chat.projectId = project.id;
        await saveChatLocal();
        ctx.refreshSidebar?.();
        ctx.toast(`Проект «${project.title}» создан`);
      },
      onAddFromGithub: () => addFromGithub(),
      onToggleSkill: (key) => {
        state.settings = saveSettings({ [key]: !state.settings[key] });
        ctx.toast(`${SKILL_LABELS[key] || key}: ${state.settings[key] ? "включено" : "выключено"}`);
      },
      onToggleWebSearch: (value) => {
        state.settings = saveSettings({ webSearch: value });
        ctx.toast(value ? "Веб-поиск включён" : "Веб-поиск выключен");
      },
      onTheme: (value) => {
        state.settings = saveSettings({ theme: value });
        ctx.applyTheme?.();
      },
      onStyle: (value) => {
        state.settings = saveSettings({ style: value });
        ctx.applyTheme?.();
        const label = value === "glass" ? "iOS Glass" : value === "noir" ? "Noir" : "Классика";
        ctx.toast(`Стиль: ${label}`, "success");
      },
      onOpenSettings: (tab) => {
        closePlusMenu();
        ctx.openSettings?.(tab);
      },
    });
  }

  async function attachArtifact(item) {
    if (!item) return;
    let text = "";
    if (item.kind === "code") {
      text = item.code || "";
    } else if (item.url) {
      try {
        text = await (await fetch(item.url)).text();
      } catch {
        text = "";
      }
    }
    if (!text) {
      toast(`«${item.name}» не сохранился в чате — приложить нечего`, "error");
      return;
    }
    const mime = item.mime || guessMime(item.name);
    attachments.push({
      id: uid("att"),
      name: item.name,
      mime,
      size: new Blob([text]).size,
      data: textToBase64(text),
      kind: "file",
      source: "artifact",
    });
    refreshAttachRow();
  }

  async function addFromGithub() {
    const url = await ctx.prompt({
      title: "Добавить из GitHub",
      text: "Ссылка на репозиторий или файл — Clauden приложит README.",
      placeholder: "https://github.com/user/repo",
    });
    if (!url?.trim()) return;
    const match = String(url).trim().match(/github\.com\/([^/]+)\/([^/#?]+)(?:\/(?:blob|raw)\/[^/]+\/(.+))?/i);
    if (!match) {
      toast("Это не похоже на ссылку GitHub", "error");
      return;
    }
    const [, owner, repoRaw, path] = match;
    const repo = repoRaw.replace(/\.git$/i, "");
    const target = path
      ? `https://raw.githubusercontent.com/${owner}/${repo}/HEAD/${path}`
      : `https://raw.githubusercontent.com/${owner}/${repo}/HEAD/README.md`;
    try {
      const res = await fetch(target);
      if (!res.ok) throw new Error(String(res.status));
      const text = await res.text();
      const name = path ? path.split("/").pop() : `${repo}-README.md`;
      attachments.push({
        id: uid("att"),
        name,
        mime: guessMime(name),
        size: new Blob([text]).size,
        data: textToBase64(text),
        kind: "file",
        source: "github",
      });
      refreshAttachRow();
      ctx.toast(`Добавлено из GitHub: ${name}`);
    } catch {
      toast("Не удалось прочитать файл из GitHub", "error");
    }
  }

  function refreshAttachRow() {
    const row = composer.attachRow;
    clear(row);
    row.hidden = !attachments.length;
    for (const item of attachments) {
      const ext = (item.name.split(".").pop() || "").toUpperCase().slice(0, 5);
      const card = el("div", { class: `att-card ${item.kind === "image" && item.data ? "is-image" : "is-file"}`, title: `${item.name} · ${bytes(item.size)}` });
      if (item.kind === "image" && item.data) {
        card.append(el("img", { class: "att-card-img", src: `data:${item.mime};base64,${item.data}`, alt: "" }));
      } else {
        card.append(el("div", { class: "att-card-glyph", html: icon("file") }));
      }
      card.append(
        el("div", { class: "att-card-name", text: item.name }),
        el("div", { class: "att-card-badge", text: ext || "FILE" }),
        el("button", {
          class: "att-card-x",
          title: "Убрать",
          onclick: (e) => {
            e.stopPropagation();
            attachments = attachments.filter((a) => a.id !== item.id);
            refreshAttachRow();
          },
          html: icon("close"),
        }),
      );
      row.append(card);
    }
    composer.sendBtn.disabled = streaming ? false : (!composer.textarea.value.trim() && !attachments.length);
  }

  async function addFiles(files) {
    for (const file of files) {
      if (file.size > 20 * 1024 * 1024) {
        toast(`Файл «${file.name}» больше 20 МБ`, "error");
        continue;
      }
      try {
        const base64 = await fileToBase64(file);
        const mime = file.type || guessMime(file.name);
        attachments.push({
          id: uid("att"),
          name: file.name,
          mime,
          size: file.size,
          data: base64,
          kind: mime.startsWith("image/") ? "image" : "file",
        });
      } catch {
        toast(`Не удалось прочитать «${file.name}»`, "error");
      }
    }
    refreshAttachRow();
  }

  function renderAllMessages() {
    clear(thread);
    for (const message of chat.messages) {
      thread.append(messageNode(message));
    }
    scrollToBottom(true);
  }

  function updateEmpty() {
    chatEl.classList.toggle("is-empty", !chat.messages.length);
  }

  function messageNode(message) {
    if (message.role === "user") return userNode(message);
    return assistantNode(message);
  }

  function userNode(message) {
    const bubble = el("div", { class: "bubble" });
    if (message.attachments?.length) {
      const row = el("div", { class: "composer-attach sent", style: { marginBottom: "8px" } });
      for (const item of message.attachments) {
        const ext = (item.name.split(".").pop() || "").toUpperCase().slice(0, 5);
        const card = el("div", { class: `att-card ${item.kind === "image" && item.data ? "is-image" : "is-file"}`, title: `${item.name} · ${bytes(item.size)}` });
        if (item.kind === "image" && item.data) {
          card.append(el("img", { class: "att-card-img", src: `data:${item.mime};base64,${item.data}`, alt: "" }));
        } else {
          card.append(el("div", { class: "att-card-glyph", html: icon("file") }));
        }
        card.append(
          el("div", { class: "att-card-name", text: item.name }),
          el("div", { class: "att-card-badge", text: ext || "FILE" }),
        );
        row.append(card);
      }
      bubble.append(row);
    }
    if (message.content) {
      const text = String(message.content);
      const long = text.length > 700 || (text.match(/\n/g) || []).length > 12;
      if (long) {

        bubble.append(
          el("details", { class: "user-prompt-long" },
            el("summary", null,
              el("span", { class: "upl-icon", html: icon("file") }),
              el("span", { class: "upl-title", text: "Длинный запрос" }),
              el("span", { class: "upl-meta", text: `${text.length} символов · показать` }),
            ),
            el("div", { class: "user-prompt-body", text }),
          ),
        );
      } else {
        bubble.append(el("div", { text }));
      }
    }
    const node = el("div", { class: "msg msg-user", dataset: { id: message.id } }, bubble);
    return node;
  }

  function assistantNode(message) {
    const node = el("div", { class: "msg msg-assistant", dataset: { id: message.id } });
    const activity = activityNode(message);
    if (activity) node.append(activity);
    const reasoning = reasoningNode(message);
    if (reasoning) node.append(reasoning);
    const body = el("div", { class: "body" });
    const md = el("div", { class: "md", html: renderMarkdown(message.content || "") });
    enhanceMarkdown(md);
    if (message.streaming) body.classList.add("streaming");
    body.append(md);
    node.append(body);
    const extras = el("div", { class: "extras" });
    renderExtras(extras, message);
    node.append(extras);
    node.append(actionsNode(message));
    return node;
  }

  function reasoningNode(message) {
    const text = String(message.thinking || "").trim();

    if (!text || !isClaudeMessage(message)) return null;
    const body = el("div", { class: "reasoning-body", text });
    const details = el("details", { class: "reasoning", ...(message.streaming ? { open: true } : {}) },
      el("summary", { class: "reasoning-head" },
        el("span", { class: "reasoning-icon", html: icon("sparkles") }),
        el("span", { text: message.streaming ? "Размышляю…" : "Размышления" }),
        el("span", { class: "reasoning-chevron", html: icon("chevronDown") }),
      ),
      body,
    );
    return details;
  }

  function visibleSteps(message) {
    return (message.steps || []).filter((step) => String(step?.name || "").toLowerCase() !== "skill");
  }

  function durationLabel(ms) {
    const value = Number(ms) || 0;
    if (value >= 3_600_000) return `${Math.floor(value / 3_600_000)} ч`;
    if (value >= 60_000) return `${Math.floor(value / 60_000)} мин`;
    if (value >= 10_000) return `${Math.round(value / 1000)} с`;
    if (value >= 1000) return `${(Math.floor(value / 100) / 10).toFixed(1)} с`;
    return "меньше секунды";
  }

  function actionLabel(step) {
    const name = String(step?.name || "").toLowerCase();
    const title = String(step?.title || "").toLowerCase();
    if (["websearch", "web_search", "web_search_v0"].includes(name) || title.startsWith("ищу:")) return "Ищу в интернете";
    if (name === "webfetch" || title.startsWith("открываю:")) return "Открываю страницу";
    if (name === "read" || name === "view" || title.startsWith("читаю")) return "Читаю файлы";
    if (["write", "edit", "create_file"].includes(name) || title.startsWith("правлю") || title.startsWith("создаю")) return "Работаю с файлами";
    if (name === "shell" || name === "bash") return "Запускаю код";
    if (name === "task" || name === "agent" || name === "subagent") return "Запускаю параллельного агента";
    if (name === "glob" || name === "grep") return "Ищу в файлах";
    if (name === "skill") return "Загружаю навык";
    return "Работаю";
  }

  function actionIcon(label) {
    if (label === "Ищу в интернете" || label === "Открываю страницу") return "globe";
    if (label === "Запускаю код") return "terminal";
    if (["Работаю с файлами", "Читаю файлы", "Ищу в файлах"].includes(label)) return "file";
    return "sparkles";
  }

  function stepIcon(step) {
    const name = String(step?.name || "").toLowerCase();
    if (["view", "read", "glob", "grep"].includes(name)) return "file";
    if (["bash", "shell", "repl"].includes(name)) return "terminal";
    if (["web_search_v0", "web_search", "websearch", "webfetch"].includes(name)) return "globe";
    return "sparkles";
  }

  function groupSteps(steps) {
    const order = [];
    const map = new Map();
    for (const step of steps) {
      if (String(step?.name || "").toLowerCase() === "skill") continue;
      const key = actionLabel(step);
      if (!map.has(key)) {
        map.set(key, { label: key, count: 0, failed: false });
        order.push(key);
      }
      const entry = map.get(key);
      entry.count += 1;
      if (step.status === "failed") entry.failed = true;
    }
    return order.map((key) => map.get(key));
  }

  function isClaudeMessage(message) {
    return (message.provider || chat.provider || state.settings.provider) === "claude";
  }

  function activityLabel(message) {
    const steps = visibleSteps(message);
    const active = [...steps].reverse().find((step) => step.status === "running");
    const answering = Boolean(message.streaming) && String(message.content || "").trim().length > 0;
    const elapsed = message.streaming ? Date.now() - (message.startedAt || message.ts || Date.now()) : 0;
    const thinkingMs = isClaudeMessage(message) ? Number(message.thinkingMs) || 0 : 0;
    if (answering) return thinkingMs > 0 ? `Думал ${durationLabel(thinkingMs)}` : "Думаю…";
    if (active) {
      const base = `${actionLabel(active)}…`;
      return elapsed > 10_000 ? `${base} ${durationLabel(elapsed)}` : base;
    }
    if (message.streaming) {

      return elapsed > 10_000 ? `Думаю… ${durationLabel(elapsed)}` : "Думаю…";
    }
    if (thinkingMs > 0) return `Думал ${durationLabel(thinkingMs)}`;
    return "Готово";
  }

  function stepsLink(message, steps) {
    const running = Boolean(message.streaming);
    const active = [...steps].reverse().find((step) => step.status === "running") || steps[steps.length - 1];
    const label = running
      ? `${active?.title || active?.name || "Шаг"}…`
      : `Шагов: ${steps.length}`;
    return el("button", {
      class: "steps-link",
      dataset: { id: message.id, live: running ? "1" : "0" },
      onclick: () => openStepsSheet(message),
    },
      el("span", { class: "steps-link-icon", html: icon(running ? "clock" : "layers") }),
      el("span", { class: "steps-link-label", text: label }),
      el("span", { class: "steps-link-chevron", html: icon("chevronRight") }),
    );
  }

  function activityNode(message) {
    const steps = visibleSteps(message);
    const thinkingMs = isClaudeMessage(message) ? Number(message.thinkingMs) || 0 : 0;
    if (!steps.length && !thinkingMs && !message.streaming) return null;

    if (isClaudeMessage(message)) {
      if (steps.length) return stepsLink(message, steps);
      if (!message.streaming) return null;
      return el("div", { class: "thinking-row" },
        el("span", { class: "thinking-row-logo spin", html: claudeMark(18) }),
        el("span", { text: "Думаю…" }),
      );
    }

    const failed = steps.some((step) => step.status === "failed");
    const running = Boolean(message.streaming);
    const groups = groupSteps(steps);
    const body = el("div", { class: "activity-body", hidden: true });
    if (groups.length) {
      body.append(el("div", { class: "activity-caption", text: "Действия" }));
      for (const group of groups) {
        body.append(el("div", { class: "activity-row" },
          el("span", { class: "activity-row-icon", html: icon(actionIcon(group.label)) }),
          el("span", { class: "activity-row-label", text: group.label }),
          group.count > 1 ? el("span", { class: "activity-row-count", text: String(group.count) }) : null,
          group.failed ? el("span", { class: "activity-row-warn", html: icon("alert") }) : null,
        ));
      }
    }
    const head = el("button", {
      class: "activity-head",
      disabled: !groups.length,
      onclick: () => {
        body.hidden = !body.hidden;
        head.classList.toggle("open", !body.hidden);
      },
    },
      el("span", {
        class: `activity-logo${running ? " spin" : ""}${failed && !running ? " failed" : ""}`,
        html: failed && !running ? icon("alert") : claudeMark(16),
      }),
      el("span", { class: "activity-label", text: activityLabel(message) }),
      groups.length ? el("span", { class: "activity-chevron", html: icon("chevronRight") }) : null,
    );
    return el("div", {
      class: "activity",
      dataset: { id: message.id, live: message.streaming ? "1" : "0" },
    }, head, body);
  }

  function openStepsSheet(message) {
    const steps = visibleSteps(message);
    if (!steps.length) return;
    const root = document.querySelector("#modal-root") || document.body;
    const close = () => backdrop.remove();
    const list = el("div", { class: "steps-sheet" });
    for (const step of steps) {
      list.append(el("button", {
        class: "step-row",
        onclick: () => {
          close();
          openStepSheet(step);
        },
      },
        el("span", { class: "step-row-icon", html: icon(stepIcon(step)) }),
        el("span", { class: `step-row-title${step.status === "failed" ? " failed" : ""}`, text: step.title || step.name || "Шаг" }),
        el("span", { class: "step-row-chevron", html: icon("chevronRight") }),
      ));
    }
    const backdrop = el("div", { class: "modal-backdrop", onclick: (event) => { if (event.target === backdrop) close(); } },
      el("div", { class: "modal", style: { width: "min(520px, 100%)" } },
        el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Сводка" })),
        el("div", { class: "modal-body" }, list),
        el("div", { class: "modal-foot" }, el("button", { class: "btn btn-ghost", text: "Закрыть", onclick: close })),
      ),
    );
    root.append(backdrop);
  }

  function openStepSheet(step) {
    const root = document.querySelector("#modal-root") || document.body;
    const close = () => backdrop.remove();
    const section = (label, value) => el("div", { class: "step-block" },
      el("div", { class: "step-block-label", text: label }),
      el("div", { class: "step-block-text", text: value }),
    );
    const backdrop = el("div", { class: "modal-backdrop", onclick: (event) => { if (event.target === backdrop) close(); } },
      el("div", { class: "modal", style: { width: "min(640px, 100%)" } },
        el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: step.title || step.name || "Шаг" })),
        el("div", { class: "modal-body" },
          section("Запрос", step.detail || "Запрос не сохранился"),
          section("Ответ", step.result || (step.status === "running" ? "Инструмент ещё выполняется" : "Ответ не сохранился")),
        ),
        el("div", { class: "modal-foot" }, el("button", { class: "btn btn-ghost", text: "Закрыть", onclick: close })),
      ),
    );
    root.append(backdrop);
  }

  let activityTicker = null;
  function tickActivity() {
    let live = 0;
    for (const node of thread.querySelectorAll('[data-live="1"]')) {
      const message = chat.messages.find((item) => item.id === node.dataset.id);
      if (!message || !message.streaming) continue;
      live += 1;
      if (node.classList.contains("steps-link")) {
        const steps = visibleSteps(message);
        const active = [...steps].reverse().find((step) => step.status === "running") || steps[steps.length - 1];
        const label = node.querySelector(".steps-link-label");
        if (label) label.textContent = active ? `${active.title || active.name || "Шаг"}…` : "Работаю…";
      } else {
        const label = node.querySelector(".activity-label");
        if (label) label.textContent = activityLabel(message);
      }
    }
    if (!live && activityTicker) {
      clearInterval(activityTicker);
      activityTicker = null;
    }
  }
  function ensureActivityTicker() {
    if (activityTicker) return;
    activityTicker = setInterval(tickActivity, 1000);
  }

  function renderExtras(container, message) {
    clear(container);
    if (message.files?.length) container.append(filesGrid(message.files));
    for (const action of message.actions || []) {
      if (action?.widget) container.append(widgetNode(action.widget));
      else if (action?.type) {
        container.append(el("div", { class: "action-chips" },
          el("span", { class: "action-chip" }, el("span", { html: icon("wand") }), el("span", { text: actionTitle(action) })),
        ));
      }
    }
    if (message.sources?.length) {
      const panel = el("div", { class: "web-search-body" });
      for (const source of message.sources.slice(0, 12)) {
        if (!source?.url) continue;
        panel.append(el("a", { class: "web-result", href: source.url, target: "_blank", rel: "noopener noreferrer", title: source.url },
          el("span", { class: "favicon", html: icon("globe") }),
          el("span", { class: "wr-title", text: source.title || hostOf(source.url) }),
          el("span", { class: "wr-domain", text: hostOf(source.url) }),
        ));
      }
      if (panel.childNodes.length) {
        const head = el("button", { class: "web-search-head", onclick: () => { panel.hidden = !panel.hidden; } },
          el("span", { class: "think-dot" }),
          el("span", { text: "Искал в интернете" }),
          el("span", { html: icon("chevronDown"), style: { width: "13px", height: "13px", opacity: ".7" } }),
        );
        container.append(el("div", { class: "web-search" }, head, panel));
      }
    }
  }

  function filesGrid(files) {
    const grid = el("div", { class: "files-grid" });
    for (const file of files) {
      const mime = file.mime || "";
      const isImage = mime.startsWith("image/");
      const isVideo = mime.startsWith("video/");
      if (isImage && file.url) {
        grid.append(el("a", { class: "media-item", href: file.url, target: "_blank", rel: "noopener", title: file.name },
          el("img", { src: file.url, alt: file.name, loading: "lazy" })));
      } else if (isVideo && file.url) {
        grid.append(el("div", { class: "media-item" },
          el("video", { src: file.url, controls: true, preload: "metadata" })));
      } else {
        grid.append(el("a", {
          class: "file-card",
          href: file.url || "#",
          target: file.url ? "_blank" : "",
          rel: "noopener",
          onclick: file.url ? null : (e) => e.preventDefault(),
        },
          el("span", { class: "file-icon", html: icon("file") }),
          el("span", { class: "file-text" },
            el("span", { class: "file-name", text: file.name || "Файл" }),
            el("span", { class: "file-size", text: bytes(file.size) }),
          ),
          file.url ? el("span", { style: { width: "16px", height: "16px", color: "var(--text-3)" }, html: icon("download") }) : null,
        ));
      }
    }
    return grid;
  }

  function widgetNode(widget) {
    const card = el("div", { class: "widget-card" });
    if (widget.title) card.append(el("div", { class: "widget-title", text: widget.title }));
    if (widget.html) {
      card.append(el("iframe", { class: "widget-frame", sandbox: "allow-scripts", srcdoc: widget.html }));
      return card;
    }
    const labels = widget.labels || [];
    const values = (widget.values || []).map(Number);
    const max = Math.max(1, ...values);
    if (widget.kind === "pie") {
      const total = values.reduce((a, b) => a + b, 0) || 1;
      let angle = 0;
      const colors = ["#d97757", "#6da7ec", "#79c47f", "#e5c07b", "#c678dd", "#56b6c2"];
      const stops = values.map((value, i) => {
        const start = angle;
        angle += (value / total) * 360;
        return `${colors[i % colors.length]} ${start}deg ${angle}deg`;
      }).join(", ");
      card.append(el("div", { style: { display: "flex", gap: "16px", alignItems: "center", flexWrap: "wrap" } },
        el("div", { style: { width: "120px", height: "120px", borderRadius: "50%", background: `conic-gradient(${stops})` } }),
        el("div", { class: "widget-bars", style: { flex: "1", minWidth: "140px" } },
          ...labels.map((label, i) => el("div", { class: "widget-bar" },
            el("span", { text: label }),
            el("span", { class: "widget-bar-value", text: `${values[i] ?? 0}${widget.unit || ""}` }),
          )),
        ),
      ));
      return card;
    }
    const bars = el("div", { class: "widget-bars" });
    labels.forEach((label, i) => {
      bars.append(el("div", { class: "widget-bar" },
        el("span", { text: label }),
        el("span", { class: "widget-bar-track" }, el("span", { class: "widget-bar-fill", style: { width: `${Math.max(2, ((values[i] || 0) / max) * 100)}%` } })),
        el("span", { class: "widget-bar-value", text: `${values[i] ?? 0}${widget.unit || ""}` }),
      ));
    });
    card.append(bars);
    return card;
  }

  function actionsNode(message) {
    if (message.streaming) return el("div", { class: "msg-actions visible" });
    const actions = el("div", { class: "msg-actions" },
      el("button", { class: "icon-btn", title: "Скопировать", onclick: () => { copyText(message.content || ""); toast("Скопировано"); }, html: icon("copy") }),
      el("button", { class: "icon-btn", title: "Озвучить", onclick: () => speak(message.content || ""), html: icon("speaker") }),
      el("button", { class: `icon-btn ${message.feedback === "up" ? "active" : ""}`, title: "Хороший ответ", onclick: (e) => setFeedback(message, "up", e.currentTarget), html: icon("thumbsUp") }),
      el("button", { class: `icon-btn ${message.feedback === "down" ? "active" : ""}`, title: "Плохой ответ", onclick: (e) => setFeedback(message, "down", e.currentTarget), html: icon("thumbsDown") }),
      el("button", { class: "icon-btn", title: "Повторить", onclick: () => retryFrom(message), html: icon("refresh") }),
      el("span", { class: "msg-time", text: relTimeShort(message.ts) }),
    );
    return actions;
  }

  function speak(text) {
    if (!("speechSynthesis" in window)) {
      toast("Браузер не умеет озвучивать текст", "error");
      return;
    }
    const clean = stripMarkdown(text || "").slice(0, 4000);
    if (!clean) return;
    const synth = window.speechSynthesis;
    if (synth.speaking) synth.cancel();
    const utterance = new SpeechSynthesisUtterance(clean);
    utterance.lang = "ru-RU";
    utterance.rate = 1;
    synth.speak(utterance);
  }

  function setFeedback(message, value, button) {
    message.feedback = message.feedback === value ? "" : value;
    button.parentElement.querySelectorAll("[data-feedback]").forEach((b) => b.classList.remove("active"));
    button.classList.toggle("active", Boolean(message.feedback));
    saveChatLocal();
  }

  const claudeStatusReady = async () => {
    if (state.claude && !state.claude.pending && !state.claude.error) return state.claude;
    try {
      state.claude = await Promise.race([
        claudeDesktop.status(),
        new Promise((resolve) => setTimeout(() => resolve({ linked: false, timedOut: true }), 15000)),
      ]);
    } catch (err) {
      state.claude = { linked: false, error: err.message };
    }
    return state.claude;
  };

  function applyServerTurnToAssistant(assistant, turn) {
    if (!turn) return;
    const text = typeof turn.text === "string" ? turn.text : "";
    if (text.trim()) assistant.content = text;
    if (Array.isArray(turn.steps) && turn.steps.length) {
      for (const step of turn.steps) {
        if (!step) continue;
        const index = assistant.steps.findIndex(
          (item) => (item.id && item.id === step.id) || (!item.id && item.name === step.name),
        );
        if (index >= 0) assistant.steps[index] = { ...assistant.steps[index], ...step };
        else assistant.steps.push(step);
      }
    }
    if (Array.isArray(turn.files) && turn.files.length) assistant.files = turn.files;
    if (Array.isArray(turn.actions) && turn.actions.length) assistant.actions = turn.actions;
    if (Array.isArray(turn.sources) && turn.sources.length) assistant.sources = turn.sources;
  }

  async function recoverServerTurn({
    chat,
    assistant,
    turnId,
    extras,
    updateText,
    maxMisses = 24,
    maxNetworkErrors = 600,
    notify = true,
  }) {
    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const deadline = Date.now() + 30 * 60_000;
    let misses = 0;
    let networkFailures = 0;
    let activeTurnId = turnId || "";
    if (notify) toast("Связь прервалась — забираю ответ с сервера", "info");
    while (Date.now() < deadline) {
      if (controller?.signal?.aborted) return { status: "stopped" };

      let active = null;
      try {
        active = (await api.activeTurn(chat.id))?.data || null;
      } catch (err) {
        if (!isNetworkError(err)) return { status: "failed", error: err };
        networkFailures += 1;
        if (networkFailures > maxNetworkErrors) break;
        await sleep(3000);
        continue;
      }

      const isLastReply = chat.messages.at(-1)?.id === assistant.id;
      if (active && (!activeTurnId || !active.turnId || active.turnId === activeTurnId || isLastReply)) {
        misses = 0;
        activeTurnId = active.turnId || activeTurnId;
        if (activeTurnId) assistant.turnId = activeTurnId;
        applyServerTurnToAssistant(assistant, active);
        updateText();
        refreshActivity(assistant);
        renderExtras(extras, assistant);
        if (active.status === "error") {
          return {
            status: "error",
            message: active.error || "Не удалось получить ответ. Попробуйте ещё раз.",
          };
        }
        if (active.status === "stopped") return { status: "stopped" };
        await sleep(2500);
        continue;
      }

      let turns = [];
      try {
        turns = (await api.turns(chat.id))?.data || [];
      } catch (err) {
        if (!isNetworkError(err)) return { status: "failed", error: err };
        networkFailures += 1;
        if (networkFailures > maxNetworkErrors) break;
        await sleep(3000);
        continue;
      }
      const turn = turns.find((item) => item?.turnId === activeTurnId) ||
        turns
          .filter((item) => !activeTurnId && Number(item?.ts || 0) >= (assistant.startedAt || assistant.ts || 0) - 60_000)
          .at(-1) ||
        null;
      if (turn) {
        if (turn.turnId) assistant.turnId = turn.turnId;
        applyServerTurnToAssistant(assistant, turn);
        if (turn.incomplete) {
          assistant.content += assistant.content
            ? "\n\n_Ответ прерван._"
            : "_Ответ прерван._";
        }
        updateText();
        refreshActivity(assistant);
        renderExtras(extras, assistant);
        return { status: "done", incomplete: Boolean(turn.incomplete) };
      }

      misses += 1;
      if (misses > maxMisses) break;
      await sleep(3000);
    }
    return {
      status: "failed",
      error: new ApiError("Не удалось получить ответ с сервера. Попробуйте ещё раз.", 0, "network"),
    };
  }

  async function resumePendingTurn(assistant) {
    if (!assistant || streaming) return;
    const node = thread.querySelector(`[data-id="${assistant.id}"]`);
    if (!node) return;
    const md = node.querySelector(".md");
    const extras = node.querySelector(".extras");
    const updateText = throttleRaf(() => {
      if (md) {
        md.innerHTML = renderMarkdown(assistant.content);
        enhanceMarkdown(md);
      }
      if (nearBottom()) scrollToBottom(false);
    });

    streaming = true;
    controller = new AbortController();
    turnId = assistant.turnId || "";
    ensureActivityTicker();
    composer.sendBtn.innerHTML = icon("stop");
    composer.sendBtn.classList.add("stop");
    composer.sendBtn.disabled = false;
    bodyStreaming(node, true);

    try {
      const recovered = await recoverServerTurn({
        chat,
        assistant,
        turnId,
        extras,
        updateText,
        maxMisses: 1,
        maxNetworkErrors: 1,
        notify: false,
      });
      if (recovered?.status === "done") {
        if (recovered.incomplete) toast("Ответ прерван, но сохранён в чате", "info");
        finish(false, null, assistant);
        return;
      }
      if (recovered?.status === "stopped") {
        assistant.content += assistant.content ? "\n\n_Ответ остановлен._" : "_Ответ остановлен._";
        updateText();
        finish(true, null, assistant);
        return;
      }
      if (recovered?.status === "error") {
        const error = new ApiError(recovered.message || "Не удалось получить ответ", 0, "stream_error");
        showError(node, error);
        finish(true, error, assistant);
        return;
      }

      assistant.content += assistant.content
        ? "\n\n_Ответ прерван._"
        : "_Ответ прерван._";
      updateText();
      finish(true, null, assistant);
    } catch (err) {
      if (err?.name === "AbortError") {
        assistant.content += assistant.content ? "\n\n_Ответ остановлен._" : "_Ответ остановлен._";
        updateText();
        finish(true, null, assistant);
        return;
      }
      assistant.content += assistant.content
        ? "\n\n_Ответ прерван._"
        : "_Ответ прерван._";
      updateText();
      finish(true, err, assistant);
    }
  }

  function bodyStreaming(node, on) {
    const body = node?.querySelector(".body");
    if (!body) return;
    body.classList.toggle("streaming", Boolean(on));
  }

  async function send() {
    if (streaming) return;

    if (provider === "clauden" && isDesktopApp() && planIsFree()) {
      await claudeStatusReady();
      if (state.claude?.linked) {
        await applyProvider("claude");
        toast("Тариф Free не включает шлюз Clauden — отвечает ваш Claude-аккаунт", "info");
      }
    }
    if (provider === "claude" && !isDesktopApp()) {
      toast("Claude-режим доступен только в приложении Clauden для ПК", "error");
      return;
    }
    if (provider === "claude") {
      await claudeStatusReady();
      if (!state.claude?.linked) {
        toast(
          state.claude?.timedOut
            ? "Claude не отвечает. Проверьте VPN и соединение."
            : "Сначала войдите в Claude",
          "error",
        );
        openModelSheet();
        return;
      }
    }
    if (provider === "custom") {
      if (!isDesktopApp() || !isCustomApiSupported()) {
        toast("Свои API доступны только в приложении Clauden для ПК", "error");
        return;
      }
      if (!currentApi() || !currentModel()) {
        toast("Сначала добавьте свой API и выберите модель", "error");
        openModelSheet();
        return;
      }
    }
    const text = composer.textarea.value.trim();
    if (!text && !attachments.length) return;

    const messageAttachments = attachments;
    attachments = [];
    refreshAttachRow();
    composer.textarea.value = "";
    composer.textarea.style.height = "auto";
    saveDraft(chat.id, "");

    const userMessage = {
      id: uid("msg"),
      role: "user",
      content: text,
      ts: Date.now(),
      attachments: messageAttachments,
    };
    chat.messages.push(userMessage);
    chat.provider = provider;
    if (!chat.title) chat.title = titleFromText(text || messageAttachments[0]?.name || "Новый чат");
    updateEmpty();
    thread.append(userNode(userMessage));
    scrollToBottom(true);
    syncShellTitle();
    composer.sendBtn.disabled = false;

    const assistant = {
      id: uid("msg"),
      role: "assistant",
      content: "",
      thinking: "",
      ts: Date.now(),
      startedAt: Date.now(),
      provider,
      steps: [],
      files: [],
      actions: [],
      sources: [],
      thinkingMs: 0,
      streaming: true,
    };

    let firstTokenAt = 0;
    const markFirstToken = () => {
      if (firstTokenAt) return;
      firstTokenAt = Date.now();

      if (assistant.thinking && !assistant.thinkingMs) {
        assistant.thinkingMs = firstTokenAt - assistant.startedAt;
      }
      refreshActivity(assistant);
    };
    chat.messages.push(assistant);
    const node = assistantNode(assistant);
    thread.append(node);
    scrollToBottom(true);

    streaming = true;
    ensureActivityTicker();
    composer.sendBtn.innerHTML = icon("stop");
    composer.sendBtn.classList.add("stop");
    composer.sendBtn.disabled = false;
    turnId = uid("turn");
    assistant.turnId = turnId;
    controller = new AbortController();
    pendingFiles = messageAttachments;

    const body = node.querySelector(".body");
    const md = node.querySelector(".md");
    const extras = node.querySelector(".extras");
    const updateText = throttleRaf(() => {
      md.innerHTML = renderMarkdown(assistant.content);
      enhanceMarkdown(md);
      if (nearBottom()) scrollToBottom(false);
    });

    try {
      if (!persisted) {
        persisted = true;
        await saveChatLocal();
        history.replaceState(null, "", `/chat/${chat.id}`);
        ctx.state.route = { name: "chat", id: chat.id, params: new URLSearchParams() };
      } else {
        await saveChatLocal();
      }

      if (provider === "claude") {
        const history = chat.messages
          .filter((message) => !message.streaming && String(message.content || "").trim())
          .map((message) => ({ role: message.role, content: message.content }));
        await streamClaudeDesktop({
          chatId: chat.id,
          conversationId: chat.claudeConversationId || "",
          model: currentModel(),
          messages: history,
          system: buildSystemPrompt(state, project),
          project: buildProjectContext(project),
          memory: buildMemoryContext(chat, state.chats),
          attachments: messageAttachments.map((file) => ({ name: file.name, mime: file.mime, data: file.data })),
          webSearch: Boolean(state.settings.webSearch),
          thinking: Boolean(state.settings.thinking),
        }, {
          signal: controller.signal,
          handlers: {
            onConversation: (id) => {
              chat.claudeConversationId = id;
              saveChatLocal().catch(() => {});
            },
            onDelta: (piece) => {
              markFirstToken();
              assistant.content += piece;
              updateText();
            },
            onStep: (step) => {
              if (!step?.id) return;
              const index = assistant.steps.findIndex((item) => item.id === step.id);
              if (index >= 0) assistant.steps[index] = { ...assistant.steps[index], ...step };
              else assistant.steps.push(step);
              refreshActivity(assistant);
            },
            onDone: (text) => {
              const aborted = Boolean(controller?.signal?.aborted);
              if (text && !aborted) {
                assistant.content = text;
                updateText();
              }
              if (aborted) {
                assistant.content += assistant.content ? "\n\n_Ответ остановлен._" : "_Ответ остановлен._";
                updateText();
                finish(true, null, assistant);
              } else {
                finish(false, null, assistant);
              }
            },
          },
        });
      } else if (provider === "custom") {
        const selectedApi = currentApi();
        const history = chat.messages
          .filter((message) => !message.streaming && (message.role === "user" || message.role === "assistant") && String(message.content || "").trim())
          .map((message) => ({ role: message.role, content: message.content }));
        const extracted = [];
        for (const file of messageAttachments) {
          if (file.kind === "image") continue;
          try {
            const result = await api.extract(file.name, file.mime, file.data);
            if (result?.text) extracted.push(`Файл «${file.name}»:\n${result.text}`);
          } catch {
            extracted.push(`Файл «${file.name}» не удалось прочитать.`);
          }
        }
        if (extracted.length) {
          for (let index = history.length - 1; index >= 0; index -= 1) {
            if (history[index].role !== "user") continue;
            history[index] = { ...history[index], content: `${history[index].content}\n\n${extracted.join("\n\n")}` };
            break;
          }
        }
        await streamCustomApi({
          apiId: selectedApi.id,
          model: currentModel(),
          messages: history,
          system: buildSystemPrompt(state, project),
          attachments: messageAttachments
            .filter((file) => file.kind === "image")
            .map((file) => ({ name: file.name, mime: file.mime, data: file.data })),
        }, {
          signal: controller.signal,
          handlers: {
            onDelta: (piece) => {
              assistant.content += piece;
              updateText();
            },
            onDone: (text) => {
              const aborted = Boolean(controller?.signal?.aborted);
              if (text && !aborted) {
                assistant.content = text;
                updateText();
              }
              if (aborted) {
                assistant.content += assistant.content ? "\n\n_Ответ остановлен._" : "_Ответ остановлен._";
                updateText();
                finish(true, null, assistant);
              } else {
                finish(false, null, assistant);
              }
            },
          },
        });
      } else {
        const payload = buildPayload(chat, assistant);
        await streamChat(payload, {
          conversationId: chat.id,
          turnId,
          signal: controller.signal,
          handlers: {
            onDelta: (piece) => {
              markFirstToken();
              assistant.content += piece;
              updateText();
            },
            onThinking: (piece) => {
              if (!piece) return;
              assistant.thinking = `${assistant.thinking || ""}${piece}`.slice(-60_000);
              refreshReasoning(assistant);
            },
            onThinkingMs: (value) => {
              assistant.thinkingMs = value;
            },
            onStep: (step) => {
              const index = assistant.steps.findIndex((s) => (s.id && s.id === step.id) || (!s.id && s.name === step.name));
              if (index >= 0) assistant.steps[index] = { ...assistant.steps[index], ...step };
              else assistant.steps.push(step);
              refreshActivity(assistant);
            },
            onFile: (file) => {
              assistant.files.push(file);
              renderExtras(extras, assistant);
              if (nearBottom()) scrollToBottom(false);
            },
            onAction: (action) => {
              if (action?.type === "task") {
                const title = String(action.title || action.text || "").trim();
                if (title) {
                  addTask(state.user?.id, title);
                  toast("Задача добавлена в список");
                }
              }
              assistant.actions.push(action);
              renderExtras(extras, assistant);
            },
            onSources: (sources) => {
              assistant.sources = sources || [];
              renderExtras(extras, assistant);
            },
            onDone: (text) => {
              if (text && text !== assistant.content) {
                assistant.content = text;
                updateText();
              }
              finish(false, null, assistant);
            },
          },
        });
      }
    } catch (err) {
      console.error("chat stream failed", err);
      if (err?.name === "AbortError") {
        assistant.content += assistant.content ? "\n\n_Ответ остановлен._" : "_Ответ остановлен._";
        md.innerHTML = renderMarkdown(assistant.content);
        finish(true, null, assistant);
        return;
      }
      let failure = err;
      if (
        provider === "clauden" &&
        !controller?.signal?.aborted &&
        (isNetworkError(err) ||
          err?.type === "incomplete_stream" ||
          err?.type === "empty_answer" ||
          err?.type === "turn_in_progress" ||
          err?.status === 409)
      ) {
        let recovered = null;
        try {
          recovered = await recoverServerTurn({
            chat,
            assistant,
            turnId,
            extras,
            updateText,
          });
        } catch (recoveryError) {
          console.error("chat recovery failed", recoveryError);
        }
        if (recovered?.status === "done") {
          if (recovered.incomplete) toast("Ответ прерван, но сохранён в чате", "info");
          finish(false, null, assistant);
          return;
        }
        if (recovered?.status === "stopped") {
          assistant.content += assistant.content ? "\n\n_Ответ остановлен._" : "_Ответ остановлен._";
          md.innerHTML = renderMarkdown(assistant.content);
          finish(true, null, assistant);
          return;
        }
        if (recovered?.status === "error") {
          failure = new ApiError(recovered.message, 0, "stream_error");
        } else if (recovered?.error) {
          failure = recovered.error;
        }
      }
      const errorBox = showError(node, failure);
      if (!assistant.content) {
        chat.messages = chat.messages.filter((m) => m.id !== assistant.id);

        if (errorBox?.parentElement === node) {
          node.replaceWith(errorBox);
          if (isNetworkError(failure) || failure?.type === "incomplete_stream" || failure?.type === "empty_answer") {
            errorBox.append(el("button", {
              class: "btn btn-secondary btn-sm",
              style: { marginTop: "8px" },
              text: "Повторить",
              onclick: () => retryLastUser(),
            }));
          }
        } else {
          node.remove();
        }
      } else {

        assistant.content += "\n\n_Ответ оборвался. Повторите запрос._";
        updateText();
      }
      finish(true, failure, assistant);
    }
  }

  function finish(stopped = false, error = null, assistant = null) {
    if (!assistant) return;
    if (!streaming) return;
    streaming = false;
    controller = null;
    assistant.streaming = false;
    const body = thread.querySelector(`[data-id="${assistant.id}"] .body`);
    body?.classList.remove("streaming");
    const node = thread.querySelector(`[data-id="${assistant.id}"]`);
    if (node && !error) {
      const actions = node.querySelector(".msg-actions");
      if (actions) actions.replaceWith(actionsNode(assistant));
    }
    const md = node?.querySelector(".md");
    if (md) { md.innerHTML = renderMarkdown(assistant.content); enhanceMarkdown(md); }
    refreshActivity(assistant);
    refreshReasoning(assistant);
    composer.sendBtn.innerHTML = icon("arrowUp");
    composer.sendBtn.classList.remove("stop");
    composer.sendBtn.disabled = !composer.textarea.value.trim() && !attachments.length;
    saveChatLocal();
    ctx.backupSoon(0);
    scrollToBottom(false);
    if (!stopped) refreshSidebarTitle();
    if (!stopped && document.hidden && state.settings.notifications && "Notification" in window && Notification.permission === "granted") {
      try {
        new Notification("Clauden", { body: stripMarkdown(assistant.content).slice(0, 160), icon: "/icons/icon-192.png?v=9" });
      } catch {  }
    }
  }

  function refreshReasoning(assistant) {
    const node = thread.querySelector(`[data-id="${assistant.id}"]`);
    if (!node) return;
    const old = node.querySelector(".reasoning");
    const fresh = reasoningNode(assistant);
    if (old && fresh) {
      const wasOpen = old.open;
      fresh.open = wasOpen || assistant.streaming;
      old.replaceWith(fresh);
    } else if (fresh) node.insertBefore(fresh, node.querySelector(".body"));
    else if (old) old.remove();
  }

  function refreshActivity(assistant) {
    const node = thread.querySelector(`[data-id="${assistant.id}"]`);
    if (!node) return;
    const old = node.querySelector(".activity, .steps-link, .thinking-row");
    const fresh = activityNode(assistant);
    if (old && fresh) old.replaceWith(fresh);
    else if (fresh) node.prepend(fresh);
    else if (old) old.remove();
    if (assistant.streaming) ensureActivityTicker();
  }

  function gatewayFallback(box, text) {
    const desktop = isDesktopApp();
    const needsPlan = desktop && planIsFree();
    box.append(
      el("div", {
        class: "muted small",
        style: { marginTop: "6px" },
        text: needsPlan
          ? "Anthropic не принимает IP этого подключения, а тариф Free не включает шлюз Clauden. Оформите подписку или отключите VPN."
          : text,
      }),
    );
    if (needsPlan) {
      box.append(el("button", {
        class: "btn btn-primary btn-sm",
        style: { marginTop: "8px" },
        text: "Оформить подписку",
        onclick: () => ctx.openSettings("plans"),
      }));
      return;
    }
    if (!desktop) return;
    box.append(el("button", {
      class: "btn btn-primary btn-sm",
      style: { marginTop: "8px" },
      text: "Переключиться на Clauden",
      onclick: async () => {
        if (streaming) return;
        await applyProvider("clauden");
        toast("Включён Clauden — запросы пойдут через наш шлюз", "success");
        await retryLastUser();
      },
    }));
  }

  function showError(node, err) {
    const box = el("div", { class: "error-box" });
    const raw = String(err?.message || "").trim();
    const timedOut = /turn (timed out|stalled)|прогресс сохранён|слишком много времени/i.test(raw);
    const claudeLinked = Boolean(isDesktopApp() && state.claude?.linked);
    const subscriptionBlocked = err?.status === 403 || err?.type === "subscription_required";
    const canUseClaude = subscriptionBlocked && claudeLinked &&
      (err?.type === "subscription_required" || /подписк|шлюз|subscription|free/i.test(raw));
    const message = timedOut
      ? "Clauden работал над задачей долго и сделал паузу. Прогресс сохранён — можно продолжить с того же места, не начиная заново."
      : /stream ended without finish_reason|incomplete[- ]stream|invalid .*stream event|streaming response failed/i.test(raw)
      ? "Сервис оборвал ответ. Попробуйте ещё раз — ответ сохранится на сервере."
      : /empty answer|пустой ответ/i.test(raw)
      ? "Сервис вернул пустой ответ. Попробуйте ещё раз."
      : /tool call stream state is inconsistent|tool output parsing failed/i.test(raw)
        ? "Модель не смогла завершить ответ. Попробуйте ещё раз."
        : isNetworkError(err) ||
      /fetch failed|terminated|socket hang up|other side closed|econnreset|upstream connection/i.test(raw)
      ? "Соединение с сервером прервалось. Проверьте интернет и повторите — ответ сохранится на сервере."
      : /^HTTP 5\d\d/i.test(raw)
        ? "Сервер временно недоступен. Попробуйте ещё раз через минуту."
        : subscriptionBlocked && canUseClaude
          ? "Тариф Clauden Free не включает шлюз, но ваш Claude-аккаунт уже привязан. Продолжите в режиме Claude — он работает без подписки Clauden."
          : raw || "Не удалось получить ответ";
    box.append(el("div", { text: message }));
    if (timedOut) {
      box.append(el("button", {
        class: "btn btn-primary btn-sm",
        style: { marginTop: "8px" },
        text: "Продолжить",
        onclick: () => {
          if (streaming) return;
          composer.textarea.value = "Продолжай с того места, где остановился. Не начинай задачу заново.";
          composer.textarea.dispatchEvent(new Event("input", { bubbles: true }));
          send();
        },
      }));
    }
    if (subscriptionBlocked) {
      box.append(el("button", { class: "btn btn-primary btn-sm", text: "Оформить подписку", onclick: () => ctx.openSettings("plans") }));
      if (claudeLinked && canUseClaude) {
        box.append(el("button", {
          class: "btn btn-secondary btn-sm",
          style: { marginTop: "8px" },
          text: "Продолжить в Claude",
          onclick: () => switchToClaudeAndRetry(),
        }));
      } else if (isDesktopApp() && !claudeLinked) {
        box.append(el("button", { class: "btn btn-secondary btn-sm", style: { marginTop: "8px" }, text: "Привязать Claude", onclick: () => ctx.openSettings("claude") }));
      } else {
        box.append(el("div", { class: "muted small", style: { marginTop: "6px" }, text: "Claude-аккаунт можно привязать в приложении Clauden для ПК." }));
      }
    } else if (err?.type === "unauthorized" || err?.type === "claude_not_linked") {
      box.append(el("button", { class: "btn btn-primary btn-sm", text: "Войти в Claude", onclick: () => openModelSheet() }));
      box.append(el("div", { class: "muted small", style: { marginTop: "6px" }, text: "Cookie claude.ai хранятся только на этом компьютере." }));
    } else if (err?.type === "blocked") {
      gatewayFallback(box, "Claude не принимает IP этого подключения. Это ограничение Anthropic для «своего Claude»; тариф Clauden работает через наш шлюз и VPN не требует.");
    } else if (/недоступен из вашей сети|unavailable.*network|not available.*region/i.test(message)) {
      gatewayFallback(box, "Anthropic не принимает это подключение. «Свой Claude» требует чистый IP, а Clauden Pro работает через наш шлюз без VPN.");
    } else if (err?.type === "throttled") {
      const retryAt = Number(err.extra?.retryAt) || 0;
      const wait = retryAt > Date.now() ? Math.ceil((retryAt - Date.now()) / 1000) : 0;
      const waitText = wait > 0
        ? (wait < 60 ? `Повторить можно через ${wait} сек.` : `Повторить можно через ${Math.ceil(wait / 60)} мин.`)
        : "Подождите минуту и повторите.";
      box.append(el("div", { class: "muted small", style: { marginTop: "6px" }, text: `Claude временно ограничил частоту запросов — это не исчерпанный тариф. ${waitText}` }));
    } else if (err?.status === 429 || err?.type === "rate_limit") {
      const retryAt = err.extra?.resets_at || err.extra?.retryAt;
      const resets = retryAt ? new Date(retryAt) : null;
      if (resets && Number.isFinite(resets.getTime())) box.append(el("div", { class: "muted small", style: { marginTop: "6px" }, text: `Лимит сбросится ${resets.toLocaleString("ru-RU")}` }));
      if (err?.type !== "rate_limit") {
        box.append(el("button", { class: "btn btn-secondary btn-sm", style: { marginTop: "8px" }, text: "Посмотреть тарифы", onclick: () => ctx.openSettings("plans") }));
      }
    } else if (err?.status === 401) {
      box.append(el("button", { class: "btn btn-secondary btn-sm", text: "Войти заново", onclick: () => ctx.signOut() }));
    }
    node.querySelector(".body")?.after(box);
    return box;
  }

  async function stop() {
    if (!streaming) return;
    if (provider !== "claude") {
      try { await api.stopTurn(chat.id, turnId); } catch {  }
    }
    controller?.abort();
  }

  async function switchToClaudeAndRetry() {
    if (streaming) return;
    await claudeStatusReady();
    if (!state.claude?.linked) {
      ctx.openSettings("claude");
      return;
    }
    await applyProvider("claude");
    await retryLastUser();
  }

  async function retryLastUser() {
    if (streaming) return;
    if (provider === "claude") chat.claudeConversationId = "";
    const lastUser = [...chat.messages].reverse().find((m) => m.role === "user");
    if (!lastUser) return;
    chat.messages = chat.messages.filter((m) => m.id !== lastUser.id);
    renderAllMessages();
    composer.textarea.value = lastUser.content || "";
    attachments = lastUser.attachments || [];
    refreshAttachRow();
    await send();
  }

  async function retryFrom(message) {
    if (streaming) return;
    const index = chat.messages.findIndex((m) => m.id === message.id);
    if (index < 0) return;
    chat.messages = chat.messages.slice(0, index);
    if (provider === "claude") chat.claudeConversationId = "";
    const lastUser = [...chat.messages].reverse().find((m) => m.role === "user");
    if (!lastUser) return;
    chat.messages = chat.messages.filter((m) => m.id !== lastUser.id);
    renderAllMessages();
    composer.textarea.value = lastUser.content || "";
    attachments = lastUser.attachments || [];
    refreshAttachRow();
    await send();
  }

  function buildPayload(chat, assistant) {
    const messages = [];
    const visible = chat.messages.filter((m) => !m.streaming && (m.content || m.attachments?.length));
    const lastUserIndex = visible.map((m) => m.role).lastIndexOf("user");
    visible.forEach((message, index) => {
      if (message.role === "system") return;
      if (index === lastUserIndex && message.attachments?.length) {
        const parts = [];
        if (message.content) parts.push({ type: "text", text: message.content });
        for (const file of message.attachments) {
          if (file.kind === "image") {
            parts.push({ type: "image_url", image_url: { url: `data:${file.mime};base64,${file.data}`, name: file.name } });
          } else {
            parts.push({ type: "file", file: { filename: file.name, mime_type: file.mime, file_data: file.data } });
          }
        }
        messages.push({ role: message.role, content: parts });
      } else {
        messages.push({ role: message.role, content: message.content || "" });
      }
    });

    const projectText = buildProjectContext(project);
    const connectors = loadConnectors().filter((c) => c.enabled !== false && c.url);
    const payload = {
      model: state.settings.model,
      max_tokens: Number(state.settings.effort) || 4096,
      stream: true,
      messages,
      clauden_system: buildSystemPrompt(state, project),
      clauden_context: buildMemoryContext(chat, state.chats),
      clauden_memory: state.settings.memory !== false,
      clauden_project: projectText,
    };
    if (connectors.length) {
      payload.clauden_connectors = connectors.map((c) => ({ id: c.id, title: c.title, url: c.url, headers: c.headers || "" }));
    }
    return payload;
  }

  async function saveChatLocal() {
    chat.updatedAt = Date.now();
    const index = state.chats.findIndex((c) => c.id === chat.id);
    if (index >= 0) state.chats[index] = chat;
    else state.chats.unshift(chat);
    state.chats.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) || (b.updatedAt || 0) - (a.updatedAt || 0));
    await store.putChat(chat);
    ctx.refreshSidebar();
    ctx.backupSoon();
  }

  function refreshSidebarTitle() {
    syncShellTitle();
    ctx.refreshSidebar();
  }

  function shareChat() {
    const lines = [`# ${chat.title || "Чат Clauden"}`, ""];
    for (const message of chat.messages) {
      if (message.role === "user") lines.push(`## Вы`, "", message.content, "");
      else if (message.content) lines.push(`## Clauden`, "", message.content, "");
    }
    copyText(lines.join("\n")).then(() => toast("Чат скопирован в буфер обмена"));
  }

  function openMenu(anchor) {
    menuAt(anchor, [
      { icon: "edit", label: "Переименовать", onClick: async () => {
        const name = await ctx.prompt({ title: "Название чата", value: chat.title });
        if (!name) return;
        chat.title = name.slice(0, 120);
        syncShellTitle();
        await saveChatLocal();
      }},
      { icon: "pin", label: chat.pinned ? "Открепить" : "Закрепить", onClick: async () => { chat.pinned = !chat.pinned; await saveChatLocal(); } },
      { icon: "folder", label: "В проект…", onClick: moveProject },
      { icon: "share", label: "Экспорт .md", onClick: exportChat },
      "-",
      { icon: "trash", label: "Удалить чат", danger: true, onClick: async () => {
        const ok = await confirmDialog({ title: "Удалить чат?", text: "Диалог исчезнет с устройства и из резервной копии.", confirm: "Удалить" });
        if (!ok) return;
        await ctx.deleteChat(chat.id);
        ctx.navigate("/chat", { replace: true });
      }},
    ]);
  }

  function exportChat() {
    const lines = [`# ${chat.title}`, ""];
    for (const message of chat.messages) {
      if (message.role === "user") lines.push("## Вы", "", message.content, "");
      else if (message.content) lines.push("## Clauden", "", message.content, "");
    }
    downloadText(lines.join("\n"), `${(chat.title || "chat").replace(/[\\/:*?"<>|]+/g, "-").slice(0, 80)}.md`, "text/markdown;charset=utf-8");
  }

  function moveProject() {
    const modal = openModal(el("div", null,
      el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Добавить в проект" })),
      el("div", { class: "modal-body" },
        el("div", { class: "list" },
          el("button", { class: "list-row", onclick: async () => { chat.projectId = ""; await saveChatLocal(); modal.close(); ctx.renderView(); } },
            el("span", { class: "list-row-icon", html: icon("inbox") }), el("span", { class: "list-row-text" }, el("span", { class: "list-row-title", text: "Без проекта" }))),
          ...state.projects.map((p) => el("button", { class: "list-row", onclick: async () => { chat.projectId = p.id; await saveChatLocal(); modal.close(); ctx.renderView(); } },
            el("span", { class: "list-row-icon", html: icon("folder") }), el("span", { class: "list-row-text" }, el("span", { class: "list-row-title", text: p.title })))),
        ),
      ),
    ));
  }

  function openModelSheet() {
    const content = el("div");
    const modal = openModal(content, { wide: false });
    let busy = false;
    pickedModel = "";

    const loginClaude = async () => {
      if (busy) return;
      busy = true;
      try {
        const result = await claudeDesktop.login();
        if (result?.linked) {
          state.claude = result;
          try { state.claudeModels = (await claudeDesktop.models()).data || []; } catch {  }
          ctx.toast(`Claude подключён: ${result.email || result.plan}`, "success");
          draw();
          refreshModelPill();
        } else if (result?.error) {
          ctx.toast(result.error, "error");
        }
      } catch (err) {
        ctx.toast(err.message || "Не удалось войти в Claude", "error");
      } finally {
        busy = false;
      }
    };

    const importClaudeDesktop = async () => {
      if (busy) return;
      busy = true;
      try {
        let closeApp = false;
        if (state.claudeDesktop?.running) {
          const ok = await confirmDialog({
            title: "Закрыть приложение Claude?",
            text: "Чтобы прочитать вход, Clauden закроет Claude. Несохранённый текст в нём может потеряться.",
            confirm: "Закрыть и импортировать",
          });
          if (!ok) return;
          closeApp = true;
        }
        const result = await claudeDesktop.importDesktop({ closeApp });
        if (result?.linked) {
          state.claude = result;
          try { state.claudeModels = (await claudeDesktop.models()).data || []; } catch {  }
          ctx.toast(`Claude подключён: ${result.email || result.plan}`, "success");
          draw();
          refreshModelPill();
        }
      } catch (err) {
        ctx.toast(err.message || "Не удалось импортировать вход Claude", "error");
      } finally {
        busy = false;
      }
    };

    const toggleRow = (label, key, hint) => {
      const input = el("input", { type: "checkbox" });
      input.checked = Boolean(state.settings[key]);
      input.addEventListener("change", () => {
        ctx.state.settings = saveSettings({ [key]: input.checked });
      });
      return el("label", { class: "setting-row", style: { cursor: "pointer" } },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: label }),
          hint ? el("div", { class: "setting-hint", text: hint }) : null,
        ),
        input,
      );
    };

    const claudeRow = (model) => {
      const active = (pickedModel || state.settings.claudeModel || claudeModels()[0]?.id || "") === model.id;
      return el("button", {
        class: `list-row ${active ? "active" : ""}`,
        onclick: () => {
          pickedModel = model.id;
          saveSettings({ claudeModel: model.id });
          applyProvider("claude").catch(() => {});
          draw();
          setTimeout(() => modal.close(), 140);
        },
      },
        el("span", { class: "list-row-icon", html: icon("sparkles") }),
        el("span", { class: "list-row-text" },
          el("span", { class: "list-row-title", text: model.name || model.id }),
        ),
        active ? el("span", { html: icon("check"), style: { width: "16px", height: "16px", color: "var(--clay)" } }) : null,
      );
    };

    const customApiRow = (item) => {
      const active = currentApi()?.id === item.id;
      return el("button", {
        class: `list-row ${active ? "active" : ""}`,
        onclick: () => {
          ctx.state.settings = saveSettings({
            customApiId: item.id,
            customModel: item.models?.[0]?.id || "",
          });
          draw();
        },
      },
        el("span", { class: "list-row-icon", html: icon("link") }),
        el("span", { class: "list-row-text" },
          el("span", { class: "list-row-title", text: item.name }),
          el("span", { class: "setting-hint", text: `${item.modelCount} ${plural(item.modelCount, "модель", "модели", "моделей")} · ${item.baseUrl}` }),
        ),
        active ? el("span", { html: icon("check"), style: { width: "16px", height: "16px", color: "var(--clay)" } }) : el("span", { html: icon("chevronRight"), style: { width: "16px", height: "16px", color: "var(--text-3)" } }),
      );
    };

    const customModelRow = (item, model) => {
      const active = provider === "custom" && currentApi()?.id === item.id && (pickedModel || currentModel()) === model.id;
      return el("button", {
        class: `list-row ${active ? "active" : ""}`,
        onclick: () => {
          pickedModel = model.id;
          ctx.state.settings = saveSettings({ provider: "custom", customApiId: item.id, customModel: model.id });
          chat.customApiId = item.id;
          applyProvider("custom").catch(() => {});
          draw();
          setTimeout(() => modal.close(), 140);
        },
      },
        el("span", { class: "list-row-icon", html: icon("sparkles") }),
        el("span", { class: "list-row-text" },
          el("span", { class: "list-row-title", text: model.name || model.id }),
          model.name && model.name !== model.id ? el("span", { class: "setting-hint", text: model.id }) : null,
        ),
        active ? el("span", { html: icon("check"), style: { width: "16px", height: "16px", color: "var(--clay)" } }) : null,
      );
    };

    const providerButton = (id, label) => el("button", {
      class: provider === id ? "active" : "",
      text: label,
      onclick: () => {
        pickedModel = "";
        applyProvider(id).catch(() => {});
        if (id === "claude") api.deleteChat(chat.id).catch(() => {});
        draw();
      },
    });

    const draw = () => {
      clear(content);

      const remote = state.models?.length ? state.models : [];
      const byId = new Map(remote.map((m) => [m.id, m]));
      for (const id of Object.keys(MAIN_MODELS)) {
        if (!byId.has(id)) byId.set(id, { id, display_name: MAIN_MODELS[id].name });
      }
      const models = [...byId.values()];
      const main = Object.keys(MAIN_MODELS).map((id) => byId.get(id)).filter(Boolean);
      const others = models.filter((m) => !MAIN_MODELS[m.id]);
      const body = el("div", { class: "modal-body" });

      if (isDesktopApp()) {
        body.append(
          el("div", { class: "section-title", text: "Провайдер" }),
          el("div", { class: "segmented", style: { width: "100%" } },
            providerButton("claude", "Claude"),
            providerButton("clauden", "Clauden"),
            isCustomApiSupported() ? providerButton("custom", "Свой API") : null,
          ),
        );
      }

      if (provider === "claude") {
        const status = state.claude;
        if (!status?.linked) {
          const desktopButton = el("button", {
            class: "btn btn-secondary btn-sm",
            text: "Использовать вход Claude",
            hidden: true,
            onclick: importClaudeDesktop,
          });
          body.append(el("div", { class: "notice-box", style: { marginTop: "12px" } },
            el("div", { class: "setting-name", text: "Claude не подключён" }),
            el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: "Войдите в claude.ai внутри приложения. Cookie останутся на этом компьютере и не уйдут на сервер Clauden." }),
            el("div", { style: { display: "flex", gap: "8px", flexWrap: "wrap" } },
              el("button", { class: "btn btn-primary btn-sm", text: "Войти в Claude", onclick: loginClaude }),
              desktopButton,
            ),
          ));
          if (isDesktopApp()) {
            claudeDesktop.desktopStatus().then((info) => {
              state.claudeDesktop = info;
              if (info?.supported && (info.installed || info.cookieDb) && info.hasSessionKey !== false) {
                desktopButton.hidden = false;
              }
            }).catch(() => {});
          }
        } else {
          body.append(
            el("div", { class: "settings-account", style: { marginTop: "12px", marginBottom: "10px" } },
              el("span", { class: "avatar", text: initials(status.name || status.email || "Claude") }),
              el("div", { class: "setting-text" },
                el("div", { class: "setting-name", text: status.name || status.email || "Claude" }),
                el("div", { class: "setting-hint", text: `${status.email || ""}${status.email ? " · " : ""}${String(status.plan || "free").toUpperCase()}` }),
              ),
              el("span", { class: "badge ok", text: "локально" }),
            ),
            el("div", { class: "list" }, ...claudeModels().map((m) => claudeRow(m))),
            el("button", {
              class: "btn btn-secondary btn-sm",
              style: { marginTop: "10px" },
              text: "Модели Clauden — Fable 5.2 и другие",
              onclick: () => {
                pickedModel = "";
                applyProvider("clauden").catch(() => {});
                draw();
              },
            }),
            el("div", { class: "section-title", text: "Инструменты" }),
            toggleRow("Веб-поиск", "webSearch", "Claude ищет свежие факты в интернете"),
            toggleRow("Расширенное мышление", "thinking", "Дольше думает над сложными задачами"),
            el("p", { class: "field-hint", style: { marginTop: "8px" }, text: "Запросы идут напрямую в claude.ai с этого компьютера. Лимиты — вашего аккаунта Claude." }),
          );
        }
      } else if (provider === "custom") {
        const apis = customApis();
        if (!apis.length) {
          body.append(el("div", { class: "notice-box", style: { marginTop: "12px" } },
            el("div", { class: "setting-name", text: "Свой API не добавлен" }),
            el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: "Подключите OpenRouter, Groq, Ollama или любой OpenAI-совместимый сервис. Ключ останется на этом компьютере." }),
            el("button", { class: "btn btn-primary btn-sm", text: "Добавить API", onclick: () => { modal.close(); ctx.openSettings("custom-api"); } }),
          ));
        } else {
          const selected = currentApi();
          body.append(
            el("div", { class: "section-title", text: "Подключения" }),
            el("div", { class: "list" }, ...apis.map((item) => customApiRow(item))),
          );
          if (selected) {
            const models = selected.models || [];
            body.append(el("div", { class: "section-title", text: `${selected.name} · модели` }));
            if (models.length) {
              body.append(el("div", { class: "list" }, ...models.slice(0, 200).map((model) => customModelRow(selected, model))));
            } else {
              body.append(el("p", { class: "field-hint", text: "У этого API нет списка моделей. Впишите id в настройках «Свой API»." }));
            }
          }
          body.append(el("button", {
            class: "btn btn-ghost btn-sm",
            style: { marginTop: "10px" },
            text: "Управление API…",
            onclick: () => { modal.close(); ctx.openSettings("custom-api"); },
          }));
          body.append(el("p", { class: "field-hint", style: { marginTop: "8px" }, text: "Запросы идут напрямую в выбранный сервис, минуя шлюз Clauden. Лимиты и оплата — на стороне провайдера." }));
        }
      } else {
        body.append(
          el("div", { class: "list" },
            ...main.map((m) => modelRow(m, MAIN_MODELS[m.id], modal, draw)),
          ),
          ...(others.length
            ? [
                el("div", { class: "section-title", text: "Другие модели" }),
                el("div", { class: "list" }, ...others.map((m) => modelRow(m, { name: m.display_name || m.id, desc: "", badge: "" }, modal, draw))),
              ]
            : []),
          ...(!isMaxUltraPlan(state.user?.plan)
            ? [el("p", { class: "field-hint", style: { marginTop: "8px" }, text: "Claude Fable 5.2 доступен только на тарифах Max и Ultra." })]
            : []),
          el("div", { class: "section-title", text: "Уровень усилий" }),
          el("div", { class: "segmented", style: { width: "100%" } },
            ...EFFORTS.map((effort) => el("button", {
              class: Number(state.settings.effort) === effort.value ? "active" : "",
              text: effort.label,
              onclick: () => { ctx.state.settings = saveSettings({ effort: effort.value }); modal.close(); },
            })),
          ),
          el("p", { class: "field-hint", style: { marginTop: "8px" }, text: "Выше уровень — длиннее и подробнее ответ, больше расход лимита." }),
        );
      }

      content.append(
        el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Выбор модели" }),
          el("button", { class: "icon-btn", onclick: () => modal.close(), html: icon("close") })),
        body,
      );
    };

    draw();
  }

  function modelRow(model, info, modal, draw) {
    const active = (pickedModel || state.settings.model) === model.id;
    const locked = model.id === "claude-fable-5-2" && !isMaxUltraPlan(state.user?.plan);
    return el("button", {
      class: `list-row ${active ? "active" : ""} ${locked ? "locked" : ""}`,
      onclick: () => {
        if (locked) {
          ctx.toast("Claude Fable 5.2 доступен только на тарифах Max и Ultra", "error");
          return;
        }
        pickedModel = model.id;
        ctx.state.settings = saveSettings({ model: model.id, provider: "clauden" });
        applyProvider("clauden").catch(() => {});

        draw();
        setTimeout(() => modal.close(), 140);
      },
    },
      el("span", { class: "list-row-icon", html: icon("sparkles") }),
      el("span", { class: "list-row-text" },
        el("span", { class: "list-row-title" }, el("span", { text: info.name || model.display_name || model.id }), info.badge ? el("span", { class: "badge warn", style: { marginLeft: "6px" }, text: info.badge }) : null),
        info.desc ? el("span", { class: "list-row-sub", text: info.desc }) : null,
      ),
      locked
        ? el("span", { html: icon("lock"), style: { width: "16px", height: "16px", color: "var(--text-3)" } })
        : active
          ? el("span", { html: icon("check"), style: { width: "16px", height: "16px", color: "var(--clay)" } })
          : null,
    );
  }

  function nearBottom() {
    return scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 140;
  }

  function scrollToBottom(force) {
    if (force || nearBottom()) scroll.scrollTop = scroll.scrollHeight;
  }

  const scrollBottom = el("button", {
    class: "scroll-bottom",
    title: "Вниз",
    onclick: () => scrollToBottom(true),
    html: icon("chevronDown"),
  });
  chatEl.append(scrollBottom);
  const syncScrollBottom = () => {
    const far = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight > 240;
    scrollBottom.classList.toggle("visible", far);
  };
  scroll.addEventListener("scroll", syncScrollBottom, { passive: true });
  requestAnimationFrame(syncScrollBottom);
}

function greeting() {
  const hour = new Date().getHours();
  const base = hour < 5 ? "Доброй ночи" : hour < 12 ? "Доброе утро" : hour < 18 ? "Добрый день" : "Добрый вечер";
  return base;
}

function modelLabel(id) {
  return MAIN_MODELS[id]?.name || String(id || "").replace(/^claude-/, "Claude ").replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

function hostOf(url) {
  try { return new URL(url).hostname.replace(/^www\./, ""); } catch { return url; }
}

function guessMime(name) {
  const ext = String(name).split(".").pop().toLowerCase();
  return {
    png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp",
    pdf: "application/pdf",
    docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    txt: "text/plain", md: "text/markdown", csv: "text/csv", json: "application/json",
    html: "text/html", svg: "image/svg+xml",
    zip: "application/zip", gz: "application/gzip", tar: "application/x-tar",
    "7z": "application/x-7z-compressed", rar: "application/vnd.rar",
    xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  }[ext] || "application/octet-stream";
}

function textToBase64(text) {
  const bytes = new TextEncoder().encode(String(text ?? ""));
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function actionTitle(action) {
  const type = action.type || "action";
  const names = {
    widget: "Визуализация",
    alarm: "Будильник",
    timer: "Таймер",
    calendar: "Событие в календаре",
    calendar_update: "Изменить событие",
    calendar_delete: "Удалить событие",
    location: "Геопозиция",
    contact: "Контакт",
    sms: "Сообщение",
    call: "Звонок",
    share: "Поделиться",
    task: "Задача",
    map: "Карта",
  };
  return names[type] || type;
}

export function buildProjectContext(project) {
  if (!project) return "";
  if (!project.instructions?.trim() && !(project.files || []).length) return "";
  const title = String(project.title || "").replace(/"/g, "").slice(0, 120);
  const lines = [
    `<project title="${title}">`,
    `Ты работаешь в проекте «${project.title}». Опирайся на его инструкции и знания в каждом ответе.`,
  ];
  if (project.description) lines.push(`Описание проекта: ${String(project.description).slice(0, 2000)}`);
  if (project.instructions?.trim()) {
    lines.push("", "Инструкции проекта (обязательны к выполнению):", String(project.instructions).slice(0, 6000));
  }
  const files = (project.files || []).filter((f) => f.text?.trim());
  if (files.length) {
    lines.push("", "Знания проекта (файлы; ссылайся на них по имени):");
    let budget = 48000;
    for (const file of files) {
      if (budget <= 0) break;
      const chunk = String(file.text).slice(0, Math.min(budget, 12000));
      lines.push(`=== ${file.name} ===`, chunk);
      budget -= chunk.length;
    }
    if (budget <= 0) lines.push("(часть файлов не поместилась в контекст)");
  }
  lines.push("</project>");
  return lines.join("\n");
}

export function buildSystemPrompt(state, project = null) {
  const settings = state.settings;
  const now = new Date().toLocaleString("ru-RU", { day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit" });
  const lines = [
    "Ты Clauden — полезный ИИ-ассистент. Отвечай на языке пользователя, кратко и по делу.",
    "Язык ответа — язык последнего сообщения пользователя. Если он пишет по-русски, отвечай по-русски.",
    `Сейчас: ${now}.`,
  ];
  if (settings.nickname) lines.push(`Обращайся к пользователю: ${settings.nickname}.`);
  if (settings.fullName && settings.fullName !== settings.nickname) lines.push(`Полное имя: ${settings.fullName}.`);
  if (settings.work) lines.push(`Чем занимается: ${settings.work}.`);
  if (settings.preferenceNote) lines.push("Инструкции пользователя (выполняй в первую очередь):", String(settings.preferenceNote).slice(0, 3000));
  lines.push(settings.artifacts ? "Артефакты включены: код и документы оформляй отдельными блоками." : "Артефакты выключены: не создавай отдельные окна артефактов.");
  lines.push(settings.codeExecution ? "Выполнение кода включено: можешь писать и запускать код, создавать файлы." : "Выполнение кода выключено: только объясняй код, не запускай его.");
  lines.push("Долгие задачи (рендер видео, сборка больших файлов, архивы) выполняй прямо в этом ходе и дождись завершения. Не отвечай «рендерю в фоне»: пользователь должен получить готовый файл в ответе.");
  if (!settings.visualizations) lines.push("Не создавай интерактивные визуализации и графики в чате.");
  if (!settings.aiArtifacts) lines.push("Не встраивай Clauden внутрь артефактов.");
  if (!settings.networkEgress) lines.push("Не устанавливай пакеты и не ходи в сеть при выполнении кода.");
  lines.push(settings.webSearch ? "Веб-поиск доступен: используй его, когда нужны свежие факты, и указывай источники." : "Веб-поиск выключен: отвечай по знаниям и явно предупреждай, если данные могли устареть.");
  const memory = loadMemory(state.user?.id);
  if (settings.memory && memory.length) {
    lines.push("Долговременная память о пользователе:");
    memory.slice(0, 40).forEach((item) => lines.push(`- ${item.text}`));
  }
  const customize = loadCustomize(state.user?.id);
  const usedSkills = new Set();
  for (const skill of customize.skills.slice(0, 8)) {
    if (!skill?.title) continue;
    usedSkills.add(skill.id);
    lines.push(`Активный навык «${skill.title}»:`, String(skill.instructions || "").slice(0, 2400));
  }
  for (const plugin of customize.plugins.slice(0, 4)) {
    if (!plugin?.title) continue;
    lines.push(`Активный плагин «${plugin.title}»:`);
    if (plugin.instructions) lines.push(String(plugin.instructions).slice(0, 2000));
    for (const id of plugin.skillIds || []) {
      if (usedSkills.has(id)) continue;
      const linked = customize.skills.find((skill) => skill.id === id);
      if (!linked) continue;
      usedSkills.add(id);
      lines.push(`Вложенный навык «${linked.title}»:`, String(linked.instructions || "").slice(0, 1600));
    }
  }
  return lines.join("\n");
}

export function buildMemoryContext(chat, chats = []) {
  const others = chats

    .filter((c) => c.provider !== "claude" && c.id !== chat.id && c.messages?.length)
    .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
    .slice(0, 5);
  if (!others.length) return "";
  const lines = [
    "Ниже — выдержки из прошлых чатов пользователя.",
    "Если он спрашивает, что обсуждали раньше, опирайся на них.",
  ];
  for (const conversation of others) {
    lines.push(`— ${conversation.title}`);
    const messages = (conversation.messages || []).filter((m) => m.role === "user" || m.content);
    const picked = [...messages.slice(0, 2), ...messages.slice(-6)];
    const seen = new Set();
    for (const message of picked) {
      const text = String(message.content || "").replace(/\n/g, " ").trim().slice(0, 320);
      if (!text || seen.has(text.slice(0, 80))) continue;
      seen.add(text.slice(0, 80));
      lines.push(`${message.role === "user" ? "  Вы" : "  Clauden"}: ${text}`);
    }
  }
  return lines.join("\n").slice(0, 6000);
}

function loadConnectors() {
  try { return JSON.parse(localStorage.getItem("clauden.connectors") || "[]"); } catch { return []; }
}
