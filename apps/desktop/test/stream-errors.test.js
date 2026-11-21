'use strict';

const test = require('node:test');
const assert = require('node:assert');

const { ClaudeClient, readSse } = require('../src/main/claude-client');

function sseResponse(events, contentType = 'text/event-stream') {
  const encoder = new TextEncoder();
  const chunks = events.map((event) => encoder.encode(`data: ${JSON.stringify(event)}\n`));
  let index = 0;
  return {
    ok: true,
    status: 200,
    redirected: false,
    headers: { get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null) },
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

function makeClient(response) {
  const client = new ClaudeClient({
    transport: async () => response,
    log: () => {},
  });
  client.setSession({ orgId: 'org-1', plan: 'pro' });
  return client;
}

test('HTML вместо SSE — это истёкшая сессия, а не пустой ответ', async () => {
  const client = makeClient({
    ok: true,
    status: 200,
    redirected: true,
    headers: { get: () => 'text/html' },
    body: null,
    async text() {
      return '<!doctype html><html><body>login</body></html>';
    },
  });
  await assert.rejects(
    () => client.stream({ conversationId: 'conv-1', messages: [{ role: 'user', content: 'привет' }] }),
    (err) => err.kind === 'unauthorized' && /страницу входа/.test(err.message),
  );
});

test('пустой поток не завершается молча', async () => {
  const client = makeClient(sseResponse([]));
  await assert.rejects(
    () => client.stream({ conversationId: 'conv-1', messages: [{ role: 'user', content: 'привет' }] }),
    (err) => /пустой ответ/.test(err.message),
  );
});

test('текстовый .py встраивается в промпт, а не загружается в wiggle', async () => {
  const calls = [];
  const client = new ClaudeClient({
    log: () => {},
    transport: async (url, init = {}) => {
      calls.push(url);
      if (url.includes('/upload')) throw new Error('upload не должен вызываться для .py');
      if (url.endsWith('/chat_conversations')) {
        return {
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          body: null,
          async text() {
            return JSON.stringify({ uuid: 'conv-1' });
          },
        };
      }
      if (url.endsWith('/completion')) {
        const body = JSON.parse(init.body);
        assert.match(body.prompt, /<file name="calc\.py">/);
        assert.match(body.prompt, /print\("hi"\)/);
        return sseResponse([
          { type: 'content_block_start', index: 0, content_block: { type: 'text', text: '' } },
          { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: 'ок' } },
          { type: 'message_stop' },
        ]);
      }
      return {
        ok: false,
        status: 404,
        headers: { get: () => null },
        body: null,
        async text() {
          return '';
        },
      };
    },
  });
  client.setSession({ orgId: 'org-1', plan: 'pro' });
  const text = await client.stream({
    model: 'claude-sonnet-5-5',
    messages: [{ role: 'user', content: 'Что делает этот файл?' }],
    attachments: [
      {
        name: 'calc.py',
        mime: 'text/x-python',
        data: Buffer.from('print("hi")').toString('base64'),
      },
    ],
  });
  assert.equal(text, 'ок');
  assert.equal(calls.some((url) => url.includes('/upload')), false);
});

test('молчащий поток обрывается по таймауту', async () => {
  const response = {
    body: {
      getReader() {
        return {
          read: () => new Promise(() => {}),
          async cancel() {},
        };
      },
    },
  };
  await assert.rejects(
    () => readSse(response, async () => {}, { idleMs: 30 }),
    (err) => /перестал отвечать/.test(err.message),
  );
});
