import {
  $, el, clear, icon, initials, toast, openModal, confirmDialog, promptDialog,
  copyText, downloadText, relTimeShort, uid, timeAgo, plural,
} from "./util.js";
import { api, auth as authApi, ApiError, isDesktopApp, isCustomApiSupported, isUpdateSupported, claudeDesktop, customApi, desktopUpdates, desktopBridge } from "./api.js";
import {
  store, loadMemory, saveMemory, saveSettings,
  loadTasks, loadScheduled, loadCustomize,
} from "./store.js";
import {
  addTask, toggleTask, deleteTask, addScheduled, deleteScheduled,
  repeatLabel, quietRangeLabel, isQuietNow, ensureNotifications,
} from "./routines.js";
import { renderProxy } from "./proxy.js";

const DESKTOP_DOWNLOAD = "/downloads/Clauden-latest.exe?v=0.2.9";

const SECTIONS = [
  { group: "" },
  { id: "account", label: "Аккаунт", icon: "user" },
  { id: "plans", label: "Тарифы", icon: "creditCard" },
  { id: "usage", label: "Использование", icon: "chart" },
  { id: "api", label: "API-ключ", icon: "key" },
  { group: "Интерфейс" },
  { id: "appearance", label: "Оформление", icon: "palette" },
  { id: "claude", label: "Ваш Claude", icon: "sparkles" },
  { id: "proxy", label: "Прокси и VPN", icon: "shield" },
  { id: "custom-api", label: "Свой API", icon: "link" },
  { id: "features", label: "Возможности", icon: "cpu" },
  { id: "notifications", label: "Уведомления", icon: "bell" },
  { id: "privacy", label: "Приватность", icon: "shield" },
  { id: "focus", label: "Благополучие", icon: "heart" },
  { id: "memory", label: "Память", icon: "brain" },
  { group: "Работа" },
  { id: "customize", label: "Навыки и плагины", icon: "layers" },
  { id: "connectors", label: "Коннекторы", icon: "plug" },
  { id: "tasks", label: "Задачи", icon: "check" },
  { id: "scheduled", label: "Отложенные", icon: "clock" },
  { id: "reflect", label: "Рефлексия", icon: "book" },
  { group: "Приложение" },
  { id: "updates", label: "Обновления", icon: "download" },
  { id: "about", label: "О приложении", icon: "info" },
];

const SECTION_KEYWORDS = {
  account: "профиль имя ник почта email пароль аккаунт устройство сессии войти выйти удалить организация id подписка",
  plans: "тариф подписка оплата цена pro max ultra счёт платёж карта отмена",
  usage: "лимиты расход токены запросы остаток сброс статистика использование",
  api: "api ключ токен интеграция разработчик openai anthropic",
  appearance: "тема оформление шрифт тёмная светлая цвет интерфейс плотность масштаб стиль стили ios glass стекло кнопки тумблеры анимации noir акцент скругление",
  claude: "claude claude.ai аккаунт вход cookie импорт чаты claude desktop мост провайдер",
  proxy: "прокси vpn vless подписка сервер dns ip сеть туннель",
  "custom-api": "свой api openai openrouter groq ollama ключ base url модель",
  features: "возможности память артефакты веб поиск код визуализация сеть инструменты модель модели лимиты контекст",
  notifications: "уведомления звук напоминания перерыв тихие часы пуш",
  privacy: "приватность данные история удалить экспорт конфиденциальность",
  focus: "благополучие перерыв фокус отдых время экран",
  memory: "память факты запомнить контекст долговременная",
  customize: "навыки плагины скиллы маркетплейс инструменты",
  connectors: "коннекторы mcp notion github slack google каталог",
  tasks: "задачи todo чеклист",
  scheduled: "отложенные расписание напоминания",
  reflect: "рефлексия дневник итоги",
  updates: "обновления версия автообновление скачать",
  about: "о приложении версия лицензия поддержка",
};

const SECTION_RENDERERS = {  account: renderAccount,
  claude: renderClaudeMode,
  proxy: renderProxy,
  "custom-api": renderCustomApi,
  plans: renderPlans,
  usage: renderUsage,
  api: renderApiKeys,
  reflect: renderReflect,
  focus: renderFocus,
  tasks: renderTasks,
  scheduled: renderScheduled,
  customize: renderCustomize,
  appearance: renderAppearance,
  features: renderFeatures,
  notifications: renderNotifications,
  memory: renderMemory,
  connectors: renderConnectors,
  privacy: renderPrivacy,
  updates: renderUpdates,
  about: renderAbout,
};

export function renderSettings(view, ctx, tab = "account") {
  document.querySelector(".settings-overlay")?.remove();
  let current = SECTIONS.some((item) => item.id === tab) ? tab : "account";

  if (current === "updates" && !isDesktopApp()) current = "about";

  const nav = el("div", { class: "settings-modal-nav" });
  const pane = el("div", { class: "settings-pane" });
  const body = el("div", { class: "settings-modal-body" }, pane);
  const searchInput = el("input", { placeholder: "Поиск", type: "search" });

  const close = () => {
    overlay.remove();
    document.removeEventListener("keydown", onKey, true);
  };
  const modal = el("div", { class: "settings-modal" },
    el("div", { class: "settings-modal-side" },
      el("div", { class: "settings-modal-title", text: "Настройки" }),
      nav,
    ),
    el("div", { class: "settings-modal-main" },
      el("div", { class: "settings-modal-head" },
        el("span", { class: "topbar-spacer" }),
        el("button", { class: "icon-btn", title: "Закрыть", onclick: close, html: icon("close") }),
      ),
      body,
    ),
  );
  const overlay = el("div", {
    class: "settings-overlay",
    onclick: (e) => { if (e.target === overlay) close(); },
  }, modal);
  view.append(overlay);

  const drawNav = () => {
    clear(nav);
    nav.append(el("div", { class: "sm-search" }, el("span", { html: icon("search") }), searchInput));
    const query = (searchInput.value || "").trim().toLowerCase();
    let shown = 0;
    for (const section of SECTIONS) {
      if (section.group !== undefined) {
        if (!query && section.group) nav.append(el("div", { class: "sm-group", text: section.group }));
        continue;
      }
      if (section.id === "api" && !canOpenApiKeys(ctx.state.user)) continue;
      if (section.id === "updates" && !isDesktopApp()) continue;
      if (query) {
        const haystack = `${section.label} ${SECTION_KEYWORDS[section.id] || ""}`.toLowerCase();
        if (!query.split(/\s+/).every((word) => haystack.includes(word))) continue;
      }
      shown += 1;
      nav.append(el("button", {
        class: `sm-item ${section.id === current ? "active" : ""}`,
        onclick: () => setTab(section.id),
      }, el("span", { html: icon(section.icon) }), section.label));
    }
    if (query && !shown) {
      nav.append(el("div", { class: "sm-empty", text: "Ничего не найдено. Попробуйте «модель», «память», «уведомления» или «тариф»." }));
    }
  };

  const drawPane = () => {
    clear(pane);
    const fn = SECTION_RENDERERS[current] || renderAccount;
    fn(pane, ctx);
  };

  const setTab = (id) => {
    const navScroll = nav.scrollTop;
    current = id;
    drawNav();

    nav.scrollTop = navScroll;
    drawPane();
    body.scrollTop = 0;
  };

  const refresh = () => {
    if (!overlay.isConnected) return;
    drawPane();
  };

  const onKey = (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      close();
    }
  };

  searchInput.addEventListener("input", drawNav);
  document.addEventListener("keydown", onKey, true);
  drawNav();
  drawPane();

  return { close, setTab, refresh, overlay };
}

function canOpenApiKeys(user) {
  if (!user) return false;
  if (user.canIssueKeys || user.codeAccess) return true;
  return String(user.plan || "Free").trim().toLowerCase() !== "free";
}

function sessionPlatformLabel(value) {
  const platform = String(value || "").trim().toLowerCase();
  if (platform === "web" || platform === "browser") return "Браузер";
  if (platform === "desktop") return "Приложение";
  if (platform === "cli") return "CLI";
  if (platform === "android") return "Android";
  return value || "—";
}

function sessionDeviceLabel(value) {
  const device = String(value || "").trim();
  if (!device) return "Устройство";
  if (/^web$/i.test(device)) return "Веб-браузер";
  if (/clauden code cli/i.test(device)) return "Clauden Code CLI";
  return device;
}

function renderAccount(pane, ctx) {
  const user = ctx.state.user;
  const settings = ctx.state.settings;
  const plan = user?.plan || "Free";
  const claude = ctx.state.claude;
  const claudeActive = settings?.provider === "claude" && claude?.linked;
  const badgeText = claudeActive
    ? `Claude ${String(claude.plan || "free").toUpperCase()}`
    : `Clauden ${plan}`;

  pane.append(
    el("h1", { class: "settings-title", text: "Профиль" }),
    el("p", { class: "settings-desc", text: "Как к вам обращаться и где вы вошли в аккаунт" }),
  );

  const workValue = String(settings.work || user?.work || "");
  const name = el("input", { class: "input", value: user?.name || "", placeholder: "Имя" });
  const nickname = el("input", { class: "input", value: settings.nickname || "", placeholder: "Как к вам обращаться" });
  const work = el("input", {
    class: "input",
    value: workValue,
    placeholder: "Например: разработка, дизайн, учёба",
    list: "profile-work-options",
    autocomplete: "off",
  });
  const works = ["Разработка", "Дизайн", "Продукт", "Маркетинг", "Образование", "Исследования", "Своё дело", "Другое"];
  const workOptions = el("datalist", { id: "profile-work-options" },
    ...works.map((option) => el("option", { value: option })),
  );
  const instructions = el("textarea", { class: "textarea", rows: "4", placeholder: "Личные предпочтения, которые Clauden должен учитывать" });
  instructions.value = settings.preferenceNote || "";

  const save = el("button", { class: "btn btn-primary", text: "Сохранить", onclick: async () => {
    save.disabled = true;
    try {
      const patch = {
        name: name.value.trim(),
        nickname: nickname.value.trim(),
        work: work.value.trim(),
        instructions: instructions.value.trim(),
      };
      const { user: updated } = await authApi.update(patch);

      const saved = {
        nickname: updated?.nickname ?? patch.nickname,
        fullName: updated?.name ?? patch.name,
        work: updated?.work ?? patch.work,
        preferenceNote: updated?.instructions ?? patch.instructions,
      };
      ctx.setUser(updated);
      ctx.state.settings = saveSettings(saved);
      work.value = saved.work;
      nickname.value = saved.nickname;
      instructions.value = saved.preferenceNote;
      ctx.toast("Профиль сохранён", "success");
    } catch (err) {
      ctx.toast(err.message || "Не удалось сохранить", "error");
    } finally {
      save.disabled = false;
    }
  }});

  pane.append(
    el("div", { class: "profile-head" },
      el("span", { class: "profile-photo", text: initials(user?.name, user?.email) }),
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: user?.name || "Без имени" }),
        el("div", { class: "setting-hint", text: user?.email || "" }),
      ),
      el("span", { class: `plan-badge ${badgeText.toLowerCase().includes("free") ? "" : "pro"}`, text: badgeText }),
    ),
    el("div", { class: "profile-grid" },
      el("div", { class: "profile-field" }, el("label", { text: "Полное имя" }), name),
      el("div", { class: "profile-field" }, el("label", { text: "Как вас зовут?" }), nickname),
      el("div", { class: "profile-field" },
        el("label", { text: "Чем вы занимаетесь?" }), work, workOptions,
      ),
      el("div", { class: "profile-field" },
        el("label", { text: "ID организации" }),
        el("div", { class: "row" },
          el("span", { class: "profile-org", text: user?.id || "—" }),
          el("button", { class: "icon-btn sm", title: "Скопировать", onclick: () => copyText(user?.id || "").then(() => ctx.toast("Скопировано")), html: icon("copy") }),
        ),
      ),
    ),
    el("div", { class: "field", style: { marginTop: "16px" } },
      el("label", { text: "Личные инструкции" }), instructions,
      el("span", { class: "field-hint", text: "Добавляются в системный промпт каждого чата." })),
    el("div", { class: "row profile-actions" }, save,
      el("button", { class: "btn btn-secondary", text: "Тарифы", onclick: () => ctx.openSettings("plans") })),
  );

  const passwordGroup = el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: user?.hasPassword ? "Пароль" : "Установить пароль" }),
        el("div", { class: "setting-hint", text: user?.hasPassword ? "Пароль для входа по email" : "Аккаунт создан через Claude — пароль позволит войти без него" }),
      ),
      el("button", { class: "btn btn-secondary btn-sm", text: user?.hasPassword ? "Сменить" : "Установить", onclick: () => setPassword(ctx) }),
    ),
  );
  pane.append(passwordGroup);

  const claudeLinked = ctx.state.claude?.linked;
  pane.append(el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("span", { class: "list-row-icon", html: icon("sparkles") }),
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Ваш Claude" }),
        el("div", { class: "setting-hint", text: !isDesktopApp()
          ? "Свой аккаунт claude.ai — в приложении Clauden для ПК"
          : claudeLinked
            ? `Подключён ${ctx.state.claude.email || "аккаунт Claude"} · тариф ${String(ctx.state.claude.plan || "free").toUpperCase()}`
            : "Свой аккаунт claude.ai или шлюз Clauden" }),
      ),
      el("button", { class: "btn btn-secondary btn-sm", text: "Настроить", onclick: () => ctx.openSettings("claude") }),
    ),
  ));

  const sessionsBox = el("div", { class: "settings-group", style: { border: "0", background: "transparent", padding: "0" } });
  sessionsBox.append(el("div", { class: "row between" },
    el("h2", { class: "settings-title", style: { fontSize: "18px", margin: "0" }, text: "Где вы вошли в систему" }),
    el("button", { class: "btn btn-secondary btn-sm", text: "Выйти на всех устройствах", onclick: async () => {
      const ok = await confirmDialog({ title: "Выйти на всех устройствах?", text: "Все сессии, кроме текущей, будут отозваны.", confirm: "Выйти везде", danger: false });
      if (!ok) return;
      try { await authApi.logoutAll(); ctx.toast("Сессии отозваны"); ctx.signOut(); } catch (err) { ctx.toast(err.message, "error"); }
    }}),
  ));
  const table = el("table", { class: "devices" },
    el("thead", null, el("tr", null,
      el("th", { text: "Устройство / клиент" }),
      el("th", { text: "Место" }),
      el("th", { text: "Вход" }),
      el("th", { text: "Активность" }),
    )),
  );
  const tbody = el("tbody");
  table.append(tbody);
  sessionsBox.append(table);
  pane.append(sessionsBox);

  authApi.sessions().then(({ data }) => {
    const rows = data || [];
    if (!rows.length) {
      tbody.append(el("tr", null, el("td", { colspan: "4", class: "muted", text: "Нет активных сессий" })));
      return;
    }
    for (const session of rows) {
      tbody.append(el("tr", null,
        el("td", null,
          el("span", { class: "row", style: { gap: "8px" } },
            el("span", { class: "list-row-icon", style: { width: "26px", height: "26px" }, html: icon("monitor") }),
            el("span", { text: sessionDeviceLabel(session.device) }),
            session.current ? el("span", { class: "current-pill", text: "Текущее" }) : null,
          ),
        ),
        el("td", { text: session.location || "—" }),
        el("td", { text: sessionPlatformLabel(session.platform) }),
        el("td", { text: relTimeShort(session.lastSeenAt || session.createdAt) }),
      ));
    }
  }).catch(() => {
    tbody.append(el("tr", null, el("td", { colspan: "4", class: "muted", text: "Не удалось загрузить список устройств" })));
  });

  pane.append(
    el("div", { class: "settings-group" },
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Выйти из аккаунта" }),
          el("div", { class: "setting-hint", text: "Чаты останутся на этом устройстве" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Выйти", onclick: () => ctx.signOut() }),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Удалить аккаунт" }),
          el("div", { class: "setting-hint", text: "Безвозвратно: профиль, сессии и резервные копии чатов" }),
        ),
        el("button", { class: "btn btn-danger btn-sm", text: "Удалить", onclick: () => deleteAccount(ctx) }),
      ),
    ),
  );
}

function renderNotifications(pane, ctx) {
  const settings = ctx.state.settings;
  const save = (patch) => {
    ctx.state.settings = saveSettings(patch);
    Object.assign(settings, patch);
  };
  pane.append(
    el("h1", { class: "settings-title", text: "Уведомления" }),
    el("p", { class: "settings-desc", text: "Когда Clauden может напомнить о себе" }),
  );
  const group = el("div", { class: "settings-group" });
  group.append(el("div", { class: "setting-row" },
    el("div", { class: "setting-text" },
      el("div", { class: "setting-name", text: "Уведомления об ответе" }),
      el("div", { class: "setting-hint", text: "Когда чат свёрнут или приложение в фоне" }),
    ),
    switchControl(settings.notifications, async (value) => {
      if (value) {
        const allowed = await ensureNotifications();
        if (!allowed) ctx.toast("Уведомления запрещены в системе", "error");
      }
      save({ notifications: value });
    }),
  ));
  group.append(el("div", { class: "setting-row" },
    el("div", { class: "setting-text" },
      el("div", { class: "setting-name", text: "Напоминания о перерыве" }),
      el("div", { class: "setting-hint", text: "Мягкое уведомление отвлечься от экрана" }),
    ),
    switchControl(settings.breakReminders, async (value) => {
      if (value) {
        const allowed = await ensureNotifications();
        if (!allowed) ctx.toast("Уведомления запрещены в системе — напоминание покажем внутри приложения", "error");
      }
      save({ breakReminders: value });
    }),
  ));
  if (settings.breakReminders) {
    const intervals = el("div", { class: "segmented", style: { margin: "0 14px 14px" } });
    for (const minutes of [15, 30, 45, 60, 90]) {
      intervals.append(el("button", {
        class: Number(settings.breakInterval) === minutes ? "active" : "",
        text: `${minutes} мин`,
        onclick: () => { save({ breakInterval: minutes }); ctx.renderSettings?.(); },
      }));
    }
    group.append(intervals);
  }
  pane.append(group);

  pane.append(el("div", { class: "section-title", text: "Тихие часы" }));
  const quiet = el("div", { class: "settings-group" });
  quiet.append(el("div", { class: "setting-row" },
    el("div", { class: "setting-text" },
      el("div", { class: "setting-name", text: "Тихие часы" }),
      el("div", { class: "setting-hint", text: "В это время напоминания не приходят" }),
    ),
    switchControl(settings.quietHours, (value) => { save({ quietHours: value }); ctx.renderSettings?.(); }),
  ));
  if (settings.quietHours) {
    const start = el("input", { class: "input", type: "time", value: fmtMinutes(settings.quietStart) });
    const end = el("input", { class: "input", type: "time", value: fmtMinutes(settings.quietEnd) });
    const commit = () => {
      save({
        quietStart: parseHHMM(start.value),
        quietEnd: parseHHMM(end.value),
      });
    };
    start.addEventListener("change", commit);
    end.addEventListener("change", commit);
    quiet.append(el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Интервал тишины" }),
      ),
      el("div", { class: "row" }, start, el("span", { text: "—" }), end),
    ));
  }
  pane.append(quiet);
}

