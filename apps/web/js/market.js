import { $, el, clear, icon, toast, debounce, uid, openModal } from "./util.js";
import { api } from "./api.js";
import { loadCustomize, saveCustomize } from "./store.js";

const TABS = [
  { id: "skills", label: "Навыки", source: "standard", kind: "skills" },
  { id: "catalog", label: "Каталог", source: "claudskills", kind: "skills", endpoint: "skills" },
  { id: "connectors", label: "Коннекторы", source: "mcp", kind: "connectors" },
  { id: "plugins", label: "Плагины", source: "", kind: "plugins" },
  { id: "yours", label: "Мои", kind: "yours" },
  { id: "market", label: "Маркет", source: "", kind: "plugins", endpoint: "plugins" },
];

export function renderMarket(view, ctx, initialTab = "skills") {
  let tab = TABS.some((item) => item.id === initialTab) ? initialTab : "skills";
  let query = "";

  const body = el("div", { class: "screen-inner" });
  const page = el("div", { class: "page" });

  const tabs = el("div", { class: "segmented", style: { marginBottom: "20px" } });
  const search = el("div", { class: "input-wrap", style: { width: "260px" } },
    el("span", { class: "input-icon", html: icon("search") }),
    el("input", { class: "input", type: "search", placeholder: "Поиск…" }),
  );
  const head = el("div", { class: "row between", style: { marginBottom: "20px", gap: "14px", flexWrap: "wrap" } },
    el("h1", { class: "page-title", style: { margin: "0" }, text: "Настройка" }),
    el("div", { class: "row" },
      search,
      el("button", { class: "icon-btn", title: "Фильтр", onclick: () => { query = ""; search.querySelector("input").value = ""; load(); }, html: icon("filter") }),
      el("button", { class: "icon-btn", title: "Сортировать", onclick: () => load(true), html: icon("sort") }),
      el("button", { class: "btn btn-secondary", onclick: () => addCustom(ctx) }, el("span", { html: icon("plus") }), "Добавить"),
    ),
  );

  const hero = el("div", { class: "customize-hero" },
    el("div", null,
      el("div", { class: "ch-from", text: "Стандартные навыки" }),
      el("div", { class: "ch-title", text: "Remotion, Figma, Vercel" }),
      el("div", { class: "ch-desc", text: "Видео на React, дизайн в Figma, UI-гайдлайны Vercel и shadcn/ui — добавляются в один клик." }),
    ),
    el("div", { class: "ch-icon", html: icon("sparkles") }),
  );

  const grid = el("div", { id: "market-grid" });
  page.append(head, tabs, hero, grid);
  body.append(page);
  view.append(body);

  const drawTabs = () => {
    clear(tabs);
    for (const item of TABS) {
      tabs.append(el("button", {
        class: `tab ${tab === item.id ? "active" : ""}`,
        text: item.label,
        onclick: () => { tab = item.id; query = ""; search.querySelector("input").value = ""; drawTabs(); load(); },
      }));
    }
  };

  const load = async (sort = false) => {
    clear(grid);
    if (tab === "yours") {
      renderInstalled(grid, ctx);
      return;
    }
    grid.append(el("div", { class: "loading-block" }, el("span", { class: "spinner" }), "Загружаю каталог…"));
    try {
      const config = TABS.find((t) => t.id === tab);
      const endpoint = config.endpoint || tab;
      const data = await api.market(endpoint, { q: query, limit: 24, source: config.source || undefined });
      clear(grid);
      const items = data?.results || [];
      if (!items.length) {
        grid.append(el("div", { class: "empty-block" },
          el("h3", { text: "Ничего не найдено" }),
          el("p", { text: data?.warning || "Попробуйте другой запрос." }),
        ));
        return;
      }
      if (data?.warning) {
        grid.append(el("div", { class: "notice-box", text: data.warning }));
      }
      if (config.source === "claudskills") {
        grid.append(el("p", {
          class: "field-hint",
          style: { marginBottom: "10px" },
          text: "Каталог ClaudSkills · 15 000+ навыков · CC BY 4.0",
        }));
      }
      grid.append(el("div", { class: "section-title", text: "Для вас" }));
      const cards = el("div", { class: "skill-grid" });
      for (const item of items) cards.append(marketCard(item, config.kind || tab, ctx));
      grid.append(cards);
      if (data.total > items.length) {
        grid.append(el("p", { class: "field-hint", style: { marginTop: "12px" }, text: `Показано ${items.length} из ${data.total}. Уточните поиск, чтобы увидеть остальное.` }));
      }
    } catch (err) {
      clear(grid);
      grid.append(el("div", { class: "error-box", text: err.message || "Не удалось загрузить каталог" }));
    }
  };

  search.querySelector("input").addEventListener("input", debounce((e) => { query = e.target.value; load(); }, 320));
  drawTabs();
  load();
}

