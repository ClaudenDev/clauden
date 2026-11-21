import crypto from "node:crypto";
import path from "node:path";

import { DocStore } from "./store.mjs";

export const KEY_PREFIX = "sk-cld-ushw-";

export class ApiKeyStore {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
    this.docs = new DocStore(path.join(config.dataDir, "keys"));
    this.index = new Map();
    this.#rebuild();
  }

  #rebuild() {
    this.index.clear();
    for (const record of this.docs.list()) {
      this.index.set(record.hash, record);
    }
  }

  #hash(token) {
    return crypto.createHash("sha256").update(String(token)).digest("hex");
  }

  forUser(userId) {
    return this.docs
      .list()
      .filter((record) => record.userId === userId)
      .map(publicKey);
  }

  create(userId) {
    const token = `${KEY_PREFIX}${crypto.randomBytes(24).toString("hex")}`;
    const record = {
      id: `key_${crypto.randomBytes(6).toString("hex")}`,
      hash: this.#hash(token),
      userId,
      createdAt: Date.now(),
      lastUsedAt: 0,
    };
    this.docs.put(record.id, record);
    this.index.set(record.hash, record);
    return { token, key: publicKey(record) };
  }

  resolve(token) {
    if (!String(token).startsWith(KEY_PREFIX)) return null;
    const record = this.index.get(this.#hash(token));
    if (!record) return null;
    record.lastUsedAt = Date.now();
    return record;
  }

  revoke(userId, id) {
    const record = this.docs.get(id);
    if (!record || record.userId !== userId) return false;
    this.docs.delete(id);
    this.index.delete(record.hash);
    return true;
  }

  reset(userId) {
    const existing = this.docs.list().filter((record) => record.userId === userId);
    for (const record of existing) {
      this.docs.delete(record.id);
      this.index.delete(record.hash);
    }
    return this.create(userId);
  }
}

function publicKey(record) {
  return {
    id: record.id,
    prefix: record.hash.slice(0, 8),
    createdAt: record.createdAt,
    lastUsedAt: record.lastUsedAt,
  };
}
