'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');

const {
  decryptAesGcm,
  stripDomainHash,
  expiresToSeconds,
  sameSiteValue,
  findDataDir,
  readClaudeCookies,
} = require('../src/main/claude-desktop');

function encryptV10(value, key, host) {
  const nonce = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce);
  const hash = crypto.createHash('sha256').update(host).digest();
  const body = Buffer.concat([cipher.update(Buffer.concat([hash, Buffer.from(value, 'utf8')])), cipher.final()]);
  return Buffer.concat([Buffer.from('v10'), nonce, body, cipher.getAuthTag()]);
}

function makeDb(file, rows) {
  const { DatabaseSync } = require('node:sqlite');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec(`CREATE TABLE cookies (
    host_key TEXT, name TEXT, value TEXT, encrypted_value BLOB,
    path TEXT, expires_utc INTEGER, is_secure INTEGER, is_httponly INTEGER, samesite INTEGER
  )`);
  const insert = db.prepare(
    'INSERT INTO cookies (host_key,name,value,encrypted_value,path,expires_utc,is_secure,is_httponly,samesite) VALUES (?,?,?,?,?,?,?,?,?)',
  );
  for (const row of rows) insert.run(...row);
  db.close();
}

test('decryptAesGcm decrypts Chromium v10 payload', () => {
  const key = crypto.randomBytes(32);
  const encrypted = encryptV10('sk-ant-sid01-test-value', key, '.claude.ai');
  const plain = decryptAesGcm(encrypted.subarray(3), key);
  assert.match(plain.toString('utf8'), /sk-ant-sid01-test-value/);
});

test('stripDomainHash removes SHA256(host) prefix', () => {
  const host = '.claude.ai';
  const hash = crypto.createHash('sha256').update(host).digest();
  const value = Buffer.concat([hash, Buffer.from('session-value')]);
  assert.equal(stripDomainHash(value, host).toString('utf8'), 'session-value');
});

test('stripDomainHash removes SHA256(host) suffix', () => {
  const host = '.claude.ai';
  const hash = crypto.createHash('sha256').update(host).digest();
  const value = Buffer.concat([Buffer.from('session-value'), hash]);
  assert.equal(stripDomainHash(value, host).toString('utf8'), 'session-value');
});

test('expiresToSeconds converts Chromium microseconds since 1601', () => {
  const unix = 1_800_000_000;
  const chromium = Math.round((unix + 11644473600) * 1e6);
  assert.equal(expiresToSeconds(chromium), unix);
  assert.equal(expiresToSeconds(0), 0);
});

test('sameSiteValue maps Chromium enum', () => {
  assert.equal(sameSiteValue(0), 'no_restriction');
  assert.equal(sameSiteValue(1), 'lax');
  assert.equal(sameSiteValue(2), 'strict');
  assert.equal(sameSiteValue(-1), undefined);
});

test('readClaudeCookies reads an Electron-style Cookies DB and decrypts sessionKey', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'clauden-desktop-test-'));
  const dataDir = path.join(root, 'Claude');
  const key = crypto.randomBytes(32);
  fs.mkdirSync(dataDir, { recursive: true });
  fs.writeFileSync(
    path.join(dataDir, 'Local State'),
    JSON.stringify({ os_crypt: { encrypted_key: key.toString('base64') } }),
  );
  const session = encryptV10('sk-ant-sid01-abcdefghijklmnopqrstuvwxyz012345', key, '.claude.ai');
  const org = encryptV10('org-uuid-1', key, '.claude.ai');
  makeDb(path.join(dataDir, 'Network', 'Cookies'), [
    ['.claude.ai', 'sessionKey', '', session, '/', 0, 1, 1, -1],
    ['.claude.ai', 'lastActiveOrg', '', org, '/', 0, 1, 0, -1],
    ['example.com', 'other', 'x', null, '/', 0, 0, 0, -1],
  ]);

  const prev = process.env.APPDATA;
  process.env.APPDATA = root;
  process.env.LOCALAPPDATA = path.join(root, 'local');
  try {
    assert.ok(findDataDir()?.cookieDb.endsWith(path.join('Network', 'Cookies')));
    const { cookies } = await readClaudeCookies();
    const names = cookies.map((cookie) => cookie.name).sort();
    assert.deepEqual(names, ['lastActiveOrg', 'sessionKey']);
    assert.match(cookies.find((cookie) => cookie.name === 'sessionKey').value, /^sk-ant-sid01-/);
    assert.equal(cookies.find((cookie) => cookie.name === 'lastActiveOrg').value, 'org-uuid-1');
  } finally {
    if (prev === undefined) delete process.env.APPDATA;
    else process.env.APPDATA = prev;
    fs.rmSync(root, { recursive: true, force: true });
  }
});
