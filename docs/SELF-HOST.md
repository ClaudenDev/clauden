# Развёртывание

Шлюз — один процесс Node.js. Ниже — вариант с systemd и nginx на одном
сервере; веб-клиент отдаётся оттуда же, с того же домена.

## 1. Зависимости

- Node.js 20+ (в репозитории нет `node_modules`)
- nginx (TLS и отдача статики)
- git (для сессий Code)

## 2. Каталоги

```bash
useradd --system --home /srv/clauden --shell /usr/sbin/nologin clauden
install -d -o clauden -g clauden /srv/clauden/{app,data}
rsync -a backend/ /srv/clauden/app/backend/
rsync -a apps/web/ /srv/clauden/app/web/
chown -R clauden:clauden /srv/clauden
```

## 3. Конфигурация

```bash
cd /srv/clauden/app/backend
cp .env.example .env
chmod 600 .env
```

Заполните минимум:

```bash
CLAUDEN_PUBLIC_BASE=https://clauden.best
CLAUDEN_UPSTREAM_BASE=…      # пул инференса
CLAUDEN_UPSTREAM_KEY=…
CLAUDEN_ACCOUNTS_RELAY=…     # поставщик подписок
CLAUDEN_ACCOUNTS_SECRET=…
CLAUDEN_ADMIN_KEY=…          # длинный случайный
CLAUDEN_MAIL_PROVIDER=resend
CLAUDEN_MAIL_API_KEY=…
```

`/health` покажет, что подключено, а что нет.

## 4. systemd

```bash
install -m 644 backend/deploy/clauden-backend.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now clauden-backend
journalctl -u clauden-backend -f
```

## 5. nginx

```bash
install -m 644 backend/deploy/nginx.conf /etc/nginx/sites-available/clauden
ln -s /etc/nginx/sites-available/clauden /etc/nginx/sites-enabled/clauden
nginx -t && systemctl reload nginx
```

Конфиг отдаёт статику из `/srv/clauden/app/web`, а `/v1/`, `/files/`, `/health`
проксирует на `127.0.0.1:8788`. Обязательно:

```nginx
proxy_buffering off;      # иначе SSE копится в буфере
proxy_read_timeout 900s;  # длинные ответы Code
```

## 6. Проверка

```bash
curl -s https://clauden.best/health | jq
curl -s https://clauden.best/v1/plans | jq '.data[].id'
```

## 7. Обновление

```bash
cd /srv/clauden/app && git pull
systemctl restart clauden-backend
```

Каталог `data/` не трогается. Откат — вернуть предыдущий коммит и
перезапустить процесс.

## 8. Бэкап

```bash
tar czf /var/backups/clauden-$(date +%F).tgz -C /srv/clauden data
```

В `data/` лежат аккаунты, сессии, заказы и журналы. Каталог закрыт `0700`,
права — только у пользователя `clauden`.

## Docker

```bash
docker compose -f backend/deploy/docker-compose.yml up -d
```

Образ собирается из `backend/Dockerfile`, данные монтируются в `./data`.
