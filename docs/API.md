# API

Базовый префикс — `/v1`. Авторизация — `Authorization: Bearer <token>`
(сессия или API-ключ `ck_…`), также принимается `x-api-key`.

Ошибки единого вида:

```json
{ "error": { "message": "…", "type": "invalid_request_error" } }
```

## Служебное

| Метод | Путь | Описание |
|---|---|---|
| GET | `/health` | статус, версия, состояние пула и поставщика |

## Авторизация

| Метод | Путь | Тело | Ответ |
|---|---|---|---|
| POST | `/v1/auth/email/code` | `{email}` | `{ok, expiresIn}` |
| POST | `/v1/auth/email/register` | `{email, code, password}` | `{token, user}` |
| POST | `/v1/auth/email` | `{email, password}` | `{token, user}` |
| POST | `/v1/auth/google` | `{id_token}` | `{token, user}` |
| POST | `/v1/auth/bridge` | `{payload, sig}` | `{token, user}` |
| GET | `/v1/auth/config` | — | `{googleEnabled, emailEnabled}` |
| GET | `/v1/auth/me` | — | `{user, plan}` |
| PATCH | `/v1/auth/me` | профиль | `{user, plan}` |
| POST | `/v1/auth/password` | `{password}` | `{ok}` |
| POST | `/v1/auth/claude/link` | `{email, plan}` | `{user, plan}` |
| GET | `/v1/auth/sessions` | — | `{data: [...]}` |
| POST | `/v1/auth/sessions/revoke` | `{id}` | `{ok}` |
| POST | `/v1/auth/logout` | — | `{ok}` |
| POST | `/v1/auth/logout-all` | — | `{ok}` |
| DELETE | `/v1/auth/account` | — | `{ok}` |

## Каталог и тарифы

| Метод | Путь | Описание |
|---|---|---|
| GET | `/v1/models` | список моделей |
| GET | `/v1/plans` | витрина тарифов |
| GET | `/v1/usage` | расход лимитов |

Модель в ответе:

```json
{
  "id": "claude-sonnet-5-5",
  "object": "model",
  "owned_by": "anthropic",
  "display_name": "Claude Sonnet 5.5",
  "name": "Claude Sonnet 5.5",
  "context_window": 1000000,
  "available": true
}
```

## Чат

### `POST /v1/chat/completions`

OpenAI-совместимое тело. Дополнительно клиент присылает:

- `x-conversation-id` — стабильный id диалога;
- `x-turn-id` — id текущего хода;
- `x-timezone`, `x-locale`, `x-clauden-client`.

Ответ — SSE:

```
data: {"choices":[{"index":0,"delta":{"clauden_thinking":"…"}}]}
data: {"choices":[{"index":0,"delta":{"content":"Привет"}}]}
data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
data: [DONE]
```

Каждые 10 секунд сервер отправляет `: ping`, пока ответ готовится.

### Журнал

| Метод | Путь | Описание |
|---|---|---|
| GET | `/v1/turns?conversation=c:…` | ходы диалога |
| GET | `/v1/turn/active?conversation=c:…` | последний ход |
| POST | `/v1/turns/stop` | `{turnId}` |

## Anthropic Messages

### `POST /v1/messages`

Тот же чат в формате Anthropic: `model`, `max_tokens`, `system`, `messages`.
`stream: true` включает SSE с событиями `message_start`, `content_block_delta`,
`message_delta`, `message_stop`.

```json
{
  "type": "message",
  "role": "assistant",
  "model": "claude-sonnet-5-5",
  "content": [{ "type": "text", "text": "…" }],
  "stop_reason": "end_turn",
  "usage": { "input_tokens": 0, "output_tokens": 0 }
}
```

## OpenAI Responses

### `POST /v1/responses`

Для Codex и других клиентов Responses API. Принимает `input` и `instructions`,
возвращает `output` с `output_text`. При `stream: true` шлёт события
`response.created`, `response.output_text.delta`, `response.completed`.

## Бэкап и память

| Метод | Путь | Описание |
|---|---|---|
| GET | `/v1/chats` | `{data, deleted}` |
| POST | `/v1/chats/backup` | `{conversations}` |
| DELETE | `/v1/chats` | очистить |
| DELETE | `/v1/chats/{id}` | удалить чат |
| GET | `/v1/projects` | проекты |
| POST | `/v1/projects/backup` | `{projects}` |
| DELETE | `/v1/projects/{id}` | удалить проект |
| GET | `/v1/memory` | записи памяти |
| POST | `/v1/memory` | `{text}` или `{items}` |
| PATCH | `/v1/memory/{id}` | `{text}` |
| DELETE | `/v1/memory/{id}` | удалить |
| DELETE | `/v1/memory` | очистить |

## API-ключи

| Метод | Путь | Описание |
|---|---|---|
| GET | `/v1/keys` | список ключей |
| POST | `/v1/keys` | выпустить (`{token, key}`) |
| POST | `/v1/keys/reset` | перевыпустить |
| DELETE | `/v1/keys/{id}` | отозвать |

## Оплата

| Метод | Путь | Описание |
|---|---|---|
| POST | `/v1/billing/checkout` | `{plan, method, autoRenew}` → `{order}` |
| GET | `/v1/billing/order/{id}` | статус заказа |
| POST | `/v1/billing/cancel` | отключить автопродление |
| GET | `/v1/billing/return` | страница возврата |
| GET | `/v1/accounts/me` | тариф и статус выданного места |

`method`: `platega` (СБП, рубли), `lava` (карты, PayPal), `xrocket` (USDT).

После оплаты заказ содержит стадии выдачи подписки:

```json
{
  "status": "paid",
  "account": { "provider": "external", "status": "active", "plan": "Pro",
               "login": "ab***@gmail.com", "issuedAt": 0, "expiresAt": 0 },
  "events": [{ "type": "created", "at": 0 }, { "type": "paid", "at": 0 },
             { "type": "account_issued", "at": 0 }]
}
```

## Clauden Code

| Метод | Путь | Описание |
|---|---|---|
| GET | `/v1/code/sessions` | список сессий |
| POST | `/v1/code/sessions` | создать |
| GET | `/v1/code/sessions/{id}` | сессия целиком |
| GET | `/v1/code/sessions/{id}/events` | SSE-события сессии |
| POST | `/v1/code/sessions/{id}/messages` | `{text}` |
| POST | `/v1/code/sessions/{id}/stop` | остановить |
| POST | `/v1/code/sessions/{id}/model` | сменить модель |
| GET | `/v1/code/sessions/{id}/files?path=` | список файлов |
| GET | `/v1/code/sessions/{id}/files/content?path=` | чтение |
| POST | `/v1/code/sessions/{id}/files/content` | `{path, content}` |
| POST | `/v1/code/sessions/{id}/files/upload` | `{name, data}` |
| GET | `/v1/code/sessions/{id}/download?path=` | скачать |
| GET | `/v1/code/sessions/{id}/diff` | git diff |
| POST | `/v1/code/sessions/{id}/commit` | `{message}` |
| POST | `/v1/code/sessions/{id}/exec` | `{command}` (если включено) |

## Админка

Требует заголовок `x-admin-key`.

| Метод | Путь | Описание |
|---|---|---|
| GET | `/v1/admin/users` | список пользователей |
| POST | `/v1/admin/plan` | `{user, plan}` выдать тариф |
