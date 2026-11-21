import path from "node:path";

import { DocStore } from "./store.mjs";

const MAX_PER_USER = 2000;

function sanitizeConversation(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = String(raw.id || "").trim();
  if (!id) return null;
  return {
    id,
    title: String(raw.title || "").slice(0, 400),
    createdAt: Number(raw.createdAt) || Date.now(),
    updatedAt: Number(raw.updatedAt) || Date.now(),
    pinned: Boolean(raw.pinned),
    provider: String(raw.provider || "clauden").slice(0, 32),
    projectId: raw.projectId ? String(raw.projectId) : "",
    claudeConversationId: raw.claudeConversationId
      ? String(raw.claudeConversationId)
      : "",
    messages: Array.isArray(raw.messages) ? raw.messages.slice(0, 4000) : [],
  };
}

export class ChatBackupStore {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
    this.docs = new DocStore(path.join(config.dataDir, "chats"));
  }

  bucket(userId) {
    return this.docs.get(userId) || { conversations: {}, deleted: {} };
  }

  list(userId) {
    const bucket = this.bucket(userId);
    return {
      data: Object.values(bucket.conversations).sort(
        (a, b) => (b.updatedAt || 0) - (a.updatedAt || 0),
      ),
      deleted: Object.entries(bucket.deleted).map(([id, deletedAt]) => ({ id, deletedAt })),
    };
  }

  put(userId, conversations) {
    const bucket = this.bucket(userId);
    let added = 0;
    for (const raw of Array.isArray(conversations) ? conversations : []) {
      const chat = sanitizeConversation(raw);
      if (!chat) continue;
      const known = bucket.conversations[chat.id];
      if (!known || chat.updatedAt >= (known.updatedAt || 0)) {
        bucket.conversations[chat.id] = chat;
        delete bucket.deleted[chat.id];
        added += 1;
      }
    }
    const ids = Object.keys(bucket.conversations);
    if (ids.length > MAX_PER_USER) {
      ids
        .sort((a, b) => (bucket.conversations[a].updatedAt || 0) - (bucket.conversations[b].updatedAt || 0))
        .slice(0, ids.length - MAX_PER_USER)
        .forEach((id) => delete bucket.conversations[id]);
    }
    this.docs.put(userId, bucket);
    return { added, total: Object.keys(bucket.conversations).length };
  }

  remove(userId, id) {
    const bucket = this.bucket(userId);
    delete bucket.conversations[id];
    bucket.deleted[id] = Date.now();
    this.docs.put(userId, bucket);
    return true;
  }

  clear(userId) {
    const bucket = this.bucket(userId);
    for (const id of Object.keys(bucket.conversations)) bucket.deleted[id] = Date.now();
    bucket.conversations = {};
    this.docs.put(userId, bucket);
    return true;
  }
}

export class ProjectBackupStore extends ChatBackupStore {
  constructor({ config, log }) {
    super({ config, log });
    this.docs = new DocStore(path.join(config.dataDir, "projects"));
  }

  put(userId, projects) {
    const bucket = this.bucket(userId);
    for (const raw of Array.isArray(projects) ? projects : []) {
      if (!raw?.id) continue;
      bucket.conversations[String(raw.id)] = {
        id: String(raw.id),
        title: String(raw.title || "").slice(0, 400),
        description: String(raw.description || "").slice(0, 4000),
        instructions: String(raw.instructions || "").slice(0, 20000),
        files: Array.isArray(raw.files) ? raw.files.slice(0, 200) : [],
        createdAt: Number(raw.createdAt) || Date.now(),
        updatedAt: Number(raw.updatedAt) || Date.now(),
      };
    }
    this.docs.put(userId, bucket);
    return { total: Object.keys(bucket.conversations).length };
  }
}

export class MemoryStore {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
    this.docs = new DocStore(path.join(config.dataDir, "memory"));
  }

  list(userId) {
    const bucket = this.docs.get(userId) || { items: [] };
    return bucket.items;
  }

  add(userId, items) {
    const bucket = this.docs.get(userId) || { items: [] };
    const now = Date.now();
    for (const raw of Array.isArray(items) ? items : [items]) {
      const text = String(raw?.text ?? raw ?? "").trim();
      if (!text) continue;
      bucket.items.push({
        id: `mem_${now.toString(36)}_${bucket.items.length}_${Math.random().toString(36).slice(2, 8)}`,
        text: text.slice(0, 4000),
        createdAt: now,
      });
    }
    this.docs.put(userId, bucket);
    return bucket.items;
  }

  update(userId, id, text) {
    const bucket = this.docs.get(userId) || { items: [] };
    const item = bucket.items.find((entry) => entry.id === id);
    if (!item) return null;
    item.text = String(text || "").slice(0, 4000);
    item.updatedAt = Date.now();
    this.docs.put(userId, bucket);
    return item;
  }

  remove(userId, id) {
    const bucket = this.docs.get(userId) || { items: [] };
    bucket.items = bucket.items.filter((entry) => entry.id !== id);
    this.docs.put(userId, bucket);
    return true;
  }

  clear(userId) {
    this.docs.put(userId, { items: [] });
    return true;
  }
}
