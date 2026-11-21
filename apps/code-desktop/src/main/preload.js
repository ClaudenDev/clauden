'use strict';

const { contextBridge, ipcRenderer } = require('electron');

const invoke = (channel, payload) => ipcRenderer.invoke(channel, payload);

const updateListeners = new Set();
ipcRenderer.on('update:event', (_event, payload) => {
  for (const listener of updateListeners) {
    try {
      listener(payload);
    } catch {

    }
  }
});

function on(channel, handler) {
  const listener = (_event, payload) => handler(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
}

contextBridge.exposeInMainWorld('clauden', {
  version: process.env.CLAUDEN_CODE_VERSION || '',
  platform: process.platform,

  auth: {
    state: () => invoke('auth:state'),
    login: (payload) => invoke('auth:login', payload),
    logout: () => invoke('auth:logout'),
    setApiUrl: (url) => invoke('auth:set-api', url),
  },

  account: {
    limits: () => invoke('account:limits'),
  },

  code: {
    repos: () => invoke('code:repos'),
    github: () => invoke('code:github'),
    githubSave: (token) => invoke('code:githubSave', { token }),
    githubRemove: () => invoke('code:githubRemove'),
    githubRepos: () => invoke('code:githubRepos'),
    sessions: () => invoke('code:sessions'),
    session: (id) => invoke('code:session', id),
    create: (body) => invoke('code:create', body),
    send: (id, text, files) => invoke('code:send', { id, text, files }),
    stop: (id) => invoke('code:stop', id),
    permission: (id, permissionId, decision) =>
      invoke('code:permission', { id, permissionId, decision }),
    form: (id, formId, answer) => invoke('code:form', { id, formId, answer }),
    diff: (id) => invoke('code:diff', id),
    files: (id, dir) => invoke('code:files', { id, dir }),
    readFile: (id, filePath) => invoke('code:readFile', { id, filePath }),
    writeFile: (id, filePath, content) => invoke('code:writeFile', { id, filePath, content }),
    createFile: (id, dir, name, kind) => invoke('code:createFile', { id, dir, name, kind }),
    renameFile: (id, filePath, name) => invoke('code:renameFile', { id, filePath, name }),
    deleteFile: (id, filePath) => invoke('code:deleteFile', { id, filePath }),
    commit: (id, message) => invoke('code:commit', { id, message }),
    usage: (id) => invoke('code:usage', id),
    connectors: (id) => invoke('code:connectors', id),
    setConnectors: (id, connectors) => invoke('code:setConnectors', { id, connectors }),
    deleteSession: (id) => invoke('code:delete', id),
    models: (id) => invoke('code:models', id),
    setModel: (id, model) => invoke('code:setModel', { id, model }),
    commands: (id) => invoke('code:commands', id),
    runCommand: (id, name, text) => invoke('code:runCommand', { id, name, text }),
    compact: (id) => invoke('code:compact', id),
    undo: (id) => invoke('code:undo', id),
    redo: (id) => invoke('code:redo', id),
    upload: (id, name, data) => invoke('code:upload', { id, name, data }),
    uploadLocal: (id, file) => invoke('code:uploadLocal', { id, file }),
    link: (id) => invoke('code:link', id),
    createLink: (id, ttlHours) => invoke('code:createLink', { id, ttlHours }),
    revokeLink: (id) => invoke('code:revokeLink', id),
    subscribe: (id, root) => invoke('code:subscribe', { id, root }),
    unsubscribe: (id) => invoke('code:unsubscribe', id),
    onEvent: (handler) => on('code:event', handler),
  },

  term: {
    cwd: () => invoke('term:cwd'),
    setCwd: (dir) => invoke('term:setCwd', dir),
    pickFolder: () => invoke('term:pickFolder'),
    run: (payload) => invoke('term:run', payload),
    kill: () => invoke('term:kill'),
    onData: (handler) => on('term:data', handler),
    onStart: (handler) => on('term:start', handler),
    onExit: (handler) => on('term:exit', handler),
    onCwd: (handler) => on('term:cwd', handler),
  },

  local: {
    pickFolder: () => invoke('local:pickFolder'),
    pickFiles: () => invoke('local:pickFiles'),
    readBase64: (file) => invoke('local:readBase64', { file }),
    blankRoot: () => invoke('local:blankRoot'),
    root: () => invoke('local:root'),
    setRoot: (dir) => invoke('local:setRoot', dir),
    list: (dir) => invoke('local:list', dir),
    read: (file) => invoke('local:read', file),
    write: (file, content) => invoke('local:write', { file, content }),
    create: (dir, name, kind) => invoke('local:create', { dir, name, kind }),
    rename: (file, name) => invoke('local:rename', { file, name }),
    remove: (file) => invoke('local:remove', file),
    diff: () => invoke('local:diff'),
    commit: (message) => invoke('local:commit', message),
    shell: () => invoke('local:shell'),
    sandbox: () => invoke('local:sandbox'),
  },

  runtime: {
    open: (workspace) => invoke('runtime:open', { workspace }),
  },

  win: {
    minimize: () => invoke('win:minimize'),
    toggleMaximize: () => invoke('win:toggleMaximize'),
    close: () => invoke('win:close'),
    onMaximize: (handler) => on('win:maximize', handler),
  },

  updates: {
    status: () => invoke('update:status'),
    check: () => invoke('update:check'),
    install: () => invoke('update:install'),
    onEvent: (handler) => {
      if (typeof handler !== 'function') return () => {};
      updateListeners.add(handler);
      return () => updateListeners.delete(handler);
    },
  },

  sys: {
    openExternal: (url) => invoke('sys:openExternal', url),
    copy: (text) => invoke('sys:copy', text),
  },
});
