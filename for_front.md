# Auth API & SSO (Google / GitHub)

Бэкенд `oh-auth` поднимается (по умолчанию) на:

```text
http://localhost:8080
```

Все ручки ниже относительно этого базового URL.

Токены:

* **access_token** — живёт 15 минут (`exp = now + 900`), используется в `Authorization: Bearer ...`.
* **refresh_token** — живёт 30 дней, используется только в `/api/auth/refresh`.

Формат ответа с токенами **везде одинаковый**:

```json
{
  "token_type": "Bearer",
  "access_token": "...",
  "expires_in": 900,
  "refresh_token": "..."
}
```

---

## 1. Регистрация по email/паролю

### POST `/api/auth/register`

Регистрация нового пользователя с email/паролем.

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "Str0ng!Passw0rd",
  "fullName": "Test User"
}
```

> Пароль должен быть не короче 8 символов и содержать строчные/прописные буквы, цифры и спецсимвол (валидация `StrongPassword`).

**Успех:**

* `201 Created`
* Body:

```json
{ "status": "ok" }
```

**Ошибки:**

* `400 BAD_REQUEST`

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "timestamp": "...",
    "details": {
      "email": "must be a well-formed email address",
      "password": "Password is too weak"
    }
  }
  ```

* `409 CONFLICT` — email уже занят:

  ```json
  {
    "code": "EMAIL_EXISTS",
    "message": "Email is already registered",
    "timestamp": "..."
  }
  ```

* `503 SERVICE_UNAVAILABLE` — проблемы с БД:

  ```json
  {
    "code": "DB_UNAVAILABLE",
    "message": "Server error. Please try later.",
    "timestamp": "..."
  }
  ```

### Пример с фронта (fetch)

```ts
async function register(email: string, password: string, fullName?: string) {
  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, fullName }),
  });

  if (!res.ok) {
    const err = await res.json();
    throw err;
  }

  return res.json(); // { status: "ok" }
}
```

---

## 2. Логин по email/паролю

### POST `/api/auth/login`

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "Str0ng!Passw0rd"
}
```

**Успех:**

* `200 OK`
* `TokenResponse` (см. в начале)

**Ошибки:**

* `400 BAD_REQUEST`, body:

  ```json
  {
    "code": "BAD_REQUEST",
    "message": "user not found",
    "timestamp": "..."
  }
  ```

  или

  ```json
  {
    "code": "BAD_REQUEST",
    "message": "bad credentials",
    "timestamp": "..."
  }
  ```

  (`user not found` — такого email нет; `bad credentials` — пароль не совпал или учётка только SSO, без пароля).

---

## 3. Обновление токенов (refresh)

### POST `/api/auth/refresh?refresh_token=...`

Обновляет пару токенов по `refresh_token`.

**Request:**

* Метод: `POST`
* Query-параметр: `refresh_token=<refresh_token из предыдущего TokenResponse>`

**Пример:**

```bash
curl -X POST "http://localhost:8080/api/auth/refresh?refresh_token=eyJ..."
```

**Успех:**

* `200 OK`
* Новый `TokenResponse` (новый `access_token` и новый `refresh_token`).

**Ошибки:**

* `400 BAD_REQUEST`, body:

  ```json
  {
    "code": "BAD_REQUEST",
    "message": "invalid refresh token",
    "timestamp": "..."
  }
  ```

  (если подсунули `access_token` или протухший / испорченный refresh).

---

## 4. Текущий пользователь

### GET `/api/me`

Возвращает информацию по текущему access-токену.

**Request:**

* Заголовок: `Authorization: Bearer <access_token>`

**Пример:**

```bash
curl -H "Authorization: Bearer eyJ..." http://localhost:8080/api/me
```

**Ответ:**

```json
{
  "userId": "b856100c-1ca5-462b-bfe5-c07b512ac08b",
  "role": "USER",
  "aud": ["offerhunt-api"],
  "iss": "http://localhost:8080"
}
```

---

## 5. SSO через Google / GitHub

SSO реализовано через стандартный Spring Security OAuth2.

Два провайдера:

* Google — `registrationId = "google"`
* GitHub — `registrationId = "github"`

### 5.1. Точка входа (redirect на провайдера)

Для старта SSO фронт **отправляет браузер** на:

* Google:
  `GET /oauth2/authorization/google`
* GitHub:
  `GET /oauth2/authorization/github`

**SPA-вариант:**

```ts
function loginWithGoogle() {
  window.location.href = '/oauth2/authorization/google';
}

