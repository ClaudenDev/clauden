import http from "node:http";

const PORT = Number(process.env.MOCK_PORT || 8899);
const WANT_PERMISSION = process.env.MOCK_PERMISSION !== "0";
const TOKEN = "mocktoken0123456789";

const sessions = new Map();
let seq = 0;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function publicView(session) {
  const last = session.messages[session.messages.length - 1];
  return {
    id: session.id,
    source: session.source || "git",
    repo: session.repo,
    repoName: session.repoName,
    branch: session.branch,
    title: session.title,
    status: session.status,
    error: session.error || "",
    createdAt: session.createdAt,
    updatedAt: session.updatedAt,
    messageCount: session.messages.length,
    lastText: last?.text || "",
    actions: [],
    todos: [],
    forms: session.forms,
    permissions: session.permissions,
    model: "clauden-code",
    steps: session.steps,
    remote: session.remote || null,
  };
}

function fullView(session) {
  return {
    ...publicView(session),
    messages: session.messages.map((message) => ({
      id: message.id,
      role: message.role,
      text: message.text,
      ts: message.ts,
      steps: message.steps || [],
    })),
  };
}

function broadcast(session, type, data) {
  for (const res of session.subscribers) {
    try {
      res.write(`event: ${type}\ndata: ${JSON.stringify(data)}\n\n`);
    } catch {

    }
  }
}

function addStep(session, step) {
  const existing = session.steps.find((item) => item.id === step.id);
  if (existing) Object.assign(existing, step);
  else session.steps.push(step);
  broadcast(session, "step", session.steps.find((item) => item.id === step.id));
}

async function runTurn(session, text) {
  session.status = "working";
  session.steps = [];
  broadcast(session, "status", { status: "working" });
  await sleep(40);

  if (session.source === "ssh") {
    addStep(session, { id: "s1", title: "Команда на компьютере", status: "running" });
    const jobId = `job_${++seq}`;
    session.lastJob = { id: jobId, result: null };
    broadcast(session, "job", {
      id: jobId,
      status: "queued",
      command: "printf 'привет из терминала'",
      timeout: 20,
    });
    const result = await new Promise((resolve) => {
      session.jobWaiter = resolve;
      setTimeout(() => resolve({ code: -1, stdout: "", stderr: "таймаут" }), 10_000);
    });
    session.lastJob.result = result;
    addStep(session, { id: "s1", title: "Команда на компьютере", status: "done" });
    broadcast(session, "delta", {
      text: `Команда вернула: \`${String(result.stdout || "").trim()}\` (код ${result.code}).`,
    });
    session.messages.push({
      id: `m${++seq}`,
      role: "assistant",
      text: `Готово. Локальная команда: ${String(result.stdout || "").trim() || "без вывода"}.`,
      ts: Date.now(),
      steps: [],
    });
    session.status = "idle";
    session.updatedAt = Date.now();
    broadcast(session, "status", { status: "idle" });
    return;
  }

  addStep(session, { id: "s1", title: "Читаю файлы", status: "running" });
  await sleep(40);
  addStep(session, { id: "s1", title: "Читаю файлы", status: "done" });

  const first = "Смотрю репозиторий и готовлю правку. ";
  broadcast(session, "delta", { text: first });
  await sleep(40);
  broadcast(session, "delta", { text: `Задача: ${text}.` });

  if (WANT_PERMISSION && !session.askedPermission) {
    session.askedPermission = true;
    const request = { id: "perm_1", action: "edit", resources: [{ path: "src/app.js" }] };
    session.permissions = [request];
    broadcast(session, "permissions", session.permissions);
    await new Promise((resolve) => {
      session.permissionWaiter = resolve;
      setTimeout(resolve, 4000);
    });
    session.permissions = [];
    broadcast(session, "permissions", []);
  }

  addStep(session, { id: "s2", title: "Правлю код", status: "running" });
  await sleep(40);
  addStep(session, { id: "s2", title: "Правлю код", status: "done" });

  const answer = `Готово. Изменил ${session.repoName} и проверил сборку.`;
  session.messages.push({ id: `m${++seq}`, role: "assistant", text: answer, ts: Date.now(), steps: [] });
  session.status = "idle";
  session.updatedAt = Date.now();
  broadcast(session, "status", { status: "idle" });
}

function createSession({ repo, prompt, body = {} }) {
  const id = `cs_mock${++seq}`;
  const isLocal = String(repo) === "ssh";
  const session = {
    id,
    source: isLocal ? "ssh" : "git",
    repo: repo || "demo",
    repoName: body.label || repo || "demo",
    branch: isLocal ? "" : "main",
    title: body.label || String(prompt || "Без названия").slice(0, 60),
    status: "idle",
    error: "",
    createdAt: Date.now(),
    updatedAt: Date.now(),
    messages: prompt
      ? [{ id: `m${++seq}`, role: "user", text: prompt, ts: Date.now() }]
      : [{ id: `m${++seq}`, role: "assistant", text: "Локальная сессия готова.", ts: Date.now() }],
    steps: [],
    permissions: [],
    forms: [],
    subscribers: new Set(),
    askedPermission: false,
    remote: isLocal
      ? {
          label: body.label || "Проект",
          target: body.target || "Этот компьютер",
          root: body.root || "",
          local: body.local !== false,
        }
      : null,
  };
  sessions.set(id, session);
  return session;
}

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json",
    "content-length": Buffer.byteLength(body),
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
    });
    req.on("end", () => {
      try {
        resolve(JSON.parse(raw || "{}"));
      } catch {
        resolve({});
      }
    });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`);
  const path = url.pathname;

  if (path === "/v1/auth/email" && req.method === "POST") {
    const body = await readBody(req);
    if (!body.email || !body.password) {
      return sendJson(res, 400, { error: { message: "Нужны email и пароль" } });
    }
    if (body.password !== "secret") {
      return sendJson(res, 401, { error: { message: "Неверный email или пароль" } });
    }
    return sendJson(res, 200, {
      token: TOKEN,
      user: { id: "u1", email: body.email, name: "Тест" },
    });
  }
  if (path === "/v1/auth/me" && req.method === "GET") {
    if (req.headers.authorization !== `Bearer ${TOKEN}`) {
      return sendJson(res, 401, { error: { message: "Не авторизован", type: "unauthorized" } });
    }
    return sendJson(res, 200, { user: { id: "u1", email: "dev@clauden.best", name: "Тест" } });
  }
  if (path === "/v1/usage" && req.method === "GET") {
    if (req.headers.authorization !== `Bearer ${TOKEN}`) {
      return sendJson(res, 401, { error: { message: "Не авторизован", type: "unauthorized" } });
    }
    return sendJson(res, 200, {
      usage: {
        plan: "Pro",
        planLabel: "Pro",
        session: { percent: 37, resetsAt: Date.now() + 2.4 * 60 * 60 * 1000 },
        weekly: { percent: 12, resetsAt: Date.now() + 3 * 24 * 60 * 60 * 1000 },
      },
      webTrial: null,
    });
  }
  if (path === "/v1/code/repos" && req.method === "GET") {
    return sendJson(res, 200, {
      data: [
        { id: "demo", name: "demo", defaultBranch: "main", description: "Демо-проект", hasPush: true },
        { id: "site", name: "site", defaultBranch: "main", description: "Сайт", hasPush: false },
      ],
    });
  }
  if (path === "/v1/code/sessions" && req.method === "GET") {
    const list = [...sessions.values()].map(publicView);
    return sendJson(res, 200, { data: list });
  }
  if (path === "/v1/code/sessions" && req.method === "POST") {
    const body = await readBody(req);
    const session = createSession({ repo: body.repo, prompt: body.prompt, body });

    if (body.repo !== "ssh" && body.prompt) {
      setTimeout(() => runTurn(session, body.prompt || ""), 20);
    }
    return sendJson(res, 200, { data: fullView(session) });
  }

  const jobMatch = path.match(/^\/v1\/code\/sessions\/([^/]+)\/device\/jobs\/([^/]+)$/);
  if (jobMatch && req.method === "POST") {
    const body = await readBody(req);
    const session = sessions.get(jobMatch[1]);
    if (!session) {
      return sendJson(res, 404, { error: { message: "Сессия не найдена", type: "session_not_found" } });
    }
    session.jobResults = session.jobResults || {};
    session.jobResults[jobMatch[2]] = body;
    session.jobWaiter?.(body);
    return sendJson(res, 200, { data: { ok: true } });
  }

  const match = path.match(/^\/v1\/code\/sessions\/([^/]+)(?:\/(.+))?$/);
  if (match) {
    const [, id, action] = match;
    const session = sessions.get(id);
    if (!session) {
      return sendJson(res, 404, { error: { message: "Сессия не найдена", type: "session_not_found" } });
    }
    if (!action && req.method === "GET") return sendJson(res, 200, { data: fullView(session) });

    if (action === "events" && req.method === "GET") {
      res.writeHead(200, {
        "content-type": "text/event-stream; charset=utf-8",
        "cache-control": "no-cache",
        connection: "keep-alive",
      });
      res.write(`event: snapshot\ndata: ${JSON.stringify(fullView(session))}\n\n`);
      session.subscribers.add(res);
      req.on("close", () => session.subscribers.delete(res));
      return;
    }
    if (action === "messages" && req.method === "POST") {
      const body = await readBody(req);
      session.messages.push({ id: `m${++seq}`, role: "user", text: body.text, ts: Date.now() });
      session.updatedAt = Date.now();
      setTimeout(() => runTurn(session, body.text), 20);
      return sendJson(res, 200, { data: fullView(session) });
    }
    if (action === "stop" && req.method === "POST") {
      session.status = "idle";
      broadcast(session, "status", { status: "idle" });
      return sendJson(res, 200, { data: fullView(session) });
    }
    if (action === "link" && req.method === "POST") {
      const body = await readBody(req);
      const minutes = Number(body.ttlMinutes) || 1440;
      const token = `mock${Date.now().toString(36)}${"0".repeat(10)}`.slice(0, 36);
      return sendJson(res, 200, {
        data: {
          token,
          url: `http://127.0.0.1:${PORT}/r/${token}`,
          sessionId: id,
          createdAt: Date.now(),
          expiresAt: Date.now() + minutes * 60 * 1000,
        },
      });
    }
    if (action === "link" && req.method === "GET") return sendJson(res, 200, { data: null });
    if (action === "link" && req.method === "DELETE") {
      return sendJson(res, 200, { data: { ok: true, removed: 1 } });
    }
    if (action === "diff" && req.method === "GET") {
      return sendJson(res, 200, {
        data: {
          dirty: true,
          files: [{ status: "M", path: "src/app.js", additions: 4, deletions: 1 }],
          patch: "--- a/src/app.js\n+++ b/src/app.js\n@@ -1,2 +1,3 @@\n-old line\n+new line\n+added line\n",
          truncated: false,
          untracked: [],
        },
      });
    }
    if (action === "files" && req.method === "GET") {
      return sendJson(res, 200, {
        data: {
          path: url.searchParams.get("path") || "",
          entries: [
            { name: "src", path: "src", type: "dir" },
            { name: "package.json", path: "package.json", type: "file", size: 512 },
          ],
        },
      });
    }
    const permissionMatch = action?.match(/^permissions\/(.+)$/);
    if (permissionMatch && req.method === "POST") {
      const body = await readBody(req);
      session.permissionAnswer = body.decision;
      session.permissionWaiter?.();
      return sendJson(res, 200, { data: fullView(session) });
    }
    const formMatch = action?.match(/^forms\/(.+)$/);
    if (formMatch && req.method === "POST") {
      const body = await readBody(req);
      session.formAnswer = body.answer;
      return sendJson(res, 200, { data: fullView(session) });
    }
  }

  return sendJson(res, 404, { error: { message: `Нет маршрута ${req.method} ${path}` } });
});

server.listen(PORT, "127.0.0.1", () => {
  process.stdout.write(`mock clauden api on http://127.0.0.1:${PORT}\n`);
});
