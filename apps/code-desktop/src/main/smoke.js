'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const SMOKE_EMAIL = process.env.CLAUDEN_SMOKE_EMAIL || 'dev@clauden.best';
const SMOKE_PASSWORD = process.env.CLAUDEN_SMOKE_PASSWORD || 'secret';
const OUT = process.env.CLAUDEN_SMOKE_OUT || path.join('/tmp', 'clauden-code-smoke.png');
const REPORT = process.env.CLAUDEN_SMOKE_REPORT || path.join('/tmp', 'clauden-code-smoke.json');

function waitFor(predicate, timeout = 6000, interval = 80) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const tick = () => {
      let value = null;
      try {
        value = predicate();
      } catch {
        value = null;
      }
      if (value) return resolve(value);
      if (Date.now() - started > timeout) return reject(new Error('timeout'));
      setTimeout(tick, interval);
    };
    tick();
  });
}

async function prepare({ api, store, apiUrl }) {
  const result = {};
  if (apiUrl) store.apiUrl = apiUrl;

  const login = await api.login(SMOKE_EMAIL, SMOKE_PASSWORD);
  result.login = Boolean(login?.token);
  result.email = store.email;

  const me = await api.me();
  result.me = me?.email || null;

  const repos = await api.repos();
  result.repos = Array.isArray(repos) ? repos.length : 0;

  const session = await api.create({ repo: 'demo', prompt: 'Смоук-тест Clauden Code' });
  result.sessionId = session?.id || null;

  try {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'clauden-local-'));
    fs.writeFileSync(path.join(root, 'README.md'), '# Демо\n', 'utf8');
    fs.mkdirSync(path.join(root, 'src'), { recursive: true });
    fs.writeFileSync(path.join(root, 'src', 'app.js'), 'export const hello = 1;\n', 'utf8');
    const git = (...args) =>
      execFileSync('git', ['-C', root, ...args], { stdio: 'ignore', timeout: 30_000 });
    git('init', '-q');
    git('add', '-A');
    git('-c', 'user.email=smoke@clauden.best', '-c', 'user.name=Smoke', 'commit', '-qm', 'init');
    fs.appendFileSync(path.join(root, 'src', 'app.js'), 'export const bye = 2;\n');

    const local = await api.create({
      repo: 'ssh',
      label: 'smoke-local',
      target: 'Этот компьютер',
      root,
      local: true,
    });
    result.localSessionId = local?.id || null;
    result.localRoot = root;
    result.localRemote = local?.remote || null;
  } catch (err) {
    result.localError = err.message || String(err);
  }
  return result;
}

async function finish({ api, app, window, prepared }) {
  const report = { ...prepared, steps: {}, consoleErrors: [], ok: false };

  try {
    const sessions = await api.sessions();
    report.steps.sessions = Array.isArray(sessions) ? sessions.length : 0;

    const id = prepared.sessionId;
    if (id) {
      const events = [];
      api.subscribe(
        id,
        (event) => events.push(event),
        (status) => events.push(`connection:${status}`),
      );

      const snapshot = await waitFor(() => (events.includes('snapshot') ? true : null), 5000).catch(
        () => false,
      );
      report.steps.snapshot = Boolean(snapshot);

      await api.send(id, 'Проверь сборку');
      const delta = await waitFor(() => (events.includes('delta') ? true : null), 6000).catch(
        () => false,
      );
      report.steps.delta = Boolean(delta);
      report.steps.eventTypes = [...new Set(events)].slice(0, 12);

      const diff = await api.diff(id);
      report.steps.diffFiles = diff?.files?.length || 0;

      const files = await api.files(id, '');
      report.steps.treeEntries = files?.entries?.length || 0;

      const link = await api.createLink(id, 24);
      report.steps.link = Boolean(link?.url);

      api.unsubscribe(id);
    }

    if (prepared.localSessionId) {
      const localId = prepared.localSessionId;
      const events = [];
      let deltaText = "";
      const runner = global.__ccJobRunner;

      api.subscribe(
        localId,
        (event, data) => {
          events.push(event);
          if (event === "delta" && data?.text) deltaText += data.text;
          if (event === "job" && data?.status === "queued") {
            report.steps.localJob = true;
            runner
              ?.handle(localId, data, prepared.localRoot)
              .catch((err) => (report.steps.localJobError = err.message));
          }
        },
        (status) => events.push(`connection:${status}`),
      );

      report.steps.localSnapshot = Boolean(
        await waitFor(() => (events.includes("snapshot") ? true : null), 5000).catch(() => false),
      );
      await api.send(localId, "Проверь папку");
      report.steps.localTurn = Boolean(
        await waitFor(() => (events.includes("delta") ? true : null), 15_000).catch(() => false),
      );
      report.steps.localDelta = deltaText.slice(0, 160);

      report.steps.localCyrillic = /привет/.test(deltaText);

      try {
        const { Workspace } = require("./workspace");
        const workspace = new Workspace();
        workspace.setRoot(prepared.localRoot);
        report.steps.localTree = workspace.list("").entries.length;
        const diff = await workspace.diff();
        report.steps.localDiffFiles = diff?.files?.length || 0;
        const read = workspace.read("README.md");
        report.steps.localRead = read.content.trim().startsWith("#");
      } catch (err) {
        report.steps.localFsError = err.message;
      }

      api.unsubscribe(localId);
    }

    await new Promise((resolve) => setTimeout(resolve, 2500));
    if (window && !window.isDestroyed()) {
      const image = await window.webContents.capturePage();
      fs.writeFileSync(OUT, image.toPNG());
      report.screenshot = OUT;
    }

    report.consoleErrors = await window?.webContents
      .executeJavaScript('(window.__ccErrors || []).slice(0, 5)')
      .catch(() => []);

    report.ok =
      report.login &&
      report.steps.snapshot &&
      report.steps.delta &&
      report.steps.diffFiles > 0 &&
      report.steps.treeEntries > 0 &&
      report.steps.localJob === true &&
      report.steps.localCyrillic === true &&
      report.steps.localDiffFiles > 0;
  } catch (err) {
    report.error = err.message || String(err);
  }

  fs.writeFileSync(REPORT, JSON.stringify(report, null, 2));
  process.stdout.write(`SMOKE ${JSON.stringify(report)}\n`);
  setTimeout(() => app.exit(report.ok ? 0 : 1), 120);
  return report;
}

module.exports = { prepare, finish, SMOKE_EMAIL };
