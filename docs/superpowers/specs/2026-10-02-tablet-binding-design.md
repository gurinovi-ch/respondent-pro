# Привязка планшета к Компании — апгрейд pairing-механизма RSP

Дата: 2026-10-02 (ревизия A, заменяет вариант от 02.10). Статус: обновление утверждено пользователем 02.10.2026 (решения: 8 символов base32, TTL 15 мин).
Репозитории: клиент — этот repo (Android); сервер — `https://github.com/gurinovi-ch/RSP` (private), рабочая копия `C:\projects\RSP` (вне OneDrive; локальный каталог `OneDrive\...\server` — устаревший архив, не использовать).

## 1. Контекст и цель

Кабинет `my.respondent.pro` (NestJS + Prisma + Vite dashboard, репо RSP) **уже содержит реализацию привязки**: `POST /api/tablets` выдаёт 6-значный PIN (TTL 24 ч, открытым текстом в колонке `Tablet.pairingPin`, `Math.random()`), `POST /api/tablets/pair {pin}` обменивает его на API-ключ (одноразово — PIN гасится), `POST /api/tablets/:id/regenerate-pin` перевыпускает, `rotateApiKey` чистит PIN. Dashboard отображает PIN.

Недостатки существующей реализации: PIN в БД открытым текстом; 6 цифр (10⁶) без rate-limit; `Math.random()`; два **разных** сообщения об ошибке (второе раскрывает состояние ключа); TTL 24 ч; ответ `pair` не содержит названия Компании; нет поля «Точка».

Цель: поднять существующий механизм до уровня «самый безопасный и самый простой», не ломая контракт dashboard, и дать клиенту метаданные для отображения.

## 2. Область

**В scope (сервер):**
- Апгрейд `Tablet.pairingPin`: хранение SHA-256 (hex), генерация 8 символов base32 через CSPRNG, TTL 15 минут, единое сообщение ошибки, rate-limit 10/мин на `pair`, IP-лог отклонений.
- Ответ `pair` расширяется: `+ organizationId, organizationName, pointName`.
- Новое поле `Tablet.pointName` (создание/обновление планшета, отображение на клиенте).
- HTTP e2e-тесты (supertest) для `pair`: единый 400, 429.

**В scope (клиент):**
- Секция «Привязка к кабинету» в Настройках, ввод кода, EncryptedSharedPreferences, HTTP `POST /api/tablets/pair`, локальная отвязка, разбор 401/403.

**Out of scope (отдельные этапы):**
- Забор настроек/контента из кабинета (GET), heartbeat/мониторинг (`lastSeenAt`/геолокация), отправка отзывов через сервер, справочник Точек с аналитикой, переименование Точки с планшета, QR-ввод кода, device-meta в heartbeat.

## 3. Утверждённые решения

1. **Точка (v1):** поле `Tablet.pointName`, заполняется и правится только в кабинете; планшет отображает. История отзывов не зависит от имени (связь через `tabletId`); справочник Point — позже.
2. **Ввод кода:** только текстом (камеру не возвращаем — CAMERA в манифесте `tools:node="remove"`).
3. **Отзыв ключа:** при явном HTTP 401/403 на авторизованных запросах — очистить локальный ключ, показать статус-строку «Привязка отменена». Не диалог. Сетевые ошибки отвязкой не считаются.
4. **Формат и TTL (утв. 02.10):** 8 символов из алфавита `2-9A-HJKMNPQRSTUVWXYZ` (32 символа, без 0/1/I/L/O), TTL **15 минут**, хэш SHA-256, CSPRNG. Существующие пути endpoint'ов сохраняются.
5. **Рабочая копия сервера:** `C:\projects\RSP` (клон RSP, вне OneDrive; `.env` перенесён). Коммиты → `main`.

## 4. Флоу

```
Кабинет (dashboard): «Добавить планшет» (имя [+ Точка]) → в ответе одноразовый код
                      (или «Перевыпустить код» = regenerate-pin)
Планшет:              Настройки (PIN) → «Привязка к кабинету» → ввод кода → «Привязать»
Сервер:               POST /api/tablets/pair {pin} → SHA-256(pin) == pairingPin,
                      TTL не истёк → погасить PIN → выдать {tabletId, tabletName,
                      apiKey, organizationId, organizationName, pointName}
Планшет:              ключ → EncryptedSharedPreferences → «Привязан: Орг · Точка»
Отзыв:                rotate-key/delete в кабинете → следующий авторизованный запрос
                      401 → локальная очистка → «Привязка отменена»
```

Свойство сохранено: код не содержит ключа — при успехе выдаётся **текущий** активный `apiKey` планшета (ротизация после выдачи кода не ломает).

## 5. Контракт API (сервер, апгрейд против существующего)

### Существующие — изменения поведения

