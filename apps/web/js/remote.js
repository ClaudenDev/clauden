import { el, clear, icon, toast, escapeHtml, relTimeShort } from "./util.js";
import { remoteClient, streamRemote } from "./api.js";
import { renderMarkdown, enhanceMarkdown } from "./markdown.js";

const STATUS_LABEL = {
  idle: "Готов",
  working: "Работает",
  failed: "Ошибка",
  interrupted: "Прервано",
};

export async function renderRemote(view, ctx, token) {
  document.body.classList.add("remote-mode");
  const shell = el("div", { class: "remote-shell" });
  view.append(shell);

  const head = el("div", { class: "remote-head" });
  const feed = el("div", { class: "remote-feed" });
  const composer = el("div", { class: "remote-composer" });
  shell.append(head, feed, composer);

  let snapshot = null;
  let status = "idle";
  let link = null;
  let closed = false;
  let controller = null;
  let pending = false;
  let lastRender = 0;
  let timer = 0;

  const textarea = el("textarea", { rows: "1", placeholder: "Сообщение агенту…" });
  const sendBtn = el("button", { class: "remote-send", title: "Отправить", html: icon("arrowUp") });
  const stopBtn = el("button", { class: "remote-stop", title: "Остановить", text: "Стоп", hidden: true });

  composer.append(
    el("div", { class: "remote-field" },
      textarea,
      el("div", { class: "remote-field-bar" },
        el("span", { class: "remote-hint", text: "Remote Control" }),
        el("span", { class: "spacer" }),
        stopBtn,
        sendBtn,
      ),
    ),
  );

  function drawHead() {
    clear(head);
    const title = snapshot?.session?.title || link?.label || "Сессия Clauden Code";
    const repo = snapshot?.session?.repoName || snapshot?.session?.repo || "";
    const branch = snapshot?.session?.branch ? ` · ${snapshot.session.branch}` : "";
    head.append(
      el("div", { class: "remote-brand" },
        el("span", { class: "remote-logo", html: icon("terminal") }),
        el("span", { class: "remote-brand-text", text: "Clauden Remote" }),
      ),
      el("div", { class: "remote-head-main" },
        el("div", { class: "remote-title", text: title }),
        el("div", { class: `remote-status ${status === "working" ? "working" : status === "failed" ? "failed" : ""}` },
          el("span", { class: "dot" }),
          el("span", { text: `${STATUS_LABEL[status] || status}${repo ? ` · ${repo}${branch}` : ""}` }),
        ),
      ),
      el("button", {
        class: "remote-icon-btn",
        title: "Обновить",
        onclick: () => refresh(true),
        html: icon("refresh"),
      }),
    );
  }

  function drawFeed() {
    clear(feed);
    if (!snapshot) {
      feed.append(el("div", { class: "remote-loading" }, el("span", { class: "spinner" }), "Подключаюсь…"));
      return;
    }
    const session = snapshot.session;
    if (!session.messages?.length) {
      feed.append(el("div", { class: "remote-empty" },
        el("div", { class: "remote-empty-title", text: "Агент пока молчит" }),
        el("div", { class: "remote-empty-sub", text: "Напишите задачу — сессия начнёт работу, а шаги появятся здесь." }),
      ));
    }
    for (const message of session.messages || []) feed.append(messageNode(message));

    for (const permission of session.permissions || []) feed.append(permissionCard(permission));
    for (const form of session.forms || []) {
      if (!isTrivialForm(form)) feed.append(formCard(form));
    }

    if (status === "working") {
      feed.append(el("div", { class: "remote-working" },
        el("span", { class: "remote-pulse" }),
        el("span", { text: "Агент работает…" }),
      ));
    }
    if (session.error) feed.append(el("div", { class: "remote-error", text: session.error }));
    scrollBottom();
  }

  function messageNode(message) {
    if (message.role === "user") {
      return el("div", { class: "remote-msg user" },
        el("div", { class: "remote-bubble", text: message.text || "" }),
      );
    }
    const node = el("div", { class: "remote-msg assistant" });
    const md = el("div", { class: "md", html: renderMarkdown(message.text || "") });
    enhanceMarkdown(md);
    node.append(md);
    if (message.steps?.length) node.append(stepsBlock(message.steps));
    return node;
  }

  function stepsBlock(steps) {
    const done = steps.filter((s) => s.status === "done").length;
    const failed = steps.some((s) => s.status === "failed");
    const list = el("div", { class: "remote-steps-list", hidden: true });
    for (const step of steps) {
      list.append(el("div", { class: `remote-step ${step.status || ""}` },
        el("span", { class: "remote-step-ico", html: icon(stepIcon(step)) }),
        el("span", { class: "remote-step-title", text: step.title || step.name || "Шаг" }),
        step.detail ? el("span", { class: "remote-step-detail", text: step.detail }) : null,
      ));
    }
    const toggle = el("button", {
      class: `remote-steps ${failed ? "failed" : ""}`,
      onclick: () => {
        list.hidden = !list.hidden;
        toggle.classList.toggle("open", !list.hidden);
      },
    },
    el("span", { class: "remote-step-ico", html: icon(failed ? "alert" : "check") }),
    el("span", { text: `Действия · ${done}/${steps.length}` }),
    el("span", { class: "remote-steps-chev", html: icon("chevronDown") }),
    );
    return el("div", { class: "remote-steps-wrap" }, toggle, list);
  }

  function stepIcon(step) {
    const name = String(step?.name || "").toLowerCase();
    if (step.status === "failed") return "alert";
    if (["bash", "shell"].includes(name)) return "terminal";
    if (["websearch", "web_search", "webfetch"].includes(name)) return "globe";
    if (["read", "write", "edit", "glob", "grep"].includes(name)) return "file";
    if (step.status === "running") return "clock";
    return "check";
  }

  function permissionCard(permission) {
    const card = el("div", { class: "remote-ask" });
    card.append(
      el("div", { class: "remote-ask-title", html: `${icon("shield")}<span>Агент просит разрешение</span>` }),
      el("div", { class: "remote-ask-code", text: permission.action || permission.title || "Инструмент" }),
    );
    const act = async (decision) => {
      try {
        await remoteClient.permission(token, permission.id, decision);
        toast(decision === "reject" ? "Отклонено" : "Разрешено");
        await refresh(true);
      } catch (err) {
        toast(err.message || "Не удалось ответить", "error");
      }
    };
    card.append(el("div", { class: "remote-ask-actions" },
      el("button", { class: "btn btn-ghost btn-sm", text: "Отклонить", onclick: () => act("reject") }),
      el("button", { class: "btn btn-secondary btn-sm", text: "Всегда", onclick: () => act("always") }),
      el("button", { class: "btn btn-primary btn-sm", text: "Разрешить", onclick: () => act("once") }),
    ));
    return card;
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

  function formCard(form) {
    const card = el("div", { class: "remote-ask" });
    const formTitle = /^questions?$/i.test(String(form.title || "").trim())
      ? "Вопрос агента"
      : form.title || "Агент ждёт ответа";
    card.append(el("div", { class: "remote-ask-title", html: `${icon("chat")}<span>${escapeHtml(formTitle)}</span>` }));
    const message = String(form.metadata?.message || "").trim();
    if (message) card.append(el("div", { class: "remote-ask-question", text: message }));
    const inputs = {};
    for (const field of form.fields || []) {
      const id = field.key || field.id || field.name;
      if (!id) continue;
      const label = field.title || field.label || id;
      const question = String(field.description || "").trim();

      if (question && question !== label) {
        card.append(el("div", { class: "remote-ask-question", text: question }));
      }
      const slot = {};
      const hasOptions = Array.isArray(field.options) && field.options.length > 0;
      const optionText = (opt) => String(typeof opt === "string" ? opt : opt?.label ?? opt?.value ?? "").trim();
      const onlyNothing = hasOptions && field.options.every((opt) => /^(nothing|no action|none|нет|ничего)$/i.test(optionText(opt)));
      const showOptions = hasOptions && !onlyNothing;
      if (showOptions) {
        slot.select = el("select", { class: "select" },
          ...field.options.map((opt) => {
            if (typeof opt === "string") return el("option", { value: opt, text: opt });
            return el("option", {
              value: opt.value ?? opt.label,
              text: opt.label ?? opt.value,
              title: opt.description || null,
            });
          }));
      }

      if (!showOptions || field.custom) {
        slot.custom = el("input", {
          class: "input",
          placeholder: showOptions ? "Свой ответ" : field.placeholder || "Ответ",
        });
      }
      inputs[id] = slot;
      const placeholderHeader = /^(no action|nothing|question|questions|вопрос|ответ)$/i.test(label.trim());
      if (!(placeholderHeader && question)) {
        card.append(el("label", { class: "remote-field-label", text: label }));
      }
      if (slot.select) card.append(slot.select);
      if (slot.custom) card.append(slot.custom);
    }
    card.append(el("button", { class: "btn btn-primary btn-sm", text: "Ответить", onclick: async () => {
      const answer = {};
      for (const [key, slot] of Object.entries(inputs)) {
        const custom = String(slot.custom?.value || "").trim();
        answer[key] = custom || slot.select?.value || "";
      }
      try {
        await remoteClient.form(token, form.id, answer);
        toast("Ответ отправлен");
        await refresh(true);
      } catch (err) {
        toast(err.message || "Не удалось ответить", "error");
      }
    }}));
    return card;
  }

  function scrollBottom() {
    requestAnimationFrame(() => { feed.scrollTop = feed.scrollHeight; });
  }

  async function refresh(force = false) {
    if (closed) return;
    const now = Date.now();
    if (!force && now - lastRender < 350) {
      clearTimeout(timer);
      timer = setTimeout(() => refresh(true), 360);
      return;
    }
    lastRender = now;
    try {
      const result = await remoteClient.snapshot(token);
      applySnapshot(result.data);
    } catch (err) {
      clear(feed);
      feed.append(el("div", { class: "remote-empty" },
        el("div", { class: "remote-empty-title", text: err.status === 404 ? "Ссылка недействительна" : "Не удалось подключиться" }),
        el("div", { class: "remote-empty-sub", text: err.status === 404 ? "Попросите новую ссылку: /remote-control в сессии." : err.message || "" }),
      ));
    }
  }

  function applySnapshot(data) {
    if (!data?.session) return;
    snapshot = data;
    link = data.link;
    status = data.session.status || "idle";
    drawHead();
    drawFeed();
    updateComposer();
  }

  function updateComposer() {
    const working = status === "working";
    textarea.disabled = working || pending;
    sendBtn.disabled = working || pending;
    stopBtn.hidden = !working;
    textarea.placeholder = working ? "Агент работает…" : "Сообщение агенту…";
  }

  async function send() {
    const text = textarea.value.trim();
    if (!text || status === "working" || pending) return;
    pending = true;
    textarea.value = "";
    updateComposer();
    try {
      await remoteClient.send(token, text);
      await refresh(true);
    } catch (err) {
      toast(err.message || "Не удалось отправить", "error");
      textarea.value = text;
    } finally {
      pending = false;
      updateComposer();
    }
  }

  sendBtn.addEventListener("click", send);
  stopBtn.addEventListener("click", async () => {
    try {
      await remoteClient.stop(token);
      toast("Останавливаю");
      await refresh(true);
    } catch (err) {
      toast(err.message || "Не удалось остановить", "error");
    }
  });
  textarea.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  });
  textarea.addEventListener("input", () => {
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(textarea.scrollHeight, 160)}px`;
  });

  drawHead();
  drawFeed();
  updateComposer();
  await refresh(true);

  const connect = async () => {
    if (closed) return;
    controller?.abort();
    controller = new AbortController();
    try {
      await streamRemote(token, {
        signal: controller.signal,
        handlers: {
          onEvent: (payload, event) => {
            if (closed) return;
            if (event === "snapshot") {
              applySnapshot(payload);
              return;
            }
            if (event === "delta") {
              const last = snapshot?.session?.messages?.slice(-1)[0];
              if (last && last.role === "assistant") {
                last.text = `${last.text || ""}${payload?.text || ""}`;
                const nodes = feed.querySelectorAll(".remote-msg.assistant .md");
                const node = nodes[nodes.length - 1];
                if (node) {
                  node.innerHTML = renderMarkdown(last.text);
                  enhanceMarkdown(node);
                  scrollBottom();
                }
                return;
              }
            }
            refresh();
          },
        },
      });
    } catch {

    }
    if (!closed) setTimeout(connect, 2500);
  };
  connect();

  window.addEventListener("beforeunload", () => { closed = true; controller?.abort(); });
}