function loginWithGithub() {
  window.location.href = '/oauth2/authorization/github';
}
```

Это запустит стандартный OAuth2-флоу:

1. Браузер → `oh-auth` (`/oauth2/authorization/...`).

2. `oh-auth` делает redirect на страницу входа провайдера.

3. Пользователь логинится и даёт согласие.

4. Провайдер делает redirect обратно на `oh-auth`:

    * Google → `/login/oauth2/code/google?...`
    * GitHub → `/login/oauth2/code/github?...`

5. `oh-auth` выполняет SSO-алгоритм (поиск/создание пользователя, привязка SSO, выдача токенов) и **редиректит на фронт**.

### 5.2. Redirect на фронт

В конфиге:

```yaml
app:
  oauth2:
    redirect: http://localhost:3000/auth/callback
    error-redirect: http://localhost:3000/auth/error
```

После успешного входа:

```text
http://localhost:3000/auth/callback#access_token=...&expires_in=900&refresh_token=...
```

Обрати внимание: токены лежат в **URL-фрагменте** после `#`, это видно только в браузере.

**Как разобрать на фронте (TypeScript):**

```ts
function parseTokensFromHash(): {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
} | null {
  const hash = window.location.hash; // "#access_token=...&expires_in=900&refresh_token=..."
  if (!hash.startsWith('#')) return null;
  const params = new URLSearchParams(hash.substring(1));

  const accessToken = params.get('access_token');
  const refreshToken = params.get('refresh_token');
  const expiresInStr = params.get('expires_in');

  if (!accessToken || !refreshToken || !expiresInStr) return null;

  return {
    accessToken,
    refreshToken,
    expiresIn: Number(expiresInStr),
  };
}
```

Обычно после этого фронт:

1. Сохраняет токены в хранилище (например, `localStorage` или в in-memory store).
2. Чистит hash (`window.history.replaceState(null, '', '/auth/callback')`), чтобы токены не светились в URL.

---

### 5.3. Поведение для UI: успешный вход

Для фронта **SSO выглядит так же, как обычный логин**:

* Всегда получаем `access_token` / `refresh_token` в стандартном формате.
* Разница только в том, **как** они доставлены:

    * password-flow → JSON-ответ `/api/auth/login`;
    * SSO → redirect на `app.oauth2.redirect` c токенами в hash.

С точки зрения сценариев:

* Если пользователь **уже существовал** (email или имеющаяся привязка):

    * Google: в логах `Google OAuth success – existing user`.
    * GitHub: `GitHub OAuth success – existing user`.

  Фронт: просто обрабатывает токены, показывает «Вход выполнен успешно».

* Если пользователь **новый**:

    * Google: `Google OAuth success – new user`.
    * GitHub: `GitHub OAuth success – new user`.

  Фронт: показывает, например, «Регистрация через Google успешно завершена!».

Сами текстовые сообщения флоу успешного входа фронт решает сам, по факту того, что токены получены.

---

### 5.4. Поведение для UI: ошибки SSO

Ошибки делятся на 2 группы:

#### 5.4.1. Пользователь отменил вход у провайдера

Если на странице Google/GitHub пользователь жмёт «Cancel» / «Отмена» — провайдер редиректит назад с ошибкой, `oh-auth` ловит её.

**Логи:**

* Google:
  `Google OAuth failed – access denied`
* GitHub:
  `GitHub OAuth failed – access denied`

**Фронт:**

* Если это **браузерный** (SPA) сценарий — будет redirect на:

  ```text
  http://localhost:3000/auth/error?message=...URLEncoded...
  ```

  Для Google: `Ошибка входа через Google.`
  Для GitHub: `Ошибка входа через GitHub.`

  Пример обработки на фронте:

  ```ts
  // /auth/error page
  const params = new URLSearchParams(window.location.search);
  const message = params.get('message') ?? 'Ошибка входа через внешний провайдер.';
  ```

