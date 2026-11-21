import { h, rpc, toast, modal } from "./ui.js";
import { state } from "./state.js";

const TOKEN_URL = "https://github.com/settings/tokens/new?scopes=repo&description=Clauden%20Code";

export async function loadGithub({ refresh = false } = {}) {
  if (state.github && !refresh) return state.github;
  const info = await rpc(window.clauden.code.github()).catch(() => null);
  state.github = info?.connected ? info : { connected: false };
  return state.github;
}

export async function loadGithubRepos({ refresh = false } = {}) {
  if (Array.isArray(state.githubRepos) && !refresh) return state.githubRepos;
  const info = await loadGithub();
  if (!info.connected) {
    state.githubRepos = [];
    return state.githubRepos;
  }
  const repos = await rpc(window.clauden.code.githubRepos()).catch((err) => {
    toast(err.message || "Не удалось загрузить репозитории GitHub", "error");
    return [];
  });
  state.githubRepos = Array.isArray(repos) ? repos : [];
  return state.githubRepos;
}

export function githubAccountLabel(info) {
  if (!info?.connected) return "GitHub не подключён";
  return `GitHub: @${info.login || "подключён"}`;
}

export function openGithubDialog({ onChanged } = {}) {
  let info = state.github || { connected: false };

  const status = h("div", { class: "update-hint" });
  const tokenLabel = h("div", { class: "field" },
    h("label", { text: "Токен GitHub" }),
  );
  const tokenInput = h("input", {
    type: "password",
    placeholder: "ghp_… или github_pat_…",
    spellcheck: "false",
    autocomplete: "off",
  });
  const error = h("div", { class: "modal-error hidden" });

  const connectBtn = h("button", {
    class: "btn btn-primary btn-sm",
    text: "Подключить GitHub",
    onclick: async () => {
      const token = tokenInput.value.trim();
      if (!token) {
        showError("Вставьте токен GitHub");
        tokenInput.focus();
        return;
      }
      connectBtn.disabled = true;
      connectBtn.textContent = "Проверяю…";
      error.classList.add("hidden");
      try {
        const next = await rpc(window.clauden.code.githubSave(token));
        info = next?.connected ? next : { connected: false };
        state.github = info;
        state.githubRepos = [];
        tokenInput.value = "";
        paint();
        toast(`GitHub подключён: @${info.login || "аккаунт"}`, "success");
        onChanged?.(info);
      } catch (err) {
        showError(err.message || "GitHub не принял токен");
      } finally {
        connectBtn.disabled = false;
        connectBtn.textContent = "Подключить GitHub";
      }
    },
  });

  const disconnectBtn = h("button", {
    class: "btn btn-secondary btn-sm",
    text: "Отключить GitHub",
    onclick: async () => {
      disconnectBtn.disabled = true;
      try {
        await rpc(window.clauden.code.githubRemove());
        info = { connected: false };
        state.github = info;
        state.githubRepos = [];
        paint();
        toast("GitHub отключён");
        onChanged?.(info);
      } catch (err) {
        showError(err.message || "Не удалось отключить GitHub");
      } finally {
        disconnectBtn.disabled = false;
      }
    },
  });

  const tokenButton = h("button", {
    class: "btn btn-ghost btn-sm",
    text: "Создать токен на GitHub",
    onclick: () => window.clauden.sys.openExternal(TOKEN_URL),
  });

  const tokenRow = h("div", { class: "field" }, tokenInput);
  const actions = h("div", { class: "github-actions" }, connectBtn, tokenButton, disconnectBtn);

  function showError(message) {
    error.textContent = message;
    error.classList.remove("hidden");
  }

  function paint() {
    status.textContent = info.connected
      ? `Подключён как @${info.login || "аккаунт"}. ` +
        "В новой сессии можно выбрать любой из ваших репозиториев."
      : "Вставьте personal access token с доступом к репозиториям. " +
        "Classic-токену нужен scope repo, fine-grained — Contents: read & write.";
    tokenLabel.classList.toggle("hidden", Boolean(info.connected));
    tokenRow.classList.toggle("hidden", Boolean(info.connected));
    connectBtn.classList.toggle("hidden", Boolean(info.connected));
    tokenButton.classList.toggle("hidden", Boolean(info.connected));
    disconnectBtn.classList.toggle("hidden", !info.connected);
  }

  paint();

  const dialog = modal({
    title: "GitHub",
    subtitle:
      "Токен хранится на сервере Clauden и используется только для клонирования " +
      "и пуша выбранных вами репозиториев.",
    body: h("div", {},
      status,
      tokenLabel,
      tokenRow,
      error,
      actions,
      h("div", {
        class: "update-hint",
        text:
          "Не используйте пароль от GitHub — нужен именно personal access token. " +
          "После подключения список репозиториев появится в «Новой сессии».",
      }),
    ),
    actions: [{ label: "Закрыть", kind: "secondary", onClick: (close) => close() }],
    width: 480,
  });

  loadGithub({ refresh: true })
    .then((next) => {
      info = next || { connected: false };
      paint();
    })
    .catch(() => {});

  setTimeout(() => tokenInput.focus(), 60);
  return dialog;
}