function renderClaudeMode(pane, ctx) {
  const paid = String(ctx.state.user?.plan || "Free").trim().toLowerCase() !== "free";
  pane.append(
    el("h1", { class: "settings-title", text: "Режим Claude" }),
    el("p", { class: "settings-desc", text: "Свой аккаунт claude.ai, переключение движка и перенос чатов" }),
  );
  if (paid) {
    pane.append(
      el("div", { class: "notice-box" },
        el("div", { class: "setting-name", text: "Clauden Pro работает без VPN" }),
        el("p", { class: "muted small", style: { margin: "6px 0 0" }, text: "Ответы готовит наш шлюз. Claude-режим ниже — это отдельный вход в ваш аккаунт Anthropic; ему может понадобиться чистый IP или прокси из-за блокировок claude.ai." }),
      ),
    );
  }

  if (!isDesktopApp()) {
    pane.append(
      el("div", { class: "notice-box" },
        el("div", { class: "setting-name", text: "Claude-режим работает в приложении для ПК" }),
        el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: "Вход в claude.ai, импорт чатов и переключение движка доступны в приложении Clauden для ПК — Windows, macOS и Linux. Cookie остаются на компьютере и не попадают на сервер." }),
        el("a", { class: "btn btn-secondary btn-sm", href: DESKTOP_DOWNLOAD, text: "Скачать приложение" }),
      ),
      el("p", { class: "field-hint", text: "На вебе ответы готовит шлюз Clauden в рамках тарифа." }),
    );
    if (String(ctx.state.user?.plan || "Free").toLowerCase() !== "free") {
      pane.append(
        el("div", { class: "settings-group" },
          el("div", { style: { padding: "14px" } },
            el("div", { class: "setting-name", text: "Вы подключены через Clauden" }),
            el("div", { class: "setting-hint", style: { color: "#2e7d4f", fontWeight: "600" }, text: "Риск блокировки за запросы через Clauden: 0" }),
            el("p", { class: "muted small", style: { margin: "6px 0 0" }, text: "Ответы готовит шлюз Clauden. Ваша сессия Claude не используется для чатов, поэтому запросы из Clauden не ведут к блокировке аккаунта." }),
          ),
        ),
      );
    }
    return;
  }

  const settings = ctx.state.settings;
  let status = ctx.state.claude || { linked: false };
  let desktop = ctx.state.claudeDesktop || null;
  const engineBox = el("div", { class: "settings-group" });
  const accountBox = el("div", { class: "settings-group" });
  const riskBox = el("div", { class: "settings-group" });
  const riskSection = el("div", {});
  riskSection.append(el("div", { class: "section-title", text: "Безопасность" }), riskBox);
  let riskState = { busy: false, error: "", data: null };

  const isActive = () => settings.provider === "claude" && Boolean(status?.linked);

  const drawEngine = () => {
    clear(engineBox);
    const active = isActive();
    engineBox.append(
      el("div", { style: { padding: "14px" } },
        el("div", { class: "setting-name", text: active ? "Сейчас работает Claude" : "Сейчас работает Clauden" }),
        el("div", { class: "setting-hint", text: active
          ? "Запросы идут напрямую в claude.ai с этого компьютера. Лимиты считает Anthropic."
          : "Ответы готовит шлюз Clauden в рамках тарифа. Аккаунт Claude не используется." }),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "segmented", style: { width: "100%" } },
          el("button", {
            class: !active ? "active" : "",
            text: "Clauden",
            onclick: () => {
              ctx.state.settings = saveSettings({ provider: "clauden" });
              settings.provider = "clauden";
              drawEngine();
              drawRisk();
              ctx.toast("Включён Clauden");
            },
          }),
          el("button", {
            class: active ? "active" : "",
            text: "Claude",
            onclick: async () => {
              if (!status?.linked) {
                const ok = await loginClaude();
                if (!ok) return;
              }
              ctx.state.settings = saveSettings({ provider: "claude" });
              settings.provider = "claude";
              draw();
              ctx.toast("Включён Claude");
            },
          }),
        ),
      ),
      el("div", { style: { padding: "0 14px 14px" } },
        el("div", { class: "field-hint", text: "Переключатель действует на новые чаты. В уже открытом чате провайдера можно сменить в выборе модели." }),
      ),
    );
  };

  const loginClaude = async (button) => {
    if (button) button.disabled = true;
    try {
      const result = await claudeDesktop.login();
      if (result?.linked) {
        status = result;
        ctx.state.claude = result;

        if (!paid) {
          ctx.state.settings = saveSettings({ provider: "claude" });
          settings.provider = "claude";
          ctx.refreshSidebar?.();
        }
        try { ctx.state.claudeModels = (await claudeDesktop.models()).data || []; } catch {  }
        ctx.toast(`Claude подключён: ${result.email || result.plan}`, "success");
        draw();
        return true;
      }
      if (result?.error) ctx.toast(result.error, "error");
    } catch (err) {
      ctx.toast(err.message || "Не удалось войти в Claude", "error");
    } finally {
      if (button) button.disabled = false;
    }
    return false;
  };

  const applyImported = async (result) => {
    status = result;
    ctx.state.claude = result;
    if (!paid) {
      ctx.state.settings = saveSettings({ provider: "claude" });
      settings.provider = "claude";
      ctx.refreshSidebar?.();
    }
    try { ctx.state.claudeModels = (await claudeDesktop.models()).data || []; } catch {  }
    draw();
    ctx.toast(`Claude подключён: ${result.email || result.plan}`, "success");
  };

  const importFromDesktop = async (button) => {
    if (button) button.disabled = true;
    try {
      if (desktop?.running) {
        const ok = await confirmDialog({
          title: "Закрыть приложение Claude?",
          text: "Чтобы прочитать вход, Clauden закроет Claude. Несохранённый текст в нём может потеряться.",
          confirm: "Закрыть и импортировать",
        });
        if (!ok) return;
        await applyImported(await claudeDesktop.importDesktop({ closeApp: true }));
        return;
      }
      try {
        await applyImported(await claudeDesktop.importDesktop({ closeApp: false }));
      } catch (err) {
        if (err.type !== "desktop_locked") throw err;
        const ok = await confirmDialog({
          title: "Закрыть приложение Claude?",
          text: "Claude держит файл cookie открытым. Закройте его и повторите — Clauden сделает это сам.",
          confirm: "Закрыть и импортировать",
        });
        if (!ok) return;
        await applyImported(await claudeDesktop.importDesktop({ closeApp: true }));
      }
    } catch (err) {
      ctx.toast(err.message || "Не удалось импортировать вход Claude", "error");
    } finally {
      if (button) button.disabled = false;
    }
  };

  const desktopHint = () => {
    if (!desktop || desktop.supported === false) return "Импорт входа доступен в Windows-приложении Clauden";
    if (!desktop.installed && !desktop.cookieDb) return "Приложение Claude не найдено. Его можно скачать бесплатно — или войти в claude.ai напрямую.";
    if (desktop.running) return "Claude запущен. Clauden предложит закрыть его, чтобы прочитать вход.";
    if (desktop.hasSessionKey) return "Найден вход в приложении Claude — можно подключить без повторного ввода пароля.";
    if (desktop.hasSessionKey === false) return "Приложение Claude установлено, но вход в нём не выполнен.";
    return "Приложение Claude установлено.";
  };

  const desktopButton = (label) => el("button", {
    class: "btn btn-secondary btn-sm",
    text: label,
    disabled: !desktop || desktop.supported === false || (!desktop.installed && !desktop.cookieDb) || desktop.hasSessionKey === false,
    onclick: (e) => importFromDesktop(e.currentTarget),
  });

  const drawAccount = () => {
    clear(accountBox);
    if (status?.linked) {
      const fromDesktop = status.source === "desktop";
      accountBox.append(
        el("div", { class: "settings-account", style: { padding: "14px" } },
          el("span", { class: "avatar", text: initials(status.name || status.email || "Claude") }),
          el("div", { class: "setting-text" },
            el("div", { class: "setting-name", text: status.name || status.email || "Claude" }),
            el("div", { class: "setting-hint", text: `${status.email || ""}${status.email ? " · " : ""}тариф ${String(status.plan || "free").toUpperCase()} · ${fromDesktop ? "вход из приложения Claude" : "cookie только на этом ПК"}` }),
          ),
          el("span", { class: "badge ok", text: fromDesktop ? "из Claude" : "подключён" }),
        ),
        el("div", { class: "setting-row" },
          el("button", { class: "btn btn-secondary btn-sm", text: "Войти заново", onclick: (e) => loginClaude(e.currentTarget) }),
          el("button", { class: "btn btn-secondary btn-sm", text: "Обновить", onclick: async (e) => {
            const button = e.currentTarget;
            button.disabled = true;
            try {
              const fresh = await claudeDesktop.refresh();
              status = fresh;
              ctx.state.claude = fresh;
              ctx.state.claudeModels = (await claudeDesktop.models()).data || [];
              draw();
              ctx.toast("Claude обновлён", "success");
            } catch (err) {
              ctx.toast(err.message || "Не удалось обновить", "error");
            } finally {
              button.disabled = false;
            }
          } }),
          el("button", { class: "btn btn-danger btn-sm", text: "Отвязать", onclick: async () => {
            const ok = await confirmDialog({ title: "Отвязать аккаунт Claude?", text: "Cookie claude.ai будут удалены с этого компьютера, приложение вернётся на Clauden. Сам аккаунт Anthropic останется прежним.", confirm: "Отвязать" });
            if (!ok) return;
            try {
              await claudeDesktop.logout();
              status = { linked: false };
              ctx.state.claude = { linked: false };
              ctx.state.claudeModels = [];
              ctx.state.settings = saveSettings({ provider: "clauden" });
              settings.provider = "clauden";
              draw();
              ctx.toast("Claude отключён");
            } catch (err) { ctx.toast(err.message || "Не удалось выйти", "error"); }
          } }),
        ),
      );
      if (!desktop || desktop.supported !== false) {
        const row = el("div", { class: "setting-row" },
          el("span", { class: "list-row-icon", html: icon("download") }),
          el("div", { class: "setting-text" },
            el("div", { class: "setting-name", text: "Вход из приложения Claude" }),
            el("div", { class: "setting-hint", text: desktopHint() }),
          ),
          desktopButton("Использовать вход Claude"),
        );
        if (desktop && !desktop.installed && !desktop.cookieDb) {
          row.querySelector(".setting-hint")?.append(
            " ",
            el("a", { href: "https://claude.ai/download", target: "_blank", rel: "noopener", text: "Скачать Claude" }),
          );
        }
        accountBox.append(row);
      }
      return;
    }

    accountBox.append(
      el("div", { style: { padding: "14px" } },
        el("div", { class: "setting-name", text: "Аккаунт Claude не подключён" }),
        el("p", { class: "muted small", style: { margin: "6px 0 12px" }, text: "Войдите своим аккаунтом claude.ai и работайте бесплатно с интерфейсом Clauden. Cookie останутся на этом компьютере и не уйдут на сервер." }),
        el("div", { style: { display: "flex", gap: "8px", flexWrap: "wrap" } },
          el("button", { class: "btn btn-primary btn-sm", text: "Войти в Claude", onclick: (e) => loginClaude(e.currentTarget) }),
          (!desktop || desktop.supported !== false) ? desktopButton("Использовать вход Claude") : null,
        ),
      ),
    );
    if (desktop && !desktop.installed && !desktop.cookieDb) {
      accountBox.append(el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("download") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Или подключите вход из приложения Claude" }),
          el("div", { class: "setting-hint", text: "Claude должен быть установлен и открыт на этом ПК." }),
        ),
        el("a", { class: "btn btn-secondary btn-sm", href: "https://claude.ai/download", target: "_blank", rel: "noopener", text: "Скачать Claude" }),
      ));
    }
  };

  const planPaid = () => {
    const plan = String(ctx.state.user?.plan || "Free").trim().toLowerCase();
    return plan !== "" && plan !== "free";
  };

  const riskColor = (level) =>
    level === "low" ? "#2e7d4f" : level === "medium" ? "var(--clay, #d97757)" : "var(--danger, #c0392b)";

  const riskRow = (label, value) =>
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-hint", text: label }),
        el("div", { class: "setting-name", style: { fontSize: "13px", fontWeight: "400" }, text: value }),
      ),
    );

  const checkRisk = async (button) => {
    if (button) button.disabled = true;
    riskState = { busy: true, error: "", data: null };
    drawRisk();
    try {
      const { ip } = await claudeDesktop.exitIp();
      riskState = { busy: false, error: "", data: await api.banRisk(ip) };
    } catch (err) {
      riskState = { busy: false, error: err.message || "Не удалось проверить риск", data: null };
    } finally {
      if (button) button.disabled = false;
      drawRisk();
    }
  };

  const drawRisk = () => {
    clear(riskBox);
    const done = () => {
      riskSection.hidden = riskBox.children.length === 0;
    };
    const active = settings.provider === "claude" && Boolean(status?.linked);
    if (!active && planPaid()) {
      riskBox.append(
        el("div", { style: { padding: "14px" } },
          el("div", { class: "setting-name", text: "Вы подключены через Clauden" }),
          el("div", {
            class: "setting-hint",
            style: { color: "#2e7d4f", fontWeight: "600" },
            text: "Риск блокировки за запросы через Clauden: 0",
          }),
          el("p", {
            class: "muted small",
            style: { margin: "6px 0 0" },
            text: "Ответы готовит шлюз Clauden. Ваша сессия Claude не используется для чатов, поэтому запросы из Clauden не ведут к блокировке аккаунта.",
          }),
        ),
      );
    }

    if (!status?.linked) {
      done();
      return;
    }

    if (riskState.busy) {
      riskBox.append(el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("globe") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Проверяю IP…" }),
          el("div", { class: "setting-hint", text: "Смотрю, какой адрес видит claude.ai" }),
        ),
      ));
      done();
      return;
    }

    if (!riskState.data) {
      riskBox.append(el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("shield") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: active ? "Проверить риск бана" : "Проверить IP для режима Claude" }),
          el("div", { class: "setting-hint", text: active
            ? "Какой IP видит Claude и насколько он чистый"
            : "Когда переключитесь на свой аккаунт Anthropic" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Проверить", onclick: (e) => checkRisk(e.currentTarget) }),
      ));
      if (riskState.error) {
        riskBox.append(el("div", { style: { padding: "0 14px 12px" } },
          el("div", { class: "field-hint", style: { color: "var(--danger, #c0392b)" }, text: riskState.error }),
        ));
      }
      done();
      return;
    }

    const data = riskState.data;
    const color = riskColor(data.level);
    riskBox.append(
      el("div", { class: "setting-row" },
        el("span", {
          class: "plan-badge",
          style: { color, borderColor: color, background: "transparent", minWidth: "62px", textAlign: "center" },
          text: `${data.score}/100`,
        }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", style: { color }, text: data.levelLabel || "Результат" }),
          el("div", { class: "setting-hint", text: data.summary }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Ещё раз", onclick: (e) => checkRisk(e.currentTarget) }),
      ),
      riskRow("IP", data.ip || "—"),
      riskRow("Где", [data.city, data.region, data.country].filter(Boolean).join(", ") || "неизвестно"),
      riskRow("Провайдер", data.provider || "неизвестен"),
      riskRow("Тип", data.kindLabel || "неизвестен"),
    );
    if (data.asn) riskBox.append(riskRow("ASN", [data.asn, data.asOrganization].filter(Boolean).join(" · ")));
    const flags = [];
    if (data.restricted) flags.push("Регион недоступен");
    if (data.flags?.tor) flags.push("Tor");
    if (data.flags?.hosting) flags.push("Дата-центр");
    if (data.flags?.proxy) flags.push("Прокси");
    if (data.flags?.vpn) flags.push("VPN");
    if (data.flags?.crawler) flags.push("Бот");
    if (data.flags?.abuser) flags.push("История злоупотреблений");
    if (data.flags?.mobile) flags.push("Мобильный");
    if (flags.length) {
      riskBox.append(el("div", { style: { padding: "0 14px 10px" } },
        el("div", { class: "setting-hint", style: { color, fontWeight: "600" }, text: flags.join(" · ") }),
      ));
    }
    if (Array.isArray(data.tips) && data.tips.length) {
      riskBox.append(el("div", { style: { padding: "0 14px 14px" } },
        ...data.tips.map((tip) => el("div", { class: "muted small", style: { marginTop: "4px" }, text: `— ${tip}` })),
      ));
    }
    done();
  };

  const draw = () => {
    drawEngine();
    drawAccount();
    drawRisk();
  };

  pane.append(
    el("div", { class: "section-title", text: "Движок" }),
    engineBox,
    el("div", { class: "section-title", text: "Аккаунт Claude" }),
    accountBox,
    riskSection,
  );

  const exportInput = el("input", { type: "file", accept: ".json,.zip,application/json,application/zip", hidden: true });
  exportInput.addEventListener("change", () => {
    const file = exportInput.files?.[0];
    exportInput.value = "";
    if (file) importClaudeExportFile(ctx, file);
  });
  pane.append(exportInput);
  pane.append(
    el("div", { class: "section-title", text: "Чаты из Claude" }),
    el("div", { class: "settings-group" },
      el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("chat") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Импортировать чаты" }),
          el("div", { class: "setting-hint", text: "Перенести переписку из вашего аккаунта claude.ai" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Открыть", onclick: () => openClaudeImport(ctx) }),
      ),
      el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("file") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Перенести из файла" }),
          el("div", { class: "setting-hint", text: "JSON или ZIP из экспорта claude.ai: conversations, projects, frames" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Выбрать файл", onclick: () => exportInput.click() }),
      ),
    ),
  );

  const toggle = (label, hint, key) => {
    const input = el("input", { type: "checkbox" });
    input.checked = Boolean(settings[key]);
    input.addEventListener("change", () => {
      ctx.state.settings = saveSettings({ [key]: input.checked });
      settings[key] = input.checked;
    });
    return el("label", { class: "setting-row", style: { cursor: "pointer" } },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: label }),
        el("div", { class: "setting-hint", text: hint }),
      ),
      input,
    );
  };
  pane.append(
    el("div", { class: "section-title", text: "Поведение Claude" }),
    el("div", { class: "settings-group" },
      toggle("Веб-поиск", "Claude ищет свежие факты в интернете", "webSearch"),
      toggle("Расширенное мышление", "Claude дольше думает над сложными задачами", "thinking"),
    ),
  );

  pane.append(
    el("div", { class: "section-title", text: "Диагностика" }),
    el("div", { class: "settings-group" },
      el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("file") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Диагностика Claude" }),
          el("div", { class: "setting-hint", text: "Скопировать состояние моста для разработчика" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Скопировать", onclick: () => {
          copyText(JSON.stringify({ provider: settings.provider, claude: status, desktop, platform: navigator.platform }, null, 2));
          ctx.toast("Диагностика скопирована");
        } }),
      ),
    ),
  );

  draw();
  claudeDesktop.status().then((fresh) => {
    status = fresh;
    ctx.state.claude = fresh;
    draw();
  }).catch(() => {});
  claudeDesktop.desktopStatus().then((info) => {
    desktop = info;
    ctx.state.claudeDesktop = info;
    draw();
  }).catch(() => {});
}

