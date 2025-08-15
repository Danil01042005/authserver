# AUTH SERVER с централизованной аутентификацией (JWT) для микросервисов

Цель — унифицировать точку входа, CORS и маршрутизацию. В этом репозитории проект разнесён по сервисам:

- api-gateway — маршрутизация и CORS (порт 8080)
- auth-service — регистрация/логин и проверка JWT на своих эндпоинтах (порт 8081, PostgreSQL)
- frontend auth — SPA (порт 3030 через Nginx)

## Какую боль решает проект

- **Единая точка входа (Ingress)**: все внешние вызовы заходят через `api-gateway`. Это устраняет разброс правил (CORS, заголовки, таймауты, лимиты) по сервисам и даёт O(1)‑место для изменений.
- **Разделение ответственности**: `auth-service` становится доменом «Идентификация/Аутентификация», а бизнес‑сервисы остаются чистыми от login/signup и криптографии.
- **Отсутствие дрейфа политик безопасности**: настройки кэширующих/безопасных заголовков, CORS и требования к авторизации живут централизованно и версионируются вместе.
- **Zero‑trust кросс‑сервисный доступ**: каждый запрос несёт JWT; доверие не строится на сетевой зоне. Это упрощает горизонтальное масштабирование и деплой в смешанных средах.
- **Наблюдаемость и контроль**: gateway — удобная точка для метрик, трейсинга, аудит‑логов, корреляционных ID и реакций (rate‑limit, circuit‑break).
- **Развёртывание без боли**: docker‑first конфигурация, health‑checks, изолированные сети (`backend`/`public`) — проект поднимается одной командой.

## Расширяемость и потенциал

- **Подключение новых сервисов (Gateway)**
  - добавляйте правила маршрутизации в `api-gateway/src/main/resources/application-docker.yml`
  - пер‑роут политики: таймауты/ретраи, лимиты скорости, заголовки, size‑limits
