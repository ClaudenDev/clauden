import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { DocStore, JsonStore, hashToken, newId } from "../src/store.mjs";
import { buildPlans, normalizePlanId, planFor, publicPlans } from "../src/plans.mjs";

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "clauden-store-"));

const json = new JsonStore(path.join(dir, "a.json"), { count: 0 });
await json.update((data) => {
  data.count += 1;
});
assert.equal(new JsonStore(path.join(dir, "a.json"), {}).get().count, 1, "json store persists");

const docs = new DocStore(path.join(dir, "docs"));
docs.put("x1", { id: "x1", value: 1 });
docs.put("../evil", { id: "evil" });
assert.equal(docs.get("x1").value, 1, "doc store get");
assert.equal(docs.list().length, 2, "doc store list");
assert.ok(docs.delete("x1"), "doc store delete");

assert.match(newId("turn"), /^turn_[a-f0-9]{24}$/, "id prefix");
assert.equal(hashToken("abc"), hashToken("abc"), "hash stable");
assert.notEqual(hashToken("abc"), hashToken("abd"), "hash differs");

const plans = buildPlans({ Pro: { price: 25 } });
assert.equal(plans.Pro.price, 25, "plan override");
assert.equal(plans.Max.price, 100, "default kept");
assert.equal(normalizePlanId(plans, "ultra"), "Ultra", "plan normalize by id");
assert.equal(normalizePlanId(plans, "MAX"), "Max", "plan normalize case");
assert.equal(planFor(plans, "unknown").id, "Free", "plan fallback");
assert.equal(planFor(plans, "Pro").limits.week, 300, "plan limits");
const listed = publicPlans(plans);
assert.deepEqual(
  listed.map((plan) => plan.id),
  ["Free", "Pro", "Max", "Ultra"],
  "public plan order",
);
assert.equal(listed[0].price, 0, "public plan price");

fs.rmSync(dir, { recursive: true, force: true });
console.log("store: ok");