function openClaudeImport(ctx) {
  let importing = false;
  const list = el("div", { class: "list" });
  const status = el("p", { class: "field-hint", text: "Загружаю чаты…" });
  const importAll = el("button", { class: "btn btn-primary btn-sm", text: "Импортировать все", disabled: true });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Чаты из Claude" })),
    el("div", { class: "modal-body" },
      el("p", { class: "muted small", style: { marginTop: "0" }, text: "Чаты читаются с claude.ai через локальный мост. На сервер Clauden уходит только готовый текст диалога, который вы решите перенести." }),
      importAll,
      list,
      status,
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Закрыть", onclick: () => { modal.close(); ctx.renderSettings?.(); } }),
    ),
  ));

  let items = [];

  const saveImported = async (chat) => {
    if (!chat) return false;
    await store.putChat(chat);
    const index = ctx.state.chats.findIndex((item) => item.id === chat.id);
    if (index >= 0) ctx.state.chats[index] = chat;
    else ctx.state.chats.unshift(chat);
    ctx.state.chats.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    ctx.refreshSidebar?.();
    ctx.backupSoon?.();
    return true;
  };

  const importOne = async (item, row, button) => {
    if (importing) return;
    importing = true;
    button.disabled = true;
    button.textContent = "Импортирую…";
    try {
      const chat = await claudeDesktop.importOne(item);
      if (!chat) {
        button.textContent = "Пусто";
        return;
      }
      await saveImported(chat);
      button.textContent = "Готово";
      button.classList.remove("btn-secondary");
      button.classList.add("btn-ghost");
      status.textContent = `Импортирован: ${chat.title}`;
      row.classList.add("active");
    } catch (err) {
      button.textContent = "Повторить";
      button.disabled = false;
      status.textContent = err.message || "Не удалось импортировать чат";
    } finally {
      importing = false;
    }
  };

  const renderList = () => {
    clear(list);
    for (const item of items) {
      const button = el("button", { class: "btn btn-secondary btn-sm", text: "Импортировать" });
      const row = el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: item.name || "Без названия" }),
          el("div", { class: "setting-hint", text: relTimeShort(item.updatedAt || 0) }),
        ),
        button,
      );
      button.addEventListener("click", () => importOne(item, row, button));
      list.append(row);
    }
    if (!items.length) {
      list.append(el("p", { class: "muted small", text: "В аккаунте Claude не найдено чатов." }));
    }
  };

  importAll.addEventListener("click", async () => {
    if (importing) return;
    let done = 0;
    for (const item of items) {
      const rows = [...list.querySelectorAll(".setting-row")];
      const row = rows[items.indexOf(item)];
      const button = row?.querySelector("button");
      if (!button || button.textContent === "Готово") continue;
      importing = false;
      await importOne(item, row, button);
      done += 1;
      status.textContent = `Импортировано ${done} из ${items.length}`;
    }
    status.textContent = `Готово: ${done} чатов`;
  });

  (async () => {
    const all = [];
    const seen = new Set();
    for (let offset = 0; offset < 1000; offset += 100) {
      const page = await claudeDesktop.importList({ offset, limit: 100 });
      if (!Array.isArray(page) || !page.length) break;
      const fresh = page.filter((item) => item?.uuid && !seen.has(item.uuid));
      if (!fresh.length) break;
      for (const item of fresh) {
        seen.add(item.uuid);
        all.push(item);
      }
    }
    items = all;
    status.textContent = items.length ? `Доступно чатов: ${items.length}` : "";
    importAll.disabled = !items.length;
    renderList();
  })().catch((err) => {
    status.textContent = err.message || "Не удалось загрузить чаты Claude";
  });
}

function claudeExportText(message) {
  const parts = Array.isArray(message?.content)
    ? message.content
    : null;
  if (parts) {
    const chunks = [];
    parts.forEach((part, index) => {
      if (typeof part === "string") {
        if (part.trim()) chunks.push(part);
        return;
      }
      const type = String(part?.type || "");
      if (type === "text" && part.text) {
        chunks.push(part.text);
        return;
      }
      if (type === "artifact") {
        const tag = claudeExportArtifact(part, `artifact-${index}`);
        if (tag) chunks.push(tag);
        return;
      }
      if (type === "tool_use" && /artifact/i.test(String(part?.name || ""))) {
        const tag = claudeExportArtifact(part?.input ?? part?.artifact ?? part, part?.id || `tool-${index}`);
        if (tag) chunks.push(tag);
      }
    });
    const text = chunks.filter(Boolean).join("\n\n").trim();
    if (text) return text;
  }
  if (typeof message?.text === "string" && message.text.trim()) return message.text;
  return "";
}

function claudeExportArtifact(raw, fallbackId) {
  let value = raw;
  if (typeof value === "string") {
    try { value = JSON.parse(value); } catch { return ""; }
  }
  if (!value || typeof value !== "object") return "";
  const content = value.content ?? value.text ?? value.code ?? value.markdown;
  if (typeof content !== "string" || !content.trim()) return "";
  const id = String(value.id || value.identifier || value.artifact_id || fallbackId || "artifact");
  const title = String(value.title || value.name || value.filename || "Артефакт");
  const type = String(value.type || value.mime_type || value.mime || "text/plain");
  const attr = (text) => String(text).replaceAll("&", "&amp;").replaceAll('"', "&quot;");
  return `<artifact identifier="${attr(id)}" type="${attr(type)}" title="${attr(title)}">\n${content}\n</artifact>`;
}

function claudeExportToChat(item) {
  const raw = Array.isArray(item?.chat_messages)
    ? item.chat_messages
    : Array.isArray(item?.messages) ? item.messages : [];
  const messages = [];
  for (const message of raw) {
    const text = claudeExportText(message).trim();
    const files = [
      ...(message?.attachments || []),
      ...(message?.files || []),
      ...(message?.files_v2 || []),
      ...(message?.attachments_v2 || []),
    ]
      .map((file) => file?.file_name || file?.name || file?.filename || "")
      .filter(Boolean);
    const content = [text, files.length ? `Вложения: ${[...new Set(files)].join(", ")}` : ""].filter(Boolean).join("\n\n");
    if (!content) continue;
    const sender = String(message?.sender || message?.role || "").toLowerCase();
    const role = sender === "assistant" ? "assistant" : sender === "system" ? "system" : "user";
    if (role === "system") continue;
    messages.push({
      id: `claude-msg-${message?.uuid || message?.id || uid("msg")}`,
      role,
      content,
      ts: Date.parse(message?.created_at || "") || 0,
    });
  }
  if (!messages.length) return null;
  const uuid = String(item?.uuid || item?.id || uid("export"));
  const firstUser = messages.find((message) => message.role === "user")?.content || "";
  const title = String(item?.name || item?.title || firstUser || "Чат Claude").trim().slice(0, 80);
  const updatedAt = Date.parse(item?.updated_at || "") || messages[messages.length - 1].ts || Date.now();
  return {
    id: `claude-export-${uuid}`,
    title: title || "Чат Claude",
    createdAt: Date.parse(item?.created_at || "") || messages[0].ts || Date.now(),
    updatedAt,
    pinned: false,
    projectId: "",
    messages,
  };
}

function mergeClaudeExportChat(old, incoming) {
  const byId = new Map();
  for (const message of old.messages || []) byId.set(message.id, message);
  for (const message of incoming.messages || []) {
    const previous = byId.get(message.id);

    if (!previous || String(message.content || "").length >= String(previous.content || "").length) {
      byId.set(message.id, message);
    }
  }
  return {
    ...incoming,
    pinned: old.pinned ?? incoming.pinned,
    projectId: old.projectId ?? incoming.projectId,
    createdAt: Math.min(old.createdAt || incoming.createdAt || Date.now(), incoming.createdAt || Date.now()),
    updatedAt: Math.max(old.updatedAt || 0, incoming.updatedAt || 0),
    messages: [...byId.values()].sort((a, b) => (a.ts || 0) - (b.ts || 0)),
  };
}

async function importClaudeChatItems(ctx, list, counters) {
  for (const item of list.slice(0, 5000)) {
    const chat = claudeExportToChat(item);
    if (!chat) {
      counters.skipped += 1;
      continue;
    }
    const old = ctx.state.chats.find((existing) => existing.id === chat.id);
    const merged = old ? mergeClaudeExportChat(old, chat) : chat;
    await store.putChat(merged);
    const index = ctx.state.chats.findIndex((existing) => existing.id === chat.id);
    if (index >= 0) ctx.state.chats[index] = merged;
    else ctx.state.chats.unshift(merged);
    counters.imported += 1;
    if (counters.imported % 25 === 0) await new Promise((resolve) => setTimeout(resolve, 0));
  }
}

const ZIP_TEXT_EXT = /\.(json|md|markdown|txt|html|htm|svg|css|js|ts|tsx|jsx|vue|py|sql|ya?ml|xml|csv|tex)$/i;

async function inflateClaude(bytes) {
  if (typeof DecompressionStream !== "function") {
    throw new Error("Браузер не умеет распаковывать ZIP — обновите Chrome или распакуйте архив вручную");
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

async function unzipClaude(buffer) {
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const u16 = (offset) => view.getUint16(offset, true);
  const u32 = (offset) => view.getUint32(offset, true);
  let eocd = -1;
  for (let offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 65_557); offset -= 1) {
    if (u32(offset) === 0x06054b50) { eocd = offset; break; }
  }
  if (eocd < 0) throw new Error("ZIP повреждён: не найден каталог");
  const count = u16(eocd + 10);
  let offset = u32(eocd + 16);
  const decoder = new TextDecoder("utf-8");
  const entries = new Map();
  let total = 0;
  for (let index = 0; index < count; index += 1) {
    if (u32(offset) !== 0x02014b50) break;
    const flags = u16(offset + 8);
    const method = u16(offset + 10);
    const compressed = u32(offset + 20);
    const uncompressed = u32(offset + 24);
    const nameLength = u16(offset + 28);
    const extraLength = u16(offset + 30);
    const commentLength = u16(offset + 32);
    const localOffset = u32(offset + 42);
    const name = decoder.decode(bytes.slice(offset + 46, offset + 46 + nameLength));
    offset += 46 + nameLength + extraLength + commentLength;
    if (!name || name.endsWith("/") || (flags & 1)) continue;
    if (!ZIP_TEXT_EXT.test(name)) continue;
    if (uncompressed > 64 * 1024 * 1024) continue;
    total += uncompressed;
    if (total > 160 * 1024 * 1024) break;
    const localName = u16(localOffset + 26);
    const localExtra = u16(localOffset + 28);
    const start = localOffset + 30 + localName + localExtra;
    const raw = bytes.slice(start, start + compressed);
    let data;
    if (method === 0) {
      data = raw;
    } else if (method === 8) {
      data = await inflateClaude(raw);
    } else {
      continue;
    }
    entries.set(name.replaceAll("\\", "/"), decoder.decode(data));
  }
  return entries;
}

async function importClaudeFrames(ctx, entries, counters) {
  const groups = new Map();
  for (const [name, text] of entries) {
    const match = name.match(/(?:^|\/)artifacts\/([^/]+)\/(.+)$/);
    if (!match) continue;
    if (!groups.has(match[1])) groups.set(match[1], new Map());
    groups.get(match[1]).set(match[2], text);
  }
  const messages = [];
  for (const [uuid, files] of groups) {
    let meta = null;
    try { meta = JSON.parse(files.get("artifact.json") || "null"); } catch { meta = null; }
    const versions = Array.isArray(meta?.versions) ? meta.versions : [];
    const active = versions.find((version) => version?.id === meta?.active_version) || versions.at(-1) || null;
    const title = String(active?.title || active?.name || meta?.title || meta?.name || "Артефакт");
    const type = String(active?.type || meta?.type || "text/html");
    const activeId = meta?.active_version || active?.id || "";
    let chosen = null;
    if (activeId) {
      chosen = [...files.keys()].find((name) => name.split("/").pop().startsWith(`${activeId}.`)) || null;
      if (!chosen) chosen = [...files.keys()].find((name) => name.includes(`/${activeId}/`)) || null;
    }
    if (!chosen) chosen = files.has("page.md") ? "page.md" : [...files.keys()].find((name) => ZIP_TEXT_EXT.test(name) && !name.endsWith("artifact.json") && !name.endsWith("comments.json")) || null;
    const content = chosen ? files.get(chosen) : "";
    if (!content || !content.trim()) continue;
    const ts = Date.parse(meta?.updated_at || active?.created_at || "") || 0;
    messages.push({
      id: `claude-frame-msg-${uuid}`,
      role: "assistant",
      content: claudeExportArtifact(
        { id: `claude-export-${uuid}`, title, type, content },
        uuid,
      ),
      ts,
    });
  }
  if (!messages.length) return;
  messages.sort((a, b) => (a.ts || 0) - (b.ts || 0));
  const chat = {
    id: "claude-export-artifacts",
    title: "Артефакты из Claude",
    createdAt: messages[0].ts || Date.now(),
    updatedAt: messages.at(-1).ts || Date.now(),
    pinned: false,
    projectId: "",
    messages,
  };
  const old = ctx.state.chats.find((existing) => existing.id === chat.id);
  const merged = old ? mergeClaudeExportChat(old, chat) : chat;
  await store.putChat(merged);
  const index = ctx.state.chats.findIndex((existing) => existing.id === chat.id);
  if (index >= 0) ctx.state.chats[index] = merged;
  else ctx.state.chats.unshift(merged);
  counters.artifacts += messages.length;
}

async function importClaudeZip(ctx, file) {
  ctx.toast("Распаковываю экспорт Claude…");
  const entries = await unzipClaude(await file.arrayBuffer());
  const counters = { imported: 0, skipped: 0, artifacts: 0 };
  await importClaudeFrames(ctx, entries, counters);
  const seen = new Set();
  for (const [name, text] of entries) {
    if (!name.endsWith(".json")) continue;
    if (name.endsWith("artifact.json") || name.endsWith("comments.json")) continue;
    let data;
    try { data = JSON.parse(text); } catch { continue; }
    const list = Array.isArray(data)
      ? data
      : data?.conversations || data?.chats || (Array.isArray(data?.messages) ? [data] : null);
    if (!Array.isArray(list)) continue;
    if (!list.some((item) => Array.isArray(item?.chat_messages))) continue;
    if (seen.has(name)) continue;
    seen.add(name);
    await importClaudeChatItems(ctx, list, counters);
  }
  ctx.state.chats.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  ctx.refreshSidebar?.();
  ctx.backupSoon?.();
  const parts = [
    counters.imported ? `чатов: ${counters.imported}` : "",
    counters.artifacts ? `артефактов: ${counters.artifacts}` : "",
    counters.skipped ? `пропущено: ${counters.skipped}` : "",
  ].filter(Boolean).join(", ");
  ctx.toast(parts ? `Импортировано ${parts}` : "В архиве не нашлось чатов и артефактов", parts ? "success" : "error");
}

async function importClaudeExportFile(ctx, file) {
  if (file.size > 160 * 1024 * 1024) {
    ctx.toast("Файл больше 160 МБ", "error");
    return;
  }
  if (/\.zip$/i.test(file.name) || String(file.type || "").includes("zip")) {
    try {
      await importClaudeZip(ctx, file);
    } catch (err) {
      ctx.toast(err.message || "Не удалось прочитать ZIP", "error");
    }
    return;
  }
  if (file.size > 80 * 1024 * 1024) {
    ctx.toast("Файл больше 80 МБ", "error");
    return;
  }
  ctx.toast("Читаю экспорт Claude…");
  try {
    const text = await file.text();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      throw new Error("Это не JSON и не ZIP экспорта Claude");
    }
    const list = Array.isArray(data) ? data : data?.conversations || data?.chats;
    if (!Array.isArray(list)) throw new Error("В файле нет списка чатов Claude");
    const counters = { imported: 0, skipped: 0 };
    await importClaudeChatItems(ctx, list, counters);
    const { imported, skipped } = counters;
    ctx.state.chats.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    ctx.refreshSidebar?.();
    ctx.backupSoon?.();
    ctx.toast(
      imported
        ? `Импортировано чатов: ${imported}${skipped ? `, пропущено: ${skipped}` : ""}`
        : "В файле не нашлось чатов с сообщениями",
      imported ? "success" : "error",
    );
  } catch (err) {
    ctx.toast(err.message || "Не удалось прочитать экспорт Claude", "error");
  }
}

function renderCustomApi(pane, ctx) {
  pane.append(
    el("h1", { class: "settings-title", text: "Свой API" }),
    el("p", { class: "settings-desc", text: "OpenAI-совместимые провайдеры: OpenRouter, Groq, Ollama и другие" }),
  );

  if (!isDesktopApp()) {
    pane.append(
      el("div", { class: "notice-box" },
        el("div", { class: "setting-name", text: "Свои API работают в приложении для ПК" }),
        el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: "Ключи хранятся в защищённом хранилище Windows (DPAPI) и не попадают на сервер Clauden. Из веб-версии произвольные API недоступны из-за CORS." }),
        el("a", { class: "btn btn-secondary btn-sm", href: DESKTOP_DOWNLOAD, text: "Скачать приложение" }),
      ),
    );
    return;
  }

  if (!isCustomApiSupported()) {
    pane.append(
      el("div", { class: "notice-box" },
        el("div", { class: "setting-name", text: "Нужна новая версия приложения" }),
        el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: "Эта сборка Clauden для ПК ещё не умеет хранить ключи своих API. Установите обновление — настройки и чаты сохранятся." }),
        el("a", { class: "btn btn-primary btn-sm", href: DESKTOP_DOWNLOAD, text: "Скачать обновление" }),
      ),
    );
    return;
  }

  const box = el("div", { class: "settings-group" });
  const add = el("button", {
    class: "btn btn-primary",
    text: "Добавить API",
    onclick: () => openCustomApiEditor(ctx, null, load),
  });
  pane.append(
    box,
    el("div", { style: { marginTop: "12px" } }, add),
    el("p", { class: "field-hint", text: "Подходит любой сервис с OpenAI-совместимыми /v1/chat/completions и /v1/models. Ключ шифруется средствами ОС, в интерфейсе он больше не показывается." }),
  );

  let items = [];

  const draw = () => {
    clear(box);
    if (!items.length) {
      box.append(el("div", { style: { padding: "16px" } },
        el("div", { class: "setting-name", text: "Пока пусто" }),
        el("div", { class: "setting-hint", text: "Добавьте API — его модели появятся в выборе модели, в разделе «Свой API»." }),
      ));
      return;
    }
    for (const item of items) {
      const active = ctx.state.settings.customApiId === item.id;
      box.append(el("div", {
        class: "setting-row",
        style: { cursor: "pointer" },
        onclick: () => openCustomApiEditor(ctx, item, load),
      },
        el("span", { class: "list-row-icon", html: icon("link") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: item.name }),
          el("div", { class: "setting-hint", text: `${item.modelCount} ${plural(item.modelCount, "модель", "модели", "моделей")} · ${item.baseUrl}` }),
        ),
        active ? el("span", { class: "badge ok", text: "активно" }) : el("span", { html: icon("chevronRight"), style: { width: "16px", height: "16px", color: "var(--text-3)" } }),
      ));
    }
  };

  const load = async () => {
    try {
      items = await customApi.list();
      ctx.state.customApis = items;
      draw();
    } catch (err) {
      clear(box);
      box.append(el("div", { style: { padding: "16px" } },
        el("div", { class: "setting-name", text: "Не удалось загрузить API" }),
        el("div", { class: "setting-hint", text: err.message || "" }),
      ));
    }
  };

  draw();
  load();
}