- **Идентичность и авторизация (Auth‑service)**
  - роли/права через `SecurityConfig` (RBAC), возможность перейти к **scopes**/**claims‑based** (ABAC)
  - multi‑tenant: `tenant_id` в claims, фильтрация данных по тенанту
- **Стойкость ключей и криптография**
  - переход с HS256 → RS256/ES256, JWKS‑эндпоинт, `kid` и прозрачная ротация ключей
  - хранение секрета/ключей во внешнем vault; автоматическая ротация и отзыв
- **Управление жизненным циклом токена**
  - короткий `access` + `refresh` со ск скользящей ротацией; список отозванных (Redis)
  - device‑binding/аттестация клиента через дополнительные claims
- **Наблюдаемость и SRE‑практики**
  - OpenTelemetry trace/span, Prometheus метрики, алерты SLO (latency/error budget)
  - structured‑logging с correlation‑id на gateway → end‑to‑end трассировка инцидентов
- **Надёжность на периметре**
  - circuit‑breaking, backoff‑retry, hedging, timeouts; защита от N+1 и штормов
  - blue/green, canary через маршрутизацию на gateway; A/B‑раскатки
- **Развитие DevEx**
  - генераторы каркаса сервиса (cookie‑cutter), статические договоры (OpenAPI), тестовые токены
  - one‑command bootstrap: `docker compose up -d --build`

## Сквозной поток запроса: от фронта до сервиса

1. Фронтенд бьётся в `api-gateway:8080`.
2. Запросы `/auth/**` проксируются в `auth-service:8081`:
   - `POST /auth/signup` — регистрация пользователя в БД
   - `POST /auth/login` — выдача JWT (HS256)
3. Клиент хранит JWT и отправляет его в `Authorization: Bearer <token>`.
4. Любые другие пути шлюз проксирует согласно правилам маршрутизации. Если цель — `auth-service`, то проверка JWT выполняется в его фильтре `JwtAuthenticationFilter`.
5. Для других микросервисов за шлюзом можно:
   - валидировать JWT в самих сервисах, или
   - добавить централизованную проверку на уровне шлюза (по требованию).

## Архитектура

```
ЛОГИН (JWT выдаёт auth-service)
-------------------------------
[Frontend]
   |
   |  POST /auth/login {username,password}
   v
[API Gateway :8080]
   |
   |  route: /auth/** -> auth-service
   v
[Auth Service :8081] -- verify creds --> [PostgreSQL]
   |
   |  <-- issue JWT (HS256)
   v
[API Gateway]  -->  { jwt }  -->  [Frontend]


ЗАЩИЩЁННЫЙ ЗАПРОС
------------------
[Frontend]
   |
   |  GET /auth/me  +  Authorization: Bearer <jwt>
   v
[API Gateway :8080] --route--> [Auth Service :8081]
                                   |
                                   |  JwtAuthenticationFilter: validate(jwt)
                                   v
                                 200 {username, roles}
```
 
 - Логин:
   - Вход: `POST /auth/login { username, password }`
   - Выход: `200 { jwt }` при успехе; `401` при неверных данных; `400` при невалидном теле запроса
 - Защищённые запросы:
   - Требуют `Authorization: Bearer <jwt>`
   - Если заголовка нет/токен просрочен/некорректен → `401`, до сервиса запрос не дойдёт
   - Если токен валиден → шлюз проксирует запрос в сервис; ответы `2xx/4xx/5xx` сервиса возвращаются клиенту без изменений
 - Куда уходит запрос после шлюза:
   - По совпадению `Path` из `spring.cloud.gateway.routes` (пример: `Path=/example/**` → `http://localhost:8081`)
   - Путь сохраняется: `GET /example/hello` → `http://localhost:8081/example/hello`
 - Что делает шлюз:
   - Валидирует JWT, настраивает CORS, удаляет дубликаты заголовков CORS
   - Не изменяет тело/статус ответа сервиса, не вмешивается в бизнес-логику
 - Что сейчас не делает (из коробки):
   - Авторизация по ролям; rate limiting; трейсинг — их можно добавить позже

## API

### POST /auth/signup
Request:

```json
{
  "username": "john",
  "password": "strong-password"
}
```

Responses:
- 200 OK — "User registered successfully."
- 400 Bad Request — "Username is already taken."

### POST /auth/login
Request:

```json
{
  "username": "john",
  "password": "strong-password"
}
```

Response 200 OK:

```json
{
  "jwt": "<token>"
}
```

Response 401: "Invalid username or password."

### Доступ к защищённым маршрутам
- Любые маршруты, не начинающиеся с `/auth/`, требуют заголовок `Authorization: Bearer <jwt>`.

## Быстрый старт

Предварительно установите: Docker + Docker Compose.

1. Переменные окружения
   - `DB_PASSWORD` — пароль пользователя `postgres` в Postgres (по умолчанию `postgres`).
   - `JWT_SECRET` — строка ≥ 32 символов (HS256).

2. Запуск всех сервисов

   ```bash
   docker compose up -d --build
   ```

3. Адреса
   - Frontend: `http://localhost:3030`
   - API Gateway: `http://localhost:8080`
   - Auth Service: `http://localhost:8081`

4. Ручной запуск модулей (опционально)
   - `api-gateway`/`auth-service`: `./gradlew bootRun`
   - `frontend auth`: `npm install && npm run build` (сборка, рантайм — Nginx в Dockerfile)


## Безопасность (важно)

- Храните `JWT_SECRET` и `DB_PASSWORD` в переменных окружения/secret‑хранилищах.
- HS256 требует длину секрета ≥ 32 символов.
- Пароли хешируются `BCrypt` (в `auth-service`).
- CORS в шлюзе настроен на dev: `http://localhost:3030`, `http://127.0.0.1:3030`.
- Сессии отключены (stateless). CSRF отключён — для stateless API.
- Health‑эндпоинты: `/actuator/health` на обоих back‑сервисах; compose использует healthchecks.


## Структура проекта

```
api-gateway/
├── src/main/java/ru/gateway/api_gateway/ (приложение + CorsConfig)
├── src/main/resources/application-docker.yml (маршруты и логгинг)
└── Dockerfile (мультистейдж)

auth-service/
├── src/main/java/ru/lms/auth/
│   ├── controller/AuthController.java (signup/login/me)
│   ├── config/SecurityConfig.java (правила доступа)
│   ├── config/JwtAuthenticationFilter.java (проверка JWT на своих эндпоинтах)
│   ├── service/* (JwtService, CustomUserDetailsService, UserService)
│   └── util/JwtUtil.java (HS256)
├── src/main/resources/application-docker.yml (datasource, logging, actuator)
└── Dockerfile (мультистейдж)

frontend auth/
├── src/* (Vite React)
├── Dockerfile (Node build → Nginx)
└── nginx.conf

docker-compose.yml (Postgres, gateway, auth-service, frontend; сети, healthchecks)
```

Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Spring Cloud Gateway (WebMVC), PostgreSQL, JJWT.