'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { wrapCommand } = require('./sandbox');

const IS_WIN = process.platform === 'win32';

const GIT_BASH_CANDIDATES = [
  'C:\\Program Files\\Git\\bin\\bash.exe',
  'C:\\Program Files (x86)\\Git\\bin\\bash.exe',
  'C:\\Program Files\\Git\\usr\\bin\\bash.exe',
];

let cached = null;

function findGitBash() {
  if (process.env.CLAUDEN_BASH && fs.existsSync(process.env.CLAUDEN_BASH)) {
    return process.env.CLAUDEN_BASH;
  }
  for (const candidate of GIT_BASH_CANDIDATES) {
    try {
      if (fs.existsSync(candidate)) return candidate;
    } catch {

    }
  }
  return '';
}

function resolveShell() {
  if (cached) return cached;

  if (process.env.CLAUDEN_SHELL) {
    const custom = process.env.CLAUDEN_SHELL;
    cached = { kind: path.basename(custom).toLowerCase().includes('bash') ? 'bash' : 'cmd', cmd: custom };
    return cached;
  }

  if (!IS_WIN) {
    const sh = fs.existsSync('/bin/bash') ? '/bin/bash' : '/bin/sh';
    cached = { kind: 'bash', cmd: sh };
    return cached;
  }

  const gitBash = findGitBash();
  if (gitBash) {
    cached = { kind: 'bash', cmd: gitBash };
    return cached;
  }

  const pwsh = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32\\WindowsPowerShell\\v1.0\\powershell.exe');
  if (fs.existsSync(pwsh)) {
    cached = { kind: 'powershell', cmd: pwsh };
    return cached;
  }

  cached = { kind: 'cmd', cmd: process.env.COMSPEC || 'cmd.exe' };
  return cached;
}

const PS_PREFIX =
  '[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;' +
  '$OutputEncoding=[System.Text.Encoding]::UTF8;' +
  '$ErrorActionPreference="Continue";';

function buildInvocation(command) {
  const shell = resolveShell();
  if (shell.kind === 'bash') {

    return { cmd: shell.cmd, args: ['-c', command], kind: 'bash' };
  }
  if (shell.kind === 'powershell') {
    return {
      cmd: shell.cmd,
      args: ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', `${PS_PREFIX} ${command}`],
      kind: 'powershell',
    };
  }
  return { cmd: shell.cmd, args: ['/d', '/s', '/c', `chcp 65001>nul & ${command}`], kind: 'cmd' };
}

function env() {
  return {
    ...process.env,
    TERM: 'xterm-256color',
    FORCE_COLOR: '1',
    CLAUDEN_CODE: '1',
    PYTHONIOENCODING: 'utf-8',
  };
}

function makeDecoder(kind) {
  let decoder = new TextDecoder('utf-8', { fatal: true });
  let broken = false;
  const fallbackName = kind === 'cmd' ? 'ibm866' : 'windows-1251';

  return {
    push(chunk) {
      if (broken) return new TextDecoder(fallbackName).decode(chunk);
      try {
        return decoder.decode(chunk, { stream: true });
      } catch {
        broken = true;
        try {
          return new TextDecoder(fallbackName).decode(chunk);
        } catch {
          return new TextDecoder('utf-8').decode(chunk);
        }
      }
    },
  };
}

function decodeAll(buffers, kind) {
  const buffer = Buffer.concat(buffers);
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(buffer);
  } catch {
    try {
      return new TextDecoder(kind === 'cmd' ? 'ibm866' : 'windows-1251').decode(buffer);
    } catch {
      return new TextDecoder('utf-8').decode(buffer);
    }
  }
}

function runCommand(
  command,
  {
    cwd,
    timeoutMs = 60_000,
    maxBytes = 400_000,
    sandbox = process.env.CLAUDEN_LOCAL_SANDBOX !== '0',
    allowNetwork = false,
  } = {},
) {
  return new Promise((resolve) => {
    const dir = path.resolve(cwd || process.cwd());
    const wrapped = sandbox ? wrapCommand(command, { cwd: dir, allowNetwork }) : null;
    const { cmd, args, kind } = wrapped || buildInvocation(command);
    let child;
    try {
      child = spawn(cmd, args, {
        cwd: dir,
        env: env(),
        windowsHide: true,
        detached: !IS_WIN,
      });
    } catch (err) {
      return resolve({
        stdout: '',
        stderr: err.message,
        code: -1,
        timedOut: false,
        sandboxed: Boolean(wrapped),
        sandboxKind: wrapped?.kind || 'none',
      });
    }

    const out = [];
    const err = [];
    let size = 0;
    let timedOut = false;
    let finished = false;

    const timer = setTimeout(() => {
      timedOut = true;
      killTree(child);
    }, Math.max(5_000, timeoutMs));

    const collect = (bucket) => (chunk) => {
      if (size > maxBytes) return;
      size += chunk.length;
      bucket.push(chunk);
    };

    child.stdout?.on('data', collect(out));
    child.stderr?.on('data', collect(err));
    child.on('error', (error) => err.push(Buffer.from(String(error.message))));
    child.on('close', (code, signal) => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      resolve({
        stdout: decodeAll(out, kind),
        stderr: decodeAll(err, kind),
        code: code === null ? -1 : code,
        signal: signal || '',
        timedOut,
        sandboxed: Boolean(wrapped),
        sandboxKind: wrapped?.kind || 'none',
      });
    });
  });
}

function killTree(child) {
  if (!child?.pid) return;
  try {
    if (IS_WIN) {
      spawn('taskkill', ['/pid', String(child.pid), '/t', '/f'], { windowsHide: true });
    } else {
      process.kill(-child.pid, 'SIGTERM');
      setTimeout(() => {
        try {
          process.kill(-child.pid, 'SIGKILL');
        } catch {

        }
      }, 1500).unref?.();
    }
  } catch {
    try {
      child.kill('SIGKILL');
    } catch {

    }
  }
}

module.exports = { resolveShell, buildInvocation, makeDecoder, decodeAll, runCommand, killTree, env, IS_WIN };