function openCustomApiEditor(ctx, api, onSaved) {
  const isNew = !api;
  const name = el("input", { class: "input", value: api?.name || "", placeholder: "OpenRouter" });
  const baseUrl = el("input", { class: "input", value: api?.baseUrl || "", placeholder: "https://openrouter.ai/api/v1" });
  const apiKey = el("input", {
    class: "input",
    type: "password",
    autocomplete: "off",
    placeholder: api?.hasKey ? "Ключ сохранён — оставьте пустым, чтобы не менять" : "sk-…",
  });
  const manual = el("textarea", { class: "textarea", rows: "2", placeholder: "qwen3.8 или id через запятую" });
  const status = el("p", { class: "field-hint", text: isNew ? "Проверьте подключение — или впишите модель вручную." : (api?.modelCount ? `Сохранено моделей: ${api.modelCount}` : "Список моделей пуст.") });
  let tested = [];

  const testBtn = el("button", { class: "btn btn-secondary btn-sm", text: "Загрузить модели", onclick: async () => {
    testBtn.disabled = true;
    status.style.color = "";
    status.textContent = "Проверяю подключение…";
    try {
      const result = await customApi.test({
        id: api?.id || "",
        baseUrl: baseUrl.value.trim(),
        apiKey: apiKey.value.trim(),
      });
      tested = result.models || [];
      status.textContent = tested.length
        ? `Подключение OK. Моделей: ${tested.length}`
        : "Подключение OK, но /models пуст — впишите id модели вручную.";
      status.style.color = tested.length ? "var(--text-2)" : "";
    } catch (err) {
      tested = [];
      status.textContent = err.message || "Не удалось подключиться";
      status.style.color = "var(--danger)";
    } finally {
      testBtn.disabled = false;
    }
  } });

  const saveBtn = el("button", { class: "btn btn-primary btn-sm", text: "Сохранить", onclick: async () => {
    if (!baseUrl.value.trim()) {
      status.textContent = "Укажите Base URL";
      status.style.color = "var(--danger)";
      return;
    }
    saveBtn.disabled = true;
    try {
      const manualModels = manual.value
        .split(/[,\n]/)
        .map((value) => value.trim())
        .filter(Boolean)
        .map((id) => ({ id, name: id }));
      const base = tested.length ? tested : (api?.models || []);
      const models = [...base, ...manualModels].filter((model, index, all) => all.findIndex((item) => item.id === model.id) === index);
      const saved = await customApi.save({
        id: api?.id || "",
        name: name.value.trim(),
        baseUrl: baseUrl.value.trim(),
        apiKey: apiKey.value.trim(),
        models,
      });
      if (!ctx.state.settings.customApiId) {
        ctx.state.settings = saveSettings({
          customApiId: saved.id,
          customModel: saved.models[0]?.id || "",
        });
      } else if (ctx.state.settings.customApiId === saved.id) {
        ctx.state.settings = saveSettings({
          customModel: saved.models.some((model) => model.id === ctx.state.settings.customModel)
            ? ctx.state.settings.customModel
            : (saved.models[0]?.id || ""),
        });
      }
      modal.close();
      ctx.toast(isNew ? "API добавлен" : "API обновлён", "success");
      onSaved?.();
    } catch (err) {
      status.textContent = err.message || "Не удалось сохранить";
      status.style.color = "var(--danger)";
    } finally {
      saveBtn.disabled = false;
    }
  } });

  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: isNew ? "Новый API" : "Изменить API" })),
    el("div", { class: "modal-body" },
      el("div", { class: "field" }, el("label", { text: "Название" }), name),
      el("div", { class: "field" }, el("label", { text: "Base URL" }), baseUrl,
        el("span", { class: "field-hint", text: "Адрес до /v1. Например https://api.openrouter.ai/v1" })),
      el("div", { class: "field" }, el("label", { text: "API-ключ" }), apiKey,
        el("span", { class: "field-hint", text: api?.hasKey ? "Сохранённый ключ не показывается. Пустое поле оставит его прежним." : "Для локальных серверов ключ можно не указывать." })),
      el("div", { class: "field" }, el("label", { text: "Модель вручную" }), manual,
        el("span", { class: "field-hint", text: "Если /v1/models недоступен, впишите id моделей через запятую." })),
      el("div", { style: { display: "flex", gap: "8px", flexWrap: "wrap", marginTop: "4px" } }, testBtn, saveBtn),
      status,
    ),
    el("div", { class: "modal-foot" },
      isNew ? null : el("button", { class: "btn btn-danger btn-sm", text: "Удалить API", onclick: async () => {
        const ok = await confirmDialog({
          title: "Удалить свой API?",
          text: "Ключ и список моделей будут удалены с этого компьютера.",
          confirm: "Удалить",
        });
        if (!ok) return;
        try {
          await customApi.remove(api.id);
          if (ctx.state.settings.customApiId === api.id) {
            ctx.state.settings = saveSettings({
              customApiId: "",
              customModel: "",
              provider: ctx.state.settings.provider === "custom" ? "clauden" : ctx.state.settings.provider,
            });
          }
          modal.close();
          ctx.toast("API удалён");
          onSaved?.();
        } catch (err) {
          ctx.toast(err.message || "Не удалось удалить API", "error");
        }
      } }),
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
    ),
  ));
}

const REFLECT_STOP = new Set([
  "это", "что", "как", "для", "или", "мне", "меня", "тебе", "тебя", "если",
  "так", "все", "ещё", "еще", "уже", "было", "есть", "быть", "только", "можно",
  "нужно", "надо", "при", "без", "над", "под", "про", "через", "чтобы", "когда",
  "потом", "сейчас", "здесь", "там", "этот", "эта", "эти", "тот", "она", "они",
  "помоги", "сделай", "добавь", "напиши", "можешь", "давай", "плиз",
  "the", "and", "for", "with", "you", "your", "this", "that",
]);

function reflectStats(chats) {
  const days = new Set();
  const hours = new Array(24).fill(0);
  const weekdays = new Array(7).fill(0);
  const wordsMap = new Map();
  const weekdayNames = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
  let messages = 0;
  let words = 0;
  let artifacts = 0;
  for (const chat of chats) {
    for (const message of chat.messages || []) {
      const text = String(message.content || "");
      if (!text.trim()) continue;
      messages += 1;
      const parts = text.split(/\s+/).filter(Boolean);
      words += parts.length;
      if (message.role === "assistant" && text.includes("```")) artifacts += 1;
      const date = new Date(Number(message.ts) || Number(chat.updatedAt) || Date.now());
      days.add(date.toISOString().slice(0, 10));
      hours[date.getHours()] += 1;
      weekdays[(date.getDay() + 6) % 7] += 1;
      if (message.role === "user") {
        for (const word of text.toLowerCase().split(/[^\p{L}\p{N}]+/u)) {
          if (word.length < 4 || REFLECT_STOP.has(word)) continue;
          wordsMap.set(word, (wordsMap.get(word) || 0) + 1);
        }
      }
    }
  }
  const peak = hours.indexOf(Math.max(...hours));
  const topDayIndex = weekdays.indexOf(Math.max(...weekdays));
  const topics = [...wordsMap.entries()].sort((a, b) => b[1] - a[1]).slice(0, 9).map(([word]) => word);
  const avg = chats.length ? Math.round(messages / chats.length) : 0;
  const insights = [];
  if (!chats.length) {
    insights.push("Пока нет чатов — статистика появится после первых разговоров.");
  } else {
    insights.push(`Больше всего сообщений приходится на ${peak}:00–${(peak + 1) % 24}:00.`);
    insights.push(`Самый активный день — ${weekdayNames[topDayIndex]}. Средний чат: ${avg} ${plural(avg, "сообщение", "сообщения", "сообщений")}.`);
    if (artifacts > 0) insights.push(`В ответах найдено ${artifacts} ${plural(artifacts, "блок", "блока", "блоков")} кода и артефактов.`);
    if (words > 0) insights.push(`Всего слов в переписке: ${words}.`);
  }
  return { chats: chats.length, messages, words, activeDays: days.size, avg, artifacts, hours, peak, topDay: weekdayNames[topDayIndex], topics, insights };
}

function renderReflect(pane, ctx) {
  const stats = reflectStats(ctx.state.chats || []);
  pane.append(
    el("h1", { class: "settings-title", text: "Рефлексия" }),
    el("p", { class: "settings-desc", text: "Как вы работаете с Clauden — по вашим чатам на этом устройстве" }),
  );

  const grid = el("div", { style: { display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(140px,1fr))", gap: "10px" } });
  const cards = [
    ["Чатов", stats.chats],
    ["Сообщений", stats.messages],
    ["Слов", stats.words],
    ["Активных дней", stats.activeDays],
    ["Средний чат", `${stats.avg} сообщ.`],
    ["Артефактов", stats.artifacts],
  ];
  for (const [label, value] of cards) {
    grid.append(el("div", { class: "card", style: { padding: "14px" } },
      el("div", { class: "setting-name", style: { fontSize: "22px" }, text: String(value) }),
      el("div", { class: "setting-hint", text: label }),
    ));
  }
  pane.append(grid);

  pane.append(el("div", { class: "section-title", text: "Когда вы работаете" }));
  const chartCard = el("div", { class: "settings-group" }, el("div", { style: { padding: "16px" } }));
  const inner = chartCard.firstChild;
  const max = Math.max(1, ...stats.hours);
  const bars = el("div", { style: { display: "grid", gridTemplateColumns: "repeat(24,1fr)", alignItems: "end", gap: "3px", height: "92px" } });
  stats.hours.forEach((value, index) => {
    bars.append(el("div", {
      title: `${index}:00 — ${value}`,
      style: {
        height: `${Math.max(2, Math.round((value / max) * 100))}%`,
        background: value === max ? "var(--clay)" : "var(--surface-3)",
        borderRadius: "3px",
      },
    }));
  });
  const labels = el("div", { class: "row between", style: { marginTop: "8px", color: "var(--text-3)", fontSize: "11px" } },
    el("span", { text: "00" }), el("span", { text: "06" }), el("span", { text: "12" }), el("span", { text: "18" }), el("span", { text: "23" }),
  );
  inner.append(bars, labels);
  if (stats.chats) {
    inner.append(el("p", { class: "muted small", style: { margin: "10px 0 0" }, text: `Пик активности: ${stats.peak}:00. Любимый день: ${stats.topDay}.` }));
  }
  pane.append(chartCard);

  if (stats.topics.length) {
    pane.append(el("div", { class: "section-title", text: "Темы, к которым вы возвращаетесь" }));
    const chips = el("div", { class: "settings-group" }, el("div", { style: { padding: "14px", display: "flex", flexWrap: "wrap", gap: "6px" } }));
    for (const topic of stats.topics) chips.firstChild.append(el("span", { class: "badge", text: topic }));
    pane.append(chips);
  }

  pane.append(el("div", { class: "section-title", text: "Наблюдения" }));
  const box = el("div", { class: "settings-group" }, el("div", { style: { padding: "16px" } }));
  for (const insight of stats.insights) {
    box.firstChild.append(el("div", { style: { display: "flex", gap: "8px", padding: "4px 0", color: "var(--text-2)", fontSize: "14px", lineHeight: "20px" } },
      el("span", { style: { color: "var(--text-3)" }, text: "—" }),
      el("span", { text: insight }),
    ));
  }
  pane.append(box);
}

function fmtMinutes(value) {
  const total = Math.max(0, Math.min(24 * 60, Number(value) || 0));
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

function parseHHMM(value) {
  const match = String(value || "").match(/^(\d{2}):(\d{2})$/);
  if (!match) return 0;
  return Number(match[1]) * 60 + Number(match[2]);
}

function renderFocus(pane, ctx) {
  const settings = ctx.state.settings;
  const save = (patch) => {
    ctx.state.settings = saveSettings(patch);
    Object.assign(settings, patch);
    ctx.renderSettings?.();
  };
  pane.append(
    el("h1", { class: "settings-title", text: "Время и фокус" }),
    el("p", { class: "settings-desc", text: "Перерывы, тихие часы и запросы по расписанию" }),
  );

  const breakGroup = el("div", { class: "settings-group" });
  breakGroup.append(el("div", { class: "setting-row" },
    el("div", { class: "setting-text" },
      el("div", { class: "setting-name", text: "Напоминания о перерыве" }),
      el("div", { class: "setting-hint", text: "Мягкое уведомление отвлечься от экрана. Можно отложить на 15 минут." }),
    ),
    switchControl(settings.breakReminders, async (value) => {
      if (value) {
        const allowed = await ensureNotifications();
        if (!allowed) ctx.toast("Уведомления запрещены в системе — напоминание покажем внутри приложения", "error");
      }
      save({ breakReminders: value });
    }),
  ));
  if (settings.breakReminders) {
    const intervals = el("div", { class: "segmented", style: { margin: "0 14px 14px" } });
    for (const minutes of [15, 30, 45, 60, 90]) {
      intervals.append(el("button", {
        class: Number(settings.breakInterval) === minutes ? "active" : "",
        text: `${minutes} мин`,
        onclick: () => save({ breakInterval: minutes }),
      }));
    }
    breakGroup.append(intervals);
  }
  pane.append(breakGroup);

  pane.append(el("div", { class: "section-title", text: "Тихие часы" }));
  const quietGroup = el("div", { class: "settings-group" });
  quietGroup.append(el("div", { class: "setting-row" },
    el("div", { class: "setting-text" },
      el("div", { class: "setting-name", text: "Тихие часы" }),
      el("div", { class: "setting-hint", text: "В это время Clauden не присылает напоминания о перерывах." }),
    ),
    switchControl(settings.quietHours, (value) => save({ quietHours: value })),
  ));
  if (settings.quietHours) {
    const days = el("div", { style: { display: "flex", gap: "5px", padding: "0 14px 10px" } });
    const current = Array.isArray(settings.quietDays) ? settings.quietDays : [];
    ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"].forEach((label, index) => {
      const day = index + 1;
      const active = current.length === 0 || current.includes(day);
      days.append(el("button", {
        class: "btn btn-sm",
        text: label,
        style: {
          width: "36px",
          padding: "0",
          height: "32px",
          borderRadius: "50%",
          background: active ? "var(--text)" : "var(--surface-3)",
          color: active ? "var(--bg)" : "var(--text-2)",
          border: "0",
        },
        onclick: () => {
          const base = current.length ? current : [1, 2, 3, 4, 5, 6, 7];
          const next = base.includes(day) ? base.filter((item) => item !== day) : [...base, day];
          save({ quietDays: next.length === 7 ? [] : next });
        },
      }));
    });
    quietGroup.append(days);
    const times = el("div", { style: { display: "flex", gap: "10px", padding: "0 14px 14px" } },
      el("label", { class: "field", style: { flex: "1", margin: "0" } },
        el("span", { class: "field-hint", text: "С" }),
        el("input", { class: "input", type: "time", value: fmtMinutes(settings.quietStart), onchange: (e) => save({ quietStart: parseHHMM(e.target.value) }) }),
      ),
      el("label", { class: "field", style: { flex: "1", margin: "0" } },
        el("span", { class: "field-hint", text: "До" }),
        el("input", { class: "input", type: "time", value: fmtMinutes(settings.quietEnd), onchange: (e) => save({ quietEnd: parseHHMM(e.target.value) }) }),
      ),
    );
    quietGroup.append(times);
    quietGroup.append(el("div", { class: "setting-hint", style: { padding: "0 14px 14px" }, text: `Сейчас: ${quietRangeLabel(settings)}${isQuietNow(settings) ? " · тихий режим включён" : ""}` }));
  }
  pane.append(quietGroup);

  const scheduled = loadScheduled(ctx.state.user?.id);
  pane.append(el("div", { class: "section-title", text: "Отложенные запросы" }));
  pane.append(el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Запросы по расписанию" }),
        el("div", { class: "setting-hint", text: scheduled.length ? `${scheduled.length} запланировано` : "Нет запланированных" }),
      ),
      el("button", { class: "btn btn-secondary btn-sm", text: "Открыть", onclick: () => ctx.openSettings("scheduled") }),
    ),
  ));
  pane.append(el("p", { class: "field-hint", text: "Напоминания работают, пока приложение открыто. Системные уведомления можно запретить в настройках ОС." }));
}

function taskDateLabel(ts) {
  if (!ts) return "";
  return new Date(ts).toLocaleDateString("ru-RU", { day: "numeric", month: "short" });
}

function renderTasks(pane, ctx) {
  const userId = ctx.state.user?.id;
  const all = loadTasks(userId);
  const open = all.filter((task) => !task.done);
  const done = all.filter((task) => task.done);
  pane.append(
    el("h1", { class: "settings-title", text: "Задачи" }),
    el("p", { class: "settings-desc", text: "Личный список дел. Clauden может добавлять сюда задачи из чата" }),
  );
  pane.append(el("div", { class: "row between", style: { marginBottom: "10px" } },
    el("span", { class: "muted small", text: open.length ? `Открытых: ${open.length}` : "Все задачи выполнены" }),
    el("button", { class: "btn btn-primary btn-sm", text: "Задача", onclick: () => openTaskDialog(ctx) }),
  ));

  if (!all.length) {
    pane.append(el("div", { class: "empty-block" },
      el("div", { html: icon("check") }),
      el("h3", { text: "Задач пока нет" }),
      el("p", { text: "Добавьте задачу вручную или попросите Clauden: «добавь задачу проверить договор»." }),
    ));
    return;
  }

  const group = el("div", { class: "settings-group" });
  for (const task of [...open, ...done]) {
    const checkbox = el("input", { type: "checkbox", checked: task.done });
    checkbox.addEventListener("change", () => {
      toggleTask(userId, task.id);
      ctx.renderSettings?.();
    });
    group.append(el("div", { class: "setting-row" },
      el("label", { style: { display: "flex", alignItems: "center" } }, checkbox),
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", style: { textDecoration: task.done ? "line-through" : "none", opacity: task.done ? ".55" : "1" }, text: task.title }),
        task.dueAt ? el("div", { class: "setting-hint", text: `Срок: ${taskDateLabel(task.dueAt)}` }) : null,
      ),
      el("button", { class: "icon-btn", title: "Удалить", onclick: () => { deleteTask(userId, task.id); ctx.renderSettings?.(); }, html: icon("trash") }),
    ));
  }
  pane.append(group);
  if (done.length) {
    pane.append(el("p", { class: "field-hint", text: `Выполнено: ${done.length}. Задачи хранятся на этом устройстве.` }));
  }
}

function openTaskDialog(ctx) {
  const input = el("input", { class: "input", placeholder: "Что нужно сделать" });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Новая задача" })),
    el("div", { class: "modal-body" }, el("div", { class: "field" }, el("label", { text: "Задача" }), input)),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      el("button", { class: "btn btn-primary", text: "Добавить", onclick: () => {
        const title = input.value.trim();
        if (!title) return;
        addTask(ctx.state.user?.id, title);
        modal.close();
        ctx.toast("Задача добавлена");
        ctx.renderSettings?.();
      } }),
    ),
  ));
  setTimeout(() => input.focus(), 30);
}

