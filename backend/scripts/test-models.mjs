import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { ChatBackupStore, MemoryStore, ProjectBackupStore } from "../src/chats.mjs";
import { ModelRegistry } from "../src/models.mjs";
import { buildPlans, planFor } from "../src/plans.mjs";
import { UsageStore } from "../src/usage.mjs";

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "clauden-models-"));
const config = { dataDir: dir, root: path.resolve(".") };

const registry = new ModelRegistry({ config, log: () => {} });
assert.equal(registry.normalize("claude-sonnet-5-5"), "claude-sonnet-5-5", "normalize id");
assert.equal(registry.normalize("claude-sonnet-5.5"), "claude-sonnet-5-5", "normalize dotted");
assert.equal(registry.normalize("anthropic/claude-opus-5"), "claude-opus-5", "normalize prefixed");
assert.equal(registry.normalize("claude-fable-5-2[1m]"), "claude-fable-5-2", "normalize suffix");
assert.equal(registry.normalize("gpt-4"), "", "unknown rejected");
assert.ok(registry.list().length >= 9, "catalog size");
assert.ok(!registry.list("Free")[0].available, "paid-only hidden on Free");
assert.ok(registry.list("Pro")[0].available, "paid-only visible on Pro");
assert.ok(registry.allows("Pro", "claude-fable-5-2"), "plan gate allows");
assert.ok(!registry.allows("Free", "claude-fable-5-2"), "plan gate blocks");

const plans = buildPlans();
const usage = new UsageStore({ config, plans, log: () => {} });
const pro = planFor(plans, "Pro");
assert.equal(usage.exceeded("u1", pro), null, "fresh usage");
for (let i = 0; i < pro.limits.window5h; i += 1) usage.record("u1", { model: "x" });
assert.ok(usage.exceeded("u1", pro) > Date.now(), "window limit reached");
const summary = usage.summary("u1", pro);
assert.equal(summary.window.used, pro.limits.window5h, "summary counts");

const chats = new ChatBackupStore({ config, log: () => {} });
chats.put("u1", [{ id: "c1", title: "One", messages: [{ role: "user", text: "hi" }] }]);
chats.put("u1", [{ id: "c1", title: "One updated", updatedAt: Date.now() + 1000, messages: [] }]);
const listed = chats.list("u1");
assert.equal(listed.data.length, 1, "chat deduplicated");
assert.equal(listed.data[0].title, "One updated", "newest wins");
chats.remove("u1", "c1");
assert.equal(chats.list("u1").data.length, 0, "chat removed");
assert.equal(chats.list("u1").deleted.length, 1, "tombstone written");

const projects = new ProjectBackupStore({ config, log: () => {} });
projects.put("u1", [{ id: "p1", title: "Proj", files: [{ name: "a.txt" }] }]);
assert.equal(projects.list("u1").data.length, 1, "project stored");

const memory = new MemoryStore({ config, log: () => {} });
const items = memory.add("u1", "remember this");
assert.equal(items.length, 1, "memory added");
assert.ok(memory.update("u1", items[0].id, "changed"), "memory updated");
assert.ok(memory.remove("u1", items[0].id), "memory removed");
memory.add("u1", ["a", "b"]);
assert.equal(memory.list("u1").length, 2, "memory bulk add");
memory.clear("u1");
assert.equal(memory.list("u1").length, 0, "memory cleared");

fs.rmSync(dir, { recursive: true, force: true });
console.log("models: ok");
