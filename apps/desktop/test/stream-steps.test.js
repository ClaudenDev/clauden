'use strict';

const test = require('node:test');
const assert = require('node:assert');

const { ClaudeClient } = require('../src/main/claude-client');

function sseResponse(events) {
  const encoder = new TextEncoder();
  const chunks = events.map((event) => encoder.encode(`data: ${JSON.stringify(event)}\n`));
  let index = 0;
  return {
    ok: true,
    status: 200,
    headers: { get: () => null },
    body: {
      getReader() {
        return {
          async read() {
            if (index >= chunks.length) return { done: true, value: undefined };
            return { done: false, value: chunks[index++] };
          },
          async cancel() {},
        };
      },
    },
    async text() {
      return '';
    },
  };
}

function jsonResponse(payload) {
  return {
    ok: true,
    status: 200,
    headers: { get: () => null },
    body: null,
    async text() {
      return JSON.stringify(payload);
    },
  };
}

const EVENTS = [
  { type: 'content_block_start', index: 0, content_block: { type: 'tool_use', id: 't1', name: 'web_search_v0', input: {} } },
  { type: 'content_block_delta', index: 0, delta: { type: 'input_json_delta', partial_json: '{"query":"погода Москва"}' } },
  { type: 'content_block_stop', index: 0, content_block: { type: 'tool_use' } },
  { type: 'content_block_start', index: 1, content_block: { type: 'tool_result', tool_use_id: 't1', content: [{ type: 'text', text: 'Найдено 3 источника' }] } },
  { type: 'content_block_stop', index: 1 },
  { type: 'content_block_start', index: 2, content_block: { type: 'text', text: '' } },
  { type: 'content_block_delta', index: 2, delta: { type: 'text_delta', text: 'Погода: +12°' } },
  { type: 'message_stop' },
];

function makeClient(events = EVENTS) {
  const client = new ClaudeClient({
    transport: async (url) => {
      if (String(url).includes('/completion')) return sseResponse(events);
      return jsonResponse({ chat_messages: [] });
    },
  });
  client.session = { orgId: 'org-1', plan: 'pro' };
  return client;
}

test('шаги инструментов приходят в onStep с заголовком и результатом', async () => {
  const client = makeClient();
  const steps = [];
  const deltas = [];

  const text = await client.stream({
    conversationId: 'conv-1',
    model: 'claude-sonnet-5-5',
    messages: [{ role: 'user', content: 'привет' }],
    onDelta: (piece) => deltas.push(piece),
    onStep: (step) => steps.push(step),
  });

  assert.deepStrictEqual(deltas, ['Погода: +12°']);
  assert.strictEqual(text, 'Погода: +12°');

  const first = steps[0];
  assert.strictEqual(first.id, 't1');
  assert.strictEqual(first.status, 'running');

  const last = steps[steps.length - 1];
  assert.strictEqual(last.status, 'done');
  assert.strictEqual(last.title, 'Ищу: погода Москва');
  assert.strictEqual(last.result, 'Найдено 3 источника');
  assert.match(last.detail, /погода Москва/);
});

test('незавершённый инструмент закрывается, когда пошёл текст', async () => {
  const client = makeClient([
    { type: 'content_block_start', index: 0, content_block: { type: 'tool_use', id: 't9', name: 'bash', input: { command: 'ls -la' } } },
    { type: 'content_block_stop', index: 0 },
    { type: 'content_block_delta', index: 1, delta: { type: 'text_delta', text: 'готово' } },
  ]);
  const steps = [];
  await client.stream({
    conversationId: 'conv-2',
    model: 'claude-sonnet-5-5',
    messages: [{ role: 'user', content: 'hi' }],
    onStep: (step) => steps.push(step),
  });

  assert.strictEqual(steps[0].title, 'ls -la');
  assert.strictEqual(steps[steps.length - 1].status, 'done');
});

test('ошибка инструмента помечает шаг failed', async () => {
  const client = makeClient([
    { type: 'content_block_start', index: 0, content_block: { type: 'tool_use', id: 't5', name: 'webfetch', input: { url: 'https://x.y' } } },
    { type: 'content_block_stop', index: 0 },
    { type: 'tool_result', tool_result: { tool_use_id: 't5', is_error: true, content: 'timeout' } },
    { type: 'content_block_delta', index: 1, delta: { type: 'text_delta', text: 'не вышло' } },
  ]);
  const steps = [];
  await client.stream({
    conversationId: 'conv-3',
    model: 'claude-sonnet-5-5',
    messages: [{ role: 'user', content: 'hi' }],
    onStep: (step) => steps.push(step),
  });

  const failed = steps.find((step) => step.status === 'failed');
  assert.ok(failed, 'шаг должен быть failed');
  assert.strictEqual(failed.result, 'timeout');
});