function scheduledDate(ts) {
  return new Date(ts).toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
}

function toLocalInput(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function renderScheduled(pane, ctx) {
  const userId = ctx.state.user?.id;
  const items = [...loadScheduled(userId)].sort((a, b) => {
    const left = a.runAt > 0 ? a.runAt : Number.MAX_SAFE_INTEGER;
    const right = b.runAt > 0 ? b.runAt : Number.MAX_SAFE_INTEGER;
    return left - right;
  });
  pane.append(
    el("h1", { class: "settings-title", text: "Отложенные" }),
    el("p", { class: "settings-desc", text: "Clauden выполнит запрос и пришлёт ответ в новый чат" }),
  );
  pane.append(el("div", { class: "row between", style: { marginBottom: "10px" } },
    el("span", { class: "muted small", text: items.length ? `${items.length} запланировано` : "Пока ничего не запланировано" }),
    el("button", { class: "btn btn-primary btn-sm", text: "Запрос", onclick: () => openScheduledDialog(ctx) }),
  ));

  if (!items.length) {
    pane.append(el("div", { class: "empty-block" },
      el("div", { html: icon("clock") }),
      el("h3", { text: "Нет отложенных запросов" }),
      el("p", { text: "Например: «завтра в 9 утра собери сводку по проекту». Запрос выполнится, пока приложение открыто." }),
    ));
    return;
  }

  const group = el("div", { class: "settings-group" });
  for (const task of items) {
    const status = task.runAt > 0
      ? `Следующий запуск: ${scheduledDate(task.runAt)} · ${repeatLabel(task.repeat)}`
      : `Выполнено: ${task.lastRunAt ? scheduledDate(task.lastRunAt) : "—"}`;
    const row = el("div", { class: "setting-row", style: { cursor: task.lastChatId ? "pointer" : "default" }, onclick: () => {
      if (task.lastChatId) ctx.navigate(`/chat/${task.lastChatId}`);
    } },
      el("span", { class: "list-row-icon", html: icon("clock") }),
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: task.title }),
        el("div", { class: "setting-hint", text: status }),
        el("div", { class: "setting-hint", style: { marginTop: "4px", color: "var(--text-3)" }, text: task.prompt.slice(0, 140) }),
        task.lastStatus ? el("div", { class: "setting-hint", style: { marginTop: "4px", color: task.lastStatus === "Готово" ? "var(--ok)" : "var(--danger)" }, text: task.lastStatus }) : null,
      ),
      el("button", { class: "icon-btn", title: "Удалить", onclick: (event) => {
        event.stopPropagation();
        deleteScheduled(userId, task.id);
        ctx.renderSettings?.();
      }, html: icon("trash") }),
    );
    group.append(row);
  }
  pane.append(group);
  pane.append(el("p", { class: "field-hint", text: "Ответы сохраняются в чаты. Запросы выполняются, пока Clauden открыт; повторяющиеся — по расписанию." }));
}

function openScheduledDialog(ctx) {
  const title = el("input", { class: "input", placeholder: "Название, например «Сводка утром»" });
  const prompt = el("textarea", { class: "textarea", rows: "4", placeholder: "Что Clauden должен сделать" });
  const when = el("input", { class: "input", type: "datetime-local", value: toLocalInput(new Date(Date.now() + 60 * 60 * 1000)) });
  let repeat = "once";
  const repeatRow = el("div", { class: "segmented", style: { width: "100%" } });
  const drawRepeat = () => {
    clear(repeatRow);
    for (const [id, label] of [["once", "Один раз"], ["daily", "Каждый день"], ["weekly", "Каждую неделю"]]) {
      repeatRow.append(el("button", {
        class: repeat === id ? "active" : "",
        text: label,
        onclick: () => { repeat = id; drawRepeat(); },
      }));
    }
  };
  drawRepeat();
  const error = el("p", { class: "field-hint", style: { color: "var(--danger)" } });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Отложенный запрос" })),
    el("div", { class: "modal-body" },
      el("div", { class: "field" }, el("label", { text: "Название" }), title),
      el("div", { class: "field" }, el("label", { text: "Запрос" }), prompt),
      el("div", { class: "field" }, el("label", { text: "Когда" }), when),
      el("div", { class: "field" }, el("label", { text: "Повтор" }), repeatRow),
      error,
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      el("button", { class: "btn btn-primary", text: "Запланировать", onclick: () => {
        const runAt = new Date(when.value).getTime();
        if (!prompt.value.trim()) { error.textContent = "Напишите, что нужно сделать"; return; }
        if (!runAt || runAt <= Date.now()) { error.textContent = "Выберите время в будущем"; return; }
        const task = addScheduled(ctx.state.user?.id, { title: title.value, prompt: prompt.value, runAt, repeat });
        if (!task) { error.textContent = "Не удалось сохранить запрос"; return; }
        modal.close();
        ctx.toast("Запрос запланирован");
        ctx.renderSettings?.();
      } }),
    ),
  ));
  setTimeout(() => prompt.focus(), 30);
}

function renderCustomize(pane, ctx) {
  const userId = ctx.state.user?.id;
  const data = loadCustomize(userId);
  pane.append(
    el("h1", { class: "settings-title", text: "Навыки и плагины" }),
    el("p", { class: "settings-desc", text: "Каталог Claude Market: навыки, плагины и MCP-коннекторы" }),
  );
  const rows = [
    ["sparkles", "Навыки", data.skills.length ? `${data.skills.length} установлено · готовые инструкции для задач` : "Готовые инструкции для типовых задач", "/market/skills"],
    ["layers", "Плагины", data.plugins.length ? `${data.plugins.length} установлено · наборы навыков и коннекторов` : "Наборы навыков и коннекторов под одну задачу", "/market/plugins"],
    ["plug", "Коннекторы", "MCP-серверы: Notion, Linear, GitHub и другие", "/settings/connectors"],
  ];
  const group = el("div", { class: "settings-group" });
  for (const [iconName, label, hint, href] of rows) {
    group.append(el("div", { class: "setting-row" },
      el("span", { class: "list-row-icon", html: icon(iconName) }),
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: label }),
        el("div", { class: "setting-hint", text: hint }),
      ),
      el("button", { class: "btn btn-secondary btn-sm", text: "Открыть", onclick: () => ctx.navigate(href) }),
    ));
  }
  pane.append(group);

  if (data.skills.length || data.plugins.length) {
    pane.append(el("div", { class: "section-title", text: "Установлено" }));
    const installed = el("div", { class: "settings-group" });
    for (const plugin of data.plugins) {
      installed.append(el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("layers") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: plugin.title || plugin.id }),
          el("div", { class: "setting-hint", text: "Плагин · подключён к чатам" }),
        ),
        el("button", { class: "icon-btn", title: "Убрать", onclick: () => {
          saveCustomize(userId, { ...data, plugins: data.plugins.filter((item) => item.id !== plugin.id) });
          ctx.toast("Плагин убран");
          ctx.renderSettings?.();
        }, html: icon("trash") }),
      ));
    }
    for (const skill of data.skills) {
      installed.append(el("div", { class: "setting-row" },
        el("span", { class: "list-row-icon", html: icon("sparkles") }),
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: skill.title || skill.id }),
          el("div", { class: "setting-hint", text: "Навык · добавляется в системный промпт" }),
        ),
        el("button", { class: "icon-btn", title: "Убрать", onclick: () => {
          saveCustomize(userId, { ...data, skills: data.skills.filter((item) => item.id !== skill.id) });
          ctx.toast("Навык убран");
          ctx.renderSettings?.();
        }, html: icon("trash") }),
      ));
    }
    pane.append(installed);
  }

  pane.append(el("p", { class: "field-hint", text: "Установленные навыки и плагины добавляются в системный промпт каждого чата. Коннекторами управляйте в разделе «Коннекторы»." }));
}

function setPassword(ctx) {
  const input = el("input", { class: "input", type: "password", placeholder: "Новый пароль (от 8 символов)" });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Пароль Clauden" })),
    el("div", { class: "modal-body" }, el("div", { class: "field" }, el("label", { text: "Пароль" }), input)),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      el("button", { class: "btn btn-primary", text: "Сохранить", onclick: async () => {
        try {
          const { user } = await authApi.setPassword(input.value);
          ctx.setUser(user);
          modal.close();
          ctx.toast("Пароль сохранён", "success");
          ctx.renderSettings?.();
        } catch (err) { ctx.toast(err.message || "Не удалось", "error"); }
      }}),
    ),
  ));
  setTimeout(() => input.focus(), 30);
}

async function deleteAccount(ctx) {
  const ok = await confirmDialog({
    title: "Удалить аккаунт?",
    text: "Это действие необратимо: профиль, подписка и все резервные копии чатов будут удалены.",
    confirm: "Удалить навсегда",
  });
  if (!ok) return;
  try {
    await authApi.deleteAccount();
    await store.clearChats();
    ctx.signOut();
  } catch (err) { ctx.toast(err.message || "Не удалось удалить аккаунт", "error"); }
}

function renderPlans(pane, ctx) {
  const user = ctx.state.user;
  pane.append(
    el("h1", { class: "settings-title", text: "Тарифы" }),
    el("p", { class: "settings-desc", text: "Больше лимитов, модели и Code. Оплата СБП, USDT или картой" }),
  );
  const currentPlan = String(user?.plan || "Free");
  const planUntil = Number(user?.planUntil) || 0;
  if (currentPlan.toLowerCase() !== "free") {
    const until = planUntil
      ? new Date(planUntil).toLocaleDateString("ru-RU", {
          day: "numeric",
          month: "long",
          year: "numeric",
        })
      : "";
    pane.append(
      el("div", { class: "notice-box", style: { marginBottom: "14px" } },
        el("div", { class: "setting-name", text: `Тариф ${currentPlan} активен` }),
        el("p", { class: "muted small", style: { margin: "6px 0 0" }, text: until ? `Оплачен до ${until}.` : "Оплата уже применена." }),
      ),
    );
  }
  const grid = el("div", { class: "plan-grid" });
  grid.append(el("p", { class: "muted small", text: "Загружаю тарифы…" }));
  if (user?.plan === "Free") {
    const claudeLinked = Boolean(ctx.state.claude?.linked);
    pane.append(el("div", { class: "notice-box", style: { marginBottom: "14px" } },
      el("div", { class: "setting-name", text: "Шлюз Clauden на тарифе Free недоступен" }),
      el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: claudeLinked
        ? "Ваш Claude-аккаунт уже привязан — можно работать в режиме Claude без подписки Clauden."
        : "Оформите подписку Clauden или привяжите свой аккаунт Claude в приложении для ПК." }),
      isDesktopApp()
        ? el("button", { class: "btn btn-secondary btn-sm", text: claudeLinked ? "Режим Claude" : "Привязать Claude", onclick: () => ctx.openSettings("claude") })
        : el("div", { class: "muted small", text: "Claude-аккаунт можно привязать в приложении Clauden для ПК." }),
    ));
  }
  pane.append(grid);
  const load = async () => {
    if (!ctx.state.plans.length) {

      for (let attempt = 0; attempt < 3 && !ctx.state.plans.length; attempt += 1) {
        try {
          const data = await api.plans();
          ctx.state.plans = data?.data || [];
        } catch {  }
        if (!ctx.state.plans.length && attempt < 2) {
          await new Promise((resolve) => setTimeout(resolve, 700 * (attempt + 1)));
        }
      }
    }
    clear(grid);
    const plans = ctx.state.plans;
    const current = user?.plan || "Free";
    const autoRenew = Boolean(user?.autoRenew);
    const onCancelRenewal = () => cancelRenewal(ctx);

    for (const plan of plans) {
      grid.append(planCard(plan, ctx, { current, autoRenew, onCancelRenewal }));
    }
    if (!plans.length) {
      grid.append(
        el("div", { class: "muted small" },
          el("p", { text: "Не удалось загрузить тарифы." }),
          el("button", {
            class: "btn btn-secondary btn-sm",
            text: "Повторить",
            onclick: () => {
              ctx.state.plans = [];
              grid.replaceChildren(el("p", { class: "muted small", text: "Загружаю тарифы…" }));
              load();
            },
          }),
        ),
      );
    }
  };
  load();
  pane.append(el("p", { class: "field-hint", style: { marginTop: "14px" }, text: "Оплата подтверждается автоматически в течение минуты. Тариф включится без повторного входа." }));
  pane.append(el("p", { class: "field-hint", style: { marginTop: "8px" } },
    "Документы: ",
    el("a", { href: "/pricing", target: "_blank", rel: "noopener" }, "тарифы и цены"),
    " · ",
    el("a", { href: "/terms", target: "_blank", rel: "noopener" }, "пользовательское соглашение"),
    " · ",
    el("a", { href: "/privacy", target: "_blank", rel: "noopener" }, "политика конфиденциальности"),
    ". Поддержка: ",
    el("a", { href: "https://t.me/ClaudenApp", target: "_blank", rel: "noopener" }, "Telegram @ClaudenApp"),
    ".",
  ));
}

function cancelRenewal(ctx) {
  const user = ctx.state.user || {};
  confirmDialog({
    title: "Отменить продление?",
    text: "Тариф " + (user.plan || "Clauden") + " останется до конца оплаченного срока. " +
      "Следующее автосписание не произойдёт.",
    confirm: "Отменить продление",
  }).then(async (ok) => {
    if (!ok) return;
    try {
      const result = await api.cancelRenewal();
      if (result?.user) ctx.setUser(result.user);
      ctx.toast("Автопродление отменено", "success");
      ctx.renderSettings?.();
    } catch (err) {
      ctx.toast(err.message || "Не удалось отменить автопродление", "error");
    }
  });
}

function planCard(plan, ctx, { current, autoRenew = false, onCancelRenewal = () => {} }) {
  const isCurrent = plan.id === current;
  const featured = plan.id === "Pro";
  const card = el("div", { class: `plan-card ${isCurrent ? "current" : ""} ${featured ? "featured" : ""}` });
  const compareAt = Number(plan.compareAt) || 0;
  card.append(
    el("div", { class: "plan-head" },
      el("span", { class: "plan-name", text: plan.label }),
      plan.badge ? el("span", { class: "badge warn", text: plan.badge }) : null,
    ),
    el("div", { class: "plan-price" },
      plan.price ? `$${plan.price}` : "Бесплатно",
      plan.price ? el("small", { text: " / мес" }) : null,
    ),
    compareAt > Number(plan.price || 0)
      ? el("div", { class: "plan-compare", text: `обычно $${compareAt}` })
      : null,
    el("div", { class: "plan-tagline", text: plan.tagline || "" }),
  );
  const features = el("ul", { class: "plan-features" });
  const featureList = plan.features || [];
  for (const feature of featureList) {
    features.append(el("li", null, el("span", { html: icon("check") }), el("span", { text: feature })));
  }
  card.append(features);

  if (plan.paid) {
    const showCancel = isCurrent && autoRenew;
    card.append(el("button", {
      class: `btn ${isCurrent ? "btn-secondary" : "btn-primary"} btn-block`,
      text: showCancel ? "Отменить продление" : isCurrent ? "Текущий тариф" : plan.cta || `Получить ${plan.label}`,
      disabled: isCurrent && !autoRenew,
      onclick: showCancel ? onCancelRenewal : () => openCheckout(ctx, plan),
    }));
  } else {
    card.append(el("div", { class: "badge", style: { alignSelf: "flex-start" }, text: isCurrent ? "Текущий тариф" : "Бесплатный" }));
  }
  return card;
}

function openCheckout(ctx, plan) {
  let method = "xrocket";
  let orderId = "";
  let timer = 0;
  let autoRenew = true;
  const methods = [
    { id: "xrocket", name: "Криптовалюта", currency: "USDT" },
    { id: "platega", name: "СБП (Россия)", currency: "RUB" },
    { id: "lava", name: "Карта / PayPal", currency: "USD" },
  ];
  const status = el("div", { class: "notice-box", hidden: true });
  const payArea = el("div", { hidden: true });
  const renewBox = el("label", { class: "checkout-renew", hidden: true },
    el("input", {
      type: "checkbox",
      checked: true,
      onchange: (event) => { autoRenew = Boolean(event.target.checked); },
    }),
    el("span", null,
      el("span", { class: "renew-title", text: "Автопродление" }),
      el("span", {
        class: "renew-hint",
        text: "Тариф продлится автоматически через 30 дней. Отключить можно в любой момент в тарифах.",
      }),
    ),
  );
  const create = el("button", { class: "btn btn-primary btn-block", text: `Оплатить ${plan.label}` });

  const body = el("div", null,
    el("div", { class: "modal-head" },
      el("h2", { class: "modal-title", text: `${plan.label} · ${(Number(plan.price || 0) * 100).toLocaleString("ru-RU")} ₽ / 30 дней` }),
    ),
    el("div", { class: "modal-body" },
      el("p", { class: "muted small", style: { marginTop: "0" }, text: plan.tagline || "" }),
      el("p", { class: "field-hint", style: { marginTop: "0" }, text: `Ориентир в долларах: $${plan.price}. Итоговая сумма в валюте платежа показывается перед подтверждением.` }),
      el("div", { class: "section-title", text: "Способ оплаты" }),
      ...methods.map((m) => el("label", {
        class: `checkout-method ${m.id === method ? "active" : ""}`,
        dataset: { method: m.id },
        onclick: () => {
          method = m.id;
          body.querySelectorAll(".checkout-method").forEach((n) => n.classList.toggle("active", n.dataset.method === method));
          renewBox.hidden = method !== "platega";
        },
      },
        el("input", { type: "radio", name: "pay-method", checked: m.id === method }),
        el("span", { class: "method-name", text: m.name }),
        el("span", { class: "method-currency", text: m.currency }),
      )),
      renewBox,
      el("p", { class: "field-hint", style: { marginTop: "10px" } },
        "Оплачивая, вы принимаете ",
        el("a", { href: "/offer", target: "_blank", rel: "noopener" }, "Публичную оферту"),
        " и ",
        el("a", { href: "/privacy", target: "_blank", rel: "noopener" }, "Политику конфиденциальности"),
        ". Цены и состав тарифов — ",
        el("a", { href: "/pricing", target: "_blank", rel: "noopener" }, "на странице тарифов"),
        ", условия возврата — ",
        el("a", { href: "/pricing#refund", target: "_blank", rel: "noopener" }, "здесь"),
        ". Поддержка: ",
        el("a", { href: "https://t.me/ClaudenApp", target: "_blank", rel: "noopener" }, "Telegram @ClaudenApp"),
        ".",
      ),
      status,
      payArea,
      create,
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Закрыть", onclick: () => { clearInterval(timer); modal.close(); } }),
    ),
  );

  const modal = openModal(body, { onClose: () => clearInterval(timer) });

  create.addEventListener("click", async () => {
    create.disabled = true;
    create.innerHTML = '<span class="spinner"></span>';
    try {
      const result = await api.checkout(plan.id, method, method === "platega" ? autoRenew : false);
      orderId = result.order?.id || "";
      const url = result.order?.url || "";
      payArea.hidden = false;
      clear(payArea);
      payArea.append(
        el("div", { class: "divider" }),
        el("div", { class: "setting-name", text: "Счёт создан" }),
        el("p", { class: "muted small", text: `Сумма: ${result.order?.chargeAmount || result.order?.amount || plan.price} ${result.order?.currency === "RUB" || method === "platega" ? "₽" : "$"}. После оплаты тариф включится автоматически.` }),
        method === "platega"
          ? el("p", { class: "muted small", text: "СБП: откройте ссылку, выберите банк и подтвердите платёж. Чек и статус оплаты придут на email аккаунта." })
          : null,
        result.order?.autoRenew
          ? el("p", { class: "muted small", text: "Автопродление включено: списание каждые 30 дней, отменить можно в тарифах." })
          : null,
        result.order?.renewalNote
          ? el("p", { class: "muted small", text: result.order.renewalNote })
          : null,
        url ? el("a", { class: "btn btn-primary btn-block", href: url, target: "_blank", rel: "noopener" }, "Открыть оплату") : null,
        url ? el("p", { class: "order-link muted", text: url }) : null,
        result.order?.account?.status === "active"
          ? el("p", { class: "muted small", text: `Подписка выдана: ${result.order.account.login || ""}` })
          : null,
      );
      status.hidden = false;
      status.textContent = "Ожидаю подтверждение платежа…";
      clearInterval(timer);
      timer = setInterval(async () => {
        if (!orderId) return;
        try {
          const { order } = await api.order(orderId);
          if (order.status === "paid") {
            clearInterval(timer);
            status.className = "notice-box";
            status.textContent = order.account?.status === "active"
              ? `Оплата получена. Подписка выдана: ${order.account.login || ""}`
              : order.account?.status === "provisioning"
                ? "Оплата получена. Выдаём подписку — это займёт до минуты."
                : "Оплата получена. Тариф включён.";
            create.remove();
            try {
              const { user } = await authApi.me();
              ctx.setUser(user);
            } catch {  }
            setTimeout(() => { modal.close(); ctx.renderSettings?.(); ctx.toast(`Тариф ${plan.label} активен`, "success"); }, 900);
          } else if (order.status === "failed" || order.status === "expired") {
            clearInterval(timer);
            status.className = "error-box";
            status.textContent = order.status === "expired" ? "Счёт истёк. Создайте новый." : "Платёж не прошёл. Попробуйте другой способ.";
            create.disabled = false;
            create.textContent = "Создать новый счёт";
          }
        } catch {  }
      }, 3000);
    } catch (err) {
      status.hidden = false;
      status.className = "error-box";
      status.textContent = err.message || "Не удалось создать счёт";
      create.disabled = false;
      create.textContent = "Попробовать снова";
    }
  });
}

