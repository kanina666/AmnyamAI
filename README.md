# Ам Ням — AI-ассистент для встреч

Мобильное приложение, которое в реальном времени записывает совещание, расшифровывает речь с разделением по спикерам и автоматически формирует краткое резюме и список задач с дедлайнами.

---

## Команда

| ФИО | Роль               |
|--|--------------------|
| Гаврилович Вероника Вячеславовна | Android-разработка |
| Губарева Екатерина Алексеевна | backend            |
| Конькова Нина Александровна | backend, ML        |
| Слонимская Ксения Григорьевна | Android-разработка |

---

## Основной сценарий

1. Пользователь входит через Google
2. Создаёт встречу с названием
3. Приложение начинает запись — транскрипт появляется на экране в реальном времени с разбивкой по спикерам
4. После остановки записи YandexGPT анализирует транскрипт
5. Пользователь получает резюме и список задач с дедлайнами
6. Задачи можно синхронизировать с Google Calendar одним нажатием

---

## Стек технологий

### Android
- **Kotlin** + **Jetpack Compose** — UI и логика приложения
- **MVVM** (ViewModel + StateFlow) — архитектура
- **Retrofit** + **OkHttp** — HTTP-клиент, REST API
- **OkHttp WebSocket** — стриминг аудио на сервер в реальном времени
- **Android AudioRecord** — захват звука с микрофона (PCM 16-bit, 16 кГц, моно)
- **Foreground Service** — фоновая запись без прерываний
- **Credential Manager API** + **Google Identity Authorization API** — авторизация через Google
- **Coil** — загрузка GIF-анимаций

---

## Android — техническая реализация

**Стек:** Kotlin · Jetpack Compose · MVVM · Coroutines · OkHttp · Retrofit

### Авторизация

- Google Sign-In через **Credential Manager API** (современный подход, без deprecated `GoogleSignIn`)
- Двухшаговый флоу: получение `idToken` + `serverAuthCode` через Google Identity Authorization API
- Обмен `serverAuthCode` на JWT-токен через собственный бэкенд
- Локальное хранение токена и профиля в SharedPreferences

### Запись встречи

- **Foreground Service** (`RecordingService`) захватывает аудио с микрофона в фоне — запись не прерывается при сворачивании приложения
- Параметры аудио: **PCM 16-bit, 16 кГц, моно** — нативный формат Yandex SpeechKit
- Чанки по **100 мс (3200 байт)** отправляются в реальном времени

### WebSocket стриминг

- Аудиочанки стримятся на бэкенд через **OkHttp WebSocket** во время записи
- Получение частичных и финальных транскриптов в реальном времени — отображаются на экране во время записи
- Автоматический реконнект при обрыве соединения (до 3 попыток с экспоненциальной паузой)
- Корректное завершение: ожидание подтверждения закрытия WS перед вызовом `/finish`

### Архитектура

- **MVVM** — вся бизнес-логика в ViewModel, UI только реагирует на состояния
- Навигация через **Navigation Compose**
- HTTP-клиент: Retrofit + OkHttp с interceptor для автоматической подстановки Bearer-токена
- Все асинхронные операции на **Kotlin Coroutines + Flow**

### Backend
- **Python 3.11** + **FastAPI** — REST API и WebSocket
- **PostgreSQL** + **SQLAlchemy** (async) + **Alembic** — база данных
- **gRPC** — взаимодействие с SpeechKit
- **Docker** + **Docker Compose** — контейнеризация
- **JWT** (python-jose) — аутентификация

### Yandex Cloud
- **SpeechKit Streaming STT v3** — потоковое распознавание речи
- **YandexGPT 5.1 Pro** — анализ транскрипта, генерация резюме и задач

### Google Cloud
- **Google OAuth 2.0** — авторизация пользователей
- **Google Calendar API** — синхронизация задач с календарём

---

## Возможности Алисы (Yandex AI)

### 1. SpeechKit — потоковая расшифровка речи
- Аудио с микрофона Android-устройства стримится на сервер чанками по 100 мс (3200 байт) через WebSocket
- Сервер передаёт аудио в **SpeechKit Streaming Recognition v3** через gRPC в реальном времени
- Используется режим **FULL_DATA** с моделью `general:rc`
- Включена **диаризация спикеров** (`SPEAKER_LABELING_ENABLED`) — каждая реплика привязывается к конкретному участнику встречи
- Включена **нормализация текста** и литературный режим для чистого вывода
- Результаты (partial / final) возвращаются на Android в реальном времени и отображаются на экране записи

