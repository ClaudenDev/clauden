'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const {
  ClaudeClient,
  ClaudeError,
  buildFirstPrompt,
  buildFollowUpPrompt,
  handleEvent,
  modelsForPlan,
  isModelId,
} = require('../src/main/claude-client');

function jsonResponse(data, status = 200) {
  const body = JSON.stringify(data);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    text: async () => body,
    arrayBuffer: async () => Buffer.from(body),
  };
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

function makeClient(routes) {
  const calls = [];
  const client = new ClaudeClient({
    transport: async (url, init = {}) => {
      calls.push({ url, method: init.method || 'GET', body: init.body });
      for (const route of routes) {
        if (route.match(url, init)) return route.response(url, init);
      }
      return jsonResponse({ error: { message: `no route: ${url}` } }, 404);
    },
    userAgent: 'test-agent',
    timezone: 'UTC',
    locale: 'ru-RU',
  });
  return { client, calls };
}

const ACCOUNT = {
  email_address: 'user@example.com',
  full_name: 'Test User',
  memberships: [{ organization: { uuid: 'org-1', capabilities: ['chat', 'pro'] } }],
};

const ORGANIZATIONS = [{ uuid: 'org-1', capabilities: ['chat', 'pro'] }];

test('buildFirstPrompt adds system, project and memory after the question', () => {
  const prompt = buildFirstPrompt({
    system: 'Будь краток.',
    project: 'Проект Аврора',
    memory: 'Любит кофе',
    messages: [
      { role: 'user', content: 'Привет' },
      { role: 'assistant', content: 'Здравствуйте' },
      { role: 'user', content: 'Что нового?' },
    ],
  });
  assert.match(prompt, /^Что нового\?/);
  assert.match(prompt, /— — —/);
  assert.match(prompt, /Будь краток\./);
  assert.match(prompt, /Контекст проекта:\nПроект Аврора/);
  assert.match(prompt, /Что я помню о пользователе:\nЛюбит кофе/);
});

test('buildFirstPrompt includes previous turns for a fresh conversation', () => {
  const prompt = buildFirstPrompt({
    messages: [
      { role: 'user', content: 'Меня зовут Андрей' },
      { role: 'assistant', content: 'Приятно познакомиться' },
      { role: 'user', content: 'Как меня зовут?' },
    ],
  });
  assert.match(prompt, /^Как меня зовут\?/);
  assert.match(prompt, /История диалога:/);
  assert.match(prompt, /Пользователь: Меня зовут Андрей/);
  assert.match(prompt, /Ассистент: Приятно познакомиться/);
});

test('buildFollowUpPrompt sends only the last user message', () => {
  const prompt = buildFollowUpPrompt({
    messages: [
      { role: 'user', content: 'Первое' },
      { role: 'assistant', content: 'Ответ' },
      { role: 'user', content: 'Второе' },
    ],
    attachments: [{ name: 'notes.pdf' }],
  });
  assert.equal(prompt, 'Второе\n\nВложения: notes.pdf');
});

test('discover reads account, organization and plan', async () => {
  const { client } = makeClient([
    { match: (url) => url.endsWith('/api/account'), response: () => jsonResponse(ACCOUNT) },
    { match: (url) => url.endsWith('/api/organizations'), response: () => jsonResponse(ORGANIZATIONS) },
  ]);
  const session = await client.discover();
  assert.equal(session.orgId, 'org-1');
  assert.equal(session.email, 'user@example.com');
  assert.equal(session.name, 'Test User');
  assert.equal(session.plan, 'pro');
});

test('modelsForPlan hides Opus on free and keeps server order', () => {
  const free = modelsForPlan('free', [
    { id: 'claude-opus-5-5', name: 'Opus' },
    { id: 'claude-sonnet-5-5', name: 'Server Sonnet' },
  ]);
  assert.equal(free[0].id, 'claude-sonnet-5-5');
  assert.equal(free[0].name, 'Server Sonnet');
  assert.ok(!free.some((model) => /opus/i.test(model.id)));

  const pro = modelsForPlan('pro', [{ id: 'claude-opus-5-5', name: 'Opus' }]);
  assert.ok(pro.some((model) => model.id === 'claude-opus-5-5'));
  assert.ok(isModelId('claude-sonnet-5-5'));
  assert.ok(!isModelId('Claude Sonnet 5.5'));
});

