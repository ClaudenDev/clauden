import { h, icon, rpc, modal, toast, relativeTime, groupLabel, confirmDialog } from "./ui.js";
import { state, resetSession } from "./state.js";
import { connectorPayload, defaultEffort } from "./connectors.js";
import { loadGithub, loadGithubRepos, openGithubDialog } from "./github.js";

export function renderSessions(host, ctx) {
  host.replaceChildren();

  if (!state.sessions.length) {
    if (state.sessionsError) {
      host.append(
        h(
          "div",
          { class: "side-empty" },
          h("div", { html: icon("alert", 22), style: { opacity: ".7", marginBottom: "8px", color: "var(--red)" } }),
          h("div", { text: "Сессии не загрузились" }),
          h("div", {
            text: state.sessionsError,
            style: { marginTop: "6px", opacity: ".8" },
          }),
          h(
            "div",
            { style: { marginTop: "12px", display: "flex", gap: "8px", justifyContent: "center" } },
            h("button", {
              class: "btn btn-primary btn-sm",
              html: `${icon("refresh", 13)}<span>Повторить</span>`,
              onclick: () => ctx.refreshSessions({ retries: 2 }),
            }),
          ),
        ),
      );
      return;
    }
    host.append(
      h(
        "div",
        { class: "side-empty" },
        h("div", { html: icon("sessions", 22), style: { opacity: ".5", marginBottom: "8px" } }),
        h("div", { text: "Пока нет сессий" }),
        h("div", { text: "Создайте первую — агент возьмёт репозиторий и начнёт работу.", style: { marginTop: "6px" } }),
        h("div", { style: { marginTop: "12px" } },
          h("button", {
            class: "btn btn-primary btn-sm",
            html: `${icon("folderPlus", 13)}<span>Открыть папку</span>`,
            onclick: () => openLocalSession(ctx),
          }),
        ),
      ),
    );
    return;
  }

  let lastGroup = "";
  for (const session of state.sessions) {
    const group = groupLabel(session.updatedAt);
    if (group !== lastGroup) {
      lastGroup = group;
      host.append(h("div", { class: "group-label", text: group }));
    }
    host.append(sessionRow(session, ctx));
  }
}

function sessionRow(session, ctx) {
  const status = session.status || "idle";
  const local = session.source === "ssh";
  const meta = [
    local ? "этот ПК" : null,
    session.repoName || session.repo,
    local ? null : session.branch,
  ]
    .filter(Boolean)
    .join(" · ");
  const active = state.session?.id === session.id;

  return h(
    "div",
    {
      class: `sess ${active ? "active" : ""}`,
      onclick: () => ctx.openSession(session.id),
      title: local ? `${session.title || session.id}\n${session.remote?.root || ""}` : session.title || session.id,
    },
    h("span", { class: `sess-dot ${status}` }),
    h(
      "div",
      { class: "sess-main" },
      h(
        "div",
        { class: "sess-title" },
        local ? h("span", { class: "sess-kind", html: icon("laptop", 11) }) : null,
        h("span", { text: session.title || "Без названия" }),
      ),
      h("div", {
        class: "sess-meta",
        text: `${meta || session.id} · ${relativeTime(session.updatedAt)}`,
      }),
    ),
    h("button", {
      class: "icon-btn sess-delete",
      title: "Удалить сессию",
      html: icon("trash", 13),
      onclick: async (event) => {
        event.stopPropagation();
        const ok = await confirmDialog(
          "Удалить сессию?",
          `${session.title || session.id}\nПапка и переписка будут удалены.`,
        );
        if (!ok) return;
        try {
          await rpc(window.clauden.code.deleteSession(session.id));
          toast("Сессия удалена");
          const rest = state.sessions.filter((item) => item.id !== session.id);
          state.sessions = rest;
          if (state.session?.id === session.id) {
            resetSession();
            if (rest[0]) await ctx.openSession(rest[0].id);
            else {
              localStorage.removeItem("cc:lastSession");
              location.reload();
            }
          }
          ctx.refreshSessions();
        } catch (err) {
          toast(err.message || "Не удалось удалить", "error");
        }
      },
    }),
  );
}

export function baseName(dir) {
  const parts = String(dir || "").split(/[\\/]/).filter(Boolean);
  return parts[parts.length - 1] || "";
}

export async function createLocalSession(root, prompt = "") {
  const data = await rpc(
    window.clauden.code.create({
      repo: "ssh",
      label: baseName(root) || "Проект",
      target: "Этот компьютер",
      root,
      local: true,
      variant: defaultEffort(),
      connectors: connectorPayload(),
    }),
  );
  localStorage.setItem("cc:lastRoot", root);
  await rpc(window.clauden.local.setRoot(root)).catch(() => {});
  if (prompt && data?.id) {
    await rpc(window.clauden.code.send(data.id, prompt)).catch(() => {});
  }
  return data;
}