| Endpoint | Auth | Изменение |
|---|---|---|
| `POST /api/tablets` | Bearer | принимает `pointName?`; в ответе `pairingPin` = **новый** 8-символьный код (открытым текстом, один раз), хранится в БД как SHA-256 hex |
| `POST /api/tablets/:id/regenerate-pin` | Bearer | как выше (новый код, TTL 15 мин) |
| `POST /api/tablets/pair` | — | body `{ pin }`; **единый** ответ ошибки `400 {"message":"Invalid or expired pairing code"}` на все случаи (включая неактивный ключ — детали только в серверный лог); успех → `{ tabletId, tabletName, apiKey, organizationId, organizationName, pointName }`; rate-limit **10/мин/IP** (429); отклонения логируются level=warn с IP |
| `POST /api/tablets/:id/rotate-key` | Bearer | без изменений (уже чистит PIN) |
| `PUT /api/tablets/:id` | Bearer | принимает `pointName?` |
| `POST /api/feedbacks` | X-API-Key | без изменений |

`POST /api/pair` (из ревизии A) и `POST /api/tablets/:id/pairing-code` **отменяются** — контракт существует.

### Требования безопасности

- Только HTTPS (терминация на `my.respondent.pro`).
- В БД — только SHA-256 hex от нормализованного кода (верхний регистр, без дефисов); открытый код покидает сервер только в ответах `create`/`regenerate-pin`.
- Генерация: `crypto.randomInt` (CSPRNG) — `Math.random()` не используется.
- Перебор: алфавит 32⁸ ≈ 8.5×10¹¹ × TTL 15 мин × rate-limit 10/мин.
- **Деплой инвалидирует выданные PIN** (семантика колонки меняется с plaintext на хэш) — после обновления перевыпустить коды.
- Логирование отклонений `pair`: `warn` с IP и причиной.

## 6. Модель данных

Без новой таблицы (PairingCode отменён). Миграция Prisma — только:

```prisma
model Tablet {
  ...
  pointName String?   // новое поле; pairingPin/pairingPinExpiresAt — без изменения типа,
                      // семантика pairingPin становится "SHA-256 hex кода"
}
```

## 7. Клиент (Android)

- **Секция «Привязка к кабинету»** в Настройках (за PIN): статус «не привязан» / «{организация} · {точка}»; поле кода (авто-верхний регистр), кнопка «Привязать»; при привязке — «Отвязать» (только локальная очистка).
- **Хранение:** `EncryptedSharedPreferences` (androidx.security:security-crypto) — `apiKey`, `tabletId`, `organizationId`, `organizationName`, `pointName`. Room/DataStore не трогаются.
- **HTTP:** `KabinetConfig.BASE_URL = "https://my.respondent.pro/api/"`; `CabinetApi @POST("tablets/pair")`, body `{ pin: <нормализованный код> }`; interceptor `X-API-Key` — для будущих авторизованных запросов.
- **Состояние:** unbound / binding / bound / failed(код|сеть|отозван); 401/403 → очистка + «Привязка отменена».
- **Строки:** только AppStrings (ru+en).
- Привязка не меняет текущие каналы отправки (Telegram/Email продолжают работать).

## 8. Тесты

- **Сервер (Jest, уже настроен; baseline 23/23):** unit — генерация (формат, хэш в БД, TTL), `pair` (happy, неверный, просрочен, неактивный ключ → единый 400, погашение), `pointName` в create/update; HTTP e2e (supertest, Prisma замокан) — единый тело 400, 429 на 11-й запрос. Gate: `npm test` + `npx tsc --noEmit` + `npm run build`.
- **Клиент (JUnit):** нормализация кода, повторный вход при занятости (двойной тап → BUSY), 400→INVALID_CODE, 401/403→REVOKED+очистка, IOException→NETWORK без очистки, отвязка; строки ru/en. Gate: `assembleDebug testDebugUnitTest`.
- **Устройственная проверка** (после деплоя): паринг end-to-end + перевыпуск → визуал пользователя.

## 9. Риски и ограничения

- Dashboard отображает PIN — после смены формата проверить `dashboard/src/pages/SettingsPage.tsx` (числовые валидации/maxLength — поправить при необходимости).
- Выданные до деплоя PIN перестают работать (см. §5) — ожидаемо и приемлемо.
- Миграции требуют доступной БД (`docker compose up -d postgres` локально; на VPS — `deploy.sh`).
- EncryptedSharedPreferences: API 23+ (minSdk 24 ✓); физический root обходит — принято.
- v1 без device-meta (модель/версия) — этап мониторинга.

## 10. Дальнейшие этапы (контекст)

2) `GET` настроек (OrgSettings → AppSettings) + heartbeat; 3) отправка отзывов через `POST /api/feedbacks` (релей Telegram на сервере); 4) контент; 5) справочник Точек + переименование с планшета; 6) геолокация с планшета (endpoint `location` уже существует на сервере).
