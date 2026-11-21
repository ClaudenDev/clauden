import { el, icon } from "./util.js";

const TELEGRAM = "https://t.me/ClaudenApp";
const SUPPORT_TG = "https://t.me/ClaudenApp";
const SUPPORT_EMAIL = "support@clauden.best";
const WINDOWS_APP = "/downloads/Clauden-latest.exe?v=0.2.9";
const MAC_ARM64_APP = "/downloads/Clauden-latest-mac-arm64.zip?v=0.2.10";
const MAC_X64_APP = "/downloads/Clauden-latest-mac-x64.zip?v=0.2.10";
const LINUX_APPIMAGE = "/downloads/Clauden-latest.AppImage";
const LINUX_DEB = "/downloads/Clauden-latest.deb";
const LINUX_TARBALL = "/downloads/Clauden-latest-linux.tar.gz";
const ANDROID_APP = "/app/latest.apk";
const CODE_CLI = "/downloads/code/clauden-code-0.1.1.tgz";
const CODE_IDE = "/downloads/code/ClaudenCode-latest-x64.exe?v=0.1.16";

const FACTS = [
  {
    label: "Что это",
    text: "Неофициальная оболочка к Claude: свой интерфейс и сервер. С Anthropic не связана.",
  },
  {
    label: "Что нужно",
    text: "Подписка Clauden или свой Claude-аккаунт.",
  },
  {
    label: "Что с данными",
    text: "История чатов — на устройстве и в бэкапе аккаунта. Вложения — только на устройстве.",
  },
];

const STEPS = [
  {
    n: "01",
    title: "Оплата",
    text: "Выбираете тариф в Clauden. Оплата картой или другим способом — прямо в интерфейсе.",
  },
  {
    n: "02",
    title: "Активация",
    text: "Запрос уходит верифицированной иностранной IT-компании. Она покупает подписку и передаёт данные нам. Обычно до 10 минут.",
  },
  {
    n: "03",
    title: "Вход",
    text: "Входите в Clauden и работаете в полном интерфейсе. Если аккаунт забанят, запросы автоматически пойдут через резервный запас ключей доступа.",
  },
];

const NOTES = [
  {
    title: "Бан аккаунта не остановит доступ",
    text: "Anthropic может заблокировать выданный аккаунт. Для вас это останется незаметным: мы сохраняем данные подписки, считаем остаток лимита и автоматически переключаем запросы на резервный запас ключей доступа с балансами. Доступ и оплаченный лимит не сгорают.",
  },
  {
    title: "Доступа к вашему Claude нет",
    text: "Перенос данных добровольный: вы сами выгружаете ZIP-архив из Claude и загружаете его в Clauden. Регистрация происходит у нас, а не у Anthropic.",
  },
  {
    title: "Бесплатного тарифа Clauden нет",
    text: "Можно подключить свой Claude-аккаунт: тогда лимиты бесплатного тарифа контролирует Anthropic, а не мы.",
  },
  {
    title: "Перенос подписки",
    text: "С забаненного аккаунта — по чеку и данным об оплате. Решение принимает администрация.",
  },
  {
    title: "Это не официальный клиент",
    text: "Claude — товарный знак Anthropic PBC. Clauden не связан с Anthropic и не представляет её.",
  },
];

const FEATURES = [
  { name: "Интерфейс", text: "Чаты, проекты, артефакты, Cowork и Code." },
  { name: "Модели", text: "Opus, Sonnet и Haiku. Дополнительно можно подключить свои API и модели." },
  { name: "Инструменты", text: "MCP, скиллы, промпты и маркетплейс." },
  { name: "Перенос", text: "Чаты и подписка из официального Claude." },
  { name: "Clauden Code", text: "На телефоне или по SSH к своему серверу." },
  { name: "Android", text: "Календарь, часы и геолокация, как в оригинальном клиенте." },
  { name: "Свои API", text: "OpenAI-совместимые провайдеры и любые модели." },
  { name: "Приложения", text: "Браузер, Windows, macOS, Linux и Android. Один аккаунт." },
];

