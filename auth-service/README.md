# gatewayauth — API Gateway с централизованной аутентификацией (JWT) для микросервисов

Цель — вынести безопасность и сквозной доступ в один слой, разгрузив микросервисы от дублирования логики.

## Какую боль решает проект

- Дублирование аутентификации во всех сервисах и рассинхронизация правил безопасности.
- Разная реализация CORS/Headers и сложность поддержки на фронте.
- Сессионная аутентификация, плохо масштабируемая в микросервисной среде.
- Разрозненная точка входа: нет единого места для rate limiting, трейсинга, аудит‑логов.

Здесь вся проверка JWT, CORS и базовые политики находятся в одном месте — в шлюзе, а сервисы получают уже аутентифицированные запросы.

## Расширяемость и потенциал

- Добавление сервисов: описывайте новые маршруты (`id`, `uri`, `predicates`) в `application.yml`.
- Роли и доступ: добавьте маппинг ролей в `UserDetails` и правила в `SecurityConfig` (`authorizeHttpRequests`).
- Refresh/rotate tokens: реализуйте refresh‑токены, ротацию секрета, хранение blacklist.
- Внешние провайдеры: интеграция с OAuth2/OIDC (Keycloak, Auth0) вместо локальной БД.
- Масштабирование: gateway — stateless, горизонтально масштабируется; вынос секрета в vault/JWKS.
- Наблюдаемость: rate limiting, circuit breaking, корреляционные ID, аудит‑логи, распределённый трейсинг.
- Реактивный стек: при необходимости перейти на `spring-cloud-starter-gateway` (WebFlux).

## Сквозной поток запроса: от фронта до сервиса

1. Фронтенд отправляет запрос регистрации/логина на `POST /auth/signup` или `POST /auth/login`.
2. Для логина `JwtService` аутентифицирует пользователя через `AuthenticationManager`, загружает его через `CustomUserDetailsService` и генерирует `JWT` с помощью `JwtUtil`.
3. Фронтенд сохраняет `JWT` и добавляет его в заголовок `Authorization: Bearer <token>` при вызовах бизнес‑API.
4. Любой запрос через шлюз (порт `8080`) проходит `JwtAuthenticationFilter`:
   - Извлекается токен, валидируется подпись и срок действия, подставляется `SecurityContext`.
   - Для путей `/auth/**` и preflight `OPTIONS` проверка не выполняется.
5. Spring Cloud Gateway маршрутизирует запрос по правилам `spring.cloud.gateway.routes` (например, `/example/** → http://localhost:8081`).
6. Микросервис получает уже проверенный запрос. При необходимости он может дополнительно валидировать токен или доверять шлюзу.

## Архитектура

```
ЛОГИН (выдача токена)
---------------------
[Frontend]
   |
   |  POST /auth/login  {username, password}
   v
[API Gateway :8080]
   |
   |  Security: authenticate(username,password)
   v
[JWT Filter + Spring Security] ----> [PostgreSQL: users]
   |                                      ^
   |--- ок, выдаём JWT --------------------|
   v
[API Gateway]  -->  { jwt }  -->  [Frontend]


ЗАЩИЩЁННЫЙ ЗАПРОС (бизнес‑API)
------------------------------
[Frontend]
   |
   |  GET /example/hello  +  Authorization: Bearer <jwt>
   v
[API Gateway :8080]
   |
   |  Security: validate <jwt> (подпись, срок)
   v
[JWT Filter]  -- OK -->  [Gateway Router]
                                |
                                |  правило: Path=/example/**
                                v
                          [Service :8081]
                                |
                                |  ответ сервиса
                                v
                           [API Gateway]
                                |
                                |  проксируем ответ как есть
                                v
                           [Frontend]
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

Предварительно установите: JDK 21, PostgreSQL, Gradle Wrapper.

1. Подготовьте БД
   - Создайте БД `jwt`.
   - Установите переменную окружения `DB_PASSWORD` с паролем пользователя `postgres`.

2. (Опционально) задайте секрет для JWT
   - `JWT_SECRET` — строка ≥ 32 символов. Если не указать, будет использован dev‑секрет из `application.yml`.

3. Запустите шлюз

   ```bash
   ./gradlew bootRun
   ```

4. Настройте маршруты сервисов
   - В `src/main/resources/application.yml` добавляйте блоки в `spring.cloud.gateway.routes`.
   - Пример уже добавлен: `/example/** → http://localhost:8081`.


## Безопасность (важно)

- Храните `JWT_SECRET` и `DB_PASSWORD` только в переменных окружения/secret‑хранилищах.
- Используйте длинный секрет (≥ 32 символов) для HS256.
- Пароли хешируются `BCrypt`.
- CORS ограничен на dev: `http://localhost:3000`, `http://127.0.0.1:3000`. Для продакшена задайте свои домены.
- Сессии отключены (stateless). CSRF отключён — актуально для stateless API.


## Структура проекта

```
gatewayauth/
├── build.gradle
├── src/
│   ├── main/java/ru/lms/gatewayauth/
│   │   ├── config/ (SecurityConfig, JwtAuthenticationFilter)
│   │   ├── controller/ (AuthController)
│   │   ├── model/ (User, AuthenticationRequest/Response)
│   │   ├── repository/ (UserRepository)
│   │   ├── service/ (JwtService, CustomUserDetailsService, UserService)
│   │   └── util/ (JwtUtil)
│   └── main/resources/application.yml
└── README.md
```

Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Spring Cloud Gateway (WebMVC), PostgreSQL, JJWT.