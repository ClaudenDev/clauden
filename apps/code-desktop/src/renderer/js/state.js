export const state = {
  auth: null,
  sessions: [],
  sessionsError: "",
  session: null,
  messages: [],
  permissions: [],
  forms: [],
  status: "idle",
  connection: "offline",
  error: "",
  streamText: "",
  streaming: false,
  job: null,
  pendingImages: [],
  models: [],
  usage: null,
  limits: null,
  limitsError: "",
  commands: [],
  github: null,
  githubRepos: [],
  sandboxInfo: null,

  tabs: [],
  activeTabId: null,

  tree: new Map(),
  expanded: new Set(),
  changed: new Map(),
  diff: null,

  view: "sessions",
  sideOpen: true,
  terminalOpen: false,
  cwd: "",
  repos: [],
};

let tabSeq = 0;

export function newTabId() {
  tabSeq += 1;
  return `t${tabSeq}`;
}

export function activeTab() {
  return state.tabs.find((tab) => tab.id === state.activeTabId) || null;
}

export function findTab(predicate) {
  return state.tabs.find(predicate) || null;
}

const listeners = new Map();

export function on(event, handler) {
  if (!listeners.has(event)) listeners.set(event, new Set());
  listeners.get(event).add(handler);
  return () => listeners.get(event)?.delete(handler);
}

export function emit(event, payload) {
  for (const handler of listeners.get(event) || []) {
    try {
      handler(payload);
    } catch (err) {
      console.error(`[bus] ${event}`, err);
    }
  }
}

export function resetSession() {
  state.session = null;
  state.messages = [];
  state.permissions = [];
  state.forms = [];
  state.status = "idle";
  state.connection = "offline";
  state.error = "";
  state.streamText = "";
  state.streaming = false;
  state.job = null;
  state.usage = null;
  state.tree = new Map();
  state.expanded = new Set();
  state.changed = new Map();
  state.diff = null;
  state.tabs = [];
  state.activeTabId = null;
}