const FAQ = [
  {
    q: "Clauden — официальный клиент Claude?",
    a: "Нет. Это неофициальная оболочка со своим сервером и интерфейсом. Claude — товарный знак Anthropic PBC, Clauden с Anthropic не связан.",
  },
  {
    q: "Что будет, если аккаунт забанят?",
    a: "У нас есть два независимых канала: выданный аккаунт Claude и резервный запас ключей доступа с балансами. Резервный запас — это заранее оплаченные запросы к тем же моделям, которые включаются вместо заблокированного аккаунта. Если аккаунт заблокируют, мы сохраняем данные подписки, считаем остаток лимита и автоматически переключаем запросы на запас. Для вас ничего не меняется: доступ и оплаченный лимит сохраняются, данные остаются на месте.",
  },
  {
    q: "Вы получаете доступ к моему аккаунту Anthropic?",
    a: "Нет. Перенос данных — только через ваш ZIP-экспорт из Claude. Вы сами загружаете архив в Clauden.",
  },
  {
    q: "Можно пользоваться бесплатно?",
    a: "Да, со своим Claude-аккаунтом: лимиты контролирует Anthropic. Тарифы Clauden платные, бесплатного тарифа нет.",
  },
  {
    q: "Можно подключить Clauden к OpenCode или Claude Code?",
    a: "Да, на тарифах Pro, Max и Ultra. В настройках аккаунта выпускается API-ключ, который работает с Anthropic- и OpenAI-совместимыми клиентами. Инструкции — в документации API: clauden.best/docs.",
  },
  {
    q: "Куда писать, если что-то не работает?",
    a: [
      "Поддержка — в Telegram: ",
      { tg: "@ClaudenApp", href: SUPPORT_TG },
      ". Сообщество — Telegram-канал ",
      { tg: "@ClaudenApp", href: TELEGRAM },
      ". Отвечаем в порядке очереди.",
    ],
  },
];

export function renderLanding(view, ctx) {
  const { state, navigate } = ctx;
  const loggedIn = Boolean(state.user);
  const openApp = () => navigate(loggedIn ? "/chat" : "/login");

  view.className = "landing-view";
  view.append(
    header({ loggedIn, openApp }),
    el("main", { class: "lp-main" },
      hero({ loggedIn, openApp }),
      facts(),
      howItWorks(),
      notes(),
      features(),
      apps({ navigate }),
      faq(),
      contact(),
    ),
    footer(),
  );
}

function header({ loggedIn, openApp }) {
  return el("header", { class: "lp-head" },
    el("div", { class: "lp-wrap lp-head-row" },
      brand(),
      el("nav", { class: "lp-nav", "aria-label": "Разделы страницы" },
        el("a", { class: "lp-nav-link", href: "/pricing" }, "Тарифы"),
        el("a", { class: "lp-nav-link", href: "/docs" }, "API"),
        navLink("#how", "Как работает"),
        navLink("#notes", "Что важно"),
        navLink("#features", "Возможности"),
        navLink("#apps", "Приложения"),
        navLink("#faq", "Вопросы"),
      ),
      el("div", { class: "lp-head-actions" },
        el("a", { class: "lp-tg", href: TELEGRAM, target: "_blank", rel: "noopener" }, "Telegram-канал"),
        el("button", { class: "lp-btn lp-btn-solid", onclick: openApp, text: loggedIn ? "Открыть Clauden" : "Войти" }),
      ),
    ),
  );
}

function brand() {
  return el("a", { class: "lp-brand", href: "/", "aria-label": "Clauden — на главную" },
    el("img", { class: "lp-brand-mark", src: "/assets/logo.svg?v=9", alt: "" }),
    el("span", { class: "lp-brand-name", text: "Clauden" }),
  );
}

function navLink(href, label) {
  return el("a", {
    class: "lp-nav-link",
    href,
    onclick: (event) => {
      event.preventDefault();
      document.querySelector(href)?.scrollIntoView({ behavior: "smooth", block: "start" });
    },
  }, label);
}

function hero({ loggedIn, openApp }) {
  return el("section", { class: "lp-hero" },
    el("div", { class: "lp-wrap lp-hero-grid" },
      el("div", { class: "lp-hero-copy" },
        el("h1", { class: "lp-title", text: "Clauden" }),
        el("p", { class: "lp-lead" },
          "Claude в браузере и приложении: полный интерфейс, модели Anthropic и перенос данных из официального аккаунта.",
        ),
        el("p", { class: "lp-text" },
          "Clauden работает через наш сервер. Если аккаунт Anthropic заблокируют, для вас ничего не изменится: доступ и оплаченный лимит сохранятся. Запросы автоматически переключатся на резервный запас ключей доступа.",
        ),
        el("div", { class: "lp-actions" },
          el("button", { class: "lp-btn lp-btn-solid lp-btn-lg", onclick: openApp },
            loggedIn ? "Открыть Clauden" : "Войти в Clauden",
            el("span", { class: "lp-btn-arrow", html: icon("arrowRight") }),
          ),
          el("a", { class: "lp-link-strong", href: WINDOWS_APP, download: true },
            el("span", { html: icon("download") }), "Скачать для Windows",
          ),
        ),
      ),
      el("div", { class: "lp-hero-mark" },
        el("img", { class: "lp-mascot", src: "/assets/mascot.svg?v=9", alt: "" }),
      ),
    ),
  );
}

