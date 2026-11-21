# Клиенты

Все клиенты говорят с одним шлюзом по одному протоколу
([docs/API.md](API.md)). Отличаются только платформа и способ доставки.

## Web — `apps/web`

Vanilla ES modules, никакой сборки. `js/api.js` — единственная точка, где
строятся HTTP-запросы; остальные модули работают с состоянием и DOM.

```
js/
  api.js        запросы, SSE, вложения
  app.js        роутинг, вход, онбординг
  chat.js       экран чата, стрим, артефакты
  code.js       Clauden Code
  settings.js   настройки, тарифы, оплата, usage
  store.js      IndexedDB для локальных копий
  markdown.js   рендер ответов
```

Статика отдаётся тем же доменом, что и API, поэтому `fetch("/v1/…")` уходит
на шлюз через nginx.

## Android — `apps/android`

Kotlin, Compose, minSdk 26. Слои:

```
data/    AuthApi, GatewayApi, CodeApi, Models, локальные настройки
ui/      экраны: чат, Code, настройки, темы, оплата
proxy/   встроенный клиент VLESS для обхода блокировок
```

Адрес шлюза — `BuildConfig.CLAUDEN_BASE_URL`, по умолчанию
`https://teleopen.space/clauden`. Пользователь может переопределить его в
настройках («Свой сервер»).

Сборка:

```bash
cd apps/android
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```

Подпись — свой keystore через `signingConfigs`; в репозитории ключей нет.

## Code Android — `apps/code-android`

Отдельное приложение для агентных сессий: терминал, диффы, файлы.
Использует те же `GatewayApi`/`CodeApi`-контракты.

## Desktop — `apps/desktop`

Electron. `main.js` — окно, обновления, мост к системным API;
`claude-client.js` — режим «Claude» через официальный аккаунт;
`src/` — интерфейс.

Сборка:

```bash
cd apps/desktop
npm ci
npm run build
```

## Code Desktop — `apps/code-desktop`

Тот же Electron-каркас для Code-сессий, отдельное окно и свой апдейтер.

## Свой API

Веб- и десктоп-клиенты умеют работать с произвольным OpenAI-совместимым
сервером: «Настройки → Свой API». Это тот же путь, которым ходят запросы к
Clauden, поэтому self-hosted шлюз подключается без правок клиента.
