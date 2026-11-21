'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { app, safeStorage } = require('electron');

const FILE_NAME = 'clauden-code.json';

class Store {
  constructor() {
    this.file = path.join(app.getPath('userData'), FILE_NAME);
    this.data = this.#read();
    this._plain = '';
  }

  async init() {
    const raw = this.data.token;
    if (!raw) return this;
    if (!raw.startsWith('enc:')) {
      this._plain = raw;
      return this;
    }
    try {
      const answer = await safeStorage.decryptStringAsync(Buffer.from(raw.slice(4), 'base64'));
      this._plain = typeof answer === 'string' ? answer : answer?.result || '';
      if (answer?.shouldReEncrypt && this._plain) await this.setToken(this._plain);
    } catch {
      this._plain = '';
    }
    return this;
  }

  #read() {
    try {
      return JSON.parse(fs.readFileSync(this.file, 'utf8')) || {};
    } catch {
      return {};
    }
  }

  #flush() {
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      fs.writeFileSync(this.file, JSON.stringify(this.data, null, 2), { mode: 0o600 });
    } catch {

    }
  }

  get(key, fallback = null) {
    const value = this.data[key];
    return value === undefined ? fallback : value;
  }

  set(key, value) {
    this.data[key] = value;
    this.#flush();
  }

  remove(key) {
    delete this.data[key];
    this.#flush();
  }

  get apiUrl() {
    return String(this.data.apiUrl || 'https://clauden.best').replace(/\/+$/, '');
  }

  set apiUrl(value) {
    this.data.apiUrl = String(value || 'https://clauden.best').replace(/\/+$/, '');
    this.#flush();
  }

  async setToken(token) {
    if (!token) {
      this._plain = '';
      delete this.data.token;
      this.#flush();
      return;
    }
    this._plain = token;
    let payload = token;
    try {

      const encrypted = await safeStorage.encryptStringAsync(token);
      payload = `enc:${encrypted.toString('base64')}`;
    } catch {
      payload = token;
    }
    this.data.token = payload;
    this.#flush();
  }

  token() {
    return this._plain || '';
  }

  clearAuth() {
    this._plain = '';
    delete this.data.token;
    delete this.data.email;
    delete this.data.user;
    this.#flush();
  }

  get email() {
    return this.data.email || '';
  }

  set email(value) {
    this.data.email = value || '';
    this.#flush();
  }

  get user() {
    return this.data.user || null;
  }

  set user(value) {
    if (value) this.data.user = value;
    else delete this.data.user;
    this.#flush();
  }
}

module.exports = { Store };
