'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { buildInvocation, makeDecoder, env, killTree, IS_WIN } = require('./shell');

class Terminal {
  constructor(emit) {
    this.emit = emit;
    this.proc = null;
    this.currentId = 0;
    this.cwd = os.homedir();
  }

  setCwd(dir) {
    this.cwd = dir ? path.resolve(dir) : os.homedir();
    return this.cwd;
  }

  run({ command, cwd }) {
    const text = String(command || '').trim();
    if (!text) return { id: 0 };

    const cd = text.match(/^cd(?:\s+(.+))?$/i);
    if (cd) return this.#changeDir(cd[1], cwd);

    if (this.proc) this.kill();

    const id = ++this.currentId;
    const dir = cwd || this.cwd;
    const { cmd, args, kind } = buildInvocation(text);

    let child;
    try {
      child = spawn(cmd, args, {
        cwd: dir,
        env: env(),
        windowsHide: true,
        detached: !IS_WIN,
      });
    } catch (err) {
      this.emit('term:data', { id, stream: 'stderr', text: `${err.message}\n` });
      this.emit('term:exit', { id, code: -1 });
      return { id };
    }

    this.proc = child;
    this.emit('term:start', { id, command: text, cwd: dir });

    const outDecoder = makeDecoder(kind);
    const errDecoder = makeDecoder(kind);

    child.stdout?.on('data', (chunk) => {
      this.emit('term:data', { id, stream: 'stdout', text: outDecoder.push(chunk) });
    });
    child.stderr?.on('data', (chunk) => {
      this.emit('term:data', { id, stream: 'stderr', text: errDecoder.push(chunk) });
    });
    child.on('error', (err) => {
      this.emit('term:data', { id, stream: 'stderr', text: `${err.message}\n` });
    });
    child.on('close', (code, signal) => {
      if (this.proc === child) this.proc = null;
      this.emit('term:exit', { id, code: code === null ? -1 : code, signal: signal || '' });
    });

    return { id };
  }

  #changeDir(raw, cwd) {
    const id = ++this.currentId;
    let target = String(raw || '').trim().replace(/^["']|["']$/g, '');

    if (!target) target = os.homedir();
    if (target === '~') target = os.homedir();
    else if (target.startsWith('~/') || target.startsWith('~\\')) {
      target = path.join(os.homedir(), target.slice(2));
    } else {
      target = path.resolve(cwd || this.cwd, target);
    }

    this.emit('term:start', { id, command: `cd ${target}`, cwd: this.cwd });

    try {
      const stat = fs.statSync(target);
      if (!stat.isDirectory()) throw new Error('не папка');
    } catch {
      this.emit('term:data', { id, stream: 'stderr', text: `cd: ${target}: нет такой папки\n` });
      this.emit('term:exit', { id, code: 1 });
      return { id, cwd: this.cwd };
    }

    this.cwd = target;
    this.emit('term:cwd', { cwd: target });
    this.emit('term:exit', { id, code: 0 });
    return { id, cwd: target, handled: true };
  }

  kill() {
    const child = this.proc;
    if (!child) return false;
    this.proc = null;
    killTree(child);
    return true;
  }
}

module.exports = { Terminal };