function renderUsage(pane, ctx) {
  pane.append(
    el("h1", { class: "settings-title", text: "Использование" }),
    el("p", { class: "settings-desc", text: "Расход лимитов тарифа" }),
    el("div", { id: "usage-box" }, el("div", { class: "loading-block" }, el("span", { class: "spinner" }), "Загружаю…")),
  );
  const box = $("#usage-box");
  api.usage().then(({ usage }) => {
    clear(box);
    if (!usage) {
      box.append(el("p", { class: "muted", text: "Нет данных." }));
      return;
    }
    if (usage.plan === "Free") {
      const claudeLinked = Boolean(ctx.state.claude?.linked);
      box.append(el("div", { class: "notice-box" },
        el("div", { class: "setting-name", text: claudeLinked ? "Шлюз Clauden на тарифе Free недоступен" : "Нужна подписка" }),
        el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: claudeLinked
          ? "Ваш Claude-аккаунт уже привязан — запросы идут через него, лимиты считает Anthropic."
          : "Оформите подписку Clauden или привяжите свой аккаунт Claude в приложении для ПК." }),
        el("button", { class: "btn btn-primary btn-sm", text: claudeLinked ? "Тарифы Clauden" : "Посмотреть тарифы", onclick: () => ctx.openSettings("plans") }),
        isDesktopApp()
          ? el("button", { class: "btn btn-secondary btn-sm", style: { marginLeft: "8px" }, text: claudeLinked ? "Режим Claude" : "Привязать Claude", onclick: () => ctx.openSettings("claude") })
          : el("div", { class: "muted small", style: { marginTop: "8px" }, text: "Claude-аккаунт можно привязать в приложении Clauden для ПК." }),
      ));
      return;
    }
    if (usage.session?.unlimited) {
      box.append(
        el("p", { class: "field-hint", style: { marginBottom: "10px" }, text: "На Ultra нет 5-часового окна: действует только недельный лимит." }),
        usageBar("Недельный лимит", usage.weekly?.percent || 0, usage.weekly?.resetsAt),
      );
    } else {
      box.append(
        usageBar("5-часовое окно", usage.session?.percent || 0, usage.session?.resetsAt),
        usageBar("Недельный лимит", usage.weekly?.percent || 0, usage.weekly?.resetsAt),
      );
    }
    box.append(
      el("p", { class: "field-hint", style: { marginTop: "10px" }, text: "Проценты показывают границу, которая закончится раньше — общий расход или output." }),
    );
  }).catch((err) => {
    clear(box);
    box.append(el("div", { class: "error-box", text: err.message || "Не удалось загрузить лимиты" }));
  });
}

function usageBar(label, percent, resetsAt) {
  const value = Math.max(0, Math.min(100, Math.round(percent)));
  const reset = resetsAt ? new Date(resetsAt) : null;
  return el("div", { class: "usage-row" },
    el("div", { class: "usage-head" }, el("span", { text: label }), el("span", { text: `${value}%` })),
    el("div", { class: "usage-bar" }, el("div", { class: `usage-fill ${value > 85 ? "high" : ""}`, style: { width: `${value}%` } })),
    el("div", { class: "usage-reset", text: reset ? `Сброс ${reset.toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })}` : "" }),
  );
}

function renderApiKeys(pane, ctx) {
  pane.append(
    el("h1", { class: "settings-title", text: "API-ключ" }),
    el("p", { class: "settings-desc" },
      "Ключ Clauden для OpenCode, Claude Code, Cursor и других Anthropic- и OpenAI-совместимых клиентов. ",
      el("a", { href: "/docs", target: "_blank", rel: "noopener", text: "Документация API" }),
    ),
  );

  const box = el("div", {});
  pane.append(box);

  let keys = [];
  let limit = 0;
  let plan = ctx.state.user?.plan || "Free";
  let canIssue = false;
  let fresh = "";
  let busy = false;
  let loaded = false;
  let error = "";

  const formatDate = (ms) => {
    if (!ms) return "—";
    return new Date(Number(ms)).toLocaleString("ru-RU", {
      day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit",
    });
  };

  const apply = (payload) => {
    keys = Array.isArray(payload?.data) ? payload.data : [];
    limit = Number(payload?.limit) || 0;
    plan = payload?.plan || plan;
    canIssue = Boolean(payload?.canIssue);
  };

  const reload = async () => {
    try {
      apply(await api.keys());
    } catch (err) {
      error = err.message || "Не удалось загрузить API-ключи";
    }
  };

  const infoRow = (label, value) => el("div", { class: "api-key-info-row" },
    el("span", { text: label }),
    el("span", { text: value }),
  );

  const keyCard = (key) => el("div", { class: "settings-group" },
    el("div", { class: "api-key-card" },
      el("div", { class: "api-key-head" },
        el("span", { class: "mono", style: { fontSize: "14px", fontWeight: "600", wordBreak: "break-all" }, text: key.prefix }),
        el("span", { class: `badge ${key.locked ? "err" : "ok"}`, text: key.locked ? "заблокирован" : "активен" }),
      ),
      el("div", { class: "api-key-info" },
        infoRow("Тариф", key.plan || plan),
        infoRow("Выпущен", formatDate(key.createdAt)),
        infoRow("Запросов", String(key.requests || 0)),
        infoRow("Токенов", `${key.tokensIn || 0} вход / ${key.tokensOut || 0} выход`),
        infoRow("Последний запрос", key.lastUsedAt ? formatDate(key.lastUsedAt) : "ещё не использовался"),
        infoRow("Привязка", key.bindMode === "device" ? "к установке" : "без привязки"),
      ),
      key.locked && key.lockReason
        ? el("p", { class: "field-hint", text: `Причина блокировки: ${key.lockReason}` })
        : null,
      el("div", { class: "row", style: { justifyContent: "flex-end", marginTop: "12px" } },
        el("button", { class: "btn btn-danger btn-sm", text: "Отозвать", disabled: busy, onclick: () => revoke(key) }),
      ),
    ),
  );

  const draw = () => {
    clear(box);

    if (!loaded && !fresh) {
      box.append(el("div", { class: "loading-block" }, el("span", { class: "spinner" }), "Загружаю…"));
      return;
    }

    if (!canIssue && !fresh) {
      box.append(
        el("div", { class: "settings-group" },
          el("div", { class: "api-key-card" },
            el("div", { class: "setting-name", text: "Только по подписке Clauden" }),
            el("p", { class: "setting-hint", style: { margin: "6px 0 12px" }, text: "API-ключ открывается подпиской Pro, Max или Ultra, оформленной в Clauden. Подписка Anthropic или тариф, выданный вручную, ключ не даёт." }),
            el("button", { class: "btn btn-primary btn-sm", text: "Посмотреть тарифы", onclick: () => ctx.openSettings("plans") }),
          ),
        ),
      );
      if (error) box.append(el("div", { class: "error-box", text: error }));
      return;
    }

    if (fresh) {
      box.append(
        el("div", { class: "settings-group" },
          el("div", { class: "api-key-card" },
            el("div", { class: "setting-name", text: "Ключ готов" }),
            el("div", { class: "setting-hint", text: "Полностью показываем один раз — сохраните сейчас." }),
            el("div", { class: "api-key-value", text: fresh }),
            el("div", { class: "row", style: { flexWrap: "wrap" } },
              el("button", { class: "btn btn-primary btn-sm", text: "Скопировать", onclick: () => {
                copyText(fresh).then(() => ctx.toast("Ключ скопирован", "success"));
              }}),
              el("button", { class: "btn btn-ghost btn-sm", text: "Готово", onclick: () => { fresh = ""; draw(); } }),
            ),
          ),
        ),
        el("p", { class: "field-hint" },
          "Ключ привязан к вашему тарифу и расходует его лимиты. Передавайте его в заголовке x-api-key или Authorization: Bearer. ",
          el("a", { href: "/docs", target: "_blank", rel: "noopener", text: "Как подключить" }),
        ),
      );
      return;
    }

    if (!keys.length) {
      box.append(
        el("div", { class: "settings-group" },
          el("div", { class: "api-key-card" },
            el("div", { class: "setting-name", text: "Ключа пока нет" }),
            el("p", { class: "setting-hint", style: { margin: "6px 0 12px" }, text: "Ключ работает с Anthropic-совместимыми клиентами: Claude Code, Cursor и другими. Он привязан к вашему тарифу и расходует его лимиты." }),
            el("button", { class: "btn btn-primary", text: busy ? "Выпускаем…" : "Получить ключ", disabled: busy, onclick: create }),
          ),
        ),
      );
    } else {
      for (const key of keys) box.append(keyCard(key));
      const actions = el("div", { class: "row", style: { flexWrap: "wrap", marginBottom: "10px" } });
      if (limit === 0 || keys.length < limit) {
        actions.append(el("button", { class: "btn btn-secondary btn-sm", text: busy ? "Выпускаем…" : "Выпустить ещё ключ", disabled: busy, onclick: create }));
      }
      actions.append(el("button", { class: "btn btn-secondary btn-sm", text: busy ? "Сбрасываем…" : "Сбросить ключ", disabled: busy, onclick: reset }));
      box.append(actions);
      if (limit > 0) {
        box.append(el("p", { class: "field-hint", text: `На тарифе ${plan} можно держать до ${limit} ключ(ей). Сброс выпускает новый ключ и сразу отзывает старые.` }));
      }
    }

    if (error) box.append(el("div", { class: "error-box", text: error }));
  };

  const create = async () => {
    if (busy) return;
    busy = true;
    error = "";
    draw();
    try {
      const created = await api.createKey();
      fresh = created.key || "";
      await reload();
      ctx.toast("Ключ выпущен", "success");
    } catch (err) {
      error = err.message || "Не удалось выпустить ключ";
    } finally {
      busy = false;
      loaded = true;
      draw();
    }
  };

  const reset = async () => {
    if (busy) return;
    const ok = await confirmDialog({
      title: "Сбросить ключ?",
      text: "Старый ключ сразу перестанет работать. Новый покажем один раз.",
      confirm: "Сбросить",
    });
    if (!ok) return;
    busy = true;
    error = "";
    draw();
    try {
      const created = await api.resetKey();
      fresh = created.key || "";
      await reload();
      ctx.toast("Ключ сброшен", "success");
    } catch (err) {
      error = err.message || "Не удалось сбросить ключ";
    } finally {
      busy = false;
      loaded = true;
      draw();
    }
  };

  const revoke = async (key) => {
    if (busy) return;
    const ok = await confirmDialog({
      title: "Отозвать ключ?",
      text: `Ключ ${key.prefix} перестанет работать. Новый можно выпустить в любой момент.`,
      confirm: "Отозвать",
    });
    if (!ok) return;
    busy = true;
    error = "";
    draw();
    try {
      await api.revokeKey(key.id);
      await reload();
      ctx.toast("Ключ отозван", "success");
    } catch (err) {
      error = err.message || "Не удалось отозвать ключ";
    } finally {
      busy = false;
      loaded = true;
      draw();
    }
  };

  draw();
  api.keys().then((payload) => {
    apply(payload);
  }).catch((err) => {
    error = err.message || "Не удалось загрузить API-ключи";
  }).finally(() => {
    loaded = true;
    draw();
  });
}

const APPEARANCE_DEFAULTS = {
  style: "classic",
  accent: "#d97757",
  radius: 12,
  glass: 65,
  buttonStyle: "soft",
  toggleStyle: "classic",
  textSize: 100,
  motion: "full",
  density: "comfortable",
};

const STYLE_PACKS = [
  { id: "classic", name: "Классика", hint: "Привычный Clauden" },
  { id: "glass", name: "iOS Glass", hint: "Стекло, свет и глубина" },
  { id: "noir", name: "Noir", hint: "Глубокий чёрный" },
];

const ACCENT_PRESETS = [
  ["#d97757", "Терракота"],
  ["#0a84ff", "iOS синий"],
  ["#af52de", "Фиолетовый"],
  ["#34c759", "Зелёный"],
  ["#ff375f", "Розовый"],
  ["#ff9f0a", "Оранжевый"],
  ["#64d2ff", "Голубой"],
  ["#8e8e93", "Графит"],
];

function appearanceSegmented(settings, ctx, key, options) {
  return el("div", { class: "segmented" },
    ...options.map(([value, label, iconName]) =>
      el("button", {
        class: settings[key] === value ? "active" : "",
        onclick: () => {
          ctx.state.settings = saveSettings({ [key]: value });
          ctx.applyTheme?.();
          ctx.renderSettings?.();
        },
      }, iconName ? el("span", { html: icon(iconName), style: { width: "14px", height: "14px" } }) : null, label)),
  );
}

function appearanceRange(ctx, { key, name, hint, min, max, value, format }) {
  const output = el("span", { class: "range-value", text: format(value) });
  const input = el("input", {
    class: "range",
    type: "range",
    min: String(min),
    max: String(max),
    step: "1",
    value: String(value),
  });
  input.addEventListener("input", () => {
    const next = Number(input.value);
    output.textContent = format(next);
    ctx.state.settings = saveSettings({ [key]: next });
    ctx.applyTheme?.();
  });
  return el("div", { class: "appearance-control" },
    el("div", { class: "appearance-head" },
      el("span", { class: "setting-name", text: name }),
      output,
    ),
    el("div", { class: "setting-hint", text: hint }),
    input,
  );
}

