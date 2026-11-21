'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { parse, parseList, buildConfig } = require('../src/main/node-parser');
const { parseTrace, exitOutsideRussia } = require('../src/main/proxy');

const PORTS = { socksPort: 20818, httpPort: 20819 };

test('разбирает vless + reality', () => {
  const node = parse(
    'vless://00000000-0000-4000-8000-000000000000@203.0.113.10:8413?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.example.com&fp=chrome&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=0000000000000000&type=tcp#Clauden%20DE',
  );
  assert.equal(node.protocol, 'vless');
  assert.equal(node.address, '203.0.113.10');
  assert.equal(node.port, 8413);
  assert.equal(node.name, 'Clauden DE');
  assert.equal(node.params.id, '00000000-0000-4000-8000-000000000000');

  const config = buildConfig(node, PORTS);
  const out = config.outbounds[0];
  assert.equal(out.protocol, 'vless');
  assert.equal(out.streamSettings.security, 'reality');
  assert.equal(out.streamSettings.realitySettings.publicKey, 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA');
  assert.equal(out.settings.vnext[0].users[0].flow, 'xtls-rprx-vision');
  assert.equal(config.inbounds[0].port, 20818);
});

test('разбирает vless + xhttp', () => {
  const node = parse(
    'vless://uuid@1.2.3.4:8443?security=tls&sni=example.com&type=xhttp&path=%2Fclauden&host=example.com#TLS',
  );
  const config = buildConfig(node, PORTS);
  const out = config.outbounds[0];
  assert.equal(out.streamSettings.network, 'xhttp');
  assert.equal(out.streamSettings.xhttpSettings.path, '/clauden');
  assert.equal(out.streamSettings.tlsSettings.serverName, 'example.com');
});

test('разбирает vmess из base64', () => {
  const payload = {
    v: '2',
    ps: 'My VMess',
    add: '5.6.7.8',
    port: '443',
    id: 'aaaa-bbbb',
    aid: '0',
    scy: 'auto',
    net: 'ws',
    host: 'cdn.example.com',
    path: '/ws',
    tls: 'tls',
    sni: 'cdn.example.com',
  };
  const raw = `vmess://${Buffer.from(JSON.stringify(payload)).toString('base64')}`;
  const node = parse(raw);
  assert.equal(node.protocol, 'vmess');
  assert.equal(node.name, 'My VMess');
  assert.equal(node.address, '5.6.7.8');

  const out = buildConfig(node, PORTS).outbounds[0];
  assert.equal(out.protocol, 'vmess');
  assert.equal(out.streamSettings.network, 'ws');
  assert.equal(out.streamSettings.wsSettings.path, '/ws');
  assert.equal(out.streamSettings.tlsSettings.serverName, 'cdn.example.com');
});

test('разбирает trojan', () => {
  const node = parse('trojan://secret@trojan.example.com:443?sni=trojan.example.com#Trojan');
  assert.equal(node.protocol, 'trojan');
  assert.equal(node.params.password, 'secret');
  const out = buildConfig(node, PORTS).outbounds[0];
  assert.equal(out.protocol, 'trojan');
  assert.equal(out.settings.servers[0].password, 'secret');
});

test('разбирает shadowsocks в обоих форматах', () => {
  const creds = Buffer.from('aes-256-gcm:pass123').toString('base64');
  const a = parse(`ss://${creds}@9.9.9.9:8388#SS`);
  assert.equal(a.protocol, 'ss');
  assert.equal(a.params.method, 'aes-256-gcm');
  assert.equal(a.params.password, 'pass123');

  const whole = Buffer.from('aes-128-gcm:pw@10.0.0.5:8389').toString('base64');
  const b = parse(`ss://${whole}#SS2`);
  assert.equal(b.address, '10.0.0.5');
  assert.equal(b.port, 8389);
});

test('разбирает socks5', () => {
  const node = parse('socks5://user:pass@127.0.0.1:1080#Local');
  assert.equal(node.protocol, 'socks');
  assert.equal(node.port, 1080);
  const out = buildConfig(node, PORTS).outbounds[0];
  assert.equal(out.protocol, 'socks');
  assert.equal(out.settings.servers[0].users[0].user, 'user');
});

test('список подписки в base64', () => {
  const text = [
    'vless://uuid1@1.1.1.1:443?security=tls&type=ws&path=%2Fa#One',
    'trojan://pw@2.2.2.2:443#Two',
    'мусорная строка',
  ].join('\n');
  const encoded = Buffer.from(text).toString('base64');
  const nodes = parseList(encoded);
  assert.equal(nodes.length, 2);
  assert.deepEqual(nodes.map((n) => n.name), ['One', 'Two']);
});

test('неизвестные ссылки игнорируются', () => {
  assert.equal(parse('http://example.com'), null);
  assert.equal(parse(''), null);
  assert.deepEqual(parseList(''), []);
});

test('разбирает /cdn-cgi/trace и страну выхода', () => {
  const exit = parseTrace('fl=123\nh=claude.ai\nip=203.0.113.10\nts=1\nloc=DE\n');
  assert.deepEqual(exit, { ip: '203.0.113.10', country: 'DE' });
  assert.equal(parseTrace('h=claude.ai\nloc=DE\n'), null);
});

test('внешним считается только известный не-RU выход', () => {
  assert.equal(exitOutsideRussia({ ip: '1.1.1.1', country: 'DE' }), true);
  assert.equal(exitOutsideRussia({ ip: '1.1.1.1', country: 'ru' }), false);
  assert.equal(exitOutsideRussia({ ip: '1.1.1.1', country: 'RU' }), false);
  assert.equal(exitOutsideRussia({ ip: '1.1.1.1', country: '' }), false);
  assert.equal(exitOutsideRussia(null), false);
});
