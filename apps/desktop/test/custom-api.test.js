'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  CustomApiManager,
  CustomApiError,
  normalizeBaseUrl,
  parseModels,
} = require('../src/main/custom-api');

function tempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'clauden-custom-api-'));
}

function sseResponse(events) {
  const text = events.map((event) => `data: ${JSON.stringify(event)}\n\n`).join('');
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text));
      controller.close();
    },
  });
  return {
    ok: true,
    status: 200,
    headers: new Headers({ 'content-type': 'text/event-stream' }),
    body: stream,
  };
}

test('normalizeBaseUrl strips trailing slashes and endpoint suffixes', () => {
  assert.equal(normalizeBaseUrl('https://openrouter.ai/api/v1/'), 'https://openrouter.ai/api/v1');
  assert.equal(normalizeBaseUrl('https://api.example.com/v1/chat/completions'), 'https://api.example.com/v1');
  assert.equal(normalizeBaseUrl('api.groq.com/openai/v1'), 'https://api.groq.com/openai/v1');
  assert.equal(normalizeBaseUrl('http://127.0.0.1:11434/v1/models'), 'http://127.0.0.1:11434/v1');
  assert.throws(() => normalizeBaseUrl('ftp://example.com'), /только http и https/);
  assert.throws(() => normalizeBaseUrl(''), /Base URL/);
});

test('parseModels accepts data, models and array payloads', () => {
  assert.deepEqual(parseModels({ data: [{ id: 'b' }, { id: 'a', name: 'A' }] }), [
    { id: 'a', name: 'A' },
    { id: 'b', name: 'b' },
  ]);
  assert.deepEqual(parseModels({ models: [{ name: 'qwen3.8' }] }), [{ id: 'qwen3.8', name: 'qwen3.8' }]);
  assert.deepEqual(parseModels([{ model: 'x' }, null, { id: 'x' }]), [{ id: 'x', name: 'x' }]);
  assert.deepEqual(parseModels(null), []);
});

test('save, list and reload keep keys private', () => {
  const dir = tempDir();
  const manager = new CustomApiManager({ dir });
  const saved = manager.save({
    name: 'OpenRouter',
    baseUrl: 'https://openrouter.ai/api/v1',
    apiKey: 'sk-secret',
    models: [{ id: 'qwen/qwen3', name: 'Qwen 3' }],
  });
  assert.equal(saved.hasKey, true);
  assert.equal(saved.apiKey, undefined);
  assert.equal(saved.modelCount, 1);

  const list = manager.list();
  assert.equal(list.length, 1);
  assert.equal(JSON.stringify(list).includes('sk-secret'), false);

  const reloaded = new CustomApiManager({ dir });
  assert.equal(reloaded.list()[0].name, 'OpenRouter');
  assert.equal(reloaded.get(saved.id).hasKey, true);

  const updated = reloaded.save({
    id: saved.id,
    name: 'OpenRouter 2',
    baseUrl: 'https://openrouter.ai/api/v1',
    apiKey: '',
  });
  assert.equal(updated.hasKey, true);
  assert.equal(updated.name, 'OpenRouter 2');
  assert.equal(updated.modelCount, 1);

  reloaded.remove(saved.id);
  assert.deepEqual(reloaded.list(), []);
  assert.throws(() => reloaded.get(saved.id), CustomApiError);
});

test('save rejects invalid base urls', () => {
  const manager = new CustomApiManager({ dir: tempDir() });
  assert.throws(() => manager.save({ baseUrl: 'file:///etc/passwd' }), /только http и https/);
  assert.throws(() => manager.save({ baseUrl: 'https://user:pass@example.com/v1' }), /Логин и пароль/);
});

