'use strict';

class ApiError extends Error {
  constructor(message, status = 0, code = '') {
    super(message);
    this.status = status;
    this.code = code;
  }
}

class Api {
  constructor(store) {
    this.store = store;
    this.streams = new Map();
  }

  get base() {
    return this.store.apiUrl;
  }

  get token() {
    return this.store.token();
  }

  headers(extra = {}) {
    const token = this.token;
    const out = {
      accept: 'application/json',
      'user-agent': 'clauden-code-desktop/0.1.0',
      'x-device-name': 'Clauden Code',
      'x-device-platform': 'desktop',
      ...extra,
    };
    if (token) {
      out.authorization = `Bearer ${token}`;
      out['x-api-key'] = token;
    }
    return out;
  }

  async request(method, path, body, { timeout = 60_000 } = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetch(`${this.base}${path}`, {
        method,
        headers: this.headers(body ? { 'content-type': 'application/json' } : {}),
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      });
      const text = await response.text();
      let payload = null;
      try {
        payload = text ? JSON.parse(text) : null;
      } catch {
        payload = { raw: text };
      }
      if (!response.ok) {
        const error = payload?.error || {};
        const message =
          (typeof error === 'string' ? error : error.message) ||
          payload?.message ||
          `Ошибка ${response.status}`;
        throw new ApiError(message, response.status, error.code || error.type || '');
      }
      return payload?.data ?? payload;
    } catch (err) {
      if (err.name === 'AbortError') throw new ApiError('Сервер не ответил вовремя', 0, 'timeout');
      if (err instanceof ApiError) throw err;
      throw new ApiError(err.message || 'Нет связи с сервером', 0, 'network');
    } finally {
      clearTimeout(timer);
    }
  }

  async login(email, password) {
    const data = await this.request('POST', '/v1/auth/email', { email, password });
    if (data?.token) {
      await this.store.setToken(data.token);
      this.store.email = email;
      if (data.user) this.store.user = data.user;
    }
    return data;
  }

  async me() {
    const data = await this.request('GET', '/v1/auth/me');
    if (data?.user) this.store.user = data.user;
    return data?.user || data;
  }

  async logout() {
    try {
      await this.request('POST', '/v1/auth/logout', {});
    } catch {

    }
    this.store.clearAuth();
  }

  repos() {
    return this.request('GET', '/v1/code/repos');
  }

  github() {
    return this.request('GET', '/v1/code/github');
  }

  githubSave(token) {
    return this.request('POST', '/v1/code/github', { token });
  }

  githubRemove() {
    return this.request('DELETE', '/v1/code/github');
  }

  githubRepos() {
    return this.request('GET', '/v1/code/github/repos');
  }

  sessions() {
    return this.request('GET', '/v1/code/sessions', undefined, { timeout: 15_000 });
  }

  session(id) {
    return this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}`);
  }

  create(body) {
    return this.request('POST', '/v1/code/sessions', body, { timeout: 120_000 });
  }

  send(id, text, files) {
    const body = files?.length ? { text, files } : { text };
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/messages`, body);
  }

  stop(id) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/stop`, {});
  }

  permission(id, permissionId, decision) {
    return this.request(
      'POST',
      `/v1/code/sessions/${encodeURIComponent(id)}/permissions/${encodeURIComponent(permissionId)}`,
      { decision },
    );
  }

  form(id, formId, answer) {
    return this.request(
      'POST',
      `/v1/code/sessions/${encodeURIComponent(id)}/forms/${encodeURIComponent(formId)}`,
      { answer },
    );
  }

  diff(id) {
    return this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}/diff`, undefined, {
      timeout: 150_000,
    });
  }

  files(id, dir = '') {
    const query = dir ? `?path=${encodeURIComponent(dir)}` : '';
    return this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}/files${query}`);
  }

  readFile(id, filePath) {
    return this.request(
      'GET',
      `/v1/code/sessions/${encodeURIComponent(id)}/files/content?path=${encodeURIComponent(filePath)}`,
      undefined,
      { timeout: 60_000 },
    );
  }

  writeFile(id, filePath, content) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/files/content`, {
      path: filePath,
      content,
    });
  }

  createFile(id, dir, name, kind = 'file') {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/files/create`, {
      path: dir,
      name,
      kind,
    });
  }

  renameFile(id, filePath, name) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/files/rename`, {
      path: filePath,
      name,
    });
  }

  deleteFile(id, filePath) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/files/delete`, {
      path: filePath,
    });
  }

  commit(id, message) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/commit`, { message });
  }

  jobResult(id, jobId, body) {
    return this.request(
      'POST',
      `/v1/code/sessions/${encodeURIComponent(id)}/device/jobs/${encodeURIComponent(jobId)}`,
      body,
      { timeout: 30_000 },
    );
  }

  link(id) {
    return this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}/link`);
  }

  createLink(id, ttlHours = 24) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/link`, { ttlHours });
  }

  revokeLink(id) {
    return this.request('DELETE', `/v1/code/sessions/${encodeURIComponent(id)}/link`);
  }

  usage(id) {
    return this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}/usage`);
  }

  connectors(id) {
    return this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}/connectors`);
  }

  setConnectors(id, connectors) {
    return this.request(
      'POST',
      `/v1/code/sessions/${encodeURIComponent(id)}/connectors`,
      { connectors },
      { timeout: 60_000 },
    );
  }

  limits() {
    return this.request('GET', '/v1/usage');
  }

  remove(id) {
    return this.request('DELETE', `/v1/code/sessions/${encodeURIComponent(id)}`);
  }

  models(id) {
    return id
      ? this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}/models`)
      : this.request('GET', '/v1/code/models');
  }

  setModel(id, model) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/model`, model);
  }

  commands(id) {
    return this.request('GET', `/v1/code/sessions/${encodeURIComponent(id)}/commands`);
  }

  runCommand(id, name, text = '') {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/command`, { name, text });
  }

  compact(id) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/compact`, {});
  }

  undo(id) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/undo`, {});
  }

  redo(id) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/redo`, {});
  }

  upload(id, name, data) {
    return this.request('POST', `/v1/code/sessions/${encodeURIComponent(id)}/files/upload`, {
      name,
      data,
    });
  }

  subscribe(id, onEvent, onState) {
    this.unsubscribe(id);
    const controller = new AbortController();
    const entry = { controller, timer: null, closed: false };
    this.streams.set(id, entry);

    const connect = async () => {
      if (entry.closed) return;
      try {
        onState?.('connecting');
        const response = await fetch(`${this.base}/v1/code/sessions/${encodeURIComponent(id)}/events`, {
          headers: this.headers({ accept: 'text/event-stream' }),
          signal: controller.signal,
        });
        if (!response.ok) {
          const fatal = [401, 403, 404].includes(response.status);
          onState?.(fatal ? 'fatal' : 'offline', response.status);
          if (!fatal) schedule();
          return;
        }
        onState?.('live');
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n');
          let split;
          while ((split = buffer.indexOf('\n\n')) >= 0) {
            const chunk = buffer.slice(0, split);
            buffer = buffer.slice(split + 2);
            let event = 'message';
            const dataLines = [];
            for (const line of chunk.split('\n')) {
              if (line.startsWith('event:')) event = line.slice(6).trim();
              else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
            }
            if (!dataLines.length) continue;
            let payload = null;
            try {
              payload = JSON.parse(dataLines.join('\n'));
            } catch {
              payload = { raw: dataLines.join('\n') };
            }
            onEvent?.(event, payload);
          }
        }
        onState?.('offline');
        schedule();
      } catch (err) {
        if (entry.closed || controller.signal.aborted) return;
        onState?.('offline');
        schedule();
      }
    };

    const schedule = () => {
      if (entry.closed || entry.timer) return;
      entry.timer = setTimeout(() => {
        entry.timer = null;
        connect();
      }, 2500);
    };

    connect();
    return () => this.unsubscribe(id);
  }

  unsubscribe(id) {
    const entry = this.streams.get(id);
    if (!entry) return;
    entry.closed = true;
    clearTimeout(entry.timer);
    entry.controller.abort();
    this.streams.delete(id);
  }

  unsubscribeAll() {
    for (const id of [...this.streams.keys()]) this.unsubscribe(id);
  }
}

module.exports = { Api, ApiError };
