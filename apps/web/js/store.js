const DB_NAME = "clauden";
const DB_VERSION = 1;

let dbPromise = null;

function tryOpen() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("chats")) {
        const store = db.createObjectStore("chats", { keyPath: "id" });
        store.createIndex("updatedAt", "updatedAt");
        store.createIndex("projectId", "projectId");
      }
      if (!db.objectStoreNames.contains("projects")) {
        db.createObjectStore("projects", { keyPath: "id" });
      }
      if (!db.objectStoreNames.contains("artifacts")) {
        db.createObjectStore("artifacts", { keyPath: "id" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function dropDb() {
  return new Promise((resolve) => {
    try {
      const req = indexedDB.deleteDatabase(DB_NAME);
      req.onsuccess = req.onerror = req.onblocked = () => resolve();
    } catch {
      resolve();
    }
  });
}

function openDb() {
  if (!dbPromise) {

    dbPromise = tryOpen().catch(async () => {
      await dropDb();
      return tryOpen();
    });
    dbPromise.catch(() => { dbPromise = null; });
  }
  return dbPromise;
}

async function tx(store, mode, fn) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(store, mode);
    const objectStore = transaction.objectStore(store);
    let result;
    try {
      result = fn(objectStore);
    } catch (err) {
      reject(err);
      return;
    }
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}

function request(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function isLostDataError(error) {
  const text = `${error?.name || ""} ${error?.message || error || ""}`;
  return /NotReadableError|Data lost|irrecoverable|missing file/i.test(text);
}

async function readAllSafe(storeName) {
  const db = await openDb();
  try {
    return (await request(db.transaction(storeName).objectStore(storeName).getAll())) || [];
  } catch (err) {
    if (!isLostDataError(err)) throw err;

    let keys = [];
    try {
      keys = (await request(
        db.transaction(storeName).objectStore(storeName).getAllKeys(),
      )) || [];
    } catch {
      keys = [];
    }

    const alive = [];
    for (const key of keys) {
      try {
        const value = await request(
          db.transaction(storeName).objectStore(storeName).get(key),
        );
        if (value) alive.push(value);
      } catch (recordError) {

        if (isLostDataError(recordError)) {
          await tx(storeName, "readwrite", (s) => s.delete(key)).catch(() => {});
        }
      }
    }

    if (keys.length && !alive.length) {
      try {
        db.close();
      } catch {

      }
      await dropDb();
      dbPromise = null;
    }
    return alive;
  }
}

async function readOneSafe(storeName, id) {
  const db = await openDb();
  try {
    return await request(db.transaction(storeName).objectStore(storeName).get(id));
  } catch (err) {
    if (!isLostDataError(err)) throw err;
    await tx(storeName, "readwrite", (s) => s.delete(id)).catch(() => {});
    return null;
  }
}

function isRecord(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value) && Boolean(value.id);
}

function byUpdatedAtDesc(a, b) {
  return (Number(b?.updatedAt) || 0) - (Number(a?.updatedAt) || 0);
}

function scrubThinking(chat) {
  if (!chat || (chat.provider || "clauden") === "claude") return false;
  let dirty = false;
  for (const message of chat.messages || []) {
    if (!message) continue;
    if (message.thinking || message.thinkingMs) {
      message.thinking = "";
      message.thinkingMs = 0;
      dirty = true;
    }
  }
  return dirty;
}

export const store = {
  async allChats() {
    const list = await readAllSafe("chats");
    const chats = [];
    for (const chat of list) {
      if (!isRecord(chat)) continue;
      if (scrubThinking(chat)) tx("chats", "readwrite", (s) => s.put(chat)).catch(() => {});
      chats.push(chat);
    }
    return chats.sort(byUpdatedAtDesc);
  },
  async getChat(id) {
    const chat = await readOneSafe("chats", id);
    if (scrubThinking(chat)) tx("chats", "readwrite", (s) => s.put(chat)).catch(() => {});
    return chat;
  },
  async putChat(chat) {
    await tx("chats", "readwrite", (s) => s.put(chat));
    return chat;
  },
  async deleteChat(id) {
    await tx("chats", "readwrite", (s) => s.delete(id));
  },
  async clearChats() {
    await tx("chats", "readwrite", (s) => s.clear());
  },
  async allProjects() {
    const list = await readAllSafe("projects");
    return list.filter(isRecord).sort(byUpdatedAtDesc);
  },
  async getProject(id) {
    return readOneSafe("projects", id);
  },
  async putProject(project) {
    await tx("projects", "readwrite", (s) => s.put(project));
    return project;
  },
  async deleteProject(id) {
    await tx("projects", "readwrite", (s) => s.delete(id));
  },
  async allArtifacts() {
    return readAllSafe("artifacts");
  },
  async putArtifact(item) {
    await tx("artifacts", "readwrite", (s) => s.put(item));
    return item;
  },
  async deleteArtifact(id) {
    await tx("artifacts", "readwrite", (s) => s.delete(id));
  },
};

const SETTINGS_KEY = "clauden.settings";

export const DEFAULT_SETTINGS = {
  theme: "dark",
  font: "default",
  style: "classic",
  accent: "#d97757",
  radius: 12,
  glass: 65,
  buttonStyle: "soft",
  toggleStyle: "classic",
  textSize: 100,
  motion: "full",
  model: "claude-opus-5-5",
  provider: "",
  claudeModel: "",
  customApiId: "",
  customModel: "",
  effort: 4096,
  webSearch: true,
  thinking: true,
  artifacts: true,
  visualizations: true,
  codeExecution: true,
  memory: true,
  aiArtifacts: true,
  networkEgress: true,
  notifications: false,
  breakReminders: false,
  breakInterval: 45,
  quietHours: false,
  quietDays: [],
  quietStart: 22 * 60,
  quietEnd: 8 * 60,
  nickname: "",
  fullName: "",
  work: "",
  preferenceNote: "",
  onboarded: false,
  lastPlan: "Free",
  seenInstall: false,
  density: "comfortable",
};

export function loadSettings() {
  try {
    const raw = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
    return { ...DEFAULT_SETTINGS, ...raw };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(patch) {
  const current = loadSettings();
  const next = { ...current, ...patch };
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(next));
  return next;
}

const SESSION_KEY = "clauden.session";

export function loadSession() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
  } catch {
    return null;
  }
}

export function saveSession(session) {
  if (!session) localStorage.removeItem(SESSION_KEY);
  else localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function memoryKey(userId) {
  return `clauden.memory.${userId || "anon"}`;
}

export function loadMemory(userId) {
  try {
    return JSON.parse(localStorage.getItem(memoryKey(userId)) || "[]");
  } catch {
    return [];
  }
}

export function saveMemory(userId, items) {
  localStorage.setItem(memoryKey(userId), JSON.stringify(items.slice(0, 60)));
}

export function draftKey(chatId) {
  return `clauden.draft.${chatId || "new"}`;
}

export function loadDraft(chatId) {
  return localStorage.getItem(draftKey(chatId)) || "";
}

export function saveDraft(chatId, text) {
  if (text) localStorage.setItem(draftKey(chatId), text);
  else localStorage.removeItem(draftKey(chatId));
}

const tasksKey = (userId) => `clauden.tasks.${userId || "anon"}`;
const scheduledKey = (userId) => `clauden.scheduled.${userId || "anon"}`;

function loadList(key) {
  try {
    const list = JSON.parse(localStorage.getItem(key) || "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function saveList(key, list) {
  localStorage.setItem(key, JSON.stringify(list.slice(0, 500)));
  return list;
}

export function loadTasks(userId) {
  return loadList(tasksKey(userId));
}

export function saveTasks(userId, tasks) {
  return saveList(tasksKey(userId), tasks);
}

export function loadScheduled(userId) {
  return loadList(scheduledKey(userId));
}

export function saveScheduled(userId, tasks) {
  return saveList(scheduledKey(userId), tasks);
}

const customizeKey = (userId) => `clauden.customize.${userId || "anon"}`;

export function loadCustomize(userId) {
  try {
    const raw = JSON.parse(localStorage.getItem(customizeKey(userId)) || "{}");
    return {
      skills: Array.isArray(raw.skills) ? raw.skills : [],
      plugins: Array.isArray(raw.plugins) ? raw.plugins : [],
    };
  } catch {
    return { skills: [], plugins: [] };
  }
}

export function saveCustomize(userId, data) {
  const next = {
    skills: (data.skills || []).slice(0, 80),
    plugins: (data.plugins || []).slice(0, 40),
  };
  localStorage.setItem(customizeKey(userId), JSON.stringify(next));
  return next;
}
