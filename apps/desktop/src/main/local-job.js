'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { runCommand } = require('./shell');
const { sandboxInfo } = require('./sandbox');

const MAX_READ = 400 * 1024;
const MAX_FS_OUT = 60_000;
const MAX_WRITE = 500 * 1024;

function isInstallOrDownload(command) {
  const text = String(command || '');
  if (/\b(?:winget|choco|scoop)\s+(?:install|upgrade|add)\b/i.test(text)) return true;
  if (/\bmsiexec\b/i.test(text)) return true;
  if (/\b(?:bitsadmin|Start-BitsTransfer)\b/i.test(text)) return true;
  if (/\bInvoke-WebRequest\b[\s\S]*-OutFile\b/i.test(text)) return true;
  if (/\b(?:curl|wget)\b[\s\S]*(?:\s-O(?:\s|$)|\s-o\s|--output\b|--remote-name\b)/i.test(text)) return true;
  if (/\b(?:npm|pnpm|yarn)\s+(?:install|i|add)\b/i.test(text)) return true;
  if (/\b(?:pip3?|python(?:3)?\s+-m\s+pip)\s+install\b/i.test(text)) return true;
  if (/\b(?:uv|uvx)\s+(?:tool|pip)\s+install\b/i.test(text)) return true;
  if (/\bcargo\s+(?:install|fetch)\b/i.test(text)) return true;
  if (/\bgit\s+clone\b/i.test(text)) return true;
  if (/\bgo\s+get\b/i.test(text)) return true;
  if (/\b(?:curl|wget)\b/i.test(text)) return true;
  return false;
}

function resolveRoot(raw) {
  const root = fs.realpathSync(path.resolve(String(raw || '')));
  const stat = fs.statSync(root);
  if (!stat.isDirectory()) throw new Error('Корень проекта — не папка');
  return root;
}

function inside(root, target) {
  const prefix = root.endsWith(path.sep) ? root : root + path.sep;
  return target === root || target.startsWith(prefix);
}

function safePath(root, rel) {
  const abs = path.resolve(root, String(rel || '.').replace(/^[/\\]+/, ''));
  if (!inside(root, abs)) {
    throw new Error(
      `Путь вне проекта: ${abs}. Корень сессии: ${root}. ` +
        'Откройте нужную папку как проект, чтобы работать с ней.',
    );
  }

  let probe = abs;
  while (!fs.existsSync(probe)) {
    const parent = path.dirname(probe);
    if (parent === probe) break;
    probe = parent;
  }
  if (!inside(root, fs.realpathSync(probe))) {
    throw new Error('Путь вне проекта (симлинк)');
  }
  if (fs.existsSync(abs) && !inside(root, fs.realpathSync(abs))) {
    throw new Error('Путь вне проекта (симлинк)');
  }
  return abs;
}

function relOf(root, abs) {
  return path.relative(root, abs).split(path.sep).join('/');
}

function listDir(root, rel) {
  const abs = safePath(root, rel);
  const stat = fs.statSync(abs);
  if (!stat.isDirectory()) throw new Error('Это не папка');
  const entries = [];
  for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
    if (entry.name === '.git' || entry.name === 'node_modules') continue;
    const full = path.join(abs, entry.name);
    let info;
    try {
      info = fs.lstatSync(full);
    } catch {
      continue;
    }
    entries.push({
      name: entry.name,
      path: relOf(root, full),
      type: entry.isDirectory() ? 'dir' : entry.isSymbolicLink() ? 'link' : 'file',

      dir: entry.isDirectory(),
      size: info.size,
      mtime: info.mtimeMs,
    });
  }
  entries.sort((a, b) => {
    if (a.type === 'dir' && b.type !== 'dir') return -1;
    if (a.type !== 'dir' && b.type === 'dir') return 1;
    return a.name.localeCompare(b.name);
  });
  const relative = relOf(root, abs);
  return {
    path: relative || '.',
    parent: relative ? path.posix.dirname(relative) : '',
    entries,
  };
}

function readFile(root, rel) {
  const abs = safePath(root, rel);
  const stat = fs.statSync(abs);
  if (!stat.isFile()) throw new Error('Это не файл');
  if (stat.size > MAX_READ) throw new Error('Файл больше 400 КБ');
  const buffer = fs.readFileSync(abs);
  const binary = buffer.includes(0);
  return {
    path: relOf(root, abs),
    size: stat.size,
    binary,
    content: binary ? '' : buffer.toString('utf8'),
  };
}

