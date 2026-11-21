'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { UpdateManager, DEFAULT_FEED, normalizeNotes } = require('../src/main/updater');

class FakeUpdater {
  constructor() {
    this.handlers = new Map();
    this.feed = null;
    this.installed = false;
    this.autoDownload = false;
    this.autoInstallOnAppQuit = false;
  }

  on(event, handler) {
    this.handlers.set(event, handler);
  }

  setFeedURL(value) {
    this.feed = value;
  }

  checkForUpdates() {
    return Promise.resolve({});
  }

  quitAndInstall() {
    this.installed = true;
  }

  emit(event, payload) {
    this.handlers.get(event)?.(payload);
  }
}

function makeManager(options = {}) {
  const updater = new FakeUpdater();
  const manager = new UpdateManager({
    updater,
    packaged: true,
    appVersion: '1.0.0',
    platform: 'win32',
    ...options,
  });
  return { manager, updater };
}

test('normalizeNotes supports strings and arrays', () => {
  assert.equal(normalizeNotes('hello'), 'hello');
  assert.equal(normalizeNotes([{ version: '1.2.0', note: 'fix' }, 'more']), '1.2.0: fix\nmore');
  assert.equal(normalizeNotes(null), '');
});

test('sets generic feed and enables background download', () => {
  const { updater } = makeManager();
  assert.deepEqual(updater.feed, { provider: 'generic', url: DEFAULT_FEED });
  assert.equal(updater.autoDownload, true);
  assert.equal(updater.autoInstallOnAppQuit, true);
});

test('dev mode reports dev instead of checking', async () => {
  const { manager } = makeManager({ packaged: false });
  const state = await manager.check();
  assert.equal(state.status, 'dev');
  assert.equal(state.currentVersion, '1.0.0');
});

test('check moves through available, downloading and downloaded', async () => {
  const { manager, updater } = makeManager();
  const checking = manager.check();
  assert.equal(manager.status().status, 'checking');
  await checking;
  updater.emit('update-available', { version: '2.0.0', releaseNotes: 'Новое' });
  assert.equal(manager.status().status, 'available');
  assert.equal(manager.status().version, '2.0.0');
  assert.equal(manager.status().releaseNotes, 'Новое');

  updater.emit('download-progress', {
    percent: 42.6,
    bytesPerSecond: 1024,
    transferred: 2048,
    total: 4096,
  });
  assert.equal(manager.status().status, 'downloading');
  assert.equal(manager.status().percent, 43);

  updater.emit('update-downloaded', { version: '2.0.0' });
  assert.equal(manager.status().status, 'downloaded');
  assert.equal(manager.status().percent, 100);
});

test('not available reports latest', async () => {
  const { manager, updater } = makeManager();
  await manager.check();
  updater.emit('update-not-available', {});
  assert.equal(manager.status().status, 'latest');
  assert.ok(manager.status().checkedAt > 0);
});

test('error event is sanitized and reported', async () => {
  const { manager, updater } = makeManager();
  await manager.check();
  updater.emit('error', new Error('net::ERR_CONNECTION_REFUSED  https://example.com'));
  assert.equal(manager.status().status, 'error');
  assert.match(manager.status().error, /ERR_CONNECTION_REFUSED/);
});

test('install only works after download and calls quitAndInstall', async () => {
  const { manager, updater } = makeManager();
  assert.equal(manager.install().ok, false);
  updater.emit('update-downloaded', { version: '2.0.0' });
  assert.equal(manager.install().ok, true);
  await new Promise((resolve) => setTimeout(resolve, 90));
  assert.equal(updater.installed, true);
});

test('onDownloaded callback fires once per version', () => {
  const calls = [];
  const { updater } = makeManager({ onDownloaded: (state) => calls.push(state.version) });
  updater.emit('update-downloaded', { version: '2.0.0' });
  updater.emit('update-downloaded', { version: '2.0.0' });
  assert.deepEqual(calls, ['2.0.0']);
});

test('status returns a copy', () => {
  const { manager } = makeManager();
  const state = manager.status();
  state.status = 'hacked';
  assert.notEqual(manager.status().status, 'hacked');
});

test('linux without AppImage reports a clear reason instead of hitting the feed', async () => {
  const { manager } = makeManager({ platform: 'linux' });
  const state = await manager.check();
  assert.equal(state.status, 'error');
  assert.match(state.error, /AppImage/);
});