function renderInstalled(grid, ctx) {
  const userId = ctx.state.user?.id;
  const customize = loadCustomize(userId);
  const connectors = loadConnectors();
  const items = [
    ...customize.skills.map((skill) => ({ ...skill, group: "Навыки", icon: "sparkles" })),
    ...customize.plugins.map((plugin) => ({ ...plugin, group: "Плагины", icon: "layers" })),
    ...connectors.map((connector) => ({
      id: connector.id,
      title: connector.title,
      description: connector.url,
      group: "Коннекторы",
      icon: "plug",
      url: connector.url,
    })),
  ];
  if (!items.length) {
    grid.append(el("div", { class: "empty-block" },
      el("div", { html: icon("layers") }),
      el("h3", { text: "Пока ничего не установлено" }),
      el("p", { text: "Откройте вкладку «Маркет» и добавьте навык, плагин или коннектор." }),
      el("button", { class: "btn btn-primary", onclick: () => ctx.navigate("/market/market") }, "В маркет"),
    ));
    return;
  }
  let group = "";
  for (const item of items) {
    if (item.group !== group) {
      group = item.group;
      grid.append(el("div", { class: "section-title", text: group }));
    }
    grid.append(el("div", { class: "skill-card" },
      el("span", { class: "skill-icon", html: icon(item.icon) }),
      el("div", { class: "skill-body" },
        el("div", { class: "skill-title", text: item.title || item.id }),
        el("div", { class: "skill-desc", text: item.description || item.instructions || "" }),
        el("div", { class: "row", style: { marginTop: "8px" } },
          el("button", { class: "btn btn-secondary btn-sm", text: "Отключить", onclick: () => removeInstalled(ctx, item) }),
          item.url ? el("a", { class: "btn btn-ghost btn-sm", href: item.url, target: "_blank", rel: "noopener", text: "Открыть" }) : null,
        ),
      ),
    ));
  }
}

function removeInstalled(ctx, item) {
  const userId = ctx.state.user?.id;
  const customize = loadCustomize(userId);
  if (item.group === "Навыки") {
    customize.skills = customize.skills.filter((skill) => skill.id !== item.id);
    saveCustomize(userId, customize);
  } else if (item.group === "Плагины") {
    customize.plugins = customize.plugins.filter((plugin) => plugin.id !== item.id);
    saveCustomize(userId, customize);
  } else {
    const list = loadConnectors().filter((connector) => connector.id !== item.id);
    localStorage.setItem("clauden.connectors", JSON.stringify(list));
  }
  ctx.toast(`«${item.title}» отключён`);
  ctx.renderView();
}

function marketInstalled(item, kind, userId) {
  const customize = loadCustomize(userId);
  return kind === "skills"
    ? customize.skills.some((skill) => skill.sourceId === item.id || skill.id === item.id)
    : kind === "plugins"
      ? customize.plugins.some((plugin) => plugin.sourceId === item.id || plugin.id === item.id)
      : loadConnectors().some((connector) => connector.title === item.title || (item.url && connector.url === item.url));
}

