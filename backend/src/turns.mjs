import path from "node:path";

import { DocStore, newId } from "./store.mjs";

const KEEP = 200;

export class TurnStore {
  constructor({ config, log }) {
    this.config = config;
    this.log = log;
    this.docs = new DocStore(path.join(config.dataDir, "turns"));
    this.chains = new Map();
    this.active = new Map();
  }

  conversationKey(value) {
    const raw = String(value || "").trim();
    if (!raw) return "";
    return raw.startsWith("c:") ? raw : `c:${raw}`;
  }

  begin({ conversation, userId, model, text }) {
    const turn = {
      id: newId("turn"),
      conversation: this.conversationKey(conversation),
      userId: userId || "",
      model,
      prompt: String(text || "").slice(0, 8000),
      text: "",
      status: "running",
      createdAt: Date.now(),
      updatedAt: Date.now(),
      usage: null,
      error: "",
    };
    this.docs.put(turn.id, turn);
    if (turn.conversation) {
      this.active.set(turn.conversation, turn.id);
      const chain = this.chains.get(turn.conversation) || [];
      chain.push(turn.id);
      while (chain.length > KEEP) {
        const dropped = chain.shift();
        this.docs.delete(dropped);
      }
      this.chains.set(turn.conversation, chain);
    }
    return turn;
  }

  append(id, delta) {
    const turn = this.docs.get(id);
    if (!turn) return;
    turn.text += delta;
    turn.updatedAt = Date.now();
    this.docs.put(id, turn);
  }

  finish(id, { status = "done", usage = null, error = "" } = {}) {
    const turn = this.docs.get(id);
    if (!turn) return null;
    turn.status = status;
    turn.usage = usage;
    turn.error = error;
    turn.updatedAt = Date.now();
    this.docs.put(id, turn);
    if (this.active.get(turn.conversation) === id) this.active.delete(turn.conversation);
    return turn;
  }

  get(id) {
    return this.docs.get(id);
  }

  latest(conversation) {
    const key = this.conversationKey(conversation);
    const id = this.active.get(key);
    if (id) {
      const turn = this.docs.get(id);
      if (turn && turn.status === "running") return turn;
      this.active.delete(key);
    }
    const chain = this.chains.get(key) || [];
    for (let i = chain.length - 1; i >= 0; i -= 1) {
      const turn = this.docs.get(chain[i]);
      if (turn) return turn;
    }
    return null;
  }

  list(conversation) {
    const key = this.conversationKey(conversation);
    return (this.chains.get(key) || [])
      .map((id) => this.docs.get(id))
      .filter(Boolean);
  }

  warm() {
    const all = this.docs.list().sort((a, b) => a.createdAt - b.createdAt);
    for (const turn of all) {
      if (!turn.conversation) continue;
      const chain = this.chains.get(turn.conversation) || [];
      chain.push(turn.id);
      this.chains.set(turn.conversation, chain);
      if (turn.status === "running") {
        turn.status = "interrupted";
        this.docs.put(turn.id, turn);
      }
    }
  }

  prune() {
    const cutoff = Date.now() - this.config.turnTtlMs;
    for (const turn of this.docs.list()) {
      if (turn.updatedAt < cutoff) this.docs.delete(turn.id);
    }
  }
}
