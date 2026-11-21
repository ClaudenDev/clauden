'use strict';

const fs = require('node:fs');
const path = require('node:path');

const IS_WIN = process.platform === 'win32';
const IS_MAC = process.platform === 'darwin';
const IS_LINUX = process.platform === 'linux';

const BWRAP_CANDIDATES = ['/usr/bin/bwrap', '/bin/bwrap', '/usr/local/bin/bwrap'];

function findBwrap() {
  if (process.env.CLAUDEN_BWRAP && fs.existsSync(process.env.CLAUDEN_BWRAP)) {
    return process.env.CLAUDEN_BWRAP;
  }
  for (const candidate of BWRAP_CANDIDATES) {
    try {
      if (fs.existsSync(candidate)) return candidate;
    } catch {

    }
  }
  return '';
}

function quoteProfileString(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

function linuxInvocation(command, { cwd, allowNetwork = false }) {
  const bwrap = findBwrap();
  if (!bwrap) return null;
  const dir = path.resolve(cwd);
  const args = [
    '--ro-bind', '/usr', '/usr',
    '--ro-bind', '/bin', '/bin',
    '--ro-bind', '/lib', '/lib',
    '--ro-bind', '/lib64', '/lib64',
    '--ro-bind', '/etc/ssl/certs', '/etc/ssl/certs',
    '--ro-bind', '/etc/ssl/openssl.cnf', '/etc/ssl/openssl.cnf',
    '--ro-bind', '/etc/passwd', '/etc/passwd',
    '--ro-bind', '/etc/group', '/etc/group',
    '--proc', '/proc',
    '--dev', '/dev',
    '--tmpfs', '/tmp',
    '--bind', dir, dir,
    '--chdir', dir,
    '--unshare-all',
  ];
  if (allowNetwork) {

    for (const file of ['/etc/resolv.conf', '/etc/hosts', '/etc/nsswitch.conf']) {
      try {
        const real = fs.realpathSync(file);
        args.push('--ro-bind', real, file);
      } catch {

      }
    }
  }
  if (allowNetwork) args.push('--share-net');
  else {

    args.push('--unshare-net');
  }
  args.push(
    '--die-with-parent',
    '--clearenv',
    '--setenv', 'HOME', dir,
    '--setenv', 'PATH', '/usr/local/bin:/usr/bin:/bin',
    '--setenv', 'LANG', 'C.UTF-8',
    '--setenv', 'LC_ALL', 'C.UTF-8',
    '--setenv', 'TERM', 'dumb',
    '--setenv', 'TMPDIR', '/tmp',
    '/bin/bash', '-c', command,
  );
  return { cmd: bwrap, args, sandboxed: true, kind: 'bwrap' };
}

function macInvocation(command, { cwd, allowNetwork = false }) {
  const dir = path.resolve(cwd);
  const profile = [
    '(version 1)',
    '(allow default)',
    allowNetwork ? '' : '(deny network*)',
    '(deny file-write*)',
    `(allow file-write* (subpath "${quoteProfileString(dir)}")` +
      ' (subpath "/private/tmp") (subpath "/tmp") (subpath "/dev"))',
  ]
    .filter(Boolean)
    .join('\n');
  return {
    cmd: '/usr/bin/sandbox-exec',
    args: ['-p', profile, '/bin/bash', '-c', command],
    sandboxed: true,
    kind: 'seatbelt',
  };
}

function wrapCommand(command, { cwd, allowNetwork = false } = {}) {
  const dir = path.resolve(cwd || process.cwd());
  try {
    if (IS_LINUX) return linuxInvocation(command, { cwd: dir, allowNetwork });
    if (IS_MAC) return macInvocation(command, { cwd: dir, allowNetwork });
  } catch {
    return null;
  }
  return null;
}

function sandboxInfo() {
  if (IS_LINUX) {
    const bwrap = findBwrap();
    return {
      available: Boolean(bwrap),
      kind: bwrap ? 'bwrap' : 'none',
      detail: bwrap
        ? 'bubblewrap: только папка проекта, сеть запрещена'
        : 'bubblewrap не найден — команды без песочницы',
    };
  }
  if (IS_MAC) {
    return {
      available: fs.existsSync('/usr/bin/sandbox-exec'),
      kind: 'seatbelt',
      detail: 'seatbelt: запись только в папку проекта, сеть запрещена',
    };
  }
  if (IS_WIN) {
    return {
      available: false,
      kind: 'none',
      detail: 'Windows: сильной песочницы нет, команды требуют подтверждения',
    };
  }
  return { available: false, kind: 'none', detail: 'Песочница недоступна' };
}

module.exports = { wrapCommand, sandboxInfo, findBwrap, IS_WIN };