async function installMarket(item, kind, ctx, button) {
  const userId = ctx.state.user?.id;
  if (button) {
    button.disabled = true;
    button.innerHTML = '<span class="spinner"></span>';
  }
  try {
    const result = await api.marketInstall(kind, item.id);
    const data = loadCustomize(userId);
    if (result.skill) {
      const skill = { ...result.skill, sourceId: item.id };
      data.skills = [skill, ...data.skills.filter((entry) => entry.id !== skill.id)];
    }
    if (result.plugin) {
      const plugin = { ...result.plugin, sourceId: item.id };
      data.plugins = [plugin, ...data.plugins.filter((entry) => entry.id !== plugin.id)];
    }
    for (const skill of result.skills || []) {
      data.skills = [
        { ...skill, sourceId: `plugin:${item.id}` },
        ...data.skills.filter((entry) => entry.id !== skill.id),
      ];
    }
    if (result.skill || result.plugin || (result.skills || []).length) {
      saveCustomize(userId, data);
    }
    if (result.connector) {
      const list = loadConnectors();
      list.push({
        id: uid("mcp"),
        title: result.connector.title || item.title,
        url: result.connector.url || item.url,
        headers: "",
        enabled: true,
        status: "installed",
      });
      localStorage.setItem("clauden.connectors", JSON.stringify(list));
    }
    const skillCount = (result.skills || []).length + (result.skill ? 1 : 0);
    ctx.toast(
      skillCount > 1
        ? `Установлено: «${item.title}» и навыков ${skillCount}`
        : `«${item.title || item.id}» установлен`,
      "success",
    );
    if (button) {
      button.textContent = "Установлено";
      button.disabled = true;
    }
    return result;
  } catch (err) {
    ctx.toast(err.message || "Не удалось установить", "error");
    if (button) {
      button.disabled = false;
      button.textContent = "Добавить";
    }
    return null;
  }
}

function detailSection(title, text) {
  return el("div", { style: { marginTop: "16px" } },
    el("div", { style: { fontSize: "13px", fontWeight: "600", marginBottom: "6px" }, text: title }),
    el("div", {
      style: {
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        fontSize: "13px",
        lineHeight: "1.55",
        color: "var(--text-2)",
        background: "var(--surface-2)",
        border: "1px solid var(--border)",
        borderRadius: "10px",
        padding: "12px",
        maxHeight: "320px",
        overflow: "auto",
      },
      text: text || "Описание не указано.",
    }),
  );
}

async function openMarketDetails(item, kind, ctx) {
  const installed = marketInstalled(item, kind, ctx.state.user?.id);
  const body = el("div", { class: "modal-body" });
  const foot = el("div", { class: "modal-foot" });
  const modal = openModal(
    el("div", null,
      el("div", { class: "modal-head" },
        el("h2", { class: "modal-title", text: item.title || item.id }),
      ),
      body,
      foot,
    ),
    { wide: true },
  );

  body.append(el("div", { class: "skill-desc", text: item.description || "" }));
  body.append(el("div", { class: "skill-meta", text: [item.author, item.category, item.source].filter(Boolean).join(" · ") }));
  body.append(el("div", { class: "loading-block", style: { marginTop: "14px" } },
    el("span", { class: "spinner" }), "Загружаю содержимое…",
  ));

  const installButton = el("button", {
    class: "btn btn-primary",
    text: installed ? "Установлено" : "Добавить",
    disabled: installed,
    onclick: async (e) => {
      const result = await installMarket(item, kind, ctx, e.currentTarget);
      if (result) {
        modal.close();
        ctx.renderView?.();
      }
    },
  });
  foot.append(
    el("button", { class: "btn btn-ghost", text: "Закрыть", onclick: () => modal.close() }),
    item.url ? el("a", { class: "btn btn-ghost", href: item.url, target: "_blank", rel: "noopener", text: "Источник" }) : el("span"),
    installButton,
  );

  try {
    const preview = await api.marketInstall(kind, item.id, true);
    body.querySelector(".loading-block")?.remove();
    if (preview.plugin) {
      body.append(detailSection("Что это", preview.plugin.description || item.description));
      if (preview.plugin.instructions && preview.plugin.instructions.length > 80) {
        body.append(detailSection("Инструкции плагина", preview.plugin.instructions));
      }
      const skills = preview.skills || [];
      if (skills.length) {
        body.append(el("div", { style: { marginTop: "16px", fontSize: "13px", fontWeight: "600" }, text: `Навыки внутри (${skills.length})` }));
        for (const skill of skills) {
          body.append(el("div", { style: { padding: "10px 0", borderBottom: "1px solid var(--border)" } },
            el("div", { style: { fontSize: "14px", fontWeight: "500" }, text: skill.title }),
            el("div", { class: "skill-desc", style: { margin: "4px 0 0" }, text: skill.description || "" }),
          ));
        }
      }
    } else if (preview.skill) {
      body.append(detailSection("Что это", preview.skill.description || item.description));
      body.append(detailSection("Инструкции навыка", preview.skill.instructions));
    } else if (preview.connector) {
      body.append(detailSection("Что это", preview.connector.description || item.description));
      if (preview.connector.url) body.append(detailSection("MCP-адрес", preview.connector.url));
    } else {
      body.append(detailSection("Что это", item.description));
    }
  } catch (err) {
    body.querySelector(".loading-block")?.remove();
    body.append(el("div", { class: "error-box", text: err.message || "Не удалось загрузить описание" }));
  }
}

