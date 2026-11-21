import { h, icon, rpc, toast, clear } from "./ui.js";
import { state } from "./state.js";

let els = null;
let history = [];
let historyIndex = -1;
let running = false;
let current = null;
let offs = [];

export async function mountTerminal(host, ctx) {
  for (const off of offs) off?.();
  offs = [];

  const output = h("div", { class: "term" });
  const input = h("input", {
    class: "term-input",
    placeholder: "Команда на этой машине…",
    spellcheck: "false",
    autocomplete: "off",
  });
  const cwdChip = h("button", {
    class: "cwd-chip",
    title: "Выбрать папку",
    onclick: async () => {
      const dir = await rpc(window.clauden.term.pickFolder());
      if (dir) {
        state.cwd = dir;
        cwdChip.textContent = shortPath(dir);
        cwdChip.title = dir;
      }
    },
  });
  const shellChip = h("span", { class: "term-shell", title: "Оболочка" });
  const killBtn = h("button", {
    class: "icon-btn",
    title: "Остановить команду",
    html: icon("stop", 13),
    onclick: () => window.clauden.term.kill(),
  });

  const form = h(
    "form",
    {
      class: "term-form",
      onsubmit: (event) => {
        event.preventDefault();
        const command = input.value;
        input.value = "";
        run(command);
      },
    },
    h("span", { class: "term-prompt", text: "❯" }),
    input,
  );

  const head = h(
    "div",
    { class: "panel-head" },
    h("span", { class: "panel-tab active", text: "Терминал" }),
    cwdChip,
    shellChip,
    h("span", { class: "spacer" }),
    killBtn,
    h("button", {
      class: "icon-btn",
      title: "Очистить",
      html: icon("trash", 13),
      onclick: () => clear(output),
    }),
    h("button", {
      class: "icon-btn",
      title: "Скрыть панель (Ctrl+`)",
      html: icon("close", 13),
      onclick: () => ctx.toggleTerminal(false),
    }),
  );

  host.append(head, output, form);
  els = { host, output, input, cwdChip, killBtn };

  const cwd = await rpc(window.clauden.term.cwd());
  state.cwd = cwd || "";
  cwdChip.textContent = shortPath(state.cwd);
  cwdChip.title = state.cwd;

  try {
    const info = await rpc(window.clauden.local.shell());
    if (info?.kind) {
      shellChip.textContent = info.kind;
      shellChip.title = `Оболочка: ${info.path || info.kind}`;
    }
  } catch {

  }

  input.addEventListener("keydown", (event) => {
    if (event.key === "ArrowUp") {
      event.preventDefault();
      if (!history.length) return;
      historyIndex = historyIndex <= 0 ? history.length - 1 : historyIndex - 1;
      input.value = history[historyIndex];
    } else if (event.key === "ArrowDown") {
      event.preventDefault();
      if (!history.length) return;
      historyIndex = historyIndex >= history.length - 1 ? 0 : historyIndex + 1;
      input.value = history[historyIndex];
    }
  });

  offs.push(window.clauden.term.onStart((payload) => {
    running = true;
    current = payload.id;
    killBtn.classList.remove("hidden");
    write(`\n❯ ${payload.command}\n`, "cmd");
    if (payload.cwd !== state.cwd) {
      state.cwd = payload.cwd;
      cwdChip.textContent = shortPath(payload.cwd);
      cwdChip.title = payload.cwd;
    }
  }));

  offs.push(window.clauden.term.onData((payload) => {
    if (payload.id !== current) return;
    write(payload.text, payload.stream === "stderr" ? "err" : "");
  }));

  offs.push(window.clauden.term.onExit((payload) => {
    if (payload.id !== current) return;
    running = false;
    killBtn.classList.add("hidden");
    write(`\n[код ${payload.code}]\n`, "meta");
  }));

  offs.push(window.clauden.term.onCwd((payload) => {
    if (!payload?.cwd) return;
    state.cwd = payload.cwd;
    cwdChip.textContent = shortPath(payload.cwd);
    cwdChip.title = payload.cwd;
  }));

  output.append(
    h(
      "div",
      { class: "meta" },
      "Локальный терминал — команды выполняются на этой машине, не на сервере.",
    ),
  );
}

function shortPath(dir) {
  if (!dir) return "~";
  const parts = String(dir).split(/[\\/]/).filter(Boolean);
  if (parts.length <= 2) return dir;
  return `…/${parts.slice(-2).join("/")}`;
}

function write(text, kind = "") {
  if (!els) return;
  const span = h("span", { class: kind, text });
  els.output.append(span);
  els.output.scrollTop = els.output.scrollHeight;
}

async function run(command) {
  if (!command || !command.trim()) return;
  if (running) {
    toast("Дождитесь завершения или остановите команду", "error");
    return;
  }
  history.push(command.trim());
  if (history.length > 100) history.shift();
  historyIndex = history.length;

  try {
    const result = await rpc(window.clauden.term.run({ command }));
    if (!result?.id) {
      write("Не удалось запустить команду\n", "err");
    }
  } catch (err) {
    write(`${err.message}\n`, "err");
  }
}

export function focusTerminal() {
  els?.input.focus();
}
