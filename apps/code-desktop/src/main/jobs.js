'use strict';

const path = require('node:path');
const { runCommand } = require('./shell');
const { sandboxInfo } = require('./sandbox');

const MAX_FS_OUT = 60_000;

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

class JobRunner {
  constructor({ api, workspace, log = () => {}, confirm = null, autoApprove = false }) {
    this.api = api;
    this.workspace = workspace;
    this.log = log;
    this.confirm = confirm;
    this.autoApprove =
      autoApprove || process.env.CLAUDEN_LOCAL_AUTO_APPROVE === '1';
    this.running = new Set();

    this.started = new Set();

    this.alwaysAllowed = new Set();
  }

  async handle(sessionId, job, root) {
    if (!job?.id || this.running.has(job.id) || this.started.has(job.id)) return;
    if (job.status && job.status !== 'queued') return;
    this.running.add(job.id);
    this.started.add(job.id);
    try {
      if (root) this.workspace.setRoot(root);
      const result = job.fs ? await this.#fs(job.fs) : await this.#shell(job, sessionId);
      this.log(`job ${job.id}: ${job.fs ? `fs ${job.fs.op}` : `shell ${String(job.command).slice(0, 60)}`}`);
      await this.#deliver(sessionId, job.id, result);
    } catch (err) {
      let delivered = false;
      try {
        await this.#deliver(sessionId, job.id, {
          stdout: '',
          stderr: '',
          code: -1,
          error: err.message || 'Не удалось выполнить',
        });
        delivered = true;
      } catch {

      }

      if (!delivered) this.started.delete(job.id);
    } finally {
      this.running.delete(job.id);
    }
  }

  async #deliver(sessionId, jobId, result) {
    let lastError = null;
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        await this.api.jobResult(sessionId, jobId, result);
        return;
      } catch (err) {
        lastError = err;
        await new Promise((resolve) => setTimeout(resolve, 700 * (attempt + 1)));
      }
    }
    throw lastError || new Error('Не удалось отправить результат задачи');
  }

  async #shell(job, sessionId) {
    const command = String(job.command || '');
    const sandbox = sandboxInfo();
    const install = isInstallOrDownload(command);

    if (!sandbox.available && !this.autoApprove && this.confirm) {
      if (!this.alwaysAllowed.has(sessionId)) {
        const answer = await this.confirm({
          title: 'Агент хочет выполнить команду на этом компьютере',
          detail:
            `${command.slice(0, 1400)}\n\n` +
            'Песочница на этой системе недоступна: команда выполнится с вашими правами.',
          allowAlways: true,
        });
        if (!answer) {
          return {
            stdout: '',
            stderr:
              'Пользователь отменил команду. Не выполняй её без явного разрешения.',
            code: 126,
          };
        }
        if (answer === 'always') this.alwaysAllowed.add(sessionId);
      }
    }

    if (install && !this.autoApprove && this.confirm) {
      const allowed = await this.confirm({
        title: 'Агент хочет скачать или установить программу',
        detail: command.slice(0, 1600),
      });
      if (!allowed) {
        return {
          stdout: '',
          stderr:
            'Пользователь отменил скачивание/установку. Не устанавливай программы без явного разрешения. ' +
            'Если программа уже запущена, найди её процесс или попроси точный путь у пользователя.',
          code: 126,
        };
      }
    }

    const timeout = Math.min(Math.max(Number(job.timeout) || 60, 5), 300) * 1000;
    const result = await runCommand(command, {
      cwd: this.workspace.root,
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

  async #fs(fsJob) {
    const op = String(fsJob.op || '');
    const target = String(fsJob.path || '');

    try {
      if (op === 'list') {
        const data = this.workspace.list(target);
        return { stdout: clipJson({ path: data.path, entries: data.entries }), stderr: '', code: 0 };
      }
      if (op === 'read') {
        const data = this.workspace.read(target);
        return { stdout: clipJson(data), stderr: '', code: 0 };
      }
      if (op === 'stat') {
        const data = this.workspace.list(path.posix.dirname(target) === '.' ? '' : path.posix.dirname(target));
        const found = data.entries.find((entry) => entry.name === path.posix.basename(target));
        return {
          stdout: clipJson(found || { error: 'не найдено' }),
          stderr: '',
          code: found ? 0 : 1,
        };
      }
      if (op === 'write') {
        this.workspace.write(target, fsJob.content || '');
        return { stdout: clipJson({ ok: true, path: target }), stderr: '', code: 0 };
      }
      return { stdout: '', stderr: `Неизвестная операция: ${op}`, code: 1 };
    } catch (err) {
      return { stdout: clipJson({ error: err.message }), stderr: err.message, code: 1 };
    }
  }
}

function clipJson(value) {
  const text = JSON.stringify(value);
  return text.length > MAX_FS_OUT ? `${text.slice(0, MAX_FS_OUT)}…` : text;
}

module.exports = { JobRunner };