function writeFile(root, rel, content) {
  const text = String(content ?? '');
  if (text.length > MAX_WRITE) throw new Error('Файл слишком большой');
  const abs = safePath(root, rel);
  fs.mkdirSync(path.dirname(abs), { recursive: true });
  fs.writeFileSync(abs, text, 'utf8');
  const written = Buffer.byteLength(text);
  return { ok: true, path: relOf(root, abs), size: written, written };
}

function clipJson(value) {
  const text = JSON.stringify(value);
  return text.length > MAX_FS_OUT ? `${text.slice(0, MAX_FS_OUT)}…` : text;
}

function fsJob(root, job) {
  const op = String(job.op || '');
  const rel = String(job.path || '');
  try {
    if (op === 'list') {
      const data = listDir(root, rel);
      return { stdout: clipJson({ path: data.path, entries: data.entries }), stderr: '', code: 0 };
    }
    if (op === 'read') {
      return { stdout: clipJson(readFile(root, rel)), stderr: '', code: 0 };
    }
    if (op === 'stat') {
      const parent = path.posix.dirname(rel) === '.' ? '' : path.posix.dirname(rel);
      const data = listDir(root, parent);
      const found = data.entries.find((entry) => entry.name === path.posix.basename(rel));
      return {
        stdout: clipJson(found || { error: 'не найдено' }),
        stderr: '',
        code: found ? 0 : 1,
      };
    }
    if (op === 'write') {
      return { stdout: clipJson(writeFile(root, rel, job.content || '')), stderr: '', code: 0 };
    }
    return { stdout: '', stderr: `Неизвестная операция: ${op}`, code: 1 };
  } catch (err) {
    return { stdout: clipJson({ error: err.message }), stderr: err.message, code: 1 };
  }
}

async function runLocalJob({ root, job, sessionId = '', confirm = null, alwaysAllowed = new Set(), log = () => {} }) {
  if (!root) throw new Error('Не выбран корень проекта');
  const dir = resolveRoot(root);
  if (!job || typeof job !== 'object') throw new Error('Пустая задача');

  if (job.fs) {
    log(`fs ${job.fs.op || ''} ${job.fs.path || ''}`.trim());
    return fsJob(dir, job.fs);
  }

  const command = String(job.command || '');
  if (!command.trim()) return { stdout: '', stderr: 'Пустая команда', code: 1 };
  const sandbox = sandboxInfo();
  const install = isInstallOrDownload(command);

  if (!sandbox.available && !alwaysAllowed.has(sessionId) && confirm) {
    const answer = await confirm({
      title: 'Агент хочет выполнить команду на этом компьютере',
      detail:
        `${command.slice(0, 1400)}\n\n` +
        'Песочница на этой системе недоступна: команда выполнится с вашими правами.',
      allowAlways: true,
    });
    if (!answer) {
      return {
        rejected: true,
        stdout: '',
        stderr: 'Пользователь отменил команду. Не выполняй её без явного разрешения.',
        code: 126,
      };
    }
    if (answer === 'always') alwaysAllowed.add(sessionId);
  }

  if (install && confirm) {
    const allowed = await confirm({
      title: 'Агент хочет скачать или установить программу',
      detail: command.slice(0, 1600),
      allowAlways: false,
    });
    if (!allowed) {
      return {
        rejected: true,
        stdout: '',
        stderr:
          'Пользователь отменил скачивание/установку. Не устанавливай программы без явного разрешения.',
        code: 126,
      };
    }
  }

  const timeout = Math.min(Math.max(Number(job.timeout) || 60, 5), 300) * 1000;
  log(`shell ${command.slice(0, 80)}`);
  const result = await runCommand(command, {
    cwd: dir,
    timeoutMs: timeout,
    sandbox: sandbox.available,
    allowNetwork: install && sandbox.available,
  });
  const suffix = result.timedOut ? '\n[превышено время выполнения]' : '';
  return {
    stdout: result.stdout.slice(0, 20_000),
    stderr: (result.stderr + suffix).slice(0, 20_000),
    code: result.timedOut ? 124 : result.code,
  };
}

module.exports = { runLocalJob, isInstallOrDownload };