### 2. YandexGPT 5.1 Pro — анализ встречи
- После окончания записи транскрипт (с метками спикеров) отправляется в **YandexGPT 5.1 Pro**
- GPT возвращает структурированный JSON с:
  - **Резюме** встречи (до 4 предложений)
  - **Списком задач** — каждая содержит исполнителя, название, описание и дедлайн в формате ISO 8601
- Используется `response_format: json_object` для гарантированного JSON-ответа
- Дедлайны вычисляются относительно текущей даты

---

## Архитектура

```
Android App
    │
    ├── AudioRecord (PCM16, 16kHz, моно)
    │       │ WebSocket (бинарные чанки по 100мс)
    │       ▼
    ├── FastAPI Backend
    │       ├── SpeechKit gRPC → TranscriptSegment (БД)
    │       └── POST /finish → YandexGPT → Summary + Tasks (БД)
    │
    └── REST API (Retrofit)
            ├── Авторизация (Google OAuth → JWT)
            ├── Управление встречами
            ├── Задачи
            └── Google Calendar Sync
```

---

## Структура репозитория

```
AmnyamAI/
├── app/                          # Android-приложение
│   └── src/main/java/com/example/amnyamai/
│       ├── data/
│       │   ├── local/            # SharedPreferences (UserStorage)
│       │   ├── model/            # Модели данных (Meeting, Task, User)
│       │   ├── remote/           # Retrofit, ApiService, DTO
│       │   └── repository/       # MeetingRepository
│       ├── service/              # RecordingService (Foreground Service)
│       ├── ui/
│       │   ├── screens/          # Экраны: Login, Home, Recording, Result, History
│       │   ├── viewmodel/        # ViewModel для каждого экрана
│       │   ├── components/       # Переиспользуемые UI-компоненты
│       │   ├── navigation/       # NavGraph, AppNavigation
│       │   └── theme/            # Цвета, типографика, тема
│       └── utils/                # WebSocketClient, GoogleConfig
│
└── backend_ml/                   # Python-бэкенд
    ├── app/
    │   ├── api/                  # Роутеры: auth, meetings, tasks, websocket
    │   ├── core/                 # Конфиг, JWT, безопасность
    │   ├── db/                   # Модели SQLAlchemy, схемы Pydantic, сессия
    │   ├── services/             # STT (SpeechKit), GPT (YandexGPT), Calendar
    │   └── utils/                # Аудио-утилиты, промпты
    ├── docker-compose.yml
    ├── Dockerfile
    ├── requirements.txt
    └── .env.example
```

---

## Компоненты системы

### Android

| Компонент | Назначение |
|-----------|-----------|
| `RecordingService` | Foreground Service, захват PCM-аудио с микрофона |
| `MeetingWebSocket` | OkHttp WebSocket, стриминг аудио и приём транскриптов |
| `MeetingRepository` | Единая точка доступа к API и кэшу |
| `RetrofitClient` | HTTP-клиент с Bearer-токеном и 401-перехватчиком |
| `UserStorage` | SharedPreferences: JWT-токен и профиль пользователя |
| `RegisterViewModel` | Двухшаговый Google Sign-In (Credential Manager + Authorization API) |
| `RecordingViewModel` | Управление записью, WebSocket, таймер |
| `HomeViewModel` | Создание встречи, логаут |
| `ResultViewModel` | Отображение итогов, подтверждение задач |

### Backend

| Компонент | Назначение |
|-----------|-----------|
| `api/auth.py` | Google OAuth callback, выдача JWT |
| `api/meetings.py` | CRUD встреч, запуск анализа (`/finish`) |
| `api/tasks.py` | Управление задачами, подтверждение, синхронизация с Calendar |
| `api/websocket.py` | WebSocket: приём аудио → SpeechKit → сохранение транскрипта |
| `services/stt_service.py` | gRPC-клиент Yandex SpeechKit Streaming v3 |
| `services/gpt_service.py` | Клиент YandexGPT, парсинг JSON-ответа |
| `services/meeting_analysis.py` | Оркестратор: транскрипт → GPT → Summary + Tasks |
| `services/calendar_service.py` | Google Calendar API: создание событий по задачам |
| `core/security.py` | JWT encode/decode, Google OAuth обмен кода на токен |
| `db/models.py` | SQLAlchemy: User, Meeting, TranscriptSegment, Task |

---

## Запуск

### Требования

- Docker + Docker Compose
- Android Studio Hedgehog или новее
- Android-устройство или эмулятор (minSdk 26)
- Аккаунт Yandex Cloud с активированными SpeechKit и YandexGPT
- Проект в Google Cloud Console с OAuth 2.0 и Calendar API

### Бэкенд

**1. Настройка переменных окружения**

```bash
cd backend_ml
cp .env.example .env
```

Перед разворачиванием нужно отредактировать `.env`:

