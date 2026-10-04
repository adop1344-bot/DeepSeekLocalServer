# DeepSeek Local Server (Android)

Локальный HTTP-сервер-мост к DeepSeek Web API, работающий прямо на телефоне. Даёт владельцу **OpenAI-совместимый endpoint**, к которому можно подключить RikkaHub и любые другие клиенты. Включает встроенный чат для теста, редактор настроек, логи с экспортом.

> ⚠️ Приложение использует **веб-токен** DeepSeek (из localStorage `userToken`), а не официальный API-ключ. Токен живёт ограниченное время (обычно ~55 минут), после чего его нужно обновить.

---

## Возможности

- **OpenAI-совместимый API**
  - `GET  /v1/models` — список моделей
  - `POST /v1/chat/completions` — основная точка (stream: true/false)
  - `POST /auth` — приём токена от bookmarklet-а
  - `GET  /auth` — статус токена
  - `GET  /health` — ping
  - `POST /reset` — сброс сессий
- **SSE-парсер** с поддержкой `[thinking]…[/thinking]` → `delta.reasoning_content`
- **Авто-продолжение**: если ответ обрывается (эвристики на `:`, `,`, `-`, открытая скобка) — досылает «продолжи» и склеивает до 4 итераций
- **PoW-модуль**: `sha3-256(salt_expire_answer)` с поиском до 4 нулевых префиксов (совместим с DeepSeekHashV1)
- **Управление сессиями**: in-memory `Map<hash, sessionId + parentMessageId>`
- **Буферизация**: длинные ответы режутся на чанки по 500 символов перед SSE
- **Foreground Service** (тип `specialUse`) + **Boot Receiver**
- **Shizuku-режим**: сервис стартует через `am start-foreground-service` от shell-UID — обходит агрессивное убийство фоновых процессов на Android 12+
- **Логи**: Room-таблица, фильтр по уровню/поиску, экспорт в Downloads, шаринг через `ACTION_SEND`, ротация старше 7 дней
- **Material You** (dynamic color), edge-to-edge, predictive back

---

## Требования

- **Android 8.0+** (minSdk 26)
- **JDK 17+** для сборки
- **Android SDK** (compileSdk 36)
- Опционально: **Shizuku** ([установка](https://shizuku.rikka.app/)) для persistent-режима

---

## Сборка

### Локально

```bash
# Debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (нужен release.keystore)
./gradlew assembleRelease
```

Если в репозитории отсутствует `gradle/wrapper/gradle-wrapper.jar` — сгенерируйте его:

```bash
gradle wrapper --gradle-version 8.10.2
```

### Через GitHub Actions

Репозиторий уже содержит `.github/workflows/build.yml`.

1. Запушьте код в свой GitHub-репозиторий.
2. Actions → **Build APK** → Run workflow.
3. Готовый `app-debug.apk` появится в **Artifacts**.

Release-сборка требует секретов:
- `SIGNING_KEY` — base64 от `release.keystore`
- `KEY_STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

---

## Использование

### 1. Первый запуск

Откройте приложение → появится предложение ввести токен. Нажмите **«Получить токен»**, чтобы открыть туториал.

### 2. Получение веб-токена DeepSeek

Токен берётся из `localStorage` сайта chat.deepseek.com.

**Способ A — bookmarklet (рекомендуется):**

1. Откройте Firefox или Kiwi на телефоне.
2. Войдите на https://chat.deepseek.com.
3. Создайте закладку с любым именем и вставьте в поле URL:

   ```
   javascript:(function(){var t=(JSON.parse(localStorage.getItem('userToken'))||{}).value||localStorage.getItem('userToken');if(!t){alert('userToken not found');return}location.href='http://<IP>:<PORT>/auth?token='+encodeURIComponent(t)})();
   ```

   Замените `<IP>:<PORT>` на адрес, который показывает приложение.
4. Тапните закладку на вкладке DeepSeek. Токен уйдёт в приложение.
5. Вернитесь в приложение — индикатор валидности станет зелёным.

**Способ B — вручную (через DevTools):**

1. Откройте chat.deepseek.com в десктопном браузере.
2. F12 → **Application** → **Local Storage** → `https://chat.deepseek.com`.
3. Скопируйте значение `userToken.value`.
4. Вставьте в поле токена в приложении.

### 3. Запуск сервера

На главном экране нажмите **ExtendedFloatingActionButton с иконкой ⏻**. Индикатор станет зелёным, а адрес — например `http://192.168.1.42:8788/v1`.

Кнопка **Copy** копирует адрес в буфер.

### 4. Подключение RikkaHub

1. Настройки → **Провайдеры** → **Добавить** → **OpenAI-совместимый**.
2. **Base URL**: `http://192.168.1.42:8788/v1` (ваш адрес).
3. **API Key**: любой непустой (например `ds-local`) — если Basic Auth выключен, ключ не проверяется.
4. **Модель**: `deepseek-chat` или `deepseek-reasoner`.
5. Сохраните и отправьте тестовое сообщение.

> Телефон и клиент должны быть в **одной Wi-Fi сети**. Если не подключается — проверьте, что Bind address = `0.0.0.0`, а роутер не блокирует локальные соединения (AP isolation).

---

## FAQ

### Сервер отключается через несколько минут

Android агрессивно убивает фоновые процессы. Решения:

1. **Настройки → Режим фона → Shizuku-persistent** (если Shizuku установлен). Это самый надёжный способ.
2. **Батарея:** Настройки Android → Приложения → DeepSeek Local Server → Батарея → **Без ограничений**.
3. **Не выгружайте** приложение из Recents.
4. Отключите оптимизацию для **всех** связанных приложений (сам сервер + клиент, если тестируете через localhost).

### Как включить Shizuku

1. Установите [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api).
2. Запустите сервис (через ADB-подключение или root).
3. В приложении: **Настройки → Режим фона → Shizuku-persistent** — появится запрос разрешения.
4. Разрешите — сервис теперь стартует через `am start-foreground-service` от shell-UID.

### Где сохраняются логи

- **В приложении**: вкладка «Логи». Экспорт — в `Downloads/deepseek-log-<timestamp>.txt`.
- **Логкат**: перехватывается в фоне и пишется в Room (последние 7 дней).

### Токен «протух» — что делать?

Токен DeepSeek живёт ~55 минут. При ошибке 401/403 сервер вернёт соответствующую ошибку. Зайдите в **Настройки → Получить токен** и обновите вручную (или повторно тапните bookmarklet).

### Почему HTTPS не поддерживается?

Это локальный HTTP для связи внутри телефона/LAN. Трафик не выходит за пределы вашей сети. Для LAN-использования можно включить Basic Auth (**Настройки → Basic Auth**) с ключом `Bearer <key>` — клиент должен будет передавать его в заголовке `Authorization`.

---

## Структура проекта

```
app/src/main/java/com/rikkahub/deepseeklocal/
├── data/
│   ├── local/db/           Room (логи)
│   ├── local/prefs/        DataStore + EncryptedSharedPreferences (токен)
│   ├── remote/deepseek/    HTTP-клиент, PoW-солвер, SSE-парсер
│   └── repository/
├── domain/model/           Sealed-состояния UI, доменные модели
├── presentation/           Compose-экраны (main, chat, logs, settings, onboarding, about)
├── server/                 Ktor-роуты, SessionManager, AutoContinue, ToolCallParser
├── service/                Foreground Service, BootReceiver, Logcat-перехват
├── shizuku/                Обёртка над Shizuku API
└── di/                     Hilt-модули
```

### Тесты

```bash
./gradlew testDebugUnitTest        # unit-тесты
./gradlew connectedDebugAndroidTest # instrumented (нужен эмулятор/устройство)
```

Покрытие:
- `PowSolverTest` — парсер челленджа и верификация решения
- `SseParserTest` — патчи, fragments, thinking, статус-слова
- `SessionManagerTest` — hash стабильность, getOrCreate, reset
- `CutHeuristicsTest` — эвристики обрыва
- `ToolCallParserTest` — извлечение `{"tool":…, "args":…}`

---

## Безопасность

- Токен хранится в **EncryptedSharedPreferences** (AndroidX Security Crypto). Никогда не хардкодится.
- **Не коммитьте** `.deepseek_token`, `.env`, `*.keystore`, `*.jks`, `secrets.properties`, `local.properties` — они уже в `.gitignore`.
- HTTPS отсутствует осознанно — это локальный HTTP-сервер для LAN. Не выставляйте его в интернет без reverse-proxy с TLS и аутентификацией.

---

## Благодарности

- [DeepSeek](https://www.deepseek.com/) — за API
- [Ktor](https://ktor.io/) — HTTP-сервер и клиент
- [Rikka Shizuku](https://github.com/RikkaApps/Shizuku) — за доступ к shell-привилегиям
- [@rezaparsian/deepseek-pow-solver](https://www.npmjs.com/package/@rezaparsian/deepseek-pow-solver) — референс PoW-алгоритма

---

## Лицензия

MIT. См. `LICENSE`.