* Если бы это был **JSON-клиент** (Accept: application/json или X-Requested-With: XMLHttpRequest):

    * HTTP `401 Unauthorized`
    * Body:

      ```json
      { "message": "Ошибка входа через Google." }
      ```

      или

      ```json
      { "message": "Ошибка входа через GitHub." }
      ```

#### 5.4.2. Ошибки БД / другие серверные ошибки

Если во время SSO что-то пошло не так:

* проблемы с БД (упала/недоступна):

    * Лог: `Google/GitHub OAuth failed – db error`

    * HTTP `503 Service Unavailable` (для JSON / AJAX-флоу)

    * Body:

      ```json
      { "message": "Ошибка сервера. Попробуйте позже." }
      ```

    * В браузерном режиме — redirect на `app.oauth2.error-redirect` с тем же `message` в query.

* ошибка вставки/апдейта в БД (constraint violation и т.п.):

    * Лог: `Google/GitHub OAuth failed – insert error`

    * HTTP `500 Internal Server Error` (JSON/AJAX)

    * Body:

      ```json
      { "message": "Что-то пошло не так. Попробуйте позже." }
      ```

    * В браузерном режиме — redirect на `/auth/error?message=...`.

---

### 5.5. JSON-режим для SSO (для тестов / BFF)

Теоретически, при SSO возможно получить **JSON с TokenResponse**, а не redirect, если:

* запрос на финальную точку (`/login/oauth2/code/{provider}`) приходит с:

    * заголовком `Accept: application/json`
    * или `X-Requested-With: XMLHttpRequest`.

В боевом SPA это не очень удобно (OAuth — redirect-based протокол), но это полезно:

* для интеграционных тестов (эмулировать последний шаг и получить JSON),
* для BFF/серверных сценариев.

Тогда при успешном входе:

* статус `200` (существующий пользователь) или `201` (новый пользователь),
* body — `TokenResponse`:

```json
{
  "token_type": "Bearer",
  "access_token": "...",
  "expires_in": 900,
  "refresh_token": "..."
}
```

---

## 6. SSO-привязки (серверное поведение, важно понимать фронту)

Таблица `auth.auth_user_sso_accounts` хранит привязки внешних аккаунтов к локальному пользователю.

При SSO входе выполняется такой алгоритм (упрощённо):

1. Найти SSO-привязку по `(provider, providerUserId)`:

    * если **есть** — берём этого пользователя, обновляем `last_login_at` и выдаём токены.
2. Если привязки нет — ищем пользователя по `email`:

    * если **есть** — создаём новую SSO-привязку (`provider + providerUserId → user_id`), обновляем `last_login_at`, выдаём токены.
    * если **нет** — создаём нового пользователя с этим `email` и `full_name`, при `emailVerified=true` ставим `email_verified_at`, создаём SSO-привязку, выдаём токены.

Для фронта это означает:

* Не важно, заходишь ли в первый раз или уже ходил:

    * если вход успешный — всегда прилетают валидные токены.
* Один и тот же email может быть использован:

    * сначала при обычной регистрации,
    * позже — для привязки Google/GitHub (будет создана запись в SSO-таблице, но user остаётся тем же).

---

## 7. Краткий чек-лист для фронта

1. **Парольный вход:**

    * POST `/api/auth/login` → сохраняем `access_token`/`refresh_token`.
    * GET `/api/me` с `Authorization: Bearer ...` — для проверки.

2. **SSO-вход (Google/GitHub):**

    * редирект на `/oauth2/authorization/google|github`;
    * после возврата на `/auth/callback` читаем `window.location.hash`, достаём токены;
    * сохраняем токены, чистим hash.

3. **Обновление токенов:**

    * при `401` от API → попробовали `/api/auth/refresh`;
    * при успехе — подменили токены и повторили оригинальный запрос;
    * при ошибке refresh — выкинули пользователя на экран логина.

4. **Ошибки SSO:**

    * страница `/auth/error` читает `message` из `window.location.search`;
    * отображает человеку:

        * `Ошибка входа через Google.` / `Ошибка входа через GitHub.` или
        * `Ошибка сервера. Попробуйте позже.` / `Что-то пошло не так. Попробуйте позже.`