# oh-auth

Локальный Issuer + Resource Server. Выдаёт и валидирует JWT, публикует публичный JWKS для других сервисов.

## Запуск локально

1. Поднять Postgres:

```bash
docker-compose up -d
```

2. Запустить сервис:

```bash
export AUTH_ISSUER=http://localhost:8080
export AUTH_AUDIENCE=offerhunt-api
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