function facts() {
  return el("section", { class: "lp-facts" },
    el("div", { class: "lp-wrap lp-facts-grid" },
      FACTS.map((fact) => el("div", { class: "lp-fact" },
        el("div", { class: "lp-label", text: fact.label }),
        el("p", { text: fact.text }),
      )),
    ),
  );
}

function sectionHead(label, title, sub) {
  return el("div", { class: "lp-section-head" },
    el("div", { class: "lp-label", text: label }),
    el("h2", { class: "lp-h2", text: title }),
    sub ? el("p", { class: "lp-text", text: sub }) : null,
  );
}

function howItWorks() {
  return el("section", { class: "lp-section", id: "how" },
    el("div", { class: "lp-wrap" },
      sectionHead("01 / Как это работает", "Оплата, активация, вход"),
      el("ol", { class: "lp-steps" },
        STEPS.map((step) => el("li", { class: "lp-step" },
          el("span", { class: "lp-step-n", text: step.n }),
          el("h3", { text: step.title }),
          el("p", { text: step.text }),
        )),
      ),
      el("p", { class: "lp-note" },
        "Активация занимает дольше 10 минут? Напишите в ",
        el("a", { href: TELEGRAM, target: "_blank", rel: "noopener" }, "Telegram"),
        " — проверим вручную.",
      ),
    ),
  );
}

function notes() {
  return el("section", { class: "lp-section lp-section-alt", id: "notes" },
    el("div", { class: "lp-wrap" },
      sectionHead("02 / Что важно знать", "Оговорки", "Что может пойти не так и как мы это решаем."),
      el("div", { class: "lp-notes" },
        NOTES.map((note, index) => el("div", { class: "lp-note-item" },
          el("span", { class: "lp-note-n", text: String(index + 1).padStart(2, "0") }),
          el("div", null,
            el("h3", { text: note.title }),
            el("p", { text: note.text }),
          ),
        )),
      ),
      el("p", { class: "lp-note lp-note-plain", text: "Цены, лимиты и условия могут меняться. Актуальные — в интерфейсе Clauden." }),
    ),
  );
}

function features() {
  return el("section", { class: "lp-section", id: "features" },
    el("div", { class: "lp-wrap" },
      sectionHead("03 / Возможности", "Что внутри", "Полный список — в приложении. Здесь основное."),
      el("dl", { class: "lp-features" },
        FEATURES.map((feature) => el("div", { class: "lp-feature-row" },
          el("dt", { text: feature.name }),
          el("dd", { text: feature.text }),
        )),
      ),
    ),
  );
}

function apps({ navigate }) {
  const items = [
    {
      label: "Браузер",
      value: "clauden.best/chat",
      action: "Открыть",
      href: "/chat",
      onclick: (event) => { event.preventDefault(); navigate("/chat"); },
    },
    {
      label: "Windows",
      value: "Установщик .exe · обновляется сам",
      action: "Скачать",
      href: WINDOWS_APP,
      download: true,
    },
    {
      label: "macOS",
      value: "Apple Silicon · M1–M4",
      action: "Скачать",
      href: MAC_ARM64_APP,
      download: true,
    },
    {
      label: "macOS",
      value: "Intel · .zip",
      action: "Скачать",
      href: MAC_X64_APP,
      download: true,
    },
    {
      label: "Linux",
      value: "AppImage · автообновление",
      action: "Скачать",
      href: LINUX_APPIMAGE,
      download: true,
    },
    {
      label: "Linux",
      value: "Пакет .deb",
      action: "Скачать",
      href: LINUX_DEB,
      download: true,
    },
    {
      label: "Linux",
      value: "Портативный .tar.gz",
      action: "Скачать",
      href: LINUX_TARBALL,
      download: true,
    },
    {
      label: "Android",
      value: "APK · та же учётная запись",
      action: "Скачать",
      href: ANDROID_APP,
      download: true,
    },
    {
      label: "Clauden Code",
      value: "IDE для Windows",
      action: "Скачать",
      href: CODE_IDE,
      download: true,
    },
    {
      label: "Clauden Code",
      value: "CLI для терминала",
      action: "Скачать",
      href: CODE_CLI,
      download: true,
    },
  ];
  return el("section", { class: "lp-section", id: "apps" },
    el("div", { class: "lp-wrap" },
      sectionHead("04 / Приложения", "Где открыть", "Веб-версия, установщики и Clauden Code — всё с одним аккаунтом."),
      el("div", { class: "lp-apps" },
        items.map((item) => el("a", {
          class: "lp-app-row",
          href: item.href,
          download: item.download || null,
          onclick: item.onclick,
        },
          el("span", { class: "lp-app-label", text: item.label }),
          el("span", { class: "lp-app-value", text: item.value }),
          el("span", { class: "lp-app-action" }, item.action, " ", el("span", { html: icon("arrowRight") })),
        )),
      ),
      el("p", { class: "lp-note lp-note-plain" },
        "Первый запуск на macOS, команды для Linux и Clauden Code — в ",
        el("a", { href: "/docs#install" }, "инструкции по установке"),
        ". Если что-то не получается, напишите в ",
        el("a", { href: TELEGRAM, target: "_blank", rel: "noopener" }, "Telegram"),
        ".",
      ),
    ),
  );
}