function renderAppearance(pane, ctx) {
  const settings = ctx.state.settings;
  const style = settings.style || "classic";
  const accent = settings.accent || "#d97757";
  pane.append(
    el("h1", { class: "settings-title", text: "Оформление" }),
    el("p", { class: "settings-desc", text: "Готовые стили и ручная настройка интерфейса" }),
  );

  const themeGroup = el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Тема" }),
        el("div", { class: "setting-hint", text: "Системная, светлая или тёмная" }),
      ),
      appearanceSegmented(settings, ctx, "theme",
        [["system", "Системная", "monitor"], ["light", "Светлая", "sun"], ["dark", "Тёмная", "moon"]]),
    ),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Шрифт" }),
        el("div", { class: "setting-hint", text: "Фирменный Anthropic Sans или системный" }),
      ),
      appearanceSegmented(settings, ctx, "font", [["default", "Clauden"], ["system", "Системный"]]),
    ),
  );
  pane.append(themeGroup);

  const packs = el("div", { class: "style-packs" },
    ...STYLE_PACKS.map((pack) => el("button", {
      class: `style-pack ${style === pack.id ? "active" : ""}`,
      type: "button",
      onclick: () => {
        ctx.state.settings = saveSettings({ style: pack.id });
        ctx.applyTheme?.();
        ctx.renderSettings?.();
        ctx.toast(`Стиль: ${pack.name}`, "success");
      },
    },
      el("span", { class: `style-pack-preview pack-${pack.id}` },
        el("span", { class: "spp-rail" }),
        el("span", { class: "spp-body" },
          el("span", { class: "spp-line" }),
          el("span", { class: "spp-line short" }),
          el("span", { class: "spp-pill" }),
        ),
      ),
      el("span", { class: "style-pack-name", text: pack.name }),
      el("span", { class: "style-pack-hint", text: pack.hint }),
    )),
  );
  pane.append(
    el("div", { class: "appearance-section-label", text: "Стиль" }),
    packs,
  );

  const swatches = el("div", { class: "accent-swatches" },
    ...ACCENT_PRESETS.map(([value, name]) => el("button", {
      class: `accent-swatch ${String(accent).toLowerCase() === value ? "active" : ""}`,
      type: "button",
      title: name,
      "aria-label": name,
      style: { background: value },
      onclick: () => {
        ctx.state.settings = saveSettings({ accent: value });
        ctx.applyTheme?.();
        ctx.renderSettings?.();
      },
    })),
  );
  const colorInput = el("input", {
    class: "accent-color",
    type: "color",
    value: accent,
    title: "Свой цвет",
  });
  colorInput.addEventListener("input", () => {
    ctx.state.settings = saveSettings({ accent: colorInput.value });
    ctx.applyTheme?.();
  });

  const customize = el("div", { class: "settings-group appearance-customize" },
    el("div", { class: "appearance-control" },
      el("div", { class: "appearance-head" },
        el("span", { class: "setting-name", text: "Акцентный цвет" }),
        colorInput,
      ),
      el("div", { class: "setting-hint", text: "Кнопки, ссылки, активные элементы и подсветка" }),
      swatches,
    ),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Кнопки" }),
        el("div", { class: "setting-hint", text: "Форма и материал кнопок" }),
      ),
      appearanceSegmented(settings, ctx, "buttonStyle", [
        ["soft", "Мягкие"], ["pill", "Pill"], ["glass", "Стекло"], ["square", "Чёткие"],
      ]),
    ),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Тумблеры" }),
        el("div", { class: "setting-hint", text: "Переключатели в стиле iOS или классические" }),
      ),
      appearanceSegmented(settings, ctx, "toggleStyle", [
        ["classic", "Классика"], ["ios", "iOS"], ["compact", "Компактные"],
      ]),
    ),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Анимации" }),
        el("div", { class: "setting-hint", text: "Плавность появления, нажатий и наведения" }),
      ),
      appearanceSegmented(settings, ctx, "motion", [
        ["full", "Полные"], ["soft", "Спокойные"], ["off", "Выкл"],
      ]),
    ),
    appearanceRange(ctx, {
      key: "radius", name: "Скругление", hint: "Форма карточек, полей и панелей",
      min: 6, max: 28, value: Number(settings.radius) || 12, format: (value) => `${value}px`,
    }),
    appearanceRange(ctx, {
      key: "glass", name: "Стекло", hint: "Прозрачность и размытие в стиле iOS Glass",
      min: 0, max: 100, value: Number.isFinite(Number(settings.glass)) ? Number(settings.glass) : 65,
      format: (value) => `${value}%`,
    }),
    appearanceRange(ctx, {
      key: "textSize", name: "Размер текста", hint: "Масштаб сообщений и интерфейса",
      min: 85, max: 125, value: Number(settings.textSize) || 100, format: (value) => `${value}%`,
    }),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Плотность" }),
        el("div", { class: "setting-hint", text: "Сколько воздуха между элементами" }),
      ),
      appearanceSegmented(settings, ctx, "density", [
        ["compact", "Компактно"], ["comfortable", "Обычно"], ["spacious", "Просторно"],
      ]),
    ),
  );
  pane.append(
    el("div", { class: "appearance-section-label", text: "Ручная настройка" }),
    customize,
  );

  pane.append(el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Сбросить оформление" }),
        el("div", { class: "setting-hint", text: "Вернуть классический стиль и стандартные значения" }),
      ),
      el("button", {
        class: "btn btn-secondary btn-sm",
        text: "Сбросить",
        onclick: () => {
          ctx.state.settings = saveSettings(APPEARANCE_DEFAULTS);
          ctx.applyTheme?.();
          ctx.renderSettings?.();
          ctx.toast("Оформление сброшено", "success");
        },
      }),
    ),
  ));

  const installBtn = el("button", { class: "btn btn-secondary btn-sm", text: "Установить", onclick: async () => {
    if (window.__claudenInstallPrompt) {
      window.__claudenInstallPrompt.prompt();
      const choice = await window.__claudenInstallPrompt.userChoice;
      if (choice?.outcome === "accepted") ctx.toast("Clauden установлен", "success");
      window.__claudenInstallPrompt = null;
    } else {
      ctx.toast("Откройте меню браузера → «Установить приложение»", "");
    }
  }});
  pane.append(el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("span", { class: "list-row-icon", html: icon("download") }),
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Приложение для ПК" }),
        el("div", { class: "setting-hint", text: "Установите clauden.best как отдельное окно с иконкой — работает в Chrome, Edge, Safari" }),
      ),
      installBtn,
    ),
  ));

  pane.append(el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Уведомления" }),
        el("div", { class: "setting-hint", text: "Сообщать, когда длинный ответ готов" }),
      ),
      switchControl(settings.notifications, async (value) => {
        if (value && "Notification" in window) {
          const permission = await Notification.requestPermission();
          if (permission !== "granted") { ctx.toast("Разрешение не выдано", "error"); ctx.renderSettings?.(); return; }
        }
        ctx.state.settings = saveSettings({ notifications: value });
      }),
    ),
  ));
}

function switchControl(value, onChange) {
  const input = el("input", { type: "checkbox", checked: Boolean(value) });
  input.addEventListener("change", () => onChange(input.checked));
  return el("label", { class: "switch" }, input, el("span", { class: "switch-track" }));
}

function renderFeatures(pane, ctx) {
  const settings = ctx.state.settings;
  pane.append(
    el("h1", { class: "settings-title", text: "Возможности" }),
    el("p", { class: "settings-desc", text: "Что Clauden умеет в чатах" }),
  );
  const rows = [
    ["webSearch", "Веб-поиск", "Свежие факты со ссылками на источники"],
    ["artifacts", "Артефакты", "Отдельные окна для кода и документов"],
    ["visualizations", "Визуализации", "Графики и интерактивные виджеты в ответах"],
    ["codeExecution", "Выполнение кода", "Запуск кода и создание файлов на сервере"],
    ["memory", "Память", "Учитывать прошлые чаты и сохранённые факты"],
    ["aiArtifacts", "Clauden внутри артефактов", "Встраивать ассистента в страницы"],
    ["networkEgress", "Сеть при выполнении кода", "Разрешить установку пакетов и запросы"],
  ];
  const group = el("div", { class: "settings-group" });
  for (const [key, name, hint] of rows) {
    group.append(el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: name }),
        el("div", { class: "setting-hint", text: hint }),
      ),
      switchControl(settings[key], (value) => {
        ctx.state.settings = saveSettings({ [key]: value });
        ctx.toast(`${name}: ${value ? "вкл" : "выкл"}`);
      }),
    ));
  }
  pane.append(group);

  pane.append(el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Модель по умолчанию" }),
        el("div", { class: "setting-hint", text: settings.model }),
      ),
      el("button", { class: "btn btn-secondary btn-sm", text: "Изменить", onclick: () => ctx.navigate("/chat") }),
    ),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Уровень усилий" }),
        el("div", { class: "setting-hint", text: "Больше усилий — подробнее ответ" }),
      ),
      el("div", { class: "segmented" },
        ...[2048, 4096, 8192, 12288, 16384].map((value, i) =>
          el("button", {
            class: Number(settings.effort) === value ? "active" : "",
            text: ["Низкий", "Средний", "Высокий", "Сверх", "Макс"][i],
            onclick: () => { ctx.state.settings = saveSettings({ effort: value }); ctx.renderSettings?.(); },
          })),
      ),
    ),
  ));
}

function renderMemory(pane, ctx) {
  const userId = ctx.state.user?.id;
  const localItems = loadMemory(userId);
  pane.append(
    el("h1", { class: "settings-title", text: "Память" }),
    el("p", { class: "settings-desc", text: "Факты и конспекты прошлых диалогов. Хранятся на сервере Clauden и работают на всех устройствах." }),
  );

  const listHost = el("div");
  const status = el("div", { class: "field-hint", style: { marginBottom: "10px" } });
  let items = [];

  const countLabel = () =>
    `${items.length} ${items.length === 1 ? "факт" : items.length >= 2 && items.length <= 4 ? "факта" : "фактов"}`;

  const draw = () => {
    clear(listHost);
    status.textContent = `Автоматически собрано из чатов: ${items.filter((item) => item.source === "chat").length} · всего ${countLabel()}`;
    if (!items.length) {
      listHost.append(el("div", { class: "empty-block" },
        el("div", { html: icon("brain") }),
        el("h3", { text: "Память пока пуста" }),
        el("p", { text: "Просто общайтесь — Clauden сам запомнит устойчивые факты. Или добавьте факт вручную: «Меня зовут…», «Я работаю…», «Мне нравится…»." }),
      ));
      return;
    }
    const group = el("div", { class: "settings-group" });
    for (const item of items) {
      group.append(el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: item.text }),
          el("div", { class: "setting-hint", text: `${item.source === "chat" ? "из чата" : item.source === "import" ? "перенесено" : "вручную"} · ${relTimeShort(item.createdAt)}` }),
        ),
        el("button", {
          class: "icon-btn",
          title: "Удалить",
          onclick: async () => {
            try {
              const { data } = await api.deleteMemory(item.id);
              items = data.items || [];
              draw();
            } catch (err) {
              ctx.toast(err.message || "Не удалось удалить", "error");
            }
          },
          html: icon("trash"),
        }),
      ));
    }
    listHost.append(group);
  };

  const load = async () => {
    status.textContent = "Загружаю память…";
    try {
      if (localItems.length) {
        try {
          await api.importMemory(localItems.map((item) => item.text).filter(Boolean));
          saveMemory(userId, []);
        } catch {

        }
      }
      const { data } = await api.memory();
      items = data.items || [];
      if (!items.length && localItems.length) {
        items = localItems.map((item) => ({ ...item, source: item.source || "manual" }));
        status.textContent = "Сервер памяти недоступен — показываю локальные факты.";
      }
      draw();
    } catch (err) {
      items = localItems.map((item) => ({ ...item, source: item.source || "manual" }));
      status.textContent = err.message || "Сервер памяти недоступен — показываю локальные факты.";
      draw();
    }
  };

  const addBtn = el("button", {
    class: "btn btn-secondary btn-sm",
    text: "Добавить факт",
    onclick: async () => {
      const text = await promptDialog({ title: "Что запомнить?", label: "Факт", multiline: true, confirm: "Запомнить" });
      if (!text?.trim()) return;
      try {
        const { data } = await api.addMemory(text.trim());
        items = data.items || [];
        draw();
        ctx.toast("Запомнил", "success");
      } catch (err) {
        const next = [{ id: uid("mem"), text: text.trim(), source: "manual", createdAt: Date.now() }, ...localItems];
        saveMemory(userId, next);
        localItems.unshift(next[0]);
        items = next;
        draw();
        ctx.toast("Сохранено локально — сервер недоступен", "error");
      }
    },
  });

  pane.append(
    el("div", { class: "row between", style: { marginBottom: "10px" } },
      el("span", { class: "muted small", text: "Долговременная память аккаунта" }),
      el("div", { class: "row" },
        el("button", { class: "btn btn-ghost btn-sm", text: "Обновить", onclick: () => load() }),
        addBtn,
      ),
    ),
    status,
    listHost,
  );

  pane.append(el("button", {
    class: "btn btn-ghost btn-sm",
    text: "Очистить всю память",
    style: { marginTop: "10px" },
    onclick: async () => {
      const ok = await confirmDialog({ title: "Очистить память?", text: "Все сохранённые факты и конспекты будут удалены на всех устройствах.", confirm: "Очистить" });
      if (!ok) return;
      try {
        const { data } = await api.clearMemory();
        items = data.items || [];
      } catch {
        items = [];
      }
      saveMemory(userId, []);
      draw();
      ctx.toast("Память очищена");
    },
  }));

  draw();
  load();
}

function loadConnectors() {
  try { return JSON.parse(localStorage.getItem("clauden.connectors") || "[]"); } catch { return []; }
}
function saveConnectors(list) {
  localStorage.setItem("clauden.connectors", JSON.stringify(list));
}

function renderConnectors(pane, ctx) {
  const connectors = loadConnectors();
  pane.append(
    el("h1", { class: "settings-title", text: "Коннекторы" }),
    el("p", { class: "settings-desc", text: "MCP-серверы: каталог Clauden, Notion, Slack, Google и другие" }),
    el("div", { class: "row", style: { marginBottom: "12px", flexWrap: "wrap" } },
      el("button", { class: "btn btn-primary btn-sm", onclick: () => addConnector(ctx), text: "Свой MCP-сервер" }),
      el("button", { class: "btn btn-secondary btn-sm", onclick: () => { ctx.closeSettings?.(); ctx.navigate("/market"); }, text: "Маркетплейс" }),
    ),
    connectorCatalogBlock(ctx),
  );
  if (!connectors.length) {
    pane.append(el("div", { class: "empty-block" },
      el("div", { html: icon("plug") }),
      el("h3", { text: "Пока ничего не подключено" }),
      el("p", { text: "Выберите сервер в каталоге выше и нажмите «Подключить». Доступно на Pro, Max и Ultra." }),
    ));
    return;
  }
  pane.append(el("div", { class: "section-title", text: "Подключённые" }));
  const group = el("div", { class: "settings-group" });
  for (const connector of connectors) {
    group.append(el("div", { class: "setting-row" },
      el("span", { class: "list-row-icon", html: icon("plug") }),
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: connector.title || connector.url }),
        el("div", { class: "setting-hint", text: `${connector.url}${connector.status ? ` · ${connector.status}` : ""}` }),
      ),
      switchControl(connector.enabled !== false, (value) => {
        connector.enabled = value;
        saveConnectors(connectors);
      }),
      el("button", { class: "btn btn-ghost btn-sm", text: "Токен", onclick: async () => {
        const headers = await promptConnectorToken(connector, ctx);
        if (!headers) return;
        connector.headers = headers;
        saveConnectors(connectors);
        try {
          const result = await api.connectorCheck(connector.url, headers);
          connector.status = result.status;
          saveConnectors(connectors);
          ctx.toast(result.status === "connected" ? "Подключено" : result.error || "Не удалось", result.status === "connected" ? "success" : "error");
          ctx.renderSettings?.();
        } catch (err) { ctx.toast(err.message || "Не удалось", "error"); }
      }}),
      el("button", { class: "btn btn-ghost btn-sm", text: "OAuth", onclick: async () => {
        try {
          const start = await api.connectorOAuthStart({ url: connector.url, connectorId: connector.id, title: connector.title });
          if (start.authUrl) window.open(start.authUrl, "clauden-mcp-oauth", "width=520,height=680");
          const result = await waitOAuthResult(start, ctx, connector);
          connector.status = result.status || "connected";
          saveConnectors(connectors);
          ctx.toast(result.status === "connected" ? "Коннектор подключён" : result.error || "Не удалось", result.status === "connected" ? "success" : "error");
          ctx.renderSettings?.();
        } catch (err) {
          const message = err.message || "OAuth не удался";
          if (/dynamic client|incompatible auth server/i.test(message)) {
            const headers = await promptConnectorToken(connector, ctx);
            if (headers) {
              connector.headers = headers;
              saveConnectors(connectors);
              const result = await api.connectorCheck(connector.url, headers).catch((e) => ({ status: "failed", error: e.message }));
              connector.status = result.status;
              saveConnectors(connectors);
              ctx.toast(result.status === "connected" ? "Подключено" : result.error || "Не удалось", result.status === "connected" ? "success" : "error");
              ctx.renderSettings?.();
              return;
            }
          }
          ctx.toast(message, "error");
        }
      }}),
      el("button", { class: "icon-btn", title: "Проверить", onclick: async (e) => {
        const btn = e.currentTarget;
        btn.innerHTML = '<span class="spinner"></span>';
        try {
          const result = await api.connectorCheck(connector.url, connector.headers || "");
          connector.status = result.status;
          saveConnectors(connectors);
          ctx.toast(result.status === "connected" ? "Подключено" : result.error || "Не удалось", result.status === "connected" ? "success" : "error");
          ctx.renderSettings?.();
        } catch (err) { ctx.toast(err.message, "error"); btn.innerHTML = icon("refresh"); }
      }, html: icon("refresh") }),
      el("button", { class: "icon-btn", title: "Удалить", onclick: () => {
        saveConnectors(connectors.filter((c) => c.id !== connector.id));
        ctx.renderSettings?.();
      }, html: icon("trash") }),
    ));
  }
  pane.append(group);
}

function connectorCatalogBlock(ctx) {
  const state = { q: "", category: "", offset: 0, total: 0, facets: [], loading: false };
  const list = el("div", { class: "settings-group" });
  const chips = el("div", { class: "row", style: { flexWrap: "wrap", gap: "8px", margin: "10px 0" } });
  const hint = el("p", { class: "field-hint", text: "" });
  const more = el("button", { class: "btn btn-secondary btn-sm", text: "Показать ещё", style: { display: "none" } });
  const search = el("input", { class: "input", placeholder: "Поиск: Notion, GitHub, Stripe…", style: { maxWidth: "360px" } });

  async function load(reset) {
    if (state.loading) return;
    state.loading = true;
    if (reset) {
      state.offset = 0;
      list.replaceChildren();
    }
    hint.textContent = "Загружаю каталог…";
    try {
      const page = await api.connectorCatalog({
        q: state.q,
        category: state.category,
        limit: 30,
        offset: state.offset,
      });
      state.total = page.total || 0;
      state.facets = page.facets || [];
      for (const item of page.results || []) {
        list.append(catalogRow(item, ctx, () => load(true)));
      }
      state.offset += (page.results || []).length;
      hint.textContent = state.total
        ? `Показано ${Math.min(state.offset, state.total)} из ${state.total}`
        : "Ничего не найдено";
      more.style.display = state.offset < state.total ? "" : "none";
      renderChips();
    } catch (err) {
      hint.textContent = err.message || "Каталог не ответил";
    } finally {
      state.loading = false;
    }
  }

  function renderChips() {
    chips.replaceChildren(
      el("button", {
        class: `btn btn-sm ${state.category ? "btn-secondary" : "btn-primary"}`,
        text: "Все",
        onclick: () => { state.category = ""; load(true); },
      }),
      ...state.facets.slice(0, 12).map((facet) =>
        el("button", {
          class: `btn btn-sm ${state.category === facet.id ? "btn-primary" : "btn-secondary"}`,
          text: `${facet.label || facet.id} · ${facet.count}`,
          onclick: () => {
            state.category = state.category === facet.id ? "" : facet.id;
            load(true);
          },
        }),
      ),
    );
  }

  let timer = 0;
  search.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      state.q = search.value.trim();
      load(true);
    }, 320);
  });
  more.addEventListener("click", () => load(false));

  const wrap = el("div", null,
    el("div", { class: "section-title", text: "Общий каталог MCP" }),
    el("p", { class: "settings-desc", text: "Тысячи remote-серверов: выбирайте и подключайте без ввода адреса." }),
    search,
    chips,
    hint,
    list,
    more,
  );
  load(true);
  return wrap;
}

function catalogRow(item, ctx, reload) {
  const existing = loadConnectors().some((c) => item.url && c.url === item.url);
  return el("div", { class: "setting-row" },
    el("span", { class: "list-row-icon", html: icon("plug") }),
    el("div", { class: "setting-text" },
      el("div", { class: "setting-name", text: item.title || item.id }),
      el("div", { class: "setting-hint", text: item.description || "" }),
      el("div", { class: "setting-hint", text: `${item.author || "MCP"} · ${item.transport || "remote"}` }),
    ),
    el("button", {
      class: `btn btn-sm ${existing ? "btn-secondary" : "btn-primary"}`,
      text: existing ? "Добавлено" : "Подключить",
      disabled: existing || !item.connectable,
      onclick: () => connectCatalogItem(item, ctx, reload),
    }),
  );
}

async function promptConnectorToken(item, ctx) {
  const value = await promptDialog({
    title: `Токен ${item.title || "сервиса"}`,
    label: "Personal access token (передаётся в заголовке Authorization)",
    placeholder: item.title === "GitHub" ? "ghp_… или github_pat_…" : "Вставьте токен",
    confirm: "Подключить",
  });
  const token = String(value || "").trim();
  if (!token) return "";
  return `Authorization: Bearer ${token}`;
}

