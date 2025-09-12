# AUTH SERVER с централизованной аутентификацией (JWT) для микросервисов

Цель — унифицировать точку входа, CORS и маршрутизацию. В этом репозитории проект разнесён по сервисам:

- **api-gateway** — маршрутизация и CORS (порт 8080)
- **auth-service** — регистрация/логин и проверка JWT (порт 8081, PostgreSQL, RS256)
- **example-under-armor-service** — пример защищённого бизнес-сервиса (порт 8082)
- **frontend auth** — SPA (порт 3030 через Nginx)
- **postgres** — база данных (порт 5432)

## Какую боль решает проект

- **Все запросы идут через `api-gateway`**. Правила CORS и заголовки настраиваются в одном месте.
- **Чистые бизнес‑сервисы**. `auth-service` отвечает за регистрацию/логин, а бизнес‑логика — отдельно.
- **Единые настройки безопасности**. Заголовки, CORS и доступ — в одном месте и под контролем.
- **Каждый запрос проверяется по JWT**. Не полагаемся на «доверенную сеть», проще масштабировать.
- **Удобная наблюдаемость**. Собираем метрики и трассировки, есть корреляционный ID для цепочек запросов.
- **Быстрый запуск**. Одна команда, health‑checks и изолированные сети (`backend`/`public`).

## Расширяемость и потенциал

- **api-gateway**: можно запускать несколько копий за балансировщиком.
- **auth-service**: можно запускать несколько копий. Важно: ключи RSA и refresh‑токены должны храниться общими для всех копий (единое хранилище/секреты).
- **example-under-armor-service**: также можно несколько копий.
- **otel-collector/prometheus/grafana/jaeger**: в демо — по одной копии; в прод — настраивайте отказоустойчивость по документации.

### Sticky и кэширование
- Сервисы кэшируют публичные ключи из JWKS — меньше нагрузка на `auth-service`.
- Gateway может кэшировать статические ответы и ограничивать размер запроса.

- **Подключение новых сервисов (Gateway)**
  - добавляйте правила маршрутизации в `api-gateway/src/main/resources/application-docker.yml`
  - политики на маршруты: таймауты, повторные попытки, заголовки, лимиты размера
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

1. **Фронтенд** идёт на `api-gateway:8080`
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
- **JWT RS256**: Асимметричная подпись токена приватным RSA‑ключом и проверка публичным ключом (production‑ready).
- **Refresh Token в httpOnly cookie**: refresh хранится только в httpOnly/SameSite=strict cookie, в БД хранится SHA‑256 хеш. Обновляется при каждом `/auth/refresh`.
- **JWKS Endpoint**: `/.well-known/jwks.json` публикует публичные RSA‑ключи (`kid`, `n`, `e`) для валидации подписи без шаринга приватного ключа.
- **Автоматический Refresh**: при 401 фронтенд прозрачно вызывает `/auth/refresh` и повторяет оригинальный запрос.

### 🔑 Потоки токенов
- `POST /auth/login` → выдаётся `access` (JWT RS256) + ставится refresh cookie.
- `POST /auth/refresh` → Атомарно: новый `access` + ротация refresh cookie; старый refresh становится недействительным.
- `POST /auth/logout` → refresh помечается отозванным, чтобы предотвратить повторное использование.

### 🏗️ **Микросервисная архитектура**
- **API Gateway**: Единая точка входа, маршрутизация, CORS
- **Auth Service**: Домен аутентификации/авторизации
- **Business Services**: Чистые от логики аутентификации
- **Изолированные сети**: `backend` (internal), `public`

### 📊 **Наблюдаемость**
- **OpenTelemetry Collector (OTEL)**: принимает и отправляет трейсы (`http://localhost:4318`). Health: `http://localhost:13133`.
- **Jaeger**: показывает цепочки запросов и их время (`http://localhost:16686`).
- **Prometheus**: собирает метрики из `/actuator/prometheus`. Настройки — в `prometheus.yml`.
- **Grafana**: дашборды и графики по метрикам Prometheus (`http://localhost:3000`). Datasource настроен в `grafana/provisioning/datasources/datasource.yml`.
- **Correlation ID**: помогает связать логи и трейсы одного запроса.
- **Структурированные логи**: проще искать проблемы.

### 🔄 **Поток данных**
- **Логин**: Frontend → Gateway → Auth Service → PostgreSQL
- **Защищённые запросы**: Frontend → Gateway → Business Service (с JWKS валидацией)
- **Health Checks**: Docker Compose проверяет готовность сервисов
- **Автоматический refresh**: Прозрачная ротация токенов

### 📖 Swagger / OpenAPI

- Auth Service (через Gateway):
  - Swagger UI: http://localhost:8080/auth/swagger-ui/index.html
  - OpenAPI JSON: http://localhost:8080/auth/v3/api-docs

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
| Auth Service (внутр.) | :8081 | Внутренний порт, ходим через gateway |
| Example Service (внутр.) | :8082 | Внутренний порт, ходим через gateway |
| **PostgreSQL** | localhost:5432 | База данных |
| **Jaeger UI** | http://localhost:16686 | Просмотр распределённых трейсов |
| **Prometheus** | http://localhost:9090 | Метрики (scrape /actuator/prometheus) |
| **Grafana** | http://localhost:3000 | Дашборды и графики (логин/пароль: admin/admin) |
| **OpenTelemetry Collector** | http://localhost:4318/v1/traces | Приём OTLP/HTTP (без UI) |

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
- **Docker** - Контейнеризация
- **OpenTelemetry** - Распределённая трассировка
- **Prometheus** - Метрики и мониторинг
- **Grafana** - Дашборды и визуализация метрик
- **Jaeger** - UI для распределённой трассировки
- **OpenAPI / Swagger** - Спецификации и UI документации API