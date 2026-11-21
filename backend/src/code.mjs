import { execFile } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

import { DocStore, newId } from "./store.mjs";

const IGNORED = new Set([".git", "node_modules", ".venv", "__pycache__", "dist", "build"]);

export class CodeManager {
  constructor({ config, pool, registry, log }) {
    this.config = config;
    this.pool = pool;
    this.registry = registry;
    this.log = log;
    this.docs = new DocStore(path.join(config.dataDir, "code-sessions"));
    this.workRoot = path.join(config.dataDir, "workspace");
    this.listeners = new Map();
    fs.mkdirSync(this.workRoot, { recursive: true });
  }

  get sessions() {
    return new Map(this.docs.list().map((session) => [session.id, session]));
  }

  #emit(sessionId, type, data) {
    const set = this.listeners.get(sessionId);
    if (!set) return;
    for (const listener of set) listener({ type, data });
  }

  subscribe(id, listener) {
    const set = this.listeners.get(id) || new Set();
    set.add(listener);
    this.listeners.set(id, set);
    return () => {
      set.delete(listener);
      if (!set.size) this.listeners.delete(id);
    };
  }

  list(userId) {
    return this.docs
      .list()
      .filter((session) => session.userId === userId)
      .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
      .map(summary);
  }

  get(userId, id) {
    const session = this.docs.get(id);
    return session && session.userId === userId ? session : null;
  }

  full(userId, id) {
    const session = this.get(userId, id);
    return session ? summary(session) : null;
  }

  create(userId, body = {}) {
    const id = newId("cs");
    const dir = path.join(this.workRoot, id);
    fs.mkdirSync(dir, { recursive: true });
    const session = {
      id,
      userId,
      title: String(body.title || "Новая сессия").slice(0, 200),
      model: this.registry.normalize(body.model) || this.registry.list()[0].id,
      repo: String(body.repo || ""),
      repoName: body.repoName ? String(body.repoName) : "",
      branch: String(body.branch || "main"),
      status: "idle",
      dir,
      messages: [],
      todos: [],
      permissions: [],
      forms: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    this.docs.put(id, session);
    return summary(session);
  }

  rename(userId, id, title) {
    const session = this.get(userId, id);
    if (!session) return null;
    session.title = String(title || session.title).slice(0, 200);
    session.updatedAt = Date.now();
    this.docs.put(id, session);
    this.#emit(id, "session", { title: session.title });
    return summary(session);
  }

  remove(userId, id) {
    const session = this.get(userId, id);
    if (!session) return false;
    this.docs.delete(id);
    try {
      fs.rmSync(session.dir, { recursive: true, force: true });
    } catch {

    }
    return true;
  }

  async send(userId, id, body = {}) {
    const session = this.get(userId, id);
    if (!session) return null;
    const text = String(body.text || body.message || "").trim();
    if (!text) return { ok: false };
    if (session.status === "running") {
      throw httpError(409, "Сессия уже отвечает");
    }
    const userMessage = { id: newId("msg"), role: "user", text, at: Date.now() };
    session.messages.push(userMessage);
    session.status = "running";
    session.updatedAt = Date.now();
    this.docs.put(id, session);
    this.#emit(id, "message", userMessage);
    this.#emit(id, "status", { status: "running" });

    const assistant = { id: newId("msg"), role: "assistant", text: "", steps: [], at: Date.now() };
    session.messages.push(assistant);
    this.docs.put(id, session);

    this.#run(session, assistant, text).catch((err) => {
      this.log(`code: session ${id} failed: ${err.message}`);
    });
    return { ok: true };
  }

  async #run(session, assistant, prompt) {
    let full = "";
    try {
      const stream = this.pool.stream(
        { messages: [{ role: "user", content: prompt }], max_tokens: 8192 },
        { model: session.model },
      );
      for await (const chunk of stream) {
        if (chunk.delta) {
          full += chunk.delta;
          assistant.text = full;
          session.updatedAt = Date.now();
          this.#emit(session.id, "delta", { id: assistant.id, text: chunk.delta });
        }
      }
      session.status = "idle";
    } catch (err) {
      session.status = "error";
      session.error = err.message;
      this.#emit(session.id, "error", { message: err.message });
    } finally {
      assistant.at = Date.now();
      session.updatedAt = Date.now();
      this.docs.put(session.id, session);
      this.#emit(session.id, "done", { id: assistant.id, text: assistant.text });
      this.#emit(session.id, "status", { status: session.status });
    }
  }

  stop(userId, id) {
    const session = this.get(userId, id);
    if (!session || session.status !== "running") return false;
    session.status = "idle";
    this.docs.put(id, session);
    this.#emit(id, "status", { status: "idle" });
    return true;
  }

  setModel(userId, id, model) {
    const session = this.get(userId, id);
    if (!session) return null;
    const normalized = this.registry.normalize(model);
    if (!normalized) throw httpError(400, "Неизвестная модель");
    session.model = normalized;
    this.docs.put(id, session);
    this.#emit(id, "session", { model: normalized });
    return summary(session);
  }

  resolve(session, relPath = "") {
    const target = path.resolve(session.dir, String(relPath || ""));
    if (!target.startsWith(session.dir)) throw httpError(400, "Недопустимый путь");
    return target;
  }

  files(userId, id, relPath = "") {
    const session = this.get(userId, id);
    if (!session) return null;
    const target = this.resolve(session, relPath);
    let entries;
    try {
      entries = fs.readdirSync(target, { withFileTypes: true });
    } catch {
      throw httpError(404, "Папка не найдена");
    }
    return entries
      .filter((entry) => !IGNORED.has(entry.name) && !entry.name.startsWith(".clauden"))
      .map((entry) => {
        const full = path.join(target, entry.name);
        let size = 0;
        try {
          size = fs.statSync(full).size;
        } catch {

        }
        return {
          name: entry.name,
          path: path.posix.join(relPath || "", entry.name),
          dir: entry.isDirectory(),
          size,
        };
      });
  }

  readFile(userId, id, relPath) {
    const session = this.get(userId, id);
    if (!session) return null;
    const target = this.resolve(session, relPath);
    const stat = fs.statSync(target);
    if (stat.size > 2 * 1024 * 1024) throw httpError(413, "Файл слишком большой");
    return { path: relPath, content: fs.readFileSync(target, "utf8") };
  }

  writeFile(userId, id, relPath, content) {
    const session = this.get(userId, id);
    if (!session) return null;
    const target = this.resolve(session, relPath);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, String(content ?? ""));
    this.#emit(id, "files", { path: relPath });
    return { path: relPath, size: Buffer.byteLength(String(content ?? "")) };
  }

  upload(userId, id, name, base64) {
    const session = this.get(userId, id);
    if (!session) return null;
    const safe = path.basename(String(name || "file"));
    const target = path.join(session.dir, safe);
    fs.writeFileSync(target, Buffer.from(String(base64 || ""), "base64"));
    return { name: safe, size: fs.statSync(target).size };
  }

  download(userId, id, relPath) {
    const session = this.get(userId, id);
    if (!session) return null;
    const target = this.resolve(session, relPath);
    return { name: path.basename(target), buffer: fs.readFileSync(target) };
  }

  exec(userId, id, command) {
    const session = this.get(userId, id);
    if (!session) return null;
    if (!this.config.code?.exec) {
      throw httpError(403, "Выполнение команд отключено на этом сервере");
    }
    return new Promise((resolve, reject) => {
      execFile(
        "/bin/sh",
        ["-lc", String(command || "")],
        { cwd: session.dir, timeout: 120_000, maxBuffer: 4 * 1024 * 1024 },
        (err, stdout, stderr) => {
          if (err && !stdout && !stderr) return reject(httpError(500, err.message));
          resolve({ code: err?.code ?? 0, stdout, stderr });
        },
      );
    });
  }

  diff(userId, id) {
    const session = this.get(userId, id);
    if (!session) return null;
    return new Promise((resolve) => {
      execFile(
        "git",
        ["diff", "--no-color", "--minimal"],
        { cwd: session.dir, maxBuffer: 8 * 1024 * 1024 },
        (err, stdout) => resolve({ diff: stdout || "", error: err && !stdout ? err.message : "" }),
      );
    });
  }

  commit(userId, id, message) {
    const session = this.get(userId, id);
    if (!session) return null;
    return new Promise((resolve, reject) => {
      execFile("git", ["add", "-A"], { cwd: session.dir }, (addErr) => {
        if (addErr) return reject(httpError(500, addErr.message));
        execFile(
          "git",
          ["commit", "-m", String(message || "clauden: update")],
          { cwd: session.dir },
          (err, stdout) => {
            if (err) return reject(httpError(500, err.message));
            resolve({ output: stdout });
          },
        );
      });
    });
  }

  usage(userId, id) {
    const session = this.get(userId, id);
    if (!session) return null;
    const messages = session.messages || [];
    const chars = messages.reduce((sum, message) => sum + String(message.text || "").length, 0);
    return {
      messages: messages.length,
      tokens: Math.round(chars / 4),
      since: session.createdAt,
    };
  }

  models(userId, id) {
    const session = this.get(userId, id);
    if (!session) return null;
    return { current: session.model, data: this.registry.list() };
  }
}

function summary(session) {
  return {
    id: session.id,
    title: session.title,
    model: session.model,
    modelAlias: session.model,
    repo: session.repo,
    repoName: session.repoName,
    branch: session.branch,
    status: session.status,
    error: session.error || "",
    messages: session.messages,
    todos: session.todos,
    permissions: session.permissions,
    forms: session.forms,
    source: "gateway",
    createdAt: session.createdAt,
    updatedAt: session.updatedAt,
  };
}

function httpError(status, message) {
  const err = new Error(message);
  err.status = status;
  return err;
}