async function waitOAuthResult(start, ctx, item) {

  if (isDesktopApp() && start?.redirectUri && typeof desktopBridge()?.app?.captureOAuth === "function") {
    try {
      const redirect = new URL(start.redirectUri);
      const port = Number(redirect.port || "80");
      const captured = await desktopBridge().app.captureOAuth({
        port,
        path: redirect.pathname || "/callback",
      });
      const callbackUrl = captured?.data?.callbackUrl || captured?.callbackUrl;
      if (callbackUrl) {
        return await api.connectorOAuthFinish(start.attemptId, callbackUrl);
      }
    } catch (err) {

    }
  }
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 2000));
    try {
      const result = await api.connectorOAuthResult(start.attemptId);
      if (result?.status === "connected" || result?.status === "failed") return result;
    } catch {

    }
  }

  const callback = await promptDialog({
    title: "Завершить вход",
    label: "Если браузер не вернулся автоматически — вставьте адрес из его адресной строки",
    placeholder: "https://clauden.best/v1/connectors/oauth/callback?code=…",
    confirm: "Завершить",
  });
  if (!callback) return { status: "pending", error: "" };
  return api.connectorOAuthFinish(start.attemptId, callback);
}

async function connectCatalogItem(item, ctx, reload) {
  if (!item.url) return;
  const list = loadConnectors();
  if (list.some((c) => c.url === item.url)) { ctx.toast("Уже добавлен"); return; }
  let headers = "";

  if (item.auth === "token") {
    headers = await promptConnectorToken(item, ctx);
    if (!headers) return;
  }
  const connector = {
    id: uid("mcp"),
    title: item.title,
    url: item.url,
    headers,
    enabled: true,
    status: "",
  };
  list.push(connector);
  saveConnectors(list);
  try {
    let check = await api.connectorCheck(item.url, headers);
    connector.status = check.status;
    saveConnectors(list);
    if (check.status === "connected") {
      ctx.toast(`«${item.title}» подключён`, "success");
      ctx.renderSettings?.();
      return;
    }
    if (check.status !== "needs_auth") {

      if (/401|unauthorized|token|auth/i.test(check.error || "") && !headers) {
        headers = await promptConnectorToken(item, ctx);
        if (headers) {
          connector.headers = headers;
          check = await api.connectorCheck(item.url, headers);
          connector.status = check.status;
          saveConnectors(list);
        }
      }
      if (check.status !== "connected") {
        ctx.toast(check.error || "Сервер не ответил", "error");
        ctx.renderSettings?.();
        return;
      }
      ctx.toast(`«${item.title}» подключён`, "success");
      ctx.renderSettings?.();
      return;
    }
    const start = await api.connectorOAuthStart({
      url: item.url,
      connectorId: connector.id,
      title: item.title,
    });
    if (start.authUrl) window.open(start.authUrl, "clauden-mcp-oauth", "width=520,height=680");
    const result = await waitOAuthResult(start, ctx, item);
    connector.status = result.status || "connected";
    saveConnectors(list);
    ctx.toast(
      result.status === "connected" ? `«${item.title}» подключён` : result.error || "Не удалось войти",
      result.status === "connected" ? "success" : "error",
    );
    ctx.renderSettings?.();
  } catch (err) {
    const message = err.message || "Не удалось подключить";

    if (/dynamic client|incompatible auth server/i.test(message) && !headers) {
      headers = await promptConnectorToken(item, ctx);
      if (headers) {
        connector.headers = headers;
        const retry = await api.connectorCheck(item.url, headers).catch((e) => ({ status: "failed", error: e.message }));
        connector.status = retry.status;
        saveConnectors(list);
        ctx.toast(
          retry.status === "connected" ? `«${item.title}» подключён` : retry.error || "Не удалось",
          retry.status === "connected" ? "success" : "error",
        );
        ctx.renderSettings?.();
        return;
      }
    }
    ctx.toast(message, "error");
    reload?.();
  }
}

function addConnector(ctx) {
  const title = el("input", { class: "input", placeholder: "Название (например, Notion)" });
  const url = el("input", { class: "input", placeholder: "https://mcp.example.com/sse" });
  const headers = el("input", { class: "input", placeholder: "Authorization: Bearer … (необязательно)" });
  const status = el("div", { class: "notice-box", hidden: true });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "MCP-коннектор" })),
    el("div", { class: "modal-body" },
      el("div", { class: "field" }, el("label", { text: "Название" }), title),
      el("div", { class: "field" }, el("label", { text: "URL сервера" }), url),
      el("div", { class: "field" }, el("label", { text: "Заголовки" }), headers),
      status,
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      el("button", { class: "btn btn-secondary", text: "Проверить", onclick: async () => {
        status.hidden = false;
        status.className = "notice-box";
        status.textContent = "Проверяю…";
        try {
          const result = await api.connectorCheck(url.value.trim(), headers.value.trim());
          status.textContent = result.status === "connected" ? "Подключено" : result.error || `Статус: ${result.status}`;
          if (result.status === "connected") {
            const list = loadConnectors();
            list.push({ id: uid("mcp"), title: title.value.trim() || url.value.trim(), url: url.value.trim(), headers: headers.value.trim(), enabled: true, status: "connected" });
            saveConnectors(list);
            setTimeout(() => { modal.close(); ctx.renderSettings?.(); }, 500);
          }
        } catch (err) { status.className = "error-box"; status.textContent = err.message || "Ошибка"; }
      }}),
      el("button", { class: "btn btn-primary", text: "Добавить", onclick: () => {
        const list = loadConnectors();
        list.push({ id: uid("mcp"), title: title.value.trim() || url.value.trim(), url: url.value.trim(), headers: headers.value.trim(), enabled: true });
        saveConnectors(list);
        modal.close();
        ctx.renderSettings?.();
      }}),
    ),
  ));
  setTimeout(() => title.focus(), 30);
}

function renderPrivacy(pane, ctx) {
  pane.append(
    el("h1", { class: "settings-title", text: "Приватность" }),
    el("p", { class: "settings-desc", text: "Данные, экспорт и условия" }),
    el("div", { class: "settings-group" },
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Экспорт данных" }),
          el("div", { class: "setting-hint", text: "Скачать чаты и проекты в JSON" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Скачать", onclick: () => {
          const payload = { exportedAt: Date.now(), user: ctx.state.user?.email, chats: ctx.state.chats, projects: ctx.state.projects };
          downloadText(JSON.stringify(payload, null, 2), `clauden-export-${new Date().toISOString().slice(0, 10)}.json`, "application/json");
        }}),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Резервная копия" }),
          el("div", { class: "setting-hint", text: "Чаты синхронизируются с аккаунтом (без вложений)" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Синхронизировать", onclick: async () => {
          try {
            await api.backupChats(ctx.state.chats
              .filter((chat) => chat.provider !== "claude")
              .map((chat) => ({ ...chat, messages: (chat.messages || []).map((m) => ({ ...m, attachments: (m.attachments || []).map(({ data, ...rest }) => rest) })) })));
            ctx.toast("Резервная копия обновлена", "success");
          } catch (err) { ctx.toast(err.message, "error"); }
        }}),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Очистить историю" }),
          el("div", { class: "setting-hint", text: "Удалить все чаты на этом устройстве" }),
        ),
        el("button", { class: "btn btn-danger btn-sm", text: "Очистить", onclick: async () => {
          const ok = await confirmDialog({ title: "Очистить историю?", text: "Все чаты будут удалены на этом устройстве и из бэкапа аккаунта.", confirm: "Очистить" });
          if (!ok) return;
          await ctx.clearAllChats();
          ctx.toast("История очищена");
          ctx.navigate("/chat");
        }}),
      ),
    ),
    el("div", { class: "settings-group", style: { padding: "14px" } },
      el("div", { class: "setting-name", style: { marginBottom: "8px" }, text: "Условия использования" }),
      el("p", { class: "setting-hint", style: { lineHeight: "1.6" }, text: "Clauden — ИИ-ассистент. Ответы могут содержать ошибки: проверяйте важную информацию. Сервис не предназначен для незаконных действий. История чатов хранится на устройстве и в резервной копии аккаунта; вложения остаются только на устройстве. Вы можете удалить аккаунт и данные в любой момент." }),
      el("p", { class: "setting-hint", style: { lineHeight: "1.6", marginTop: "8px" }, text: "Clauden использует модели Anthropic и партнёрские шлюзы. Не отправляйте секреты и чужие персональные данные без необходимости." }),
      el("div", { class: "row wrap", style: { marginTop: "12px" } },
        el("a", { class: "btn btn-secondary btn-sm", href: "/terms", target: "_blank", rel: "noopener" }, "Пользовательское соглашение"),
        el("a", { class: "btn btn-secondary btn-sm", href: "/privacy", target: "_blank", rel: "noopener" }, "Политика конфиденциальности"),
        el("a", { class: "btn btn-secondary btn-sm", href: "/pricing#refund", target: "_blank", rel: "noopener" }, "Оплата и возврат"),
      ),
    ),
  );
}

let updatesUnsubscribe = null;

function renderUpdates(pane, ctx) {
  if (updatesUnsubscribe) {
    updatesUnsubscribe();
    updatesUnsubscribe = null;
  }
  pane.append(
    el("h1", { class: "settings-title", text: "Обновления" }),
    el("p", { class: "settings-desc", text: "Автоматическая загрузка новых версий приложения Clauden" }),
  );

  if (!isDesktopApp()) {
    pane.append(el("div", { class: "notice-box" },
      el("div", { class: "setting-name", text: "Обновления устанавливаются в приложении для ПК" }),
      el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: "Веб-версия обновляется сама при загрузке страницы. Автообновление и установка новой сборки доступны в приложении для ПК: в Windows и Linux-AppImage, на macOS обновление ставится вручную с сайта." }),
      el("a", { class: "btn btn-secondary btn-sm", href: DESKTOP_DOWNLOAD, text: "Скачать приложение" }),
    ));
    return;
  }

  if (!isUpdateSupported()) {
    pane.append(el("div", { class: "notice-box" },
      el("div", { class: "setting-name", text: "Нужна версия 0.1.6 или новее" }),
      el("p", { class: "muted small", style: { margin: "6px 0 10px" }, text: "Эта сборка ещё не умеет обновляться автоматически. Установите свежую версию вручную — дальше обновления будут приходить сами." }),
      el("a", { class: "btn btn-primary btn-sm", href: DESKTOP_DOWNLOAD, text: "Скачать обновление" }),
    ));
    return;
  }

  const versionHint = el("div", { class: "setting-hint", text: "Версия приложения…" });
  const box = el("div", { class: "settings-group" });
  const statusName = el("div", { class: "setting-name", text: "Проверяю состояние…" });
  const statusHint = el("div", { class: "setting-hint", text: "" });
  const progressTrack = el("div", { style: { height: "6px", borderRadius: "3px", background: "var(--surface-3)", overflow: "hidden", marginTop: "10px" } });
  const progressBar = el("div", { style: { height: "100%", width: "0%", background: "var(--clay)", transition: "width .2s ease" } });
  progressTrack.append(progressBar);
  const checkBtn = el("button", { class: "btn btn-secondary btn-sm", text: "Проверить обновления" });
  const installBtn = el("button", { class: "btn btn-primary btn-sm", text: "Установить и перезапустить", hidden: true });
  const actions = el("div", { style: { display: "flex", gap: "8px", flexWrap: "wrap", marginTop: "12px" } }, checkBtn, installBtn);
  box.append(el("div", { style: { padding: "16px" } }, statusName, versionHint, statusHint, progressTrack, actions));
  pane.append(box);

  const mb = (value) => `${(Number(value || 0) / 1024 / 1024).toFixed(1)} МБ`;
  const draw = (state = {}) => {
    const status = state.status || "idle";
    progressTrack.hidden = status !== "downloading" && status !== "available";
    progressBar.style.width = `${Math.max(0, Math.min(100, Number(state.percent) || 0))}%`;
    installBtn.hidden = status !== "downloaded";
    checkBtn.disabled = status === "checking" || status === "downloading";
    statusHint.style.color = "";
    if (status === "checking") {
      statusName.textContent = "Проверяю обновления…";
      statusHint.textContent = "Соединяюсь с clauden.best";
    } else if (status === "available") {
      statusName.textContent = `Доступна версия ${state.version || ""}`;
      statusHint.textContent = "Скачиваю обновление в фоне…";
    } else if (status === "downloading") {
      statusName.textContent = `Скачиваю ${state.version || "обновление"}… ${Number(state.percent) || 0}%`;
      statusHint.textContent = `${mb(state.transferred)} из ${mb(state.total)}${state.bytesPerSecond ? ` · ${mb(state.bytesPerSecond)}/с` : ""}`;

    } else if (status === "downloaded") {
      statusName.textContent = `Версия ${state.version || "обновление"} готова`;
      statusHint.textContent = "Установка перезапустит приложение. Чаты и настройки сохранятся.";
    } else if (status === "latest") {
      statusName.textContent = "У вас последняя версия";
      statusHint.textContent = state.checkedAt ? `Проверено ${timeAgo(state.checkedAt)}` : "";
    } else if (status === "error") {
      statusName.textContent = "Не удалось проверить обновления";
      statusHint.textContent = state.error || "Проверьте соединение и попробуйте снова";
      statusHint.style.color = "var(--danger)";
    } else if (status === "dev") {
      statusName.textContent = "Проверка доступна в установленной версии";
      statusHint.textContent = "В режиме разработки автообновление отключено";
    } else if (status === "unsupported") {
      statusName.textContent = "Автообновление недоступно";
      statusHint.textContent = state.error || "Эта платформа не поддерживает автообновление";
    } else {
      statusName.textContent = "Готово к проверке";
      statusHint.textContent = "Обновления проверяются автоматически при запуске и раз в 6 часов";
    }
  };

  checkBtn.addEventListener("click", async () => {
    checkBtn.disabled = true;
    try {
      const state = await desktopUpdates.check();
      draw(state);
    } catch (err) {
      draw({ status: "error", error: err.message });
    } finally {
      checkBtn.disabled = false;
    }
  });

  installBtn.addEventListener("click", async () => {
    const ok = await confirmDialog({
      title: "Установить обновление?",
      text: "Приложение закроется, установит новую версию и запустится снова. Чаты и настройки сохранятся.",
      confirm: "Установить",
    });
    if (!ok) return;
    try {
      installBtn.disabled = true;
      const result = await desktopUpdates.install();
      if (result?.ok === false) throw new Error(result.error || "Не удалось установить обновление");
    } catch (err) {
      installBtn.disabled = false;
      ctx.toast(err.message || "Не удалось установить обновление", "error");
    }
  });

  draw();
  desktopUpdates.status().then(draw).catch((err) => draw({ status: "error", error: err.message }));
  updatesUnsubscribe = desktopUpdates.onEvent(draw);
  const infoPromise = window.claudenDesktop?.app?.info?.();
  if (infoPromise?.then) {
    infoPromise.then((result) => {
      if (result?.ok && result.data?.version) {
        versionHint.textContent = `Установлена версия ${result.data.version} · Electron ${result.data.electron}`;
      }
    }).catch(() => {});
  }

  pane.append(el("div", { class: "section-title", text: "Как это работает" }));
  pane.append(el("div", { class: "settings-group" },
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Проверка при запуске" }),
        el("div", { class: "setting-hint", text: "Приложение проверяет обновления через 20 секунд после старта и каждые 6 часов" }),
      ),
    ),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Загрузка в фоне" }),
        el("div", { class: "setting-hint", text: "Новая версия скачивается сама, установка — по кнопке или при выходе" }),
      ),
    ),
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: "Проверка файла" }),
        el("div", { class: "setting-hint", text: "SHA-512 из latest.yml сверяется перед установкой. Подпись кода не настроена, SmartScreen может предупредить" }),
      ),
    ),
  ));
  pane.append(el("p", { class: "field-hint", text: "Если обновление не приходит, скачайте установщик вручную — он поставится поверх и сохранит данные." }));
}

function renderAbout(pane, ctx) {
  const user = ctx.state.user;
  const versionName = el("div", { class: "setting-name", text: "Clauden Web 1.0.0" });
  const docRow = (name, href, label = "Открыть") =>
    el("div", { class: "setting-row" },
      el("div", { class: "setting-text" },
        el("div", { class: "setting-name", text: name }),
      ),
      el("a", { class: "btn btn-secondary btn-sm", href, target: "_blank", rel: "noopener" }, label),
    );
  pane.append(
    el("h1", { class: "settings-title", text: "О приложении" }),
    el("p", { class: "settings-desc", text: "Clauden для веба и ПК" }),
    el("div", { class: "settings-group" },
      el("div", { class: "setting-row" },
        el("img", { src: "/assets/logo.svg?v=9", style: { width: "40px", height: "40px" }, alt: "" }),
        el("div", { class: "setting-text" }, versionName),
        el("span", { class: `plan-badge ${user?.plan !== "Free" ? "pro" : ""}`, text: `Clauden ${user?.plan || "Free"}` }),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Установка на ПК" }),
          el("div", { class: "setting-hint", text: "Chrome/Edge: меню → Установить Clauden. Safari: Поделиться → На экран «Домой»." }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Установить", onclick: () => ctx.openSettings("appearance") }),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Android-приложение" }),
          el("div", { class: "setting-hint", text: "Та же учётная запись, те же чаты и подписка" }),
        ),
        el("a", { class: "btn btn-secondary btn-sm", href: "https://teleopen.space/clauden/app/latest.apk" }, "Скачать APK"),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Продавец" }),
          el("div", { class: "setting-hint", text: "Самозанятый Зайцев Станислав Алексеевич · ИНН 362201956766 · г. Воронеж" }),
        ),
      ),
      el("div", { class: "setting-row" },
        el("div", { class: "setting-text" },
          el("div", { class: "setting-name", text: "Сервер" }),
          el("div", { class: "setting-hint", text: "clauden.best · шлюз Clauden" }),
        ),
        el("button", { class: "btn btn-secondary btn-sm", text: "Проверить", onclick: async () => {
          try { const health = await api.health(); ctx.toast(`Сервер работает · ${health.active_turns ?? 0} активных`, "success"); }
          catch { ctx.toast("Сервер недоступен", "error"); }
        }}),
      ),
    ),
    el("div", { class: "settings-group" },
      docRow("Тарифы и цены", "/pricing"),
      docRow("Публичная оферта", "/offer"),
      docRow("Пользовательское соглашение", "/terms"),
      docRow("Политика конфиденциальности", "/privacy"),
      docRow("Оплата и возврат", "/pricing#refund"),
      docRow("Статус сервисов", "/status", "Открыть"),
      docRow("Поддержка в Telegram", "https://t.me/ClaudenApp", "Написать"),
    ),
    el("div", { class: "settings-group" },
      docRow("Телеграм канал", "https://t.me/ClaudenApp", "Открыть"),
      docRow("Телеграм чат", "https://t.me/+hNcFDcTTr6BkODFi", "Открыть"),
      docRow("Поддержка", "https://t.me/ClaudenSup", "Написать"),
    ),
    el("p", { class: "field-hint" },
      "© 2026 Clauden. Интерфейс вдохновлён claude.ai; весь код веб-версии — собственный. Поддержка: ",
      el("a", { href: "https://t.me/ClaudenApp", target: "_blank", rel: "noopener" }, "Telegram @ClaudenApp"), "."),
  );
  if (isDesktopApp()) {
    window.claudenDesktop.app.info().then((result) => {
      if (result?.ok && result.data?.version) {
        versionName.textContent = `Clauden Desktop ${result.data.version} · Electron ${result.data.electron}`;
      }
    }).catch(() => {});
  }
}
