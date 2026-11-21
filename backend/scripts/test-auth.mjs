import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { AuthStore, publicUser } from "../src/auth.mjs";

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "clauden-auth-"));
const logs = [];
const config = {
  dataDir: dir,
  auth: { tokenTtlMs: 60_000, codeTtlMs: 60_000, googleClientIds: [], bridgeSecret: "", devCodes: true },
  mail: { provider: "", apiKey: "", from: "" },
};
const plans = { Free: { paid: false }, Pro: { paid: true } };
const auth = new AuthStore({ config, plans, log: (line) => logs.push(line) });

const sent = await auth.requestEmailCode("User@Example.com");
assert.ok(sent.devCode, "dev code returned");
await assert.rejects(() => auth.requestEmailCode("user@example.com"), /подождите/, "rate limit");

const registered = await auth.registerEmail(
  { email: "user@example.com", code: sent.devCode, password: "long-enough" },
  { device: "test" },
);
assert.ok(registered.token, "token issued");
assert.equal(registered.user.email, "user@example.com", "email normalized");
assert.equal(registered.user.plan, "Free", "default plan");

const session = await auth.loginEmailPassword({ email: "user@example.com", password: "long-enough" });
assert.ok(session.token, "login token");
await assert.rejects(
  () => auth.loginEmailPassword({ email: "user@example.com", password: "wrong-password" }),
  /Неверный email или пароль/,
  "wrong password rejected",
);

const user = auth.userForToken(session.token);
assert.ok(user, "token resolves");
assert.equal(auth.sessionList(user.id, session.token).length, 2, "two sessions listed");
assert.ok(auth.sessionList(user.id, session.token).some((s) => s.current), "current session flagged");

auth.updateProfile(user, { name: "Иван", birthday: "1990-01-01", onboarded: true, terms: true });
assert.equal(user.name, "Иван", "profile updated");
assert.ok(user.termsAt > 0, "terms timestamp");
await auth.changePassword(user, "another-password");
const relogin = await auth.loginEmailPassword({ email: "user@example.com", password: "another-password" });
assert.ok(relogin.token, "password changed");

auth.setPlan(user, "Pro", { until: 0, method: "admin" });
assert.equal(user.plan, "Pro", "plan granted");
assert.equal(publicUser(user, plans).codeAccess, true, "code access follows paid plan");

auth.logout(session.token);
assert.equal(auth.userForToken(session.token), null, "logout invalidates");

auth.deleteUser(user);
assert.equal(Object.keys(auth.users).length, 0, "account deleted");

fs.rmSync(dir, { recursive: true, force: true });
console.log("auth: ok");
