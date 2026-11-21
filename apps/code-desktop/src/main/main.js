'use strict';

const path = require('node:path');
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const {
  app,
  BrowserWindow,
  Menu,
  dialog,
  ipcMain,
  net,
  Notification,
  protocol,
  shell,
  clipboard,
} = require('electron');

const { Store } = require('./store');
const { Api, ApiError } = require('./api');
const { Terminal } = require('./terminal');
const { Workspace } = require('./workspace');
const { JobRunner } = require('./jobs');
const { resolveShell } = require('./shell');
const { sandboxInfo } = require('./sandbox');
const { UpdateManager } = require('./updater');
const { openRuntimeWindow } = require('./runtime');

const RENDERER = path.join(__dirname, '..', 'renderer');
const IS_MAC = process.platform === 'darwin';
const IS_DEV = process.argv.includes('--dev');
const IS_SMOKE = process.argv.includes('--smoke');

if (process.env.CLAUDEN_CODE_DATA) {
  app.setPath('userData', process.env.CLAUDEN_CODE_DATA);
}

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'app',
    privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true },
  },
]);

let mainWindow = null;
let store = null;
let api = null;
let terminal = null;
let workspace = null;
let jobRunner = null;
let updater = null;
const sessionRoots = new Map();
const sessionPollers = new Map();
const sessionWaiting = new Map();

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1040,
    minHeight: 640,
    show: false,
    backgroundColor: '#141414',
    frame: IS_MAC,
    titleBarStyle: IS_MAC ? 'hiddenInset' : 'default',
    trafficLightPosition: IS_MAC ? { x: 14, y: 12 } : undefined,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      spellcheck: false,
    },
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('maximize', () => send('win:maximize', true));
  mainWindow.on('unmaximize', () => send('win:maximize', false));
  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith('app://')) {
      event.preventDefault();
      if (/^https?:/i.test(url)) shell.openExternal(url);
    }
  });

  mainWindow.loadURL('app://clauden/index.html');
  if (IS_DEV) mainWindow.webContents.openDevTools({ mode: 'detach' });
}

function send(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(channel, payload);
}

function ok(data) {
  return { ok: true, data: data === undefined ? null : data };
}

function fail(err) {
  const status = err instanceof ApiError ? err.status : 0;
  const code = err instanceof ApiError ? err.code : '';
  return {
    ok: false,
    error: { message: err?.message || 'Неизвестная ошибка', status, code },
  };
}

function handle(channel, fn) {
  ipcMain.handle(channel, async (_event, payload) => {
    try {
      return ok(await fn(payload));
    } catch (err) {
      return fail(err);
    }
  });
}

