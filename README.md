# AUTH SERVER с централизованной аутентификацией (JWT) для микросервисов

Цель — унифицировать точку входа, CORS и маршрутизацию. В этом репозитории проект разнесён по сервисам:

- **api-gateway** — маршрутизация и CORS (порт 8080)
- **auth-service** — регистрация/логин и проверка JWT (порт 8081, PostgreSQL, RS256)
- **example-under-armor-service** — пример защищённого бизнес-сервиса (порт 8082)
- **frontend auth** — SPA (порт 3030 через Nginx)
- **postgres** — база данных (порт 5432)

## Какую боль решает проект

- **Единая точка входа (Ingress)**: все внешние вызовы заходят через `api-gateway`. Это устраняет разброс правил (CORS, заголовки, таймауты) по сервисам и даёт O(1)‑место для изменений.
- **Разделение ответственности**: `auth-service` становится доменом «Идентификация/Аутентификация», а бизнес‑сервисы остаются чистыми от login/signup и криптографии.
- **Отсутствие дрейфа политик безопасности**: настройки кэширующих/безопасных заголовков, CORS и требования к авторизации живут централизованно и версионируются вместе.
- **Zero‑trust кросс‑сервисный доступ**: каждый запрос несёт JWT; доверие не строится на сетевой зоне. Это упрощает горизонтальное масштабирование и деплой в смешанных средах.
- **Наблюдаемость и контроль**: gateway — удобная точка для метрик, трейсинга, аудит‑логов и корреляционных ID.
- **Развёртывание без боли**: docker‑first конфигурация, health‑checks, изолированные сети (`backend`/`public`) — проект поднимается одной командой.

## Расширяемость и потенциал

- **Подключение новых сервисов (Gateway)**
  - добавляйте правила маршрутизации в `api-gateway/src/main/resources/application-docker.yml`
  - пер‑роут политики: таймауты/ретраи, заголовки, size‑limits