test('stream creates a conversation, emits deltas and returns artifact markup', async () => {
  const events = [
    { type: 'completion', completion: 'Привет' },
    { type: 'completion', completion: ', мир!' },
    {
      type: 'content_block_start',
      index: 0,
      content_block: { type: 'tool_use', name: 'artifacts', input: {} },
    },
    {
      type: 'content_block_delta',
      index: 0,
      delta: { type: 'input_json_delta', partial_json: '{"id":"a1","type":"text/html","title":"Страница","content":"<h1>Hi</h1>"}' },
    },
    { type: 'content_block_stop', index: 0 },
  ];
  const { client, calls } = makeClient([
    { match: (url) => url.endsWith('/api/account'), response: () => jsonResponse(ACCOUNT) },
    { match: (url) => url.endsWith('/api/organizations'), response: () => jsonResponse(ORGANIZATIONS) },
    {
      match: (url, init) => url.endsWith('/chat_conversations') && init.method === 'POST',
      response: () => jsonResponse({ uuid: 'conv-1' }),
    },
    {
      match: (url) => url.endsWith('/completion'),
      response: () => sseResponse(events),
    },
    {
      match: (url) => url.includes('/chat_conversations/conv-1'),
      response: () =>
        jsonResponse({
          chat_messages: [
            {
              sender: 'assistant',
              text: 'Привет, мир!',
              content: [
                { type: 'tool_use', name: 'artifacts', input: { id: 'a1', type: 'text/html', title: 'Страница', content: '<h1>Hi</h1>' } },
              ],
            },
          ],
        }),
    },
  ]);
  client.setSession(await client.discover());
  const deltas = [];
  let conversationId = '';
  const text = await client.stream({
    messages: [{ role: 'user', content: 'Скажи привет' }],
    model: 'claude-sonnet-5-5',
    system: 'Будь краток',
    onConversation: (id) => {
      conversationId = id;
    },
    onDelta: (piece) => deltas.push(piece),
  });
  assert.equal(conversationId, 'conv-1');
  assert.deepEqual(deltas, ['Привет', ', мир!']);
  assert.match(text, /Привет, мир!/);
  assert.match(text, /<artifact identifier="a1" type="text\/html" title="Страница">/);
  assert.match(text, /<h1>Hi<\/h1>/);
  const completion = calls.find((call) => call.url.endsWith('/completion'));
  const payload = JSON.parse(completion.body);
  assert.equal(payload.model, 'claude-sonnet-5-5');
  assert.match(payload.prompt, /^Скажи привет/);
  assert.match(payload.prompt, /Будь краток/);
});

test('message_limit throws rate_limit only for exceeded_limit', () => {
  const state = { artifactBuffers: new Map(), fileDisplays: new Map() };
  const within = JSON.stringify({
    type: 'message_limit',
    message_limit: {
      type: 'within_limit',
      resolved: { status: 'exceeded', percent: 100, limit: { resets_at: '2026-11-05T00:00:00Z' } },
      windows: {},
    },
  });
  assert.equal(handleEvent(within, state), null);

  const exceeded = JSON.stringify({
    type: 'message_limit',
    message_limit: {
      type: 'exceeded_limit',
      resetsAt: '2026-11-05T00:00:00Z',
      message: 'Лимит на сегодня закончился',
    },
  });
  assert.throws(
    () => handleEvent(exceeded, state),
    (err) => {
      assert.ok(err instanceof ClaudeError);
      assert.equal(err.kind, 'rate_limit');
      assert.equal(err.message, 'Лимит на сегодня закончился');
      assert.equal(err.retryAt, Date.parse('2026-11-05T00:00:00Z'));
      return true;
    },
  );

  const approachingZero = JSON.stringify({
    type: 'message_limit',
    message_limit: { type: 'approaching_limit', remaining: 0, resetsAt: '2026-11-05T00:00:00Z' },
  });
  assert.throws(() => handleEvent(approachingZero, state), (err) => err.kind === 'rate_limit');

  const approaching = JSON.stringify({
    type: 'message_limit',
    message_limit: { type: 'approaching_limit', remaining: 5, resetsAt: '2026-11-05T00:00:00Z' },
  });
  assert.equal(handleEvent(approaching, state), null);
});

test('http 401 on account maps to unauthorized', async () => {
  const { client } = makeClient([
    { match: (url) => url.endsWith('/api/account'), response: () => jsonResponse({ error: { message: 'nope' } }, 401) },
  ]);
  await assert.rejects(
    () => client.discover(),
    (err) => {
      assert.ok(err instanceof ClaudeError);
      assert.equal(err.kind, 'unauthorized');
      return true;
    },
  );
});