function marketCard(item, tab, ctx) {
  const installed = marketInstalled(item, tab, ctx.state.user?.id);
  const install = el("button", {
    class: "btn btn-secondary btn-sm",
    text: installed ? "Установлено" : "Добавить",
    disabled: installed,
    onclick: async (e) => {
      e.stopPropagation();
      const result = await installMarket(item, tab, ctx, e.currentTarget);
      if (result) ctx.renderView?.();
    },
  });
  const iconName = tab === "connectors" ? "plug" : tab === "plugins" ? "layers" : "sparkles";
  return el("div", { class: "skill-card" },
    el("span", { class: "skill-icon", html: icon(iconName) }),
    el("div", { class: "skill-body" },
      el("div", {
        class: "skill-title",
        style: { cursor: "pointer" },
        text: item.title || item.id,
        onclick: () => openMarketDetails(item, tab, ctx),
      }),
      el("div", { class: "skill-desc", text: item.description || "" }),
      el("div", { class: "skill-meta", text: [item.author, item.stars ? `★ ${item.stars}` : "", item.category].filter(Boolean).join(" · ") }),
      el("div", { class: "skill-actions" },
        install,
        el("button", { class: "btn btn-ghost btn-sm", text: "Описание", onclick: () => openMarketDetails(item, tab, ctx) }),
        item.url ? el("a", {
          class: "btn btn-ghost btn-sm",
          href: item.url,
          target: "_blank",
          rel: "noopener",
          text: "Источник",
          onclick: (e) => e.stopPropagation(),
        }) : null,
      ),
    ),
  );
}

function addCustom(ctx) {
  const title = el("input", { class: "input", placeholder: "Название" });
  const text = el("textarea", { class: "textarea", rows: "5", placeholder: "Инструкции навыка: что и как делать" });
  const url = el("input", { class: "input", placeholder: "https://example.com/mcp (для коннектора)" });
  const error = el("div", { class: "error-box", hidden: true });
  const modal = openModal(el("div", null,
    el("div", { class: "modal-head" }, el("h2", { class: "modal-title", text: "Добавить навык или коннектор" })),
    el("div", { class: "modal-body" },
      el("div", { class: "field" }, el("label", { text: "Название" }), title),
      el("div", { class: "field" }, el("label", { text: "Инструкции" }), text),
      el("div", { class: "field" }, el("label", { text: "Адрес MCP-сервера (необязательно)" }), url),
      error,
    ),
    el("div", { class: "modal-foot" },
      el("button", { class: "btn btn-ghost", text: "Отмена", onclick: () => modal.close() }),
      el("button", { class: "btn btn-primary", text: "Добавить", onclick: () => {
        const name = title.value.trim();
        if (!name) { error.textContent = "Введите название"; error.hidden = false; return; }
        const userId = ctx.state.user?.id;
        const customize = loadCustomize(userId);
        if (url.value.trim()) {
          const list = loadConnectors();
          list.push({
            id: uid("mcp"),
            title: name,
            url: url.value.trim(),
            headers: "",
            enabled: true,
            status: "installed",
          });
          localStorage.setItem("clauden.connectors", JSON.stringify(list));
        } else {
          customize.skills = [
            { id: uid("skill"), title: name, instructions: text.value.trim(), source: "custom" },
            ...customize.skills,
          ];
          saveCustomize(userId, customize);
        }
        modal.close();
        ctx.toast(`«${name}» добавлен`, "success");
        ctx.renderView();
      }}),
    ),
  ));
  setTimeout(() => title.focus(), 30);
}

function loadConnectors() {
  try { return JSON.parse(localStorage.getItem("clauden.connectors") || "[]"); } catch { return []; }
}
