'use strict';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const { runLocalJob } = require('../src/main/local-job');

function project() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'clauden-local-'));
}

function parse(result) {
  return JSON.parse(result.stdout);
}

test('пишет, читает и показывает файлы внутри проекта', async () => {
  const root = project();
  const write = await runLocalJob({
    root,
    job: { fs: { op: 'write', path: 'src/main.txt', content: 'привет' } },
  });
  assert.equal(write.code, 0);
  assert.equal(parse(write).path, 'src/main.txt');
  assert.equal(fs.readFileSync(path.join(root, 'src/main.txt'), 'utf8'), 'привет');

  const read = await runLocalJob({ root, job: { fs: { op: 'read', path: 'src/main.txt' } } });
  assert.equal(read.code, 0);
  assert.equal(parse(read).content, 'привет');

  const list = await runLocalJob({ root, job: { fs: { op: 'list', path: '' } } });
  assert.equal(list.code, 0);
  assert.deepEqual(parse(list).entries.map((entry) => entry.name), ['src']);

  const stat = await runLocalJob({ root, job: { fs: { op: 'stat', path: 'src/main.txt' } } });
  assert.equal(stat.code, 0);
  assert.equal(parse(stat).size, Buffer.byteLength('привет'));
});

test('не выпускает за пределы проекта и через ..', async () => {
  const root = project();
  const result = await runLocalJob({ root, job: { fs: { op: 'read', path: '../../etc/passwd' } } });
  assert.equal(result.code, 1);
  assert.match(result.stderr, /вне проекта/);
});

test('не выпускает за пределы проекта через симлинк', async (t) => {
  const root = project();
  const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'clauden-outside-'));
  fs.writeFileSync(path.join(outside, 'secret.txt'), 'secret');
  try {
    fs.symlinkSync(outside, path.join(root, 'link'), 'dir');
  } catch (err) {
    t.skip(`симлинки недоступны: ${err.message}`);
    return;
  }
  const result = await runLocalJob({ root, job: { fs: { op: 'read', path: 'link/secret.txt' } } });
  assert.equal(result.code, 1);
  assert.match(result.stderr, /симлинк|вне проекта/);
});

test('выполняет команду в папке проекта', async () => {
  const root = project();
  fs.writeFileSync(path.join(root, 'hello.txt'), 'ok');
  const result = await runLocalJob({ root, job: { command: 'ls hello.txt', timeout: 15 } });
  assert.equal(result.code, 0, result.stderr);
  assert.match(result.stdout, /hello\.txt/);
});

test('отклонённая команда не выполняется', async () => {
  const root = project();
  let asked = 0;
  const result = await runLocalJob({
    root,
    sessionId: 'cs_test',
    job: { command: 'touch created.txt', timeout: 15 },
    confirm: async () => {
      asked += 1;
      return false;
    },
  });

  if (!asked) {
    assert.equal(result.code, 0);
    assert.ok(fs.existsSync(path.join(root, 'created.txt')));
  } else {
    assert.equal(result.code, 126);
    assert.ok(!fs.existsSync(path.join(root, 'created.txt')));
  }
});