function registerIpc() {

  handle('auth:state', () => ({
    authenticated: Boolean(store.token()),
    email: store.email,
    user: store.user,
    apiUrl: store.apiUrl,
    version: app.getVersion(),
    platform: process.platform,
  }));

  handle('auth:login', async ({ email, password, apiUrl }) => {
    if (apiUrl) store.apiUrl = apiUrl;
    const data = await api.login(String(email || '').trim(), String(password || ''));
    return { user: data?.user || null, email: store.email, apiUrl: store.apiUrl };
  });

  handle('auth:logout', async () => {
    api.unsubscribeAll();
    for (const timer of sessionPollers.values()) clearInterval(timer);
    sessionPollers.clear();
    sessionWaiting.clear();
    sessionRoots.clear();
    await api.logout();
    return true;
  });

  handle('auth:set-api', (url) => {
    store.apiUrl = url;
    return store.apiUrl;
  });

  handle('code:repos', () => api.repos());
  handle('code:github', () => api.github());
  handle('code:githubSave', ({ token }) => api.githubSave(token));
  handle('code:githubRemove', () => api.githubRemove());
  handle('code:githubRepos', () => api.githubRepos());
  handle('code:sessions', () => api.sessions());
  handle('code:session', (id) => api.session(id));
  handle('code:create', (body) => api.create(body));
  handle('code:send', ({ id, text, files }) => api.send(id, text, files));
  handle('code:stop', (id) => api.stop(id));
  handle('code:permission', ({ id, permissionId, decision }) =>
    api.permission(id, permissionId, decision),
  );
  handle('code:form', ({ id, formId, answer }) => api.form(id, formId, answer));
  handle('code:diff', (id) => api.diff(id));
  handle('code:files', ({ id, dir }) => api.files(id, dir));
  handle('code:readFile', ({ id, filePath }) => api.readFile(id, filePath));
  handle('code:writeFile', ({ id, filePath, content }) => api.writeFile(id, filePath, content));
  handle('code:createFile', ({ id, dir, name, kind }) => api.createFile(id, dir, name, kind));
  handle('code:renameFile', ({ id, filePath, name }) => api.renameFile(id, filePath, name));
  handle('code:deleteFile', ({ id, filePath }) => api.deleteFile(id, filePath));
  handle('code:commit', ({ id, message }) => api.commit(id, message));
  handle('code:link', (id) => api.link(id));
  handle('code:createLink', ({ id, ttlHours }) => api.createLink(id, ttlHours));
  handle('code:revokeLink', (id) => api.revokeLink(id));
  handle('code:usage', (id) => api.usage(id));
  handle('code:connectors', (id) => api.connectors(id));
  handle('code:setConnectors', ({ id, connectors }) => api.setConnectors(id, connectors));
  handle('account:limits', () => api.limits());
  handle('update:status', () => updater?.status() || { status: 'unsupported' });
  handle('update:check', () => (updater ? updater.check() : { status: 'unsupported' }));
  handle('update:install', () => {
    if (!updater) throw new Error('Автообновление недоступно');
    return updater.install();
  });
  handle('code:delete', (id) => api.remove(id));
  handle('code:models', (id) => api.models(id));
  handle('code:setModel', ({ id, model }) => api.setModel(id, model));
  handle('code:commands', (id) => api.commands(id));
  handle('code:runCommand', ({ id, name, text }) => api.runCommand(id, name, text));
  handle('code:compact', (id) => api.compact(id));
  handle('code:undo', (id) => api.undo(id));
  handle('code:redo', (id) => api.redo(id));
  handle('code:upload', ({ id, name, data }) => api.upload(id, name, data));
  handle('code:uploadLocal', async ({ id, file }) => {
    const name = path.basename(String(file || '')) || 'file';
    const stat = fs.statSync(file);
    if (stat.size > 20 * 1024 * 1024) throw new Error('Файл больше 20 МБ');
    const data = fs.readFileSync(file).toString('base64');
    return api.upload(id, name, data);
  });

  handle('runtime:open', ({ workspace: root } = {}) =>
    openRuntimeWindow({
      parent: mainWindow,
      workspace: root || workspace?.root || '',
    }),
  );

  handle('code:subscribe', (payload) => {
    const id = typeof payload === 'string' ? payload : payload?.id;
    const root = typeof payload === 'string' ? '' : payload?.root || '';
    if (!id) throw new Error('Не указана сессия');
    if (root) sessionRoots.set(id, root);

    const runQueued = (jobs) => {
      for (const job of Array.isArray(jobs) ? jobs : []) {
        if (job?.status !== 'queued') continue;
        jobRunner
          .handle(id, job, sessionRoots.get(id))
          .catch((err) => process.stdout.write(`[jobs] ${err.message}\n`));
      }
    };

    const waitOnce = async () => {
      if (!sessionWaiting.get(id)) return;
      try {
        const full = await api.session(id);
        sessionWaiting.set(id, full?.status === 'waiting_device');
        runQueued(full?.jobs);
      } catch {

      }
    };

    api.subscribe(
      id,
      (event, data) => {
        send('code:event', { id, event, payload: data });

        if (event === 'snapshot') {
          sessionWaiting.set(id, data?.status === 'waiting_device');
          runQueued(data?.jobs);
        } else if (event === 'job') {
          if (data?.status === 'queued' || data?.status === 'running') {
            sessionWaiting.set(id, true);
            if (data.status === 'queued') runQueued([data]);
          } else {
            sessionWaiting.set(id, false);
          }
        } else if (event === 'status') {
          sessionWaiting.set(id, data?.status === 'waiting_device');
          if (data?.status === 'waiting_device') waitOnce();
        }
      },
      (status, extra) => send('code:event', { id, event: 'connection', payload: { status, extra } }),
    );

    const previous = sessionPollers.get(id);
    if (previous) clearInterval(previous);
    const timer = setInterval(waitOnce, 4000);
    timer.unref?.();
    sessionPollers.set(id, timer);
    return true;
  });

  handle('code:unsubscribe', (id) => {
    const timer = sessionPollers.get(id);
    if (timer) clearInterval(timer);
    sessionPollers.delete(id);
    sessionWaiting.delete(id);
    api.unsubscribe(id);
    sessionRoots.delete(id);
    return true;
  });

  handle('term:cwd', () => terminal.cwd);
  handle('term:setCwd', (dir) => terminal.setCwd(dir));
  handle('term:pickFolder', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Папка для терминала',
      properties: ['openDirectory', 'createDirectory'],
    });
    if (result.canceled || !result.filePaths?.length) return null;
    return terminal.setCwd(result.filePaths[0]);
  });
  handle('term:run', (payload) => terminal.run(payload || {}));
  handle('term:kill', () => terminal.kill());

  handle('local:pickFolder', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Папка проекта на этом компьютере',
      properties: ['openDirectory', 'createDirectory'],
    });
    if (result.canceled || !result.filePaths?.length) return null;
    return result.filePaths[0];
  });

  handle('local:pickFiles', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Прикрепить файлы',
      properties: ['openFile', 'multiSelections'],
    });
    return result.canceled ? [] : result.filePaths || [];
  });

  handle('local:readBase64', async ({ file }) => {
    const filePath = String(file || '');
    const stat = fs.statSync(filePath);
    if (!stat.isFile()) throw new Error('Это не файл');
    if (stat.size > 5 * 1024 * 1024) throw new Error('Картинка больше 5 МБ');
    return {
      name: path.basename(filePath) || 'image',
      data: fs.readFileSync(filePath).toString('base64'),
    };
  });

  handle('local:blankRoot', () => {
    const dir = path.join(app.getPath('userData'), 'workspaces', `blank-${Date.now()}`);
    fs.mkdirSync(dir, { recursive: true });
    return dir;
  });

  handle('local:root', () => workspace.root);
  handle('local:setRoot', (dir) => {
    const root = workspace.setRoot(dir);
    terminal.setCwd(root);
    return root;
  });
  handle('local:list', (dir) => workspace.list(dir));
  handle('local:read', (file) => workspace.read(file));
  handle('local:write', ({ file, content }) => workspace.write(file, content));
  handle('local:create', ({ dir, name, kind }) => workspace.create(dir, name, kind));
  handle('local:rename', ({ file, name }) => workspace.rename(file, name));
  handle('local:remove', (file) => workspace.remove(file));
  handle('local:diff', () => workspace.diff());
  handle('local:commit', (message) => workspace.commit(message));
  handle('local:shell', () => {
    const shell = resolveShell();
    return { kind: shell.kind, path: shell.cmd };
  });
  handle('local:sandbox', () => sandboxInfo());

  handle('win:minimize', () => {
    mainWindow?.minimize();
    return true;
  });
  handle('win:toggleMaximize', () => {
    if (!mainWindow) return false;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
    return mainWindow.isMaximized();
  });
  handle('win:close', () => {
    mainWindow?.close();
    return true;
  });
  handle('sys:openExternal', (url) => {
    if (/^https?:/i.test(String(url))) shell.openExternal(String(url));
    return true;
  });
  handle('sys:copy', (text) => {
    clipboard.writeText(String(text ?? ''));
    return true;
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  app.whenReady().then(async () => {
    app.setName('Clauden Code');
    if (process.platform === 'win32') app.setAppUserModelId('space.teleopen.claudencode');
    if (!IS_MAC) Menu.setApplicationMenu(null);

    store = new Store();
    await store.init();
    api = new Api(store);
    terminal = new Terminal((channel, payload) => send(channel, payload));
    workspace = new Workspace();
    jobRunner = new JobRunner({
      api,
      workspace,
      autoApprove: IS_SMOKE,
      log: (message) => process.stdout.write(`[jobs] ${message}\n`),
      confirm: async ({ title, detail, allowAlways = false }) => {
        if (!mainWindow || mainWindow.isDestroyed()) return false;
        const buttons = allowAlways
          ? ['Отменить', 'Разрешить один раз', 'Разрешить всегда']
          : ['Отменить', 'Разрешить'];
        const result = await dialog.showMessageBox(mainWindow, {
          type: 'warning',
          title: 'Clauden Code',
          message: title,
          detail,
          buttons,
          defaultId: 0,
          cancelId: 0,
          noLink: true,
        });
        if (result.response === 0) return false;
        if (allowAlways && result.response === 2) return 'always';
        return 'once';
      },
    });
    global.__ccJobRunner = jobRunner;

    if (!IS_SMOKE) {
      updater = new UpdateManager({
        logger: (message) => process.stdout.write(`${message}\n`),
        getWindow: () => mainWindow,
        onDownloaded: (state) => {
          try {
            if (!Notification.isSupported()) return;
            const note = new Notification({
              title: 'Clauden Code',
              body: `Обновление ${state.version || ''} скачано. Установите его в Настройках → Обновления.`,
            });
            note.on('click', () => {
              if (!mainWindow || mainWindow.isDestroyed()) return;
              mainWindow.show();
              mainWindow.focus();
            });
            note.show();
          } catch (err) {
            process.stdout.write(`[update] notification failed: ${err.message}\n`);
          }
        },
      });
    }

    protocol.handle('app', (request) => {
      const url = new URL(request.url);
      let relative = decodeURIComponent(url.pathname);
      if (!relative || relative === '/') relative = '/index.html';
      const target = path.join(RENDERER, relative);
      if (!target.startsWith(RENDERER) || !fs.existsSync(target)) {
        return new Response('Not found', { status: 404 });
      }
      return net.fetch(pathToFileURL(target).toString());
    });

    registerIpc();

    if (IS_SMOKE) {
      const smoke = require('./smoke');
      let prepared = {};
      try {
        prepared = await smoke.prepare({ api, store, apiUrl: process.env.CLAUDEN_SMOKE_API });
      } catch (err) {
        process.stdout.write(`SMOKE PREPARE FAILED ${err.message}\n`);
      }
      createWindow();
      await smoke.finish({ api, app, window: mainWindow, prepared });
      return;
    }

    createWindow();

    if (process.argv.includes('--runtime')) {
      openRuntimeWindow({ parent: mainWindow }).catch((err) => {
        dialog.showErrorBox('Clauden Runtime', err.message);
      });
    }

    const canAutoUpdate = process.platform !== 'linux' || Boolean(process.env.APPIMAGE);
    if (canAutoUpdate && updater) {
      setTimeout(() => updater.check().catch(() => {}), 20_000);
      setInterval(() => updater.check().catch(() => {}), 6 * 60 * 60 * 1000);
    }

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    terminal?.kill();
    api?.unsubscribeAll();
    if (!IS_MAC) app.quit();
  });
}