function faq() {
  return el("section", { class: "lp-section", id: "faq" },
    el("div", { class: "lp-wrap lp-faq-wrap" },
      sectionHead("05 / Поддержка", "Вопросы"),
      el("div", { class: "lp-faq" },
        FAQ.map((item) => el("details", { class: "lp-faq-item" },
          el("summary", { class: "lp-faq-q" },
            el("span", { text: item.q }),
            el("span", { class: "lp-faq-mark", html: icon("plus") }),
          ),
          el("p", { class: "lp-faq-a" },
            ...(Array.isArray(item.a) ? item.a.map((part) =>
              typeof part === "string"
                ? part
                : el("a", { href: part.href, target: "_blank", rel: "noopener" }, part.tg)
            ) : [item.a]),
          ),
        )),
      ),
    ),
  );
}

function contact() {
  return el("section", { class: "lp-contact" },
    el("div", { class: "lp-wrap lp-contact-row" },
      el("div", null,
        el("h2", { class: "lp-h2", text: "Не нашли ответ?" }),
        el("p", { class: "lp-text", text: "Поддержка — в Telegram или на почту. Сообщество — Telegram-канал @ClaudenApp." }),
      ),
      el("div", { style: { display: "flex", gap: "18px", flexWrap: "wrap" } },
        el("a", { class: "lp-contact-link", href: SUPPORT_TG, target: "_blank", rel: "noopener" },
          "Telegram: @ClaudenApp", el("span", { html: icon("arrowRight") }),
        ),
        el("a", { class: "lp-contact-link", href: TELEGRAM, target: "_blank", rel: "noopener" },
          "Канал: @ClaudenApp", el("span", { html: icon("arrowRight") }),
        ),
        el("a", { class: "lp-contact-link", href: `mailto:${SUPPORT_EMAIL}` },
          SUPPORT_EMAIL, el("span", { html: icon("arrowRight") }),
        ),
      ),
    ),
  );
}

function footer() {
  const year = new Date().getFullYear();
  return el("footer", { class: "lp-footer" },
    el("div", { class: "lp-wrap lp-footer-row" },
      el("div", { class: "lp-footer-brand" },
        el("img", { class: "lp-footer-mark", src: "/assets/logo.svg?v=9", alt: "" }),
        el("span", { text: `© ${year} Clauden` }),
      ),
      el("div", { class: "lp-footer-links" },
        el("a", { href: "/docs" }, "API"),
        el("a", { href: "/pricing" }, "Тарифы"),
        el("a", { href: "/offer" }, "Оферта"),
        el("a", { href: "/privacy" }, "Приватность"),
        el("a", { href: "/status" }, "Статус"),
        el("a", { href: "/pricing#refund" }, "Оплата и возврат"),
        el("a", { href: TELEGRAM, target: "_blank", rel: "noopener" }, "Telegram-канал"),
      ),
    ),
    el("div", { class: "lp-wrap lp-footer-note" },
      "Самозанятый Зайцев Станислав Алексеевич, ИНН 362201956766, г. Воронеж. Claude — товарный знак Anthropic PBC. Clauden не связан с Anthropic.",
      el("br"),
      "Поддержка в Telegram: ",
      el("a", { href: SUPPORT_TG, target: "_blank", rel: "noopener" }, "@ClaudenApp"),
      ". Сообщество в Telegram: ",
      el("a", { href: TELEGRAM, target: "_blank", rel: "noopener" }, "@ClaudenApp"),
      ".",
    ),
  );
}