```env
# JWT — любая случайная строка
JWT_SECRET_KEY=your-secret-key-here

# Google OAuth (Web Application из Google Cloud Console)
GOOGLE_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxxxxxxx
GOOGLE_REDIRECT_URI=http://localhost:8000/api/v1/auth/google/callback

# Yandex Cloud
YANDEX_CLOUD_FOLDER_ID=b1gxxxxxxxxxxxxxxxxx
YANDEX_CLOUD_API_KEY=AQVNxxxxxxxxxxxxxxxxxxxxxxxxxx
YANDEX_GPT_MODEL=yandexgpt-5.1
YANDEX_GPT_MODEL_VERSION=pro
```

**2. Запуск**

```bash
docker compose up --build
```

- REST API: `http://localhost:8000`
- Swagger UI: `http://localhost:8000/docs`
- База данных: PostgreSQL на порту `5432` (внутри Docker)

**3. Тестирование без Android**

В режиме `DEBUG=true` доступны дополнительные эндпоинты:

```
POST /api/v1/auth/dev-login         # получить JWT без Google
POST /api/v1/meetings/{id}/transcript  # добавить транскрипт вручную
```

### Android

**1. Клонировать репозиторий и открыть в Android Studio**

**2. Настроить Web Client ID**

В файле `app/src/main/java/com/example/amnyamai/utils/GoogleConfig.kt`:
```kotlin
const val WEB_CLIENT_ID = "ваш-web-client-id.apps.googleusercontent.com"
```

**3. Настроить адрес сервера**

В файле `app/src/main/java/com/example/amnyamai/data/remote/RetrofitClient.kt`:
```kotlin
private const val PROD_URL = "http://<IP вашего сервера>:8000/"
```

Для эмулятора используется `http://10.0.2.2:8000/` автоматически.

**4. Собрать и запустить**

```
Build → Run 'app'
```

---

## Переменные окружения

| Переменная | Обязательная | Описание |
|-----------|:------------:|---------|
| `JWT_SECRET_KEY` |      +       | Секрет для подписи JWT-токенов |
| `GOOGLE_CLIENT_ID` |      +       | OAuth Client ID (Web Application) |
| `GOOGLE_CLIENT_SECRET` |      +       | OAuth Client Secret |
| `GOOGLE_REDIRECT_URI` |      +       | URI редиректа для OAuth |
| `YANDEX_CLOUD_FOLDER_ID` |      +       | ID каталога Yandex Cloud |
| `YANDEX_CLOUD_API_KEY` |      +       | API-ключ для SpeechKit и YandexGPT |
| `YANDEX_GPT_MODEL` |      —       | Модель GPT (по умолчанию `yandexgpt-5.1`) |
| `YANDEX_GPT_MODEL_VERSION` |      —       | Версия модели (по умолчанию `pro`) |
| `YANDEX_GPT_TEMPERATURE` |      —       | Температура генерации (по умолчанию `0.3`) |
| `SPEECHKIT_MODEL` |      —       | Модель STT (по умолчанию `general:rc`) |
| `SPEECHKIT_LANGUAGE_CODE` |      —       | Язык распознавания (по умолчанию `ru-RU`) |
| `DATABASE_URL` |      —       | Строка подключения к PostgreSQL |
| `DEBUG` |      —       | Включить dev-эндпоинты (по умолчанию `true`) |

---

## Зависимости

### Backend (`requirements.txt`)

| Библиотека | Назначение |
|-----------|-----------|
| `fastapi` | Web-фреймворк, REST API и WebSocket |
| `uvicorn` | ASGI-сервер |
| `sqlalchemy[asyncio]` + `asyncpg` | Async ORM + PostgreSQL-драйвер |
| `pydantic` + `pydantic-settings` | Валидация данных и конфиг из `.env` |
| `python-jose` | JWT-токены |
| `httpx` | Async HTTP-клиент (Google OAuth) |
| `grpcio` + `yandexcloud` | gRPC-клиент для SpeechKit |
| `openai` | OpenAI-совместимый клиент для YandexGPT API |
| `google-api-python-client` | Google Calendar API |

### Android (`build.gradle.kts`)

| Библиотека | Назначение |
|-----------|-----------|
| `Jetpack Compose` + `Material3` | Декларативный UI |
| `Navigation Compose` | Навигация между экранами |
| `Lifecycle ViewModel` | MVVM, управление состоянием |
| `Retrofit` + `OkHttp` | HTTP-клиент, REST API |
| `Kotlin Coroutines` | Асинхронность |
| `Credentials` + `GoogleId` | Credential Manager API, Google Sign-In |
| `play-services-auth` | Google Identity Authorization API (serverAuthCode) |
| `Coil` + `coil-gif` | Загрузка и отображение GIF |


