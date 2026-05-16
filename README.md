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

## Стек технологий

### Android
- **Kotlin** + **Jetpack Compose** — UI и логика приложения
- **MVVM** (ViewModel + StateFlow) — архитектура
- **Retrofit** + **OkHttp** — HTTP-клиент, REST API
- **OkHttp WebSocket** — стриминг аудио на сервер в реальном времени
- **Android AudioRecord** — захват звука с микрофона (PCM 16-bit, 16 кГц, моно)
- **Foreground Service** — фоновая запись без прерываний
- **Google Sign-In SDK** — авторизация через Google
- **Coil** — загрузка GIF-анимаций

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

## Запуск

### Бэкенд

1. Скопировать `.env.example` → `.env` и заполнить:
   - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` — из Google Cloud Console (Web Application)
   - `YANDEX_CLOUD_API_KEY` — API-ключ Yandex Cloud
   - `YANDEX_CLOUD_FOLDER_ID` — ID каталога Yandex Cloud
   - `JWT_SECRET_KEY` — любая случайная строка

```bash
cd backend_ml
docker compose up --build
```

API будет доступно на `http://localhost:8000`  
Swagger-документация: `http://localhost:8000/docs`

### Android

1. Открыть проект в Android Studio
2. В `GoogleConfig.kt` вставить Web Client ID из Google Cloud Console
3. В `RetrofitClient.kt` указать IP-адрес сервера
4. Запустить на устройстве или эмуляторе

---

## Основной сценарий

1. Пользователь входит через Google
2. Создаёт встречу с названием
3. Приложение начинает запись — транскрипт появляется на экране в реальном времени с разбивкой по спикерам
4. После остановки записи YandexGPT анализирует транскрипт
5. Пользователь получает резюме и список задач с дедлайнами
6. Задачи можно синхронизировать с Google Calendar одним нажатием
