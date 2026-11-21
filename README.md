# Clauden — Open Source

Открытые исходники клиентов Clauden и шлюза, через который они работают.

Clauden — это оболочка для работы с Claude: веб-версия, приложение для Android,
приложения для Windows/macOS/Linux и среда для агентной разработки (Clauden Code).
Этот репозиторий содержит всё, что выполняется на устройстве пользователя, и
серверный шлюз, который обслуживает клиентов.

```
apps/web            веб-клиент (vanilla ES modules, без сборки)
apps/android        Android-приложение (Kotlin, Jetpack Compose)
apps/code-android   Clauden Code для Android
apps/desktop        десктоп-клиент (Electron + claude-client)
apps/code-desktop   Clauden Code для десктопа
backend             HTTP-шлюз: аккаунты, тарифы, чаты, Code, оплата
docs                архитектура, API, развёртывание
```

## Что здесь есть

- **Клиенты полностью.** Веб, Android, десктоп и Code — исходники без обфускации
  и без сборочных артефактов. Из них собирается то же приложение, которое
  скачивается с сайта.
- **Шлюз полностью.** Аккаунты, вход по email и Google, тарифы и лимиты,
  синхронизация чатов и проектов, память, API-ключи, сессии Code, оплата
  через Platega/Lava/xRocket.
- **Протокол полностью.** Клиенты общаются со шлюзом по OpenAI-совместимому
  HTTP API; шлюз отдаёт SSE, поддерживает вложения, инструменты и Code-сессии.
  Описание — в [docs/API.md](docs/API.md).

## Что здесь не публикуется

Два внешних контракта остаются у оператора сервиса и не входят в репозиторий:

1. **Пул инференса.** Шлюз не содержит ключей провайдеров моделей. Он
   пересылает запросы в пул по адресу `CLAUDEN_UPSTREAM_BASE`; ключ и адрес
   выдаются при развёртывании. Контракт пула — обычный OpenAI
   chat-completions, поэтому self-hosted инсталляция может указать там любой
   совместимый сервер.
2. **Поставщик подписок.** Аккаунты для платных тарифов выдаёт внешний
   поставщик. Его домен и API по условиям договора публиковать нельзя —
   в коде стоит заглушка
   `https://<поставщик не разрешил публиковать домен>`.
   Подробнее — [docs/ACCOUNTS.md](docs/ACCOUNTS.md).

## Быстрый старт

Шлюз не требует внешних зависимостей, только Node.js 20+.

```bash
cd backend
cp .env.example .env      # заполните то, что есть у вас
npm start                 # http://127.0.0.1:8788
curl localhost:8788/health
```

Проверка без провайдера моделей — есть встроенный мок пула:

```bash
npm test          # смоук-тесты без сети
npm run smoke     # полный сценарий: регистрация → тариф → стрим → журнал
```

Веб-клиент — статика, отдаётся любым сервером:

```bash
cd apps/web
python3 -m http.server 8080
```

Он обращается к `/v1/...` на том же origin, поэтому рядом должен быть
reverse proxy на шлюз. Готовый конфиг nginx — `backend/deploy/nginx.conf`.

## Сборка клиентов

| Клиент | Команда | Требования |
|---|---|---|
| Web | — | статика, ничего собирать не нужно |
| Android | `cd apps/android && ./gradlew assembleRelease` | JDK 17, Android SDK 35 |
| Code Android | `cd apps/code-android && ./gradlew assembleRelease` | JDK 17, Android SDK 35 |
| Desktop | `cd apps/desktop && npm ci && npm run build` | Node 20, Electron |
| Code Desktop | `cd apps/code-desktop && npm ci && npm run build` | Node 20, Electron |

Адрес шлюза задаётся переменной сборки:

- Android: `CLAUDEN_BASE_URL` в `gradle.properties` или `--build-arg`.
- Desktop: `CLAUDEN_BASE_URL` при сборке, либо экран «Свой API» для
  собственного сервера.
- Web: тот же origin, на котором лежит статика.

## Тарифы и лимиты

| Тариф | Цена | 5 часов | Неделя |
|---|---|---|---|
| Free | $0 | — | — |
| Pro | $20 | 45 | 300 |
| Max | $100 | 225 | 1500 |
| Ultra | $180 | 333 | 2220 |

Лимиты считаются по завершённым запросам и лежат в `backend/src/usage.mjs`.
Тарифы и цены — `backend/src/plans.mjs`, переопределяются
`backend/config/plans.json`.

## Лицензия

MIT. См. [LICENSE](LICENSE). Название и логотип Clauden — товарные знаки
сервиса; форки могут использовать код, но не бренд.

## Поддержка

- Telegram: [@ClaudenApp](https://t.me/ClaudenApp)
- Почта: support@clauden.best
- Уязвимости: [SECURITY.md](SECURITY.md)
