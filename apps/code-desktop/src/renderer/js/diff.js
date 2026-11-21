import { h, icon, rpc, toast, clear } from "./ui.js";
import { state } from "./state.js";
import { ws } from "./ws.js";

export function renderDiff(host, ctx) {
  clear(host);
  const data = state.diff;

  if (!data) {
    host.append(
      h(
        "div",
        { class: "diff-empty" },
        h("div", { html: icon("diff", 22), style: { opacity: ".5", marginBottom: "10px" } }),
        h("div", { text: "Считаем изменения…" }),
      ),
    );
    return;
  }

  const files = data.files || [];
  const totalAdd = files.reduce((sum, file) => sum + (file.additions || 0), 0);
  const totalDel = files.reduce((sum, file) => sum + (file.deletions || 0), 0);

  const commitInput = h("input", { placeholder: "Сообщение коммита", spellcheck: "false" });
  const commitBtn = h(
    "button",
    {
      class: "btn btn-secondary btn-sm",
      text: "Коммит",
      onclick: async () => {
        const message = commitInput.value.trim();
        if (!message) {
          toast("Введите сообщение коммита", "error");
          return;
        }
        commitBtn.disabled = true;
        commitBtn.textContent = "Коммитим…";
        try {
          await ws.commit(message);
          toast("Изменения закоммичены");
          commitInput.value = "";
          await ctx.refreshDiff();
        } catch (err) {
          toast(err.message || "Не удалось закоммитить", "error");
        } finally {
          commitBtn.disabled = false;
          commitBtn.textContent = "Коммит";
        }
      },
    },
  );

  const head = h(
    "div",
    { class: "diff-head" },
    h("span", { html: icon("diff", 14), style: { display: "flex", color: "var(--ink-3)" } }),
    h(
      "span",
      { class: "diff-summary" },
      files.length ? `${files.length} ${plural(files.length)}` : "Изменений нет",
      files.length
        ? h(
            "span",
            {},
            " · ",
            h("span", { class: "add", text: `+${totalAdd}` }),
            " ",
            h("span", { class: "del", text: `−${totalDel}` }),
          )
        : null,
    ),
    h(
      "div",
      { class: "diff-commit" },
      commitInput,
      commitBtn,
      h("button", {
        class: "icon-btn",
        title: "Обновить",
        html: icon("refresh", 14),
        onclick: () => ctx.refreshDiff(),
      }),
    ),
  );

  const body = h("div", { class: "diff-body" });

  if (!files.length) {
    body.append(
      h(
        "div",
        { class: "diff-empty" },
        h("div", {
          text: data.notGit
            ? "Папка не является git-репозиторием — изменения не отслеживаются. Файлы и терминал работают."
            : "Рабочая папка чистая — агент пока ничего не изменил.",
        }),
      ),
    );
  } else {
    const sections = parsePatch(data.patch || "");
    const byPath = new Map(sections.map((section) => [section.path, section]));

    for (const file of files) {
      const section = byPath.get(file.path) || byPath.get(stripPrefix(file.path));
      const block = h("div", { class: "diff-file", id: `df-${hash(file.path)}` });

      block.append(
        h(
          "div",
          { class: "diff-file-head" },
          h("span", { class: `st ${file.status}`, text: file.status }),
          h("span", { text: file.path }),
          h(
            "span",
            { class: "nums" },
            file.additions ? h("span", { class: "add", style: { color: "var(--green)" }, text: `+${file.additions}` }) : null,
            file.deletions ? h("span", { class: "del", style: { color: "var(--red)" }, text: `−${file.deletions}` }) : null,
          ),
        ),
      );

      if (section?.lines?.length) {
        for (const line of section.lines) block.append(diffLine(line));
      } else {
        block.append(
          h(
            "div",
            { class: "diff-line meta" },
            h("span", { class: "ln", text: "" }),
            h("span", { class: "txt", text: "Diff для этого файла недоступен — бинарный файл или слишком большой объём" }),
          ),
        );
      }
      body.append(block);
    }

    if (data.truncated) {
      body.append(
        h("div", { class: "diff-empty", text: "Diff обрезан — слишком большой объём изменений." }),
      );
    }
  }

  host.append(h("div", { class: "diff" }, head, body));
}

function diffLine(line) {
  const label =
    line.type === "add" ? "+" : line.type === "del" ? "−" : line.type === "hunk" ? "" : " ";
  const number = line.type === "del" ? line.oldNo : line.newNo;
  return h(
    "div",
    { class: `diff-line ${line.type}` },
    h("span", { class: "ln", text: number ? String(number) : "" }),
    h("span", { class: "txt", text: `${label}${line.type === "hunk" ? line.text : line.text}` }),
  );
}

function stripPrefix(path) {
  return String(path || "").replace(/^[ab]\//, "");
}

function hash(text) {
  let value = 0;
  for (let i = 0; i < text.length; i += 1) value = (value * 31 + text.charCodeAt(i)) | 0;
  return Math.abs(value).toString(36);
}

function plural(count) {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return "файл";
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "файла";
  return "файлов";
}

export function parsePatch(patch) {
  const sections = [];
  let current = null;
  let oldNo = 0;
  let newNo = 0;

  for (const raw of String(patch).split("\n")) {
    if (raw.startsWith("diff --git ")) {
      const match = raw.match(/^diff --git a\/(.+?) b\/(.+)$/);
      current = { path: match ? match[2] : raw.slice(11), lines: [] };
      sections.push(current);
      continue;
    }
    if (raw.startsWith("+++ ") || raw.startsWith("--- ") || raw.startsWith("index ")) continue;
    if (!current) continue;

    if (raw.startsWith("@@")) {
      const match = raw.match(/@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)$/);
      if (match) {
        oldNo = Number(match[1]);
        newNo = Number(match[2]);
      }
      current.lines.push({ type: "hunk", text: raw, oldNo: 0, newNo: 0 });
      continue;
    }
    if (raw.startsWith("+")) {
      current.lines.push({ type: "add", text: raw.slice(1), oldNo: 0, newNo: newNo++ });
      continue;
    }
    if (raw.startsWith("-")) {
      current.lines.push({ type: "del", text: raw.slice(1), oldNo: oldNo++, newNo: 0 });
      continue;
    }
    if (raw.startsWith("\\")) continue;
    current.lines.push({ type: "ctx", text: raw.slice(1), oldNo: oldNo++, newNo: newNo++ });
  }

  return sections;
}

export async function refreshDiff(ctx, { silent = false } = {}) {
  if (!state.session) return null;
  try {
    const data = await ws.diff();
    state.diff = data;
    state.changed = new Map((data?.files || []).map((file) => [file.path, file]));
    if (!silent) {
      ctx.renderTabs();
      ctx.renderWork();
      ctx.renderSide();
    } else {
      ctx.renderTabs();
    }
    return data;
  } catch (err) {
    if (!silent) toast(err.message || "Не удалось получить diff", "error");
    return null;
  }
}