export async function openLocalSession(ctx) {
  let root = "";
  try {
    root = await rpc(window.clauden.local.pickFolder());
  } catch (err) {
    toast(err.message || "Не удалось выбрать папку", "error");
    return;
  }
  if (!root) return;

  toast(`Открываем ${baseName(root)}…`, "info", 60_000);
  try {
    const data = await createLocalSession(root, "");
    toast("Локальная сессия готова — агент работает с папкой на этом ПК");
    await ctx.refreshSessions();
    if (data?.id) ctx.openSession(data.id);
  } catch (err) {
    toast(err.message || "Не удалось открыть папку", "error");
  }
}

export async function openNewSession(ctx, { mode: initialMode = "local" } = {}) {
  let mode = initialMode;

  const folderInput = h("input", {
    readonly: true,
    placeholder: "Папка не выбрана",
    spellcheck: "false",
    value: localStorage.getItem("cc:lastRoot") || "",
  });
  const pickBtn = h("button", {
    class: "btn btn-secondary btn-sm",
    text: "Выбрать…",
    onclick: async () => {
      try {
        const dir = await rpc(window.clauden.local.pickFolder());
        if (dir) {
          folderInput.value = dir;
          errorBox.classList.add("hidden");
        }
      } catch (err) {
        showError(err.message || "Не удалось выбрать папку");
      }
    },
  });

  const repoSelect = h("select");
  const githubHint = h("div", { class: "field-hint", text: "" });
  const githubBtn = h("button", {
    class: "btn btn-secondary btn-sm",
    text: "GitHub",
    onclick: () =>
      openGithubDialog({
        onChanged: async (info) => {
          paintGithub(info);
          await loadRepoChoices();
        },
      }),
  });
  let githubInfo = state.github || { connected: false };
  const paintGithub = (info) => {
    githubInfo = info?.connected ? info : { connected: false };
    state.github = githubInfo;
    githubHint.textContent = githubInfo.connected
      ? `GitHub подключён как @${githubInfo.login || "аккаунт"} — репозитории в списке.`
      : "Вставьте GitHub-токен, чтобы выбрать свой репозиторий, или возьмите проект из реестра Clauden.";
    githubBtn.textContent = githubInfo.connected ? "Сменить GitHub" : "Подключить GitHub";
  };
  paintGithub(githubInfo);
  const branchInput = h("input", {
    placeholder: "ветка (необязательно)",
    spellcheck: "false",
  });
  const promptInput = h("textarea", {
    rows: 3,
    placeholder: "Что сделать? Например: почини сборку и прогони тесты",
  });
  const serverPrompt = h("textarea", {
    rows: 3,
    placeholder: "Что сделать? Например: добавь тесты на авторизацию",
  });
  const errorBox = h("div", { class: "modal-error hidden" });

  const showError = (message) => {
    errorBox.textContent = message;
    errorBox.classList.remove("hidden");
  };

  const localBlock = h(
    "div",
    {},
    h(
      "div",
      { class: "field" },
      h("label", { text: "Папка проекта на этом компьютере" }),
      h("div", { class: "folder-row" }, folderInput, pickBtn),
    ),
    h("div", {
      class: "field-hint",
      text: "Агент читает и правит файлы прямо здесь — ничего не загружается на сервер. Git, если он есть, тоже локальный.",
    }),
    h("div", { class: "field" }, h("label", { text: "Задача (необязательно)" }), promptInput),
  );

  const serverBlock = h(
    "div",
    {},
    h(
      "div",
      { class: "field" },
      h("label", { text: "Репозиторий" }),
      h("div", { class: "folder-row" }, repoSelect, githubBtn),
      githubHint,
    ),
    h("div", { class: "field" }, h("label", { text: "Ветка" }), branchInput),
    h("div", { class: "field" }, h("label", { text: "Задача" }), serverPrompt),
  );

  const localTab = h("button", {
    class: "seg-btn active",
    html: `${icon("laptop", 13)}<span>Этот компьютер</span>`,
    onclick: () => setMode("local"),
  });
  const serverTab = h("button", {
    class: "seg-btn",
    html: `${icon("server", 13)}<span>Сервер Clauden</span>`,
    onclick: () => setMode("server"),
  });
  const seg = h("div", { class: "seg" }, localTab, serverTab);

  const subtitle = h("p", { class: "modal-sub" });
  const connectorNote = h("div", {
    class: "field-hint",
    text: connectorPayload().length
      ? `MCP-коннекторов из настроек: ${connectorPayload().length}`
      : "MCP-коннекторы добавляются в настройках (шестерёнка на панели).",
  });

  const dialog = modal({
    title: "Новая сессия",
    subtitle: null,
    body: h("div", {}, seg, subtitle, connectorNote, localBlock, serverBlock, errorBox),
    actions: [
      { label: "Отмена", kind: "ghost", onClick: (close) => close() },
      {
        label: "Открыть папку",
        kind: "primary",
        onClick: async (close) => {
          errorBox.classList.add("hidden");
          const button = dialog.footer.querySelector(".btn-primary");

          if (mode === "local") {
            const root = folderInput.value.trim();
            const task = promptInput.value.trim();
            if (!root) {
              showError("Выберите папку проекта");
              return;
            }
            button.disabled = true;
            button.textContent = "Открываем…";
            try {
              const data = await createLocalSession(root, task);
              close();
              toast("Локальная сессия создана");
              await ctx.refreshSessions();
              if (data?.id) ctx.openSession(data.id);
            } catch (err) {
              showError(err.message || "Не удалось открыть папку");
              button.disabled = false;
              button.textContent = "Открыть папку";
            }
            return;
          }

          const repo = repoSelect.value;
          const task = serverPrompt.value.trim();
          if (!repo) {
            showError("Выберите репозиторий");
            return;
          }
          button.disabled = true;
          button.textContent = "Создаём…";
          try {
            const data = await rpc(
              window.clauden.code.create({
                repo,
                branch: branchInput.value.trim() || undefined,
                prompt: task || undefined,
                variant: defaultEffort(),
                connectors: connectorPayload(),
              }),
            );
            close();
            toast("Сессия создана");
            await ctx.refreshSessions();
            if (data?.id) ctx.openSession(data.id);
          } catch (err) {
            showError(err.message || "Не удалось создать сессию");
            button.disabled = false;
            button.textContent = "Создать";
          }
        },
      },
    ],
  });

  const primary = dialog.footer.querySelector(".btn-primary");

  function setMode(next) {
    mode = next;
    localTab.classList.toggle("active", next === "local");
    serverTab.classList.toggle("active", next === "server");
    localBlock.classList.toggle("hidden", next !== "local");
    serverBlock.classList.toggle("hidden", next !== "server");
    subtitle.textContent =
      next === "local"
        ? "Агент работает с папкой на этом компьютере: команды и файлы — локально."
        : "Агент работает в клоне репозитория на сервере Clauden.";
    primary.textContent = next === "local" ? "Открыть папку" : "Создать";
    errorBox.classList.add("hidden");
  }

  setMode(mode);

  async function loadRepoChoices() {
    repoSelect.replaceChildren(h("option", { value: "", text: "Загружаю репозитории…" }));
    let repos = state.repos || [];
    if (!repos.length) {
      repos = (await rpc(window.clauden.code.repos()).catch(() => [])) || [];
      state.repos = repos;
    }

    let info = state.github;
    try {
      info = await loadGithub({ refresh: true });
    } catch {
      info = state.github || { connected: false };
    }
    paintGithub(info);

    const githubRepos = info.connected
      ? await loadGithubRepos({ refresh: true }).catch(() => [])
      : [];

    repoSelect.replaceChildren();
    let count = 0;

    if (repos.length) {
      const group = document.createElement("optgroup");
      group.label = "Clauden";
      for (const repo of repos) {
        const id = repo.id || repo.repoId || repo.name || repo.path;
        const label = repo.name || repo.label || repo.fullName || id;
        group.append(h("option", { value: id, text: label }));
        count += 1;
      }
      repoSelect.append(group);
    }

    if (githubRepos.length) {
      const group = document.createElement("optgroup");
      group.label = `GitHub · @${info.login || "аккаунт"}`;
      for (const repo of githubRepos) {
        const id = repo.id || `gh:${repo.name}`;
        const label = `${repo.name}${repo.private ? " · private" : ""}`;
        group.append(h("option", { value: id, text: label }));
        count += 1;
      }
      repoSelect.append(group);
    }

    if (!count) {
      repoSelect.append(h("option", { value: "", text: "Нет доступных репозиториев" }));
    }
  }

  try {
    await loadRepoChoices();
  } catch (err) {
    showError(err.message || "Не удалось загрузить репозитории");
  }

  setTimeout(() => {
    if (mode === "local") {
      if (folderInput.value) promptInput.focus();
      else pickBtn.focus();
    } else {
      serverPrompt.focus();
    }
  }, 60);
  return dialog;
}
