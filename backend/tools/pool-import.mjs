#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

import { config } from "../src/config.mjs";
import { AnthropicKeyPool, decryptPool } from "../src/keypool.mjs";

const args = process.argv.slice(2);

function usage() {
  console.error(
    [
      "Использование:",
      "  node tools/pool-import.mjs add <file-with-keys> [label]",
      "  node tools/pool-import.mjs list",
      "  node tools/pool-import.mjs check",
      "",
      "Нужен CLAUDEN_POOL_SECRET. Ключи в файле — по одному в строке, формат sk-ant-…",
      "Файл с ключами после импорта удалите: пул хранится в data/pool/anthropic.json (0600).",
    ].join("\n"),
  );
}

const command = args[0];

if (!command || command === "help") {
  usage();
  process.exit(command ? 0 : 1);
}

if (!config.pool.secret) {
  console.error("CLAUDEN_POOL_SECRET не задан — пул нельзя прочитать или записать");
  process.exit(1);
}

const pool = new AnthropicKeyPool({ config, log: (line) => console.log(line) });
const poolFile = path.join(config.dataDir, "pool", "anthropic.json");

if (command === "add") {
  const file = args[1];
  if (!file) {
    usage();
    process.exit(1);
  }
  const keys = fs
    .readFileSync(file, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"));
  const total = pool.importKeys(keys, { label: args[2] || "" });
  console.log(`добавлено: ${keys.length}, всего в пуле: ${total}`);
  console.log(`пул: ${poolFile}`);
} else if (command === "list") {
  const status = pool.status();
  if (!status.total) {
    console.log("пул пуст");
    process.exit(0);
  }
  console.log(`всего ${status.total}, активных ${status.active}, на паузе ${status.cooling}, мёртвых ${status.invalid}`);
  for (const key of status.keys) {
    const cooldown = key.cooldownMs ? ` пауза ${Math.ceil(key.cooldownMs / 1000)}с` : "";
    console.log(`  ${key.id}  ${key.label}  ${key.fingerprint}  ${key.state}${cooldown}  сбоев ${key.failures}`);
  }
} else if (command === "check") {
  if (!fs.existsSync(poolFile)) {
    console.log("файла пула нет");
    process.exit(1);
  }
  const entries = decryptPool(poolFile, config.pool.secret);
  console.log(`файл читается: ${entries.length} ключей`);
  console.log(`активных: ${entries.filter((entry) => entry.state === "active").length}`);
} else {
  usage();
  process.exit(1);
}
