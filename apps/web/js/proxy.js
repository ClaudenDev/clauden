import { el, clear, icon, toast, isMobile } from "./util.js";
import { proxyDesktop, isDesktopApp } from "./api.js";

const PING_LABEL = (ms) => (ms > 0 ? `${ms} мс` : "—");

function statusText(state) {
  if (state.status === "on") return "Подключено";
  if (state.status === "connecting") return "Подключение…";
  if (state.status === "error") return "Ошибка";
  return "Выключено";
}

function nodeRow(state, node, { onSelect, onPing, pings }) {
  const selected = node.id === state.selectedId;
  const ping = pings.get(node.id);
  const row = el("div", {
    class: `proxy-node${selected ? " selected" : ""}`,
    onclick: () => onSelect(node.id),
  },
    el("span", { class: "proxy-node-radio", html: icon(selected ? "check" : "circle") }),
    el("div", { class: "proxy-node-text" },
      el("div", { class: "proxy-node-name", text: node.name }),
      el("div", { class: "proxy-node-meta", text: `${node.protocolLabel} · ${node.endpoint}` }),
    ),
    el("span", { class: "proxy-node-ping", text: ping === undefined ? "" : PING_LABEL(ping) }),
    el("button", {
      class: "icon-btn sm",
      title: "Проверить пинг",
      onclick: (event) => { event.stopPropagation(); onPing(node.id); },
      html: icon("bolt"),
    }),
  );
  if (node.unsupported) row.classList.add("disabled");
  return row;
}

