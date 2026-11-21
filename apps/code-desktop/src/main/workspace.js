'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');

const execFileAsync = promisify(execFile);

const SKIP = new Set(['.git', 'node_modules']);
const MAX_READ = 400 * 1024;
const MAX_PATCH = 400 * 1024;

class Workspace {
  constructor() {
    this.root = fs.realpathSync(process.cwd());

    this.hooksDir = path.join(os.tmpdir(), 'clauden-code-no-hooks');
    try {
      fs.mkdirSync(this.hooksDir, { recursive: true });
    } catch {

    }
  }

  setRoot(dir) {
    this.root = fs.realpathSync(path.resolve(dir || process.cwd()));
    return this.root;
  }

  #abs(relative) {
    const abs = path.resolve(this.root, relative || '.');
    const rel = path.relative(this.root, abs);
    if (rel.startsWith('..') || path.isAbsolute(rel)) {
      throw new Error(
        `Путь вне проекта: ${abs}. Корень сессии: ${this.root}. ` +
        'Откройте нужную папку как проект (Ctrl+N → «Этот компьютер»).',
      );
    }

    let probe = abs;
    while (!fs.existsSync(probe)) {
      const parent = path.dirname(probe);
      if (parent === probe) break;
      probe = parent;
    }
    const prefix = this.root.endsWith(path.sep) ? this.root : this.root + path.sep;
    const inside = (value) => value === this.root || value.startsWith(prefix);
    const realProbe = fs.realpathSync(probe);
    if (!inside(realProbe)) {
      throw new Error('Путь вне проекта (симлинк)');
    }
    if (fs.existsSync(abs)) {
      const realAbs = fs.realpathSync(abs);
      if (!inside(realAbs)) {
        throw new Error('Путь вне проекта (симлинк)');
      }
    }
    return abs;
  }

  #rel(abs) {
    return path.relative(this.root, abs).split(path.sep).join('/');
  }

  list(dir = '') {
    const abs = this.#abs(dir);
    let stat;
    try {
      stat = fs.statSync(abs);
    } catch {
      throw new Error('Папка не найдена');
    }
    if (!stat.isDirectory()) throw new Error('Это не папка');

    const entries = [];
    for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
      if (SKIP.has(entry.name)) continue;
      const full = path.join(abs, entry.name);
      let info;
      try {
        info = fs.lstatSync(full);
      } catch {
        continue;
      }
      entries.push({
        name: entry.name,
        path: this.#rel(full),
        type: entry.isDirectory() ? 'dir' : entry.isSymbolicLink() ? 'link' : 'file',
        size: info.size,
        mtime: info.mtimeMs,
      });
    }

    entries.sort((a, b) => {
      if (a.type === 'dir' && b.type !== 'dir') return -1;
      if (a.type !== 'dir' && b.type === 'dir') return 1;
      return a.name.localeCompare(b.name);
    });

    const relative = this.#rel(abs);
    return {
      path: relative || '.',
      parent: relative ? path.posix.dirname(relative) : '',
      entries,
    };
  }

  read(file) {
    const abs = this.#abs(file);
    const stat = fs.statSync(abs);
    if (!stat.isFile()) throw new Error('Это не файл');
    if (stat.size > MAX_READ) throw new Error('Файл больше 400 КБ');
    const buffer = fs.readFileSync(abs);
    const binary = buffer.includes(0);
    return {
      path: this.#rel(abs),
      size: stat.size,
      binary,
      content: binary ? '' : buffer.toString('utf8'),
    };
  }

  write(file, content) {
    const abs = this.#abs(file);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    fs.writeFileSync(abs, String(content ?? ''), 'utf8');
    return { ok: true, path: this.#rel(abs) };
  }

  create(dir, name, kind = 'file') {
    const safe = String(name || '').trim();
    if (!safe || safe.includes('/') || safe.includes('\\')) throw new Error('Недопустимое имя');
    const target = this.#abs(path.posix.join(dir || '.', safe));
    if (fs.existsSync(target)) throw new Error('Уже существует');
    if (kind === 'dir') fs.mkdirSync(target, { recursive: true });
    else fs.writeFileSync(target, '', 'utf8');
    return { ok: true, path: this.#rel(target) };
  }

  rename(file, name) {
    const safe = String(name || '').trim();
    if (!safe || safe.includes('/') || safe.includes('\\')) throw new Error('Недопустимое имя');
    const from = this.#abs(file);
    const to = this.#abs(path.join(path.relative(this.root, path.dirname(from)), safe));
    if (fs.existsSync(to)) throw new Error('Имя занято');
    fs.renameSync(from, to);
    return { ok: true, path: this.#rel(to) };
  }

  remove(file) {
    const abs = this.#abs(file);
    fs.rmSync(abs, { recursive: true, force: true });
    return { ok: true };
  }

  async git(args, { timeout = 60_000 } = {}) {
    return execFileAsync(
      'git',
      [
        '-C',
        this.root,
        '-c',
        `core.hooksPath=${this.hooksDir.replace(/\\/g, '/')}`,
        ...args,
      ],
      {
        timeout,
        maxBuffer: 16 * 1024 * 1024,
        windowsHide: true,
        env: { ...process.env, GIT_TERMINAL_PROMPT: '0' },
      },
    );
  }

  async isRepo() {
    try {
      const { stdout } = await this.git(['rev-parse', '--is-inside-work-tree'], { timeout: 15_000 });
      return stdout.trim() === 'true';
    } catch {
      return false;
    }
  }

  async diff() {
    if (!(await this.isRepo())) {
      return { files: [], patch: '', truncated: false, untracked: [], dirty: false, notGit: true };
    }

    const hasHead = await this.git(['rev-parse', '--verify', 'HEAD'])
      .then(() => true)
      .catch(() => false);
    const headArgs = hasHead ? ['HEAD'] : [];

    const [numstat, status, untracked] = await Promise.all([
      this.git(['diff', '--no-ext-diff', '--no-textconv', '--numstat', ...headArgs]).catch(() => ({ stdout: '' })),
      this.git(['status', '--porcelain', '-uall']).catch(() => ({ stdout: '' })),
      this.git(['ls-files', '--others', '--exclude-standard']).catch(() => ({ stdout: '' })),
    ]);

    let patch = await this.git(['diff', '--no-ext-diff', '--no-textconv', '--no-color', ...headArgs]).catch(() => ({ stdout: '' }));

    const fresh = untracked.stdout.split('\n').filter(Boolean).slice(0, 20);
    for (const file of fresh) {
      const one = await this.git(['diff', '--no-ext-diff', '--no-textconv', '--no-color', '--no-index', '/dev/null', file]).catch(
        (err) => ({ stdout: err.stdout || '' }),
      );
      if (one.stdout) patch.stdout += one.stdout;
    }

    const files = status.stdout
      .split('\n')
      .filter(Boolean)
      .map((line) => ({ status: line.slice(0, 2).trim(), path: line.slice(3).trim() }));

    const counts = {};
    for (const line of numstat.stdout.split('\n').filter(Boolean)) {
      const [add, del, file] = line.split('\t');
      counts[file] = {
        additions: add === '-' ? 0 : Number(add) || 0,
        deletions: del === '-' ? 0 : Number(del) || 0,
      };
    }

    const text = patch.stdout || '';
    return {
      files: files.map((file) => ({ ...file, ...(counts[file.path] || {}) })),
      patch: text.length > MAX_PATCH ? text.slice(0, MAX_PATCH) : text,
      truncated: text.length > MAX_PATCH,
      untracked: untracked.stdout.split('\n').filter(Boolean).slice(0, 200),
      dirty: files.length > 0,
    };
  }

  async commit(message) {
    const text = String(message || '').trim();
    if (!text) throw new Error('Нужно сообщение коммита');
    if (!(await this.isRepo())) throw new Error('Папка не является git-репозиторием');
    await this.git(['add', '-A']);
    const { stdout } = await this.git(['commit', '-m', text], { timeout: 120_000 });
    return { ok: true, output: stdout.trim() };
  }
}

module.exports = { Workspace };