test('create_file display_content becomes an artifact tag', () => {
  const state = { artifactBuffers: new Map(), fileDisplays: new Map() };
  const start = JSON.stringify({
    type: 'content_block_start',
    index: 2,
    content_block: {
      type: 'tool_use',
      name: 'create_file',
      display_content: {
        json_block: JSON.stringify({ language: 'python', filename: 'main.py', code: 'print("hi")' }),
      },
    },
  });
  assert.equal(handleEvent(start, state), null);
  const stop = JSON.stringify({ type: 'content_block_stop', index: 2 });
  const tag = handleEvent(stop, state);
  assert.match(tag, /<artifact identifier="file:main\.py" type="text\/x-python" title="main\.py">/);
  assert.match(tag, /print\("hi"\)/);
});

test('importConversation keeps attachments, extracted text and nested assets', async () => {
  const conversation = {
    uuid: 'conv-1',
    name: 'Files',
    current_leaf_message_uuid: 'm2',
    chat_messages: [
      {
        uuid: 'm1',
        parent_message_uuid: null,
        index: 0,
        sender: 'human',
        text: 'посмотри файлы',
        created_at: '2026-11-06T10:00:00Z',
        attachments: [
          {
            id: 'f1',
            file_name: 'creator.py',
            file_type: 'text/x-python',
            extracted_content: 'print("creator")',
          },
        ],
      },
      {
        uuid: 'm2',
        parent_message_uuid: 'm1',
        index: 1,
        sender: 'assistant',
        text: '',
        created_at: '2026-11-06T10:01:00Z',
        content: [
          {
            type: 'tool_result',
            content: [
              { type: 'text', text: 'готово' },
              {
                type: 'image',
                source: {
                  type: 'base64',
                  media_type: 'image/png',
                  data: Buffer.from('img').toString('base64'),
                },
              },
            ],
          },
        ],
        files: [
          {
            file_uuid: 'f2',
            file_name: 'doc.pdf',
            document_asset: { url: 'https://cdn.example/doc.pdf' },
          },
        ],
      },
    ],
  };
  const { client } = makeClient([
    {
      match: (url) => url.includes('/chat_conversations/conv-1'),
      response: () => jsonResponse(conversation),
    },
  ]);

  const chat = await client.importConversation('org', { uuid: 'conv-1', updatedAt: 0 });

  assert.equal(chat.messages.length, 2);
  assert.match(chat.messages[0].content, /creator\.py/);
  assert.match(chat.messages[0].content, /print\("creator"\)/);
  assert.equal(chat.messages[0].files[0].text, 'print("creator")');
  assert.match(chat.messages[1].content, /готово/);
  assert.ok(
    chat.messages[1].files.some(
      (file) => file.mime === 'image/png' && file.url.startsWith('data:image/png;base64,'),
    ),
  );
  assert.ok(
    chat.messages[1].files.some(
      (file) => file.name === 'doc.pdf' && file.url === 'https://cdn.example/doc.pdf',
    ),
  );
});

test('downloadFile uses the /contents endpoint and byte Accept header', async () => {
  const calls = [];
  const client = new ClaudeClient({
    transport: async (url, init = {}) => {
      calls.push({ url, init });
      return {
        ok: true,
        status: 200,
        headers: new Headers({
          'content-type': 'application/octet-stream',
          'content-length': '3',
        }),
        arrayBuffer: async () => Buffer.from('abc'),
      };
    },
  });

  const got = await client.downloadFile('org', {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'a.bin',
    url: '',
  });

  assert.ok(got);
  assert.equal(got.data, Buffer.from('abc').toString('base64'));
  assert.match(calls[0].url, /\/files\/11111111-1111-1111-1111-111111111111\/contents$/);
  assert.equal(calls[0].init.headers.Accept, 'application/octet-stream, */*');
});