- **Идентичность и авторизация (Auth‑service)**
  - роли/права через `SecurityConfig` (RBAC), возможность перейти к **scopes**/**claims‑based** (ABAC)
  - multi‑tenant: `tenant_id` в claims, фильтрация данных по тенанту
- **Стойкость ключей и криптография**
  - хранение секрета/ключей во внешнем vault (Vault, AWS KMS, Azure Key Vault)
  - автоматическая ротация ключей по расписанию
  - поддержка нескольких активных ключей для smooth rotation
  - интеграция с HSM (Hardware Security Modules)
- **Управление жизненным циклом токена**
  - device‑binding/аттестация клиента через дополнительные claims
  - интеграция с Redis для хранения refresh токенов (высокая нагрузка)
  - поддержка нескольких refresh токенов на устройство
  - механизм отзыва всех токенов пользователя при подозрительной активности
- **Наблюдаемость и SRE‑практики**
  - алерты SLO (latency/error budget) на основе метрик
  - distributed tracing с Jaeger для сложных запросов
  - интеграция с ELK stack для продвинутого лог-анализа
- **Надёжность на периметре**
  - circuit‑breaking, backoff‑retry, hedging, timeouts; защита от N+1 и штормов
  - blue/green, canary через маршрутизацию на gateway; A/B‑раскатки
- **Развитие DevEx**
  - генераторы каркаса сервиса (cookie‑cutter), статические договоры (OpenAPI), тестовые токены
  - one‑command bootstrap: `docker compose up -d --build`

## Сквозной поток запроса: от фронта до сервиса

1. **Фронтенд** бьётся в `api-gateway:8080`
2. **Запросы `/auth/**`** проксируются в `auth-service:8081`:
   - `POST /auth/signup` — регистрация пользователя в БД
   - `POST /auth/login` — выдача JWT (RS256) + refresh token в httpOnly cookie
   - `POST /auth/refresh` — ротация токенов (новый JWT + новый refresh)
   - `POST /auth/logout` — отзыв refresh token
3. **Клиент** хранит JWT в localStorage, refresh token в httpOnly cookie
4. **Защищённые запросы**:
   - `/auth/**` — JWT проверяется в `auth-service` через `JwtAuthenticationFilter`
   - `/example-under-armor/**` — JWT валидируется через JWKS endpoint в `example-under-armor-service`
5. **Автоматический refresh**: при 401 ошибки фронтенд автоматически обновляет токены

## Архитектура

```
ЛОГИН + РЕГИСТРАЦИЯ (RS256 + Refresh Token)
--------------------------------------------
[Frontend :3030]  ← JWT в localStorage
   |                    ← Refresh в httpOnly cookie
   |  POST /auth/login {username,password}
   v
[API Gateway :8080]  ← Correlation ID filter
   |
   |  route: /auth/** → auth-service:8081
   v
[Auth Service :8081] ← RS256 JWT generation
   |                    ← BCrypt password hashing
   |  ← validate credentials → [PostgreSQL :5432]
   |  ← store refresh token hash
   v
[API Gateway] → {jwt, refresh_cookie} → [Frontend]

ЗАЩИЩЁННЫЕ ЗАПРОСЫ (JWT + JWKS Validation)
-------------------------------------------
[Frontend] → POST /example-under-armor/me + Bearer <jwt>
   ↓
[API Gateway :8080] → route: /example-under-armor/** → example-under-armor-service:8082
   ↓
[Example Under Armor Service :8082] ← OAuth2ResourceServer
                                       ← JWKS validation: /.well-known/jwks.json
                                       ← auth-service:8081/.well-known/jwks.json
   ↓
200 {username, roles} → [API Gateway] → [Frontend]
```

## Ключевые особенности архитектуры

### 🔐 **Аутентификация (RS256 + Refresh Tokens)**
- **JWT RS256**: Асимметричное шифрование (production-ready)
- **Refresh Tokens**: В httpOnly cookies, хешируются SHA-256 в БД
- **JWKS Endpoint**: `/.well-known/jwks.json` для прозрачной валидации
- **Автоматический Refresh**: При 401 фронтенд обновляет токены

### 🏗️ **Микросервисная архитектура**
- **API Gateway**: Единая точка входа, маршрутизация, CORS
- **Auth Service**: Домен аутентификации/авторизации
- **Business Services**: Чистые от логики аутентификации
- **Изолированные сети**: `backend` (internal), `public`

### 📊 **Наблюдаемость**
- **OpenTelemetry**: Трассировка запросов
- **Prometheus**: Метрики производительности
- **Correlation ID**: Сквозная трассировка
- **Structured Logging**: Логи с контекстом

### 🔄 **Поток данных**
- **Логин**: Frontend → Gateway → Auth Service → PostgreSQL
- **Защищённые запросы**: Frontend → Gateway → Business Service (с JWKS валидацией)
- **Health Checks**: Docker Compose проверяет готовность сервисов
- **Автоматический refresh**: Прозрачная ротация токенов

## API

### 🔐 Аутентификация

#### POST /auth/signup
Регистрация нового пользователя

**Request:**
```json
{
  "username": "john",
  "password": "strong-password"
}
```

**Responses:**
- `200 OK` — "User registered successfully."
- `400 Bad Request` — "Username is already taken."

#### POST /auth/login
Аутентификация пользователя

**Request:**
```json
{
  "username": "john",
  "password": "strong-password"
}
```

**Response 200:**
```json
{
  "jwt": "<access_token_rs256>"
}
```
*Примечание:* Refresh token устанавливается в httpOnly cookie

**Response 401:** "Invalid username or password."

#### POST /auth/refresh
Обновление токенов (ротация)

**Response 200:**
```json
{
  "jwt": "<new_access_token>"
}
```

#### POST /auth/logout
Выход из системы (отзыв refresh token)

**Response 200:**
```json
{
  "ok": true
}
```

### 🏢 Бизнес-сервисы

#### GET /example-under-armor/me
Пример защищённого эндпоинта

**Headers:**
```
Authorization: Bearer <jwt>
```

**Response 200:**
```json
{
  "username": "john",
  "roles": ["ROLE_USER"]
}
```

### 🔧 Системные эндпоинты

#### GET /.well-known/jwks.json
JWKS endpoint для валидации токенов

**Response:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "<key_id>",
      "n": "<modulus>",
      "e": "<exponent>"
    }
  ]
}
```

#### GET /actuator/health
Health check эндпоинт

**Response:**
```json
{
  "status": "UP"
}
```

## 🚀 Быстрый старт

### 📋 Предварительные требования
- Docker + Docker Compose
- Java 21 (для локального запуска)

### ⚙️ Настройка переменных окружения

Создайте файл `.env` в корне проекта:
```bash
DB_PASSWORD=your_secure_password_here
```

### 🏃‍♂️ Запуск проекта

```bash
# Запуск всех сервисов
docker compose up -d --build

# Просмотр логов
docker compose logs -f

# Остановка
docker compose down
```

### 🌐 Доступ к сервисам

| Сервис | URL | Описание |
|--------|-----|----------|
| **Frontend** | http://localhost:3030 | React SPA |
| **API Gateway** | http://localhost:8080 | Единая точка входа |
| **Auth Service** | http://localhost:8081 | Сервис аутентификации |
| **Example Service** | http://localhost:8082 | Пример бизнес-сервиса |
| **PostgreSQL** | localhost:5432 | База данных |

### 📖 Документация API

| Сервис | Swagger UI | OpenAPI JSON |
|--------|------------|--------------|
| Auth Service | http://localhost:8081/swagger-ui | http://localhost:8081/v3/api-docs |
| Example Service | http://localhost:8082/swagger-ui | http://localhost:8082/v3/api-docs |

## 🔒 Безопасность (Production-Ready)

### 🛡️ Аутентификация и Авторизация
- **RS256 JWT**: Асимметричное шифрование (production-grade)
- **Refresh Tokens**: В httpOnly cookies, хешируются SHA-256 в БД
- **JWKS Endpoint**: `/.well-known/jwks.json` для прозрачной валидации
- **Автоматическая ротация**: Refresh токены обновляются при каждом использовании

### 🔐 Хранение секретов
- **RSA ключи**: Генерируются автоматически при запуске (2048 бит)
- **Пароли**: BCrypt хеширование
- **Refresh токены**: SHA-256 хеши в БД (не plaintext)
- **Переменные окружения**: Все секреты через `.env` файл

### 🚨 Защита от атак
- **Zero-Trust**: Каждый запрос требует JWT валидации
- **Stateless**: Нет серверных сессий
- **CSRF отключён**: Для stateless API
- **CORS**: Строго настроен на frontend домен
- **Health checks**: Docker Compose проверяет готовность сервисов

### 📊 Мониторинг безопасности
- **Correlation ID**: Сквозная трассировка запросов
- **Structured logging**: Логи с контекстом для аудита
- **OpenTelemetry**: Трассировка подозрительной активности
- **Prometheus metrics**: Мониторинг производительности

### ⚠️ Важные замечания
- **RSA ключи** генерируются при каждом запуске (для demo)
- **В production** используйте внешнее хранилище ключей (Vault, AWS KMS)
- **HTTPS обязательна** для защиты JWT в трафике
- **Регулярно обновляйте** refresh токены и ключи


## 🛠️ Технологии

- **Java 21** - JVM платформа
- **Spring Boot 3** - Фреймворк приложений
- **Spring Security** - Аутентификация и авторизация
- **Spring Data JPA** - ORM для PostgreSQL
- **Spring Cloud Gateway** - API Gateway
- **JJWT** - JWT токены (RS256)
- **OAuth2 Resource Server** - JWT валидация в сервисах
- **PostgreSQL** - Реляционная база данных
- **React + TypeScript** - Frontend
- **Docker + Docker Compose** - Контейнеризация
- **OpenTelemetry** - Распределённая трассировка
- **Prometheus** - Метрики и мониторинг