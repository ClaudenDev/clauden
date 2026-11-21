import path from "node:path";

import { DocStore } from "./store.mjs";

const FIVE_HOURS = 5 * 60 * 60 * 1000;
const WEEK = 7 * 24 * 60 * 60 * 1000;

export class UsageStore {
  constructor({ config, plans, log }) {
    this.config = config;
    this.plans = plans;
    this.log = log;
    this.docs = new DocStore(path.join(config.dataDir, "usage"));
  }

  #record(userId) {
    const now = Date.now();
    const record = this.docs.get(userId) || { userId, events: [] };
    const cutoff = now - WEEK;
    record.events = record.events.filter((event) => event.at > cutoff);
    return record;
  }

  summary(userId, plan) {
    const record = this.#record(userId);
    const now = Date.now();
    const limits = plan.limits || {};
    const window5h = record.events.filter((event) => event.at > now - FIVE_HOURS);
    const week = record.events;
    const count = (events) => events.reduce((sum, event) => sum + (event.count || 1), 0);
    const used5h = count(window5h);
    const usedWeek = count(week);
    return {
      plan: plan.id,
      window: {
        used: used5h,
        limit: limits.window5h || 0,
        resetsAt: window5h.length ? window5h[0].at + FIVE_HOURS : now + FIVE_HOURS,
      },
      week: {
        used: usedWeek,
        limit: limits.week || 0,
        resetsAt: week.length ? week[0].at + WEEK : now + WEEK,
      },
      updatedAt: now,
    };
  }

  exceeded(userId, plan) {
    const limits = plan.limits || {};
    if (!limits.window5h && !limits.week) return null;
    const record = this.#record(userId);
    const now = Date.now();
    const window5h = record.events.filter((event) => event.at > now - FIVE_HOURS);
    const used5h = window5h.reduce((sum, event) => sum + (event.count || 1), 0);
    if (limits.window5h && used5h >= limits.window5h) {
      return window5h[0].at + FIVE_HOURS;
    }
    const usedWeek = record.events.reduce((sum, event) => sum + (event.count || 1), 0);
    if (limits.week && usedWeek >= limits.week) {
      return record.events[0].at + WEEK;
    }
    return null;
  }

  record(userId, event = {}) {
    const record = this.#record(userId);
    record.events.push({
      at: Date.now(),
      count: Number(event.count) || 1,
      model: event.model || "",
      tokens: Number(event.tokens) || 0,
    });
    this.docs.put(userId, record);
  }
}