test('upload sends chat files to the conversation wiggle endpoint', async () => {
  const calls = [];
  const client = new ClaudeClient({
    transport: async (url, init = {}) => {
      calls.push({ url, init });
      return jsonResponse({
        file_uuid: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        file_name: 'quest.pdf',
        size_bytes: 3,
        path: '/mnt/user-data/uploads/quest.pdf',
      });
    },
  });

  const file = await client.upload('org-1', 'conv-1', {
    name: 'quest.pdf',
    mime: 'application/pdf',
    data: Buffer.from('abc').toString('base64'),
  });

  assert.equal(file.uuid, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
  assert.equal(file.path, '/mnt/user-data/uploads/quest.pdf');
  assert.match(calls[0].url, /\/organizations\/org-1\/conversations\/conv-1\/wiggle\/upload-file$/);
});

test('downloadFile falls back to wiggle download for sandbox paths', async () => {
  const calls = [];
  const client = new ClaudeClient({
    transport: async (url, init = {}) => {
      calls.push({ url, init });
      return {
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/pdf', 'content-length': '3' }),
        arrayBuffer: async () => Buffer.from('abc'),
      };
    },
  });

  const got = await client.downloadFile(
    'org-1',
    { id: 'file-1', name: 'quest.pdf', url: '', path: '/mnt/user-data/uploads/quest.pdf' },
    'conv-1',
  );

  assert.ok(got);
  assert.match(calls[0].url, /\/conversations\/conv-1\/wiggle\/download-file\?path=%2Fmnt%2Fuser-data%2Fuploads%2Fquest.pdf/);
});

test('http 429 without usage wording becomes throttled with Retry-After', async () => {
  let bodyUsed = false;
  const client = new ClaudeClient({
    transport: async (url) => {
      if (url.endsWith('/chat_conversations')) return jsonResponse({ uuid: 'conv-1' });
      return {
        ok: false,
        status: 429,
        headers: new Headers({
          'content-type': 'application/json',
          'retry-after': '42',
        }),
        async text() {

          if (bodyUsed) throw new TypeError('Body is unusable: Body has already been read');
          bodyUsed = true;
          return JSON.stringify({
            error: { type: 'rate_limit_error', message: 'Rate limited. Please try again later.' },
          });
        },
      };
    },
  });
  client.requireSession = async () => ({ orgId: 'org-1' });

  const before = Date.now();
  await assert.rejects(
    () => client.stream({ model: 'claude-sonnet-5-5', messages: [{ role: 'user', content: 'привет' }] }),
    (err) => {
      assert.ok(err instanceof ClaudeError);
      assert.equal(err.kind, 'throttled');
      assert.match(err.message, /Rate limited/);
      assert.ok(err.retryAt >= before + 41_000);
      assert.ok(err.retryAt <= Date.now() + 43_000);
      return true;
    },
  );
});

test('text attachment is inlined into the prompt instead of uploaded', async () => {
  const text = 'Секретный текст из notes.txt';
  const calls = [];
  const client = new ClaudeClient({
    transport: async (url, init = {}) => {
      calls.push({ url, body: init.body });
      if (url.endsWith('/chat_conversations')) return jsonResponse({ uuid: 'conv-1' });
      if (url.endsWith('/completion')) {
        return sseResponse([{ type: 'completion', completion: 'Прочитал файл' }]);
      }
      if (url.includes('/chat_conversations/conv-1')) {
        return jsonResponse({ chat_messages: [{ sender: 'assistant', text: 'Прочитал файл' }] });
      }
      throw new Error(`unexpected request: ${url}`);
    },
  });
  client.requireSession = async () => ({ orgId: 'org-1' });

  const answer = await client.stream({
    model: 'claude-sonnet-5-5',
    messages: [{ role: 'user', content: 'Прочитай notes.txt' }],
    attachments: [
      {
        name: 'notes.txt',
        mime: 'text/plain',
        data: Buffer.from(text).toString('base64'),
      },
    ],
  });

  assert.equal(answer, 'Прочитал файл');
  assert.ok(!calls.some((call) => /upload/i.test(call.url)), 'text attachment must not be uploaded');
  const completion = calls.find((call) => call.url.endsWith('/completion'));
  const payload = JSON.parse(completion.body);
  assert.match(payload.prompt, /notes\.txt/);
  assert.match(payload.prompt, /<attached_files>/);
  assert.match(payload.prompt, new RegExp(text));
});

test('completion sends uploaded files only as uuid list, without duplicate attachments', () => {
  const client = new ClaudeClient({ transport: async () => jsonResponse({}) });
  const req = client.completionRequest({
    orgId: 'org-1',
    conversationId: 'conv-1',
    model: 'claude-sonnet-5-5',
    prompt: 'посмотри файл',
    files: [{ uuid: 'file-uuid-1', name: 'quest.pdf', size: 3, mime: 'application/pdf' }],
    webSearch: false,
    thinking: false,
  });
  const payload = JSON.parse(req.init.body);
  assert.deepEqual(payload.files, ['file-uuid-1']);
  assert.deepEqual(payload.attachments, []);
});