export async function renderProxy(pane, ctx) {
  pane.append(
    el("h1", { class: "settings-title", text: "Прокси и VPN" }),
    el("p", {
      class: "settings-desc",
      text: "claude.ai не открывается из вашей сети? Включите туннель — трафик приложения пойдёт через него.",
    }),
  );

  if (!isDesktopApp() || !proxyDesktop.available()) {
    pane.append(el("div", { class: "notice-box", text: "Прокси доступен только в приложении Clauden для ПК. Обновите приложение." }));
    return;
  }

  const pings = new Map();
  let state = null;

  const draw = () => {
    clear(pane);
    pane.append(
      el("h1", { class: "settings-title", text: "Прокси и VPN" }),
      el("p", {
        class: "settings-desc",
        text: "claude.ai не открывается из вашей сети? Включите туннель — трафик приложения пойдёт через него.",
      }),
    );
    if (!state) {
      pane.append(el("div", { class: "muted small", text: "Загрузка…" }));
      return;
    }

    if (state.exitIp) {
      const outside = Boolean(state.exitOutsideRussia);
      pane.append(
        el("div", { class: "settings-group" },
          el("div", { class: "section-title", text: "Ваш выход в интернет" }),
          el("div", { class: "setting-hint", text: `${state.exitCountry || "неизвестно"} · ${state.exitIp}` }),
          el("div", {
            class: "setting-hint",
            text: outside
              ? "claude.ai открывается напрямую: прокси Clauden не нужен, свой VPN можно не отключать."
              : "Выход из России: для claude.ai нужен быстрый канал или прокси Clauden.",
          }),
        ),
      );
    }
    if (state.autoSkipped && state.autoSkipReason) {
      pane.append(el("div", { class: "notice-box", text: state.autoSkipReason }));
    }

    const busy = state.status === "connecting";
    const on = state.status === "on";
    const selected = state.selected;

    pane.append(
      el("div", { class: `proxy-card${on ? " on" : ""}` },
        el("div", { class: "proxy-card-icon", html: icon(on ? "shieldCheck" : "shield") }),
        el("div", { class: "setting-text" },
          el("div", { class: "proxy-card-title", text: statusText(state) }),
          el("div", { class: "setting-hint", text: on ? (state.activeName || selected?.name || "") : (selected ? `Сервер: ${selected.name}` : "Сервер не выбран") }),
        ),
        el("button", {
          class: `btn ${on ? "btn-secondary" : "btn-primary"} btn-sm`,
          text: on ? "Выключить" : "Включить",
          disabled: busy || !selected,
          onclick: async (event) => {
            const button = event.currentTarget;
            button.disabled = true;
            button.textContent = on ? "Выключение…" : "Подключение…";
            try {
              state = on ? await proxyDesktop.disconnect() : await proxyDesktop.connect();
              if (state.status === "error") toast(state.error || "Не удалось подключиться", "error");
              else if (state.status === "on") toast("Прокси включён");
            } catch (err) {
              toast(err.message || "Не удалось переключить прокси", "error");
            }
            draw();
          },
        }),
      ),
    );

    if (state.status === "error" && state.error) {
      pane.append(el("div", { class: "notice-box", text: state.error }));
    }

    const select = async (id) => {
      try {
        state = await proxyDesktop.select(id);
        draw();
      } catch (err) {
        toast(err.message || "Не удалось выбрать сервер", "error");
      }
    };
    const ping = async (id) => {
      pings.set(id, -1);
      draw();
      try {
        const result = await proxyDesktop.ping(id);
        pings.set(id, result.ok ? result.ms : 0);
      } catch {
        pings.set(id, 0);
      }
      draw();
    };

    const builtinGroup = el("div", { class: "settings-group" },
      el("div", { class: "section-title", text: "Встроенный прокси Clauden" }),
      el("div", { class: "setting-hint", text: "Готовые узлы, ничего настраивать не нужно." }),
    );
    const builtinList = el("div", { class: "proxy-list" });
    for (const node of state.builtin || []) {
      builtinList.append(nodeRow(state, node, { onSelect: select, onPing: ping, pings }));
    }
    if (!(state.builtin || []).length) {
      builtinList.append(el("div", { class: "muted small", text: "Список узлов недоступен" }));
    }
    builtinGroup.append(builtinList,
      el("button", {
        class: "btn btn-secondary btn-sm",
        text: "Обновить список",
        onclick: async (event) => {
          const button = event.currentTarget;
          button.disabled = true;
          try {
            state = await proxyDesktop.refresh();
          } catch (err) {
            toast(err.message || "Не удалось обновить список", "error");
          }
          draw();
        },
      }),
    );
    pane.append(builtinGroup);

    const subInput = el("input", {
      class: "input",
      placeholder: "https://… или вставьте список vless:// vmess:// trojan:// ss://",
      value: state.subscription && !state.subscription.startsWith("vless://") && !state.subscription.startsWith("vmess://") ? state.subscription : "",
    });
    const subGroup = el("div", { class: "settings-group" },
      el("div", { class: "section-title", text: "Своя подписка" }),
      el("div", { class: "field" }, subInput),
      el("div", { class: "row" },
        el("button", {
          class: "btn btn-primary btn-sm",
          text: "Загрузить",
          onclick: async (event) => {
            const button = event.currentTarget;
            button.disabled = true;
            button.textContent = "Загрузка…";
            try {
              state = await proxyDesktop.subscribe(subInput.value.trim());
              toast(`Серверов в подписке: ${(state.nodes || []).length}`);
              draw();
            } catch (err) {
              toast(err.message || "Не удалось загрузить подписку", "error");
              button.disabled = false;
              button.textContent = "Загрузить";
            }
          },
        }),
        el("button", {
          class: "btn btn-ghost btn-sm",
          text: "Очистить",
          onclick: async () => {
            try {
              state = await proxyDesktop.subscribe("");
            } catch {

            }
            subInput.value = "";
            draw();
          },
        }),
      ),
    );
    const nodeList = el("div", { class: "proxy-list" });
    for (const node of state.nodes || []) {
      nodeList.append(nodeRow(state, node, { onSelect: select, onPing: ping, pings }));
    }
    if ((state.nodes || []).length) subGroup.append(nodeList);
    pane.append(subGroup);

    pane.append(
      el("div", { class: "settings-group" },
        el("div", { class: "setting-row" },
          el("div", { class: "setting-text" },
            el("div", { class: "setting-name", text: "Включать при запуске" }),
            el("div", { class: "setting-hint", text: "Поднимать туннель автоматически при старте приложения" }),
          ),
          el("label", { class: "switch" },
            el("input", {
              type: "checkbox",
              checked: state.autoStart ? "" : null,
              onchange: async (event) => {
                try {
                  state = await proxyDesktop.autostart(event.currentTarget.checked);
                } catch (err) {
                  toast(err.message || "Не удалось сохранить настройку", "error");
                }
              },
            }),
            el("span", { class: "switch-track" }),
          ),
        ),
        el("div", { class: "setting-hint", text: `Локальный SOCKS5: 127.0.0.1:${state.socksPort || 20818} · HTTP: 127.0.0.1:${state.httpPort || 20819}` }),
        state.binary === false
          ? el("div", { class: "notice-box", text: "В этой сборке нет ядра прокси — переустановите приложение." })
          : null,
      ),
    );

    if (isMobile()) pane.classList.add("mobile");
  };

  draw();
  try {
    state = await proxyDesktop.state();

    try {
      const withExit = await proxyDesktop.ownExit();
      if (withExit) state = withExit;
    } catch {

    }
  } catch (err) {
    state = null;
    toast(err.message || "Прокси недоступен", "error");
  }
  draw();
}
