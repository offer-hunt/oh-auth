# oh-auth

Локальный Issuer + Resource Server. Выдаёт и валидирует JWT, публикует публичный JWKS для других сервисов.

## Запуск локально

1. Поднять Postgres:

```bash
docker-compose up -d
```

2. Запустить сервис:

```bash
Закидываем в .env переменные

set -a
source .env
set +a

echo "$GOOGLE_CLIENT_ID" - проверка что видим
./gradlew bootRun

```

### Проверки

```bash
# JWKS (публичные ключи)
curl -s http://localhost:8080/oauth2/jwks | jq .

# OIDC discovery
curl -s http://localhost:8080/.well-known/openid-configuration | jq .
```

## Тесты локально

Java:

```bash
./gradlew clean test
```

## Где образ

Docker Hub: `offerhunt/oh-auth:<git-sha7>`
Страница репозитория: [https://hub.docker.com/r/offerhunt/oh-auth](https://hub.docker.com/r/offerhunt/oh-auth)

## Где развернут и где БД

пока пусто

---

## Коротко о том, что отдаёт сервис

* **JWKS:** `GET /oauth2/jwks` — публичные ключи для проверки подписи JWT другими сервисами.
* **OIDC discovery:** `GET /.well-known/openid-configuration` — метаданные провайдера.
* **Защита API:**
    * открыто: `/api/public/**`
    * только `ROLE_ADMIN`: `/api/admin/**`
    * всё остальное под `Bearer` access-JWT.

### Вход через Google и GitHub

#### Настройка клиентов

В консоли провайдера надо чтобы совпадало:

- Google: `http://localhost:8080/login/oauth2/code/google`
- GitHub: `http://localhost:8080/login/oauth2/code/github`

В `.env` / переменных окружения:

```bash
AUTH_ISSUER=http://localhost:8080
AUTH_AUDIENCE=offerhunt-api

GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
AUTH_OAUTH2_REDIRECT=http://localhost:3000/auth/callback
AUTH_OAUTH2_ERROR_REDIRECT=http://localhost:3000/auth/error

MAIL_USERNAME=den.moskvin2045@gmail.com
MAIL_PASSWORD=password
MAIL_HOST=localhost
MAIL_PORT=1025
PASSWORD_RESET_FROM=you@local.test
```


## MailHog (локальная почта)
`docker run -d --name mailhog -p 1025:1025 -p 8025:8025 mailhog/mailhog`
После отправки письма открой в браузере: http://localhost:8025