test('test() loads models from /models with bearer key', async () => {
  const manager = new CustomApiManager({ dir: tempDir() });
  const original = global.fetch;
  let seen = null;
  global.fetch = async (url, init) => {
    seen = { url, headers: init.headers };
    return {
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ data: [{ id: 'z-model' }, { id: 'a-model' }] }),
      text: async () => '',
    };
  };
  try {
    const result = await manager.test({ baseUrl: 'https://example.com/v1', apiKey: 'sk-1' });
    assert.equal(seen.url, 'https://example.com/v1/models');
    assert.equal(seen.headers.authorization, 'Bearer sk-1');
    assert.deepEqual(result.models.map((model) => model.id), ['a-model', 'z-model']);
  } finally {
    global.fetch = original;
  }
});

test('stream() parses SSE deltas and returns full text', async () => {
  const dir = tempDir();
  const manager = new CustomApiManager({ dir });
  const api = manager.save({ name: 'Local', baseUrl: 'http://127.0.0.1:11434/v1', models: [{ id: 'm' }] });
  const original = global.fetch;
  global.fetch = async () => sseResponse([
    { choices: [{ delta: { content: 'Привет' } }] },
    { choices: [{ delta: { content: ', мир' } }] },
  ]);
  try {
    const chunks = [];
    const text = await manager.stream({
      apiId: api.id,
      model: 'm',
      messages: [{ role: 'user', content: 'hi' }],
      system: 'system prompt',
      onDelta: (piece) => chunks.push(piece),
    });
    assert.equal(text, 'Привет, мир');
    assert.deepEqual(chunks, ['Привет', ', мир']);
  } finally {
    global.fetch = original;
  }
});

test('stream() sends image attachments as image_url parts', async () => {
  const dir = tempDir();
  const manager = new CustomApiManager({ dir });
  const api = manager.save({ name: 'Vision', baseUrl: 'http://127.0.0.1:11434/v1', models: [{ id: 'm' }] });
  const original = global.fetch;
  let sent = null;
  global.fetch = async (url, init) => {
    sent = JSON.parse(init.body);
    return sseResponse([{ choices: [{ delta: { content: 'ok' } }] }]);
  };
  try {
    await manager.stream({
      apiId: api.id,
      model: 'm',
      messages: [{ role: 'user', content: 'что на картинке?' }],
      attachments: [
        { name: 'photo.png', mime: 'image/png', data: 'AAAA' },
        { name: 'notes.txt', mime: 'text/plain', data: 'BBBB' },
      ],
    });
    const last = sent.messages[sent.messages.length - 1];
    assert.equal(Array.isArray(last.content), true);
    assert.equal(last.content[0].text, 'что на картинке?');
    assert.equal(last.content[1].type, 'image_url');
    assert.equal(last.content[1].image_url.url, 'data:image/png;base64,AAAA');
    assert.equal(last.content.length, 2);
  } finally {
    global.fetch = original;
  }
});

test('stream() returns partial text when aborted', async () => {
  const dir = tempDir();
  const manager = new CustomApiManager({ dir });
  const api = manager.save({ name: 'Local', baseUrl: 'http://127.0.0.1:11434/v1', models: [{ id: 'm' }] });
  const original = global.fetch;
  const controller = new AbortController();
  global.fetch = async (url, init) => {
    const stream = new ReadableStream({
      start(inner) {
        inner.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"part"}}]}\n\n'));
        init.signal.addEventListener('abort', () => {
          const error = new Error('aborted');
          error.name = 'AbortError';
          inner.error(error);
        }, { once: true });
      },
    });
    return {
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'text/event-stream' }),
      body: stream,
    };
  };
  try {
    const chunks = [];
    const promise = manager.stream({
      apiId: api.id,
      model: 'm',
      messages: [{ role: 'user', content: 'hi' }],
      signal: controller.signal,
      onDelta: (piece) => {
        chunks.push(piece);
        controller.abort();
      },
    });
    const text = await promise;
    assert.equal(text, 'part');
    assert.deepEqual(chunks, ['part']);
  } finally {
    global.fetch = original;
  }
});
