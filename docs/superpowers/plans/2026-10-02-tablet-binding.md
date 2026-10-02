# Tablet Binding — апгрейд pairing в RSP + Android-клиент — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Привязать планшет RESPONDENT.PRO к Компании через одноразовый 8-символьный pairing-код (15 мин) с хэшированным хранением и rate-limit — апгрейд существующего PIN-механизма RSP — плюс экран привязки в Android-приложении.

**Architecture:** Фаза A — сервер `C:\projects\RSP` (клон `gurinovi-ch/RSP`, git → push `main`): апгрейд `Tablet.pairingPin` (SHA-256, CSPRNG, TTL 15 мин, единый 400, throttler) + поле `pointName`, без новых таблиц. Фаза B — клиент в worktree `kiosk-mode`: `CabinetApi @POST("tablets/pair")` → `EncryptedSharedPreferences` → секция «Привязка к кабинету».

**Tech Stack:** NestJS 10 + Prisma (jest 30 настроен, baseline 23/23; доустановка: `@nestjs/throttler`, `@nestjs/testing`, `supertest`); Android — Retrofit 2.9, Hilt, androidx.security:security-crypto 1.1.0-alpha06, Compose, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-02-tablet-binding-design.md` (**ревизия A от 02.10.2026** — апгрейд существующего контракта; прежний вариант с `POST /api/pair` и таблицей `PairingCode` отменён). План аргументирует от спеки — читать оба.

## Global Constraints

- **Сервер:** `C:\projects\RSP` — рабочая копия готова и проверена (clone чистый, `.env` перенесён, baseline `npm.cmd test` = 23/23, `npx.cmd tsc --noEmit` чист, `npm.cmd run build` ок). Ветка `main`; **каждая задача = коммит + `git push origin main`**.
- **PowerShell Execution Policy блокирует `npm.ps1`/`npx.ps1`** — везде использовать **`npm.cmd`** / **`npx.cmd`** (иначе `PSSecurityException`).
- Ворота серверных задач: `npm.cmd test` + `npx.cmd tsc --noEmit -p tsconfig.json` + `npm.cmd run build`.
- **Клиент:** worktree `C:\projects\feedback-app\.worktrees\kiosk-mode`; коммит → push `kiosk-mode` (одна задача — один коммит). Сборка: `$env:JAVA_HOME="C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug testDebugUnitTest`.
- Новые UI-строки — только AppStrings (ru+en). Room/DataStore не трогать. Логи — `Log.i`. minSdk 24. Визуал — пользователь. Деструктивное/деплой — только по явному «да».
- **Безопасность (спека §5):** единый `400 {"message":"Invalid or expired pairing code"}`; TTL 15 мин; в БД только SHA-256 hex нормализованного кода; генерация `crypto.randomInt` из алфавита `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (32 символа — **совпадает с клиентской проверкой**); `POST /api/tablets/pair` — 10 req/min/IP (429); отклонения логируются `warn` с IP.

## Review Focus

1. **Ввод кода с мусором** (пробелы/дефисы/регистр/кириллица) → нормализация до отправки; тест нормализации — Task 5.
2. **Двойной тап «Привязать»** → guard повторного входа; тест с зависшим первым запросом — Task 5.
3. **Раскрытие причины сервером** (неактивный ключ = отдельное сообщение, как сейчас) → единый 400; unit-тест всех неудач в Task 2 + HTTP e2e «тело байт в байт» в Task 3.
4. **401/403 vs сетевые ошибки** → отозванный ключ чистит локальное состояние, timeout — нет; тесты — Task 5.
5. **rotate-key до погашения** → `pair` выдаёт текущий активный ключ, а старый PIN уже сгорел при rotate; тесты — Task 2.

---

## Фаза A — Сервер (`C:\projects\RSP`)

### Task 1: pointName — схема, миграция, endpoints (TDD)

**Files:**
- Test: `C:\projects\RSP\src\tablets\tablets.service.spec.ts` (новый)
- Modify: `C:\projects\RSP\prisma\schema.prisma`
- Modify: `C:\projects\RSP\src\tablets\tablets.service.ts` (create/update)
- Modify: `C:\projects\RSP\src\tablets\tablets.controller.ts` (типы body)

**Interfaces:**
- Consumes: `TabletsService.create(organizationId, data)`, `update(tabletId, organizationId, data)` (существующие).
- Produces: `create(orgId, { name: string; pointName?: string })`, `update(id, orgId, { name?; isActive?; pointName? })`; колонка `Tablet.pointName String?` — читают Task 2 (ответ `pair`) и клиент.

- [ ] **Step 1: Падающий тест**

Create `src\tablets\tablets.service.spec.ts`:

```ts
import { TabletsService } from './tablets.service';

describe('TabletsService pointName', () => {
  let prisma: any;
  let service: TabletsService;

  beforeEach(() => {
    prisma = {
      apiKey: { create: jest.fn().mockResolvedValue({ id: 'k1' }) },
      tablet: {
        create: jest.fn().mockResolvedValue({}),
        findFirst: jest.fn(),
        update: jest.fn().mockResolvedValue({}),
        delete: jest.fn(),
      },
    };
    service = new TabletsService(prisma as any);
  });

  it('create: pointName уходит в БД, без него — null', async () => {
    await service.create('o1', { name: 'Планшет 1', pointName: 'Точка №2' });
    expect(prisma.tablet.create.mock.calls[0][0].data.pointName).toBe('Точка №2');

    await service.create('o1', { name: 'Планшет 2' });
    expect(prisma.tablet.create.mock.calls[1][0].data.pointName).toBeNull();
  });

  it('update: pointName доходит до prisma, чужие поля не появляются', async () => {
    prisma.tablet.findFirst.mockResolvedValue({ id: 't1' });
    await service.update('t1', 'o1', { pointName: 'Новое имя' });
    expect(prisma.tablet.update.mock.calls[0][0].data).toEqual({ pointName: 'Новое имя' });

    await service.update('t1', 'o1', { name: 'X' });
    expect(prisma.tablet.update.mock.calls[1][0].data).toEqual({ name: 'X' });
  });
});
```

- [ ] **Step 2: Запустить — упасть**

Run: `npm.cmd test -- tablets.service`
Expected: FAIL (1st test: `data.pointName` === undefined).

- [ ] **Step 3: Схема + миграция**

`prisma\schema.prisma`, в `model Tablet` после `name String`:

```prisma
  pointName      String?
```

Run (в `C:\projects\RSP`):
```
npx.cmd prisma generate
npx.cmd prisma migrate dev --name tablet_point_name
```
Prerequisite: локальная БД поднята — если команда сообщает об отсутствии соединения, выполнить `docker.cmd compose up -d postgres` (или `docker compose up -d postgres`) и повторить. Fallback при ошибке дрейфа: `npx.cmd prisma db push`.
Expected: миграция создана в `prisma/migrations/`, `Generated Prisma Client`.

- [ ] **Step 4: Реализация**

`src\tablets\tablets.service.ts`:

- сигнатура `create(organizationId: string, data: { name: string; pointName?: string })`, в `data` объект `tablets.create` добавить строку:
```ts
        pointName: data.pointName ?? null,
```
- сигнатура `update(tabletId: string, organizationId: string, data: { name?: string; isActive?: boolean; pointName?: string })` (тело пробрасывается в `prisma.tablet.update` как есть — Prisma игнорирует `undefined`).

`src\tablets\tablets.controller.ts`:
- create: `@Body() body: { name: string; pointName?: string }`
- update: `@Body() body: { name?: string; isActive?: boolean; pointName?: string }`

- [ ] **Step 5: Запустить — пройти**

Run: `npm.cmd test -- tablets.service`
Expected: PASS (2 tests).

- [ ] **Step 6: Гейт + коммит + push**

```
npm.cmd test
npx.cmd tsc --noEmit -p tsconfig.json
npm.cmd run build
git add -A
git commit -m "feat(tablets): pointName field for install location"
git push origin main
```
Expected: 25/25 тестов (23 + 2), tsc/build чистые, push принят.

---

### Task 2: Апгрейд pairing — SHA-256, CSPRNG, TTL 15 мин, единый 400 (TDD)

**Files:**
- Test: `C:\projects\RSP\src\tablets\pairing.spec.ts` (новый)
- Modify: `C:\projects\RSP\src\tablets\tablets.service.ts`

**Interfaces:**
- Consumes: колонки `Tablet.pairingPin`, `pairingPinExpiresAt` (существующие), `pointName` (Task 1).
- Produces: `pair(input: unknown): Promise<{ tabletId; tabletName; apiKey; organizationId; organizationName; pointName }>`; `create`/`regeneratePin` возвращают `pairingPin` **открытым текстом** (в БД — хэш) — Task 3 тестирует `pair` через HTTP.

- [ ] **Step 1: Падающий тест**

Create `src\tablets\pairing.spec.ts`:

```ts
import { TabletsService } from './tablets.service';
import { BadRequestException } from '@nestjs/common';
import { createHash } from 'crypto';

const sha = (s: string) => createHash('sha256').update(s).digest('hex');

describe('TabletsService pairing upgrade', () => {
  let prisma: any;
  let service: TabletsService;

  beforeEach(() => {
    prisma = {
      apiKey: { create: jest.fn().mockResolvedValue({ id: 'k1' }), update: jest.fn() },
      tablet: {
        create: jest.fn().mockResolvedValue({}),
        findFirst: jest.fn(),
        update: jest.fn().mockResolvedValue({}),
        delete: jest.fn(),
      },
    };
    service = new TabletsService(prisma as any);
  });

  it('create: код 8 символов алфавита, в БД — SHA-256, в ответе — открытый код, TTL 15 мин', async () => {
    prisma.tablet.create.mockImplementation(async (q: any) => q.data);
    const res = await service.create('o1', { name: 'Планшет' });

    const code: string = res.pairingPin;
    expect(code).toMatch(/^[2-9ABCDEFGHJKMNPQRSTUVWXYZ]{8}$/);

    const data = prisma.tablet.create.mock.calls[0][0].data;
    expect(data.pairingPin).toBe(sha(code));
    const delta = data.pairingPinExpiresAt.getTime() - Date.now();
    expect(delta).toBeGreaterThan(14 * 60 * 1000);
    expect(delta).toBeLessThanOrEqual(15 * 60 * 1000 + 2000);
  });

  it('regenerate-pin: новый код, хэш в БД, ответ содержит открытый код', async () => {
    prisma.tablet.findFirst.mockResolvedValue({ id: 't1', organizationId: 'o1' });
    prisma.tablet.update.mockImplementation(async (q: any) => q.data);
    const res = await service.regeneratePin('t1', 'o1');

    expect(res.pairingPin).toMatch(/^[2-9ABCDEFGHJKMNPQRSTUVWXYZ]{8}$/);
    const data = prisma.tablet.update.mock.calls[0][0].data;
    expect(data.pairingPin).toBe(sha(res.pairingPin));
    expect(data.pairingPinExpiresAt.getTime()).toBeLessThanOrEqual(Date.now() + 15 * 60 * 1000 + 2000);
  });

  it('pair: нормализация входа, поиск по хэшу, успех с org/точкой, PIN погашается', async () => {
    prisma.tablet.findFirst.mockResolvedValue({
      id: 't1',
      name: 'Планшет у входа',
      organizationId: 'o1',
      pointName: 'Точка №1',
      organization: { name: 'ООО Ромашка' },
      apiKey: { key: 'rpro_x', isActive: true },
    });
    prisma.tablet.update.mockResolvedValue({});

    const res = await service.pair('abcd-efgh');

    const where = prisma.tablet.findFirst.mock.calls[0][0].where;
    expect(where.pairingPin).toBe(sha('ABCDEFGH'));
    expect(where.pairingPinExpiresAt.gt.getTime()).toBeLessThanOrEqual(Date.now() + 1000);

    expect(res).toEqual({
      tabletId: 't1',
      tabletName: 'Планшет у входа',
      apiKey: 'rpro_x',
      organizationId: 'o1',
      organizationName: 'ООО Ромашка',
      pointName: 'Точка №1',
    });
    expect(prisma.tablet.update.mock.calls[0][0].data).toEqual({
      pairingPin: null,
      pairingPinExpiresAt: null,
    });
  });

  const msgOf = async (fn: () => Promise<unknown>) =>
    (await fn().catch((e) => e as Error)).message;

  it('pair: неверный / просроченный(поиск вернул null) / неактивный ключ / не-строка → единое сообщение', async () => {
    prisma.tablet.findFirst.mockResolvedValue(null);
    const notFound = await msgOf(() => service.pair('ZZZZZZZZ'));

    prisma.tablet.findFirst.mockResolvedValue({
      id: 't1',
      organizationId: 'o1',
      organization: { name: 'Org' },
      apiKey: { key: 'k', isActive: false },
    });
    const inactive = await msgOf(() => service.pair('ZZZZZZZZ'));

    const notString = await msgOf(() => service.pair(123 as unknown as string));

    expect(new Set([notFound, inactive, notString]).size).toBe(1);
    expect(notFound).toBe('Invalid or expired pairing code');
    expect(inactive).toBeInstanceOf(BadRequestException);
  });

  it('rotateApiKey: попутно гасит PIN (старый код не действует после ротации)', async () => {
    prisma.tablet.findFirst.mockResolvedValue({
      id: 't1',
      apiKeyId: 'k1',
      apiKey: { id: 'k1' },
      name: 'Н',
    });
    await service.rotateApiKey('t1', 'o1');
    const data = prisma.tablet.update.mock.calls[0][0].data;
    expect(data.pairingPin).toBeNull();
    expect(data.pairingPinExpiresAt).toBeNull();
  });
});
```

- [ ] **Step 2: Запустить — упасть**

Run: `npm.cmd test -- pairing.spec`
Expected: FAIL (код — 6 цифр/открытый текст, поиск plaintext, ответ без org и т.д.).

- [ ] **Step 3: Реализация апгрейда**

`src\tablets\tablets.service.ts` — импорты и константы:

```ts
import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { v4 as uuidv4 } from 'uuid';
import { createHash, randomInt } from 'crypto';

/** Алфавит без 0/O/1/I/L — ровно 32 символа (совпадает с клиентской проверкой). */
export const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const CODE_LEN = 8;
const TTL_MS = 15 * 60 * 1000;
export const INVALID_CODE_MSG = 'Invalid or expired pairing code';
```

В классе:

```ts
  private readonly logger = new Logger(TabletsService.name);
```

`create` — генерация и хранение (заменить строки `const pin = this.generatePin();` и `pairingPin: pin`):

```ts
    const code = this.generateCode();
```
```ts
        pairingPin: this.codeHash(code),
        pairingPinExpiresAt: new Date(Date.now() + TTL_MS),
```
и завернуть результат, чтобы ответ содержал открытый код:

```ts
    const tablet = await this.prisma.tablet.create({ ... }); // как выше, pointName из Task 1
    return { ...tablet, pairingPin: code };
```

`pair` — целиком заменить:

```ts
  /**
   * Pair a tablet using an 8-char one-time code (SHA-256 at rest, TTL 15 min).
   * Called by the Android app. Returns the API key + organization/point meta.
   */
  async pair(input: unknown) {
    const invalid = () => new BadRequestException(INVALID_CODE_MSG);

    if (typeof input !== 'string') throw invalid();
    const normalized = input.replace(/[^0-9A-Za-z]/g, '').toUpperCase();
    if (normalized.length !== CODE_LEN) throw invalid();

    const tablet = await this.prisma.tablet.findFirst({
      where: {
        pairingPin: this.codeHash(normalized),
        pairingPinExpiresAt: { gt: new Date() },
      },
      include: {
        apiKey: { select: { key: true, isActive: true } },
        organization: { select: { name: true } },
      },
    });

    if (!tablet) throw invalid();

    if (!tablet.apiKey || !tablet.apiKey.isActive) {
      this.logger.warn(`pairing rejected: api key inactive tablet=${tablet.id}`);
      throw invalid();
    }

    // Одноразовость: гасим PIN после успешной выдачи ключа
    await this.prisma.tablet.update({
      where: { id: tablet.id },
      data: { pairingPin: null, pairingPinExpiresAt: null },
    });

    return {
      tabletId: tablet.id,
      tabletName: tablet.name,
      apiKey: tablet.apiKey.key,
      organizationId: tablet.organizationId,
      organizationName: tablet.organization.name,
      pointName: tablet.pointName,
    };
  }
```

`regeneratePin` — заменить генерацию и вернуть открытый код:

```ts
    const code = this.generateCode();
    const updated = await this.prisma.tablet.update({
      where: { id: tabletId },
      data: {
        pairingPin: this.codeHash(code),
        pairingPinExpiresAt: new Date(Date.now() + TTL_MS),
      },
      select: {
        id: true,
        name: true,
        pairingPin: true,
        pairingPinExpiresAt: true,
      },
    });
    return { ...updated, pairingPin: code };
```

Приватные методы (заменяют `generatePin` — удалить старый `Math.random`-метод):

```ts
  private generateCode(): string {
    let code = '';
    for (let i = 0; i < CODE_LEN; i++) code += ALPHABET[randomInt(ALPHABET.length)];
    return code;
  }

  private codeHash(code: string): string {
    return createHash('sha256').update(code).digest('hex');
  }
```

- [ ] **Step 4: Запустить — пройти**

Run: `npm.cmd test -- pairing.spec`
Expected: PASS (5 tests).

- [ ] **Step 5: Гейт + коммит + push**

```
npm.cmd test
npx.cmd tsc --noEmit -p tsconfig.json
npm.cmd run build
git add -A
git commit -m "feat(security): pairing code hashed at rest, CSPRNG 8-char, TTL 15m, uniform 400"
git push origin main
```
Expected: 30/30 тестов, tsc/build чистые.

---

### Task 3: Rate-limit 10/мин + IP-лог + HTTP e2e (TDD)

**Files:**
- Modify: `C:\projects\RSP\package.json` (зависимости)
- Modify: `C:\projects\RSP\src\app.module.ts`
- Modify: `C:\projects\RSP\src\tablets\tablets.controller.ts`
- Test: `C:\projects\RSP\src\tablets\pairing-http.spec.ts` (новый)
- Modify: `C:\projects\RSP\README.md`

**Interfaces:**
- Consumes: `TabletsService.pair` (Task 2), `INVALID_CODE_MSG` (Task 2).
- Produces: `POST /api/tablets/pair` — 10 req/min/IP (429), единый тело 400, warn-лог с IP; дефолт остальных маршрутов — 100/мин.

- [ ] **Step 1: Зависимости**

```
npm.cmd install @nestjs/throttler
npm.cmd install -D @nestjs/testing supertest @types/supertest
```
Expected: добавлены в package.json (package-lock обновится).

- [ ] **Step 2: Падающий HTTP-тест**

Create `src\tablets\pairing-http.spec.ts`:

```ts
import { BadRequestException, INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import * as request from 'supertest';
import { TabletsModule } from './tablets.module';
import { TabletsService } from './tablets.service';
import { PrismaService } from '../prisma/prisma.service';
import { ConfigModule } from '@nestjs/config';

describe('POST /api/tablets/pair (HTTP)', () => {
  let app: INestApplication;
  const pair = jest.fn();

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [ConfigModule.forRoot({ isGlobal: true }), TabletsModule],
    })
      .overrideProvider(TabletsService).useValue({ pair })
      .overrideProvider(PrismaService).useValue({})
      .compile();

    app = moduleRef.createNestApplication();
    app.setGlobalPrefix('api');
    await app.init();
  });

  afterAll(() => app.close());
  beforeEach(() => pair.mockReset());

  it('400: тело ошибки — ровно {"message":"Invalid or expired pairing code"}', async () => {
    pair.mockRejectedValue(new BadRequestException('Invalid or expired pairing code'));
    const res = await request(app.getHttpServer())
      .post('/api/tablets/pair')
      .send({ pin: 'ZZZZZZZZ' });
    expect(res.status).toBe(400);
    expect(res.body).toEqual({ message: 'Invalid or expired pairing code' });
  });

  it('успех: JSON-ответ сервиса проходит без искажений', async () => {
    pair.mockResolvedValue({
      tabletId: 't1', tabletName: 'Н', apiKey: 'rpro_x',
      organizationId: 'o1', organizationName: 'ООО', pointName: 'Точка',
    });
    const res = await request(app.getHttpServer())
      .post('/api/tablets/pair')
      .send({ pin: 'ABCD2345' });
    expect(res.status).toBe(200);
    expect(res.body).toEqual({
      tabletId: 't1', tabletName: 'Н', apiKey: 'rpro_x',
      organizationId: 'o1', organizationName: 'ООО', pointName: 'Точка',
    });
    expect(pair).toHaveBeenCalledWith('ABCD2345');
  });

  it('11-й запрос за минуту → 429', async () => {
    pair.mockResolvedValue({ tabletId: 't', tabletName: 'n', apiKey: 'k', organizationId: 'o', organizationName: 'O', pointName: null });
    let last = 0;
    for (let i = 0; i < 11; i++) {
      last = (await request(app.getHttpServer())
        .post('/api/tablets/pair')
        .send({ pin: 'ABCD2345' })).status;
    }
    expect(last).toBe(429);
  });
});
```

- [ ] **Step 3: Запустить — упасть на 429**

Run: `npm.cmd test -- pairing-http`
Expected: FAIL — 3-й тест (11-й запрос вернёт 200, throttler не подключён).

- [ ] **Step 4: Подключить throttler**

`src\app.module.ts`:

```ts
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { APP_GUARD } from '@nestjs/core';
```
```ts
@Module({
  imports: [
    ThrottlerModule.forRoot({ throttlers: [{ ttl: 60_000, limit: 100 }] }),
    // ...остальные imports без изменений
  ],
  providers: [{ provide: APP_GUARD, useClass: ThrottlerGuard }],
})
```

`src\tablets\tablets.controller.ts` — импорты:

```ts
import { Logger } from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
```

в класс:

```ts
  private readonly logger = new Logger(TabletsController.name);
```

маршрут `pair` — заменить на:

```ts
  @Post('pair')
  @Throttle({ default: { limit: 10, ttl: 60000 } })
  @ApiOperation({ summary: 'Pair tablet using 8-char one-time code (called by Android app)' })
  async pair(@Req() req: AuthenticatedRequest, @Body() body: { pin?: unknown }) {
    try {
      return await this.tabletsService.pair(body?.pin);
    } catch (e) {
      // Диагностика попыток (спека §5): IP + причина; ответ клиенту не меняется
      this.logger.warn(`pairing rejected ip=${req.ip} error=${(e as Error).message}`);
      throw e;
    }
  }
```

(сигнатура сервиса теперь `pair(input: unknown)` — тип body тоже `pin?: unknown`.)

- [ ] **Step 5: Запустить — пройти**

Run: `npm.cmd test -- pairing-http`
Expected: PASS (3 tests).

- [ ] **Step 6: README**

`README.md`:
- строка таблицы: `| POST | \`/api/tablets/pair\` | - | **Pair tablet using 8-char one-time code (SHA-256, TTL 15 min, rate-limit 10/min)** |` (заменить 6-digit-строку);
- секция `## Tablet Pairing Flow (PIN-code)` → заменить «6-digit PIN» на «8-char code (base32: 2-9 A-H J K M N P-Z)», шаг 4 пример: `App → POST /api/tablets/pair { pin: "K7M2QX9F" }`, добавить примечание: код одноразовый, действует 15 минут.

- [ ] **Step 7: Гейт + коммит + push**

```
npm.cmd test
npx.cmd tsc --noEmit -p tsconfig.json
npm.cmd run build
git add -A
git commit -m "feat(security): rate-limit pairing endpoint, IP audit log, http e2e"
git push origin main
```
Expected: 33/33 тестов, tsc/build чистые.

---

### Task 4: Совместимость dashboard + финальные гейты сервера

**Files:**
- Modify (если потребуется): `C:\projects\RSP\dashboard\src\pages\SettingsPage.tsx`
- Read-only проверка: `dashboard\src\api\client.ts`

**Interfaces:**
- Consumes: новый формат кода (Task 2).

- [ ] **Step 1: Проверить отображение кода в dashboard**

Run:
```
Select-String -Path "C:\projects\RSP\dashboard\src\pages\SettingsPage.tsx" -Pattern "newTabletPin|pairingPin|maxLength|inputMode|parseInt|Number\("
```
Ожидание: `pairingPin` хранится в строковом состоянии (`setNewTabletPin((t as any).pairingPin || '')`) и отображается как текст — менять НЕ нужно. Если найдена числовая валидация/`maxLength=6`/`parseInt` — убрать ограничение (код теперь 8 символов, алфавитный), оставив строковое отображение.

- [ ] **Step 2: Сборка dashboard**

Run: `npm.cmd run build` (в каталоге `C:\projects\RSP\dashboard`)
Expected: `tsc && vite build` без ошибок.

- [ ] **Step 3: Полные гейты сервера**

```
npm.cmd test
npx.cmd tsc --noEmit -p tsconfig.json
npm.cmd run build
```
Expected: все зелёные (33/33).

- [ ] **Step 4: Коммит (если были правки) + push**

```
git add -A
git commit -m "fix(dashboard): render 8-char pairing code as text"
git push origin main
```
Если правок не было — коммит не делать, зафиксировать вывод в отчёте.

---

## Фаза B — Android-приложение

База: `C:\projects\feedback-app\.worktrees\kiosk-mode` (ветка `kiosk-mode`).

### Task 5: Ядро привязки клиента (TDD)

**Files:**
- Create: `app\src\main\java\com\respondent\pro\cabinet\KabinetConfig.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\CabinetApi.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\BindingStorage.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\CabinetBinder.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\CabinetState.kt`
- Modify: `app\build.gradle.kts` (зависимость security-crypto)
- Test: `app\src\test\java\com\respondent\pro\cabinet\CabinetBinderTest.kt`

**Interfaces:**
- Consumes (контракт Task 2/3): `POST {BASE_URL}tablets/pair` body `{pin}` → 200 `{apiKey, tabletId, tabletName, organizationId, organizationName, pointName}` | 400 `{"message":"Invalid or expired pairing code"}` | 429.
- Produces (используют Task 6–7): `normalizePairingCode(raw: String): String?`; `class CabinetBinder(storage, api)` с `suspend fun pair(rawCode): PairOutcome`, `currentBinding()`, `unbindLocal()`, `isRevoked(httpCode): Boolean`; `enum class PairOutcome { PAIRED, BUSY, INVALID_CODE, NETWORK_ERROR, REVOKED }`; `sealed class CabinetState` + `enum class FailKind`; `interface CabinetApi @POST("tablets/pair") suspend fun pair(@Body body: PairRequest): PairResponse`; `PairRequest(pin)`, `PairResponse(apiKey, tabletId, organizationId, organizationName, pointName)`; `BindingStorage` + `StoredBinding`.

- [ ] **Step 1: Зависимость в build.gradle.kts** (после `implementation("androidx.room:room-ktx:2.6.1")`):

```kotlin
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

- [ ] **Step 2: Написать падающий тест**

Create `app\src\test\java\com\respondent\pro\cabinet\CabinetBinderTest.kt`:

```kotlin
package com.respondent.pro.cabinet

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class CabinetBinderTest {

    private class FakeStorage : BindingStorage {
        var data: StoredBinding? = null
        var clearCalls = 0
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null; clearCalls++ }
    }

    private class FakeApi(private val respond: suspend (PairRequest) -> PairResponse) : CabinetApi {
        var calls = 0
        var lastBody: PairRequest? = null
        override suspend fun pair(body: PairRequest): PairResponse {
            calls++
            lastBody = body
            return respond(body)
        }
    }

    private val ok = PairResponse("rpro_k", "t1", "o1", "ООО Ромашка", "Точка")

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test fun `normalize strips spaces, dashes and case`() {
        assertEquals("ABCDEFGH", normalizePairingCode(" abcd-efgh "))
        assertEquals("23456789", normalizePairingCode("2345-6789"))
    }

    @Test fun `normalize rejects wrong length, forbidden letters and cyrillic`() {
        assertNull(normalizePairingCode("abc"))
        assertNull(normalizePairingCode("abcd1234"))   // цифра 1 запрещена
        assertNull(normalizePairingCode("abcd12345"))  // 9 символов
        assertNull(normalizePairingCode("абвгд"))
    }

    @Test fun `pair success sends normalized pin and stores binding`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { ok }
        val outcome = CabinetBinder(storage, api).pair("abcd-efgh")
        assertEquals(PairOutcome.PAIRED, outcome)
        assertEquals("ABCDEFGH", api.lastBody?.pin)
        assertEquals("rpro_k", storage.data?.apiKey)
        assertEquals("Точка", storage.data?.pointName)
        assertEquals(1, api.calls)
    }

    @Test fun `invalid format fails locally without network call`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { ok }
        assertEquals(PairOutcome.INVALID_CODE, CabinetBinder(storage, api).pair("1"))
        assertEquals(0, api.calls)
        assertNull(storage.data)
    }

    @Test fun `http 400 maps to INVALID_CODE nothing stored`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { throw httpError(400) }
        assertEquals(PairOutcome.INVALID_CODE, CabinetBinder(storage, api).pair("abcd-efgh"))
        assertNull(storage.data)
    }

    @Test fun `http 401 maps to REVOKED and clears storage`() = runBlocking {
        val storage = FakeStorage()
        storage.data = StoredBinding("rpro_old", "t1", "o1", "Org", null)
        val api = FakeApi { throw httpError(401) }
        assertEquals(PairOutcome.REVOKED, CabinetBinder(storage, api).pair("abcd-efgh"))
        assertEquals(1, storage.clearCalls)
    }

    @Test fun `io exception maps to NETWORK_ERROR storage untouched`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { throw IOException("timeout") }
        assertEquals(PairOutcome.NETWORK_ERROR, CabinetBinder(storage, api).pair("abcd-efgh"))
        assertEquals(0, storage.clearCalls)
    }

    @Test fun `double submit while first in flight returns BUSY`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val api = FakeApi { gate.await(); ok }
        val binder = CabinetBinder(FakeStorage(), api)
        val first = async { binder.pair("abcd-efgh") }
        yield()
        assertEquals(PairOutcome.BUSY, binder.pair("abcd-efgh"))
        gate.complete(Unit)
        assertEquals(PairOutcome.PAIRED, first.await())
        assertEquals(1, api.calls)
    }

    @Test fun `unbindLocal clears storage`() {
        val storage = FakeStorage()
        storage.data = StoredBinding("k", "t", "o", "Org", null)
        CabinetBinder(storage, FakeApi { ok }).unbindLocal()
        assertNull(storage.data)
    }

    @Test fun `isRevoked clears only on 401 403`() {
        val storage = FakeStorage()
        storage.data = StoredBinding("k", "t", "o", "Org", null)
        val binder = CabinetBinder(storage, FakeApi { ok })
        assertTrue(binder.isRevoked(401))
        assertNull(storage.data)
        storage.data = StoredBinding("k", "t", "o", "Org", null)
        assertEquals(false, binder.isRevoked(500))
        assertNotNull(storage.data)
    }
}
```

**Примечание:** `PairResponse` — ровно те 5 полей, что возвращает сервер (лишние `tabletName` из JSON Gson игнорирует, поля в data class нет — безопасно).

- [ ] **Step 3: Запустить — упасть**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CabinetBinderTest*"`
Expected: FAIL компиляцией (`unresolved reference: normalizePairingCode` и т.п.).

- [ ] **Step 4: Реализация**

Create `KabinetConfig.kt`:

```kotlin
package com.respondent.pro.cabinet

/** Адрес API кабинета (сервер my.respondent.pro). */
object KabinetConfig {
    const val BASE_URL = "https://my.respondent.pro/api/"
}
```

Create `CabinetApi.kt`:

```kotlin
package com.respondent.pro.cabinet

import retrofit2.http.Body
import retrofit2.http.POST

interface CabinetApi {
    /** Обмен одноразового pairing-кода на API-ключ планшета. */
    @POST("tablets/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse
}

data class PairRequest(val pin: String)

data class PairResponse(
    val apiKey: String,
    val tabletId: String,
    val organizationId: String,
    val organizationName: String,
    val pointName: String?
)
```

Create `BindingStorage.kt`:

```kotlin
package com.respondent.pro.cabinet

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class StoredBinding(
    val apiKey: String,
    val tabletId: String,
    val organizationId: String,
    val organizationName: String,
    val pointName: String?
)

/** Хранение ключа привязки. Интерфейс — чтобы юнит-тесты подменяли реализацию. */
interface BindingStorage {
    fun read(): StoredBinding?
    fun write(binding: StoredBinding)
    fun clear()
}

/**
 * Реализация поверх EncryptedSharedPreferences (Android Keystore).
 * Room и DataStore не используются (правило проекта).
 */
class EncryptedBindingStorage(context: Context) : BindingStorage {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cabinet_binding",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun read(): StoredBinding? {
        val apiKey = prefs.getString(KEY_API, null) ?: return null
        return StoredBinding(
            apiKey = apiKey,
            tabletId = prefs.getString(KEY_TABLET, "") ?: "",
            organizationId = prefs.getString(KEY_ORG_ID, "") ?: "",
            organizationName = prefs.getString(KEY_ORG_NAME, "") ?: "",
            pointName = prefs.getString(KEY_POINT, null)
        )
    }

    override fun write(binding: StoredBinding) {
        prefs.edit()
            .putString(KEY_API, binding.apiKey)
            .putString(KEY_TABLET, binding.tabletId)
            .putString(KEY_ORG_ID, binding.organizationId)
            .putString(KEY_ORG_NAME, binding.organizationName)
            .putString(KEY_POINT, binding.pointName)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_TABLET = "tablet_id"
        const val KEY_ORG_ID = "org_id"
        const val KEY_ORG_NAME = "org_name"
        const val KEY_POINT = "point_name"
    }
}
```

Create `CabinetState.kt`:

```kotlin
package com.respondent.pro.cabinet

enum class FailKind { INVALID_CODE, NETWORK, REVOKED }

/** Состояние экрана «Привязка к кабинету» (UI + ViewModel). */
sealed class CabinetState {
    object Unbound : CabinetState()
    object Binding : CabinetState()
    data class Bound(val organizationName: String, val pointName: String?) : CabinetState()
    data class Failed(val kind: FailKind) : CabinetState()
}
```

Create `CabinetBinder.kt`:

```kotlin
package com.respondent.pro.cabinet

import retrofit2.HttpException
import java.io.IOException

enum class PairOutcome { PAIRED, BUSY, INVALID_CODE, NETWORK_ERROR, REVOKED }

/**
 * Нормализация ввода: убирает пробелы/дефисы, верхний регистр.
 * Алфавит обязан совпадать с серверным (TabletsService.ALPHABET):
 * 2-9 A-H J K M N P-Z — без 0/1/I/L/O.
 */
fun normalizePairingCode(raw: String): String? {
    val cleaned = raw.filter { it.isLetterOrDigit() }.uppercase()
    if (cleaned.length != 8) return null
    val allowed = cleaned.all {
        it in '2'..'9' || it in 'A'..'H' || it in 'J'..'K' || it in 'M'..'N' || it in 'P'..'Z'
    }
    return if (allowed) cleaned else null
}

/**
 * Логика привязки без Android-зависимостей (тестируется юнитами).
 * Хранение и HTTP — инъекцией.
 */
class CabinetBinder(
    private val storage: BindingStorage,
    private val api: CabinetApi
) {
    @Volatile
    private var busy = false

    fun currentBinding(): StoredBinding? = storage.read()

    /** Привязка. Повторный вызов во время выполнения → BUSY (защита от двойного тапа). */
    suspend fun pair(rawCode: String): PairOutcome {
        if (busy) return PairOutcome.BUSY
        busy = true
        return try {
            val code = normalizePairingCode(rawCode)
                ?: return PairOutcome.INVALID_CODE
            val r = api.pair(PairRequest(code))
            storage.write(
                StoredBinding(r.apiKey, r.tabletId, r.organizationId, r.organizationName, r.pointName)
            )
            PairOutcome.PAIRED
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> { storage.clear(); PairOutcome.REVOKED }
                400, 404 -> PairOutcome.INVALID_CODE
                else -> PairOutcome.NETWORK_ERROR
            }
        } catch (e: IOException) {
            PairOutcome.NETWORK_ERROR
        } finally {
            busy = false
        }
    }

    /** Локальная отвязка (серверный revoke — в кабинете). */
    fun unbindLocal() {
        storage.clear()
    }

    /**
     * Разбор ошибки авторизованного (X-API-Key) запроса.
     * 401/403 → ключ отозван: чистим локальное состояние, возвращаем true.
     * Сетевые и прочие ошибки состоянию не мешают.
     */
    fun isRevoked(httpCode: Int): Boolean {
        if (httpCode == 401 || httpCode == 403) {
            storage.clear()
            return true
        }
        return false
    }
}
```

- [ ] **Step 5: Запустить — пройти**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CabinetBinderTest*"`
Expected: PASS (10 tests).

- [ ] **Step 6: Коммит + push**

```powershell
git add app/src/main/java/com/respondent/pro/cabinet app/build.gradle.kts app/src/test/java/com/respondent/pro/cabinet
git commit -m "feat(cabinet): pairing-code normalization, encrypted binding storage, binder"
git push origin kiosk-mode
```

---

### Task 6: DI и состояние в SettingsViewModel

**Files:**
- Modify: `app\src\main\java\com\respondent\pro\di\AppModule.kt`
- Modify: `app\src\main\java\com\respondent\pro\viewmodel\SettingsViewModel.kt`

**Interfaces:**
- Consumes: всё из Task 5.
- Produces: Hilt-провайдеры `CabinetApi`, `BindingStorage`; `SettingsViewModel.cabinetState: StateFlow<CabinetState>`, `fun pairCabinet(rawCode: String)`, `fun unbindCabinet()` — использует Task 7.

- [ ] **Step 1: Провайдеры в AppModule**

`di\AppModule.kt` — импорты и два провайдера:

```kotlin
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.CabinetApi
import com.respondent.pro.cabinet.EncryptedBindingStorage
import com.respondent.pro.cabinet.KabinetConfig
```
```kotlin
    /** API кабинета — отдельный Retrofit со своим baseUrl (не путать с Telegram). */
    @Provides
    @Singleton
    fun provideCabinetApi(): CabinetApi {
        return Retrofit.Builder()
            .baseUrl(KabinetConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CabinetApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBindingStorage(@ApplicationContext context: Context): BindingStorage {
        return EncryptedBindingStorage(context)
    }
```

**Примечание:** отдельный Retrofit НЕ регистрируем как `@Provides Retrofit` — это конфликт дублирования биндинга с `provideRetrofit` (Telegram); `CabinetApi` создаётся внутри своего провайдера.

- [ ] **Step 2: Состояние в SettingsViewModel**

`viewmodel\SettingsViewModel.kt` — импорты:

```kotlin
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.CabinetApi
import com.respondent.pro.cabinet.CabinetBinder
import com.respondent.pro.cabinet.CabinetState
import com.respondent.pro.cabinet.FailKind
import com.respondent.pro.cabinet.PairOutcome
import com.respondent.pro.cabinet.StoredBinding
```

Конструктор — добавить параметры:

```kotlin
    private val kioskManager: KioskManager,
    private val cabinetApi: CabinetApi,
    private val bindingStorage: BindingStorage,
    @ApplicationContext private val appContext: Context
```

После поля `_isRunningQrDiagnostics` добавить:

```kotlin
    private val cabinetBinder = CabinetBinder(bindingStorage, cabinetApi)

    /** Состояние привязки к кабинету (инициализируется из сохранённого ключа). */
    private val _cabinetState = MutableStateFlow<CabinetState>(
        cabinetBinder.currentBinding()?.toBound() ?: CabinetState.Unbound
    )
    val cabinetState: StateFlow<CabinetState> = _cabinetState

    private fun StoredBinding.toBound() =
        CabinetState.Bound(organizationName, pointName)

    /** Обмен pairing-кода на ключ. Повторный вызов защищён (state Binding + BUSY). */
    fun pairCabinet(rawCode: String) {
        if (_cabinetState.value is CabinetState.Binding) return
        viewModelScope.launch {
            _cabinetState.value = CabinetState.Binding
            val outcome = cabinetBinder.pair(rawCode)
            _cabinetState.value = when (outcome) {
                PairOutcome.PAIRED ->
                    cabinetBinder.currentBinding()?.toBound() ?: CabinetState.Unbound
                PairOutcome.BUSY -> CabinetState.Binding
                PairOutcome.INVALID_CODE -> CabinetState.Failed(FailKind.INVALID_CODE)
                PairOutcome.NETWORK_ERROR -> CabinetState.Failed(FailKind.NETWORK)
                PairOutcome.REVOKED -> CabinetState.Failed(FailKind.REVOKED)
            }
            Log.i("SettingsViewModel", "cabinet pair: $outcome")
        }
    }

    /** Локальная отвязка (серверный revoke — в кабинете). */
    fun unbindCabinet() {
        cabinetBinder.unbindLocal()
        _cabinetState.value = CabinetState.Unbound
        Log.i("SettingsViewModel", "cabinet unbound locally")
    }
```

- [ ] **Step 3: Гейт (компиляция + все тесты)**

Run: `$env:JAVA_HOME="C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, все тесты зелёные.

*Почему без юнит-теста ViewModel: логика живёт в `CabinetBinder` (Task 5, покрыта 10 тестами); SettingsViewModel в проекте никогда не тестировался — консистентность кодовой базы.*

- [ ] **Step 4: Коммит + push**

```powershell
git add app/src/main/java/com/respondent/pro/di/AppModule.kt app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt
git commit -m "feat(cabinet): DI providers and binding state in SettingsViewModel"
git push origin kiosk-mode
```

---

### Task 7: UI «Привязка к кабинету» + строки (TDD строк)

**Files:**
- Modify: `app\src\main\java\com\respondent\pro\ui\i18n\AppStrings.kt` (12 строк ru+en)
- Create: `app\src\test\java\com\respondent\pro\ui\i18n\CabinetStringsTest.kt`
- Modify: `app\src\main\java\com\respondent\pro\ui\screens\SettingsScreen.kt` (CabinetCard)

**Interfaces:**
- Consumes: `cabinetState/pairCabinet/unbindCabinet` (Task 6), `CabinetState` (Task 5).

- [ ] **Step 1: Падающий тест строк**

Create `app\src\test\java\com\respondent\pro\ui\i18n\CabinetStringsTest.kt`:

```kotlin
package com.respondent.pro.ui.i18n

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinetStringsTest {

    private fun values(s: AppStrings) = listOf(
        s.kabinetCardTitle, s.kabinetStatusUnbound, s.kabinetStatusBound,
        s.kabinetStatusBoundPoint, s.kabinetCodeLabel, s.kabinetBindButton,
        s.kabinetBinding, s.kabinetUnbindButton, s.kabinetHint,
        s.kabinetErrorInvalid, s.kabinetErrorNetwork, s.kabinetRevoked
    )

    @Test
    fun `cabinet strings non-blank in ru and en`() {
        assertTrue(values(ruStrings).all { it.isNotBlank() })
        assertTrue(values(enStrings).all { it.isNotBlank() })
    }

    @Test
    fun `ru differs from en for every key`() {
        values(ruStrings).zip(values(enStrings)).forEach { (ru, en) ->
            assertFalse("ru and en must differ: $ru", ru == en)
        }
    }

    @Test
    fun `bound formats contain placeholders`() {
        assertTrue(ruStrings.kabinetStatusBound.contains("%s"))
        assertTrue(ruStrings.kabinetStatusBoundPoint.contains("%1\$s"))
        assertTrue(ruStrings.kabinetStatusBoundPoint.contains("%2\$s"))
    }
}
```

- [ ] **Step 2: Запустить — упасть компиляцией**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CabinetStringsTest*"`
Expected: FAIL (`unresolved reference: kabinetCardTitle`).

- [ ] **Step 3: Строки в AppStrings**

`ui\i18n\AppStrings.kt` — в класс после `kioskQrDiagNote: String,`:

```kotlin
    // Привязка к кабинету
    val kabinetCardTitle: String,
    val kabinetStatusUnbound: String,
    val kabinetStatusBound: String,
    val kabinetStatusBoundPoint: String,
    val kabinetCodeLabel: String,
    val kabinetBindButton: String,
    val kabinetBinding: String,
    val kabinetUnbindButton: String,
    val kabinetHint: String,
    val kabinetErrorInvalid: String,
    val kabinetErrorNetwork: String,
    val kabinetRevoked: String,
```

В `ruStrings` (перед `btnDone`):

```kotlin
    kabinetCardTitle = "Привязка к кабинету",
    kabinetStatusUnbound = "Не привязан к Компании",
    kabinetStatusBound = "Привязан: %s",
    kabinetStatusBoundPoint = "Привязан: %1\$s · %2\$s",
    kabinetCodeLabel = "Код привязки из кабинета",
    kabinetBindButton = "Привязать",
    kabinetBinding = "Привязка…",
    kabinetUnbindButton = "Отвязать",
    kabinetHint = "Код: в кабинете → Планшеты → «Перевыпустить код». Действует 15 минут, одноразовый.",
    kabinetErrorInvalid = "Неверный или просроченный код",
    kabinetErrorNetwork = "Нет связи с сервером",
    kabinetRevoked = "Привязка отменена",
```

В `enStrings` (перед `btnDone`):

```kotlin
    kabinetCardTitle = "Cabinet binding",
    kabinetStatusUnbound = "Not bound to a company",
    kabinetStatusBound = "Bound: %s",
    kabinetStatusBoundPoint = "Bound: %1\$s · %2\$s",
    kabinetCodeLabel = "Pairing code from the cabinet",
    kabinetBindButton = "Bind",
    kabinetBinding = "Binding…",
    kabinetUnbindButton = "Unbind",
    kabinetHint = "Code: in the cabinet → Tablets → \"Regenerate code\". Valid 15 minutes, single-use.",
    kabinetErrorInvalid = "Invalid or expired code",
    kabinetErrorNetwork = "Server unreachable",
    kabinetRevoked = "Binding revoked",
```

*Примечание: `kabinetStatusBoundPoint` содержит литерал `$s` — в Kotlin-строке экранируется `\$`; в тесте плейсхолдеры проверяются как `%1$s`/`%2$s`.*

- [ ] **Step 4: Запустить — пройти**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CabinetStringsTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: UI — CabinetCard**

`ui\screens\SettingsScreen.kt`:

Импорт: `import com.respondent.pro.cabinet.CabinetState`, `import com.respondent.pro.cabinet.FailKind`.

В теле `SettingsScreen` (рядом с `var qrDiagExpanded`):

```kotlin
    var cabinetCode by remember { mutableStateOf("") }
```

Рядом с `val isRunningQrDiagnostics`:

```kotlin
    val cabinetState by viewModel.cabinetState.collectAsState()
```

В call-site, сразу после блока `KioskCard(...)`:

```kotlin
            // 14. Привязка к кабинету
            CabinetCard(
                state = cabinetState,
                code = cabinetCode,
                onCodeChange = { cabinetCode = it.uppercase().take(11) },
                onBind = { viewModel.pairCabinet(cabinetCode) },
                onUnbind = {
                    cabinetCode = ""
                    viewModel.unbindCabinet()
                }
            )
```

Новый composable (перед `private fun diagTitle`):

```kotlin
@Composable
private fun CabinetCard(
    state: CabinetState,
    code: String,
    onCodeChange: (String) -> Unit,
    onBind: () -> Unit,
    onUnbind: () -> Unit
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = strings.kabinetCardTitle,
                style = MaterialTheme.typography.titleMedium
            )

            val statusText = when (state) {
                CabinetState.Unbound -> strings.kabinetStatusUnbound
                CabinetState.Binding -> strings.kabinetBinding
                is CabinetState.Bound ->
                    if (state.pointName.isNullOrBlank()) {
                        String.format(strings.kabinetStatusBound, state.organizationName)
                    } else {
                        String.format(
                            strings.kabinetStatusBoundPoint,
                            state.organizationName,
                            state.pointName
                        )
                    }
                is CabinetState.Failed -> when (state.kind) {
                    FailKind.INVALID_CODE -> strings.kabinetErrorInvalid
                    FailKind.NETWORK -> strings.kabinetErrorNetwork
                    FailKind.REVOKED -> strings.kabinetRevoked
                }
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = when (state) {
                    is CabinetState.Bound -> MaterialTheme.colorScheme.primary
                    CabinetState.Binding -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 4.dp)
            )

            if (state is CabinetState.Bound) {
                Button(
                    onClick = onUnbind,
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(strings.kabinetUnbindButton)
                }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text(strings.kabinetCodeLabel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                    enabled = state != CabinetState.Binding
                )
                Button(
                    onClick = onBind,
                    enabled = state != CabinetState.Binding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = if (state == CabinetState.Binding) {
                            strings.kabinetBinding
                        } else {
                            strings.kabinetBindButton
                        }
                    )
                }
                Text(
                    text = strings.kabinetHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 6: Гейт + коммит + push**

```
$env:JAVA_HOME="C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug testDebugUnitTest
git add app/src/main/java/com/respondent/pro/ui app/src/test/java/com/respondent/pro/ui/i18n/CabinetStringsTest.kt
git commit -m "feat(cabinet): binding section in settings with status, code input and unbind"
git push origin kiosk-mode
```
Expected: BUILD SUCCESSFUL, все тесты зелёные.

---

### Task 8: Финальная верификация и ledger

**Files:**
- Modify: `.superpowers\sdd\2026-09-28-kiosk-mode\progress.md`

**Interfaces:**
- Consumes: Task 1–7.

- [ ] **Step 1: Полный прогон клиента**

Run: `$env:JAVA_HOME="C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL; 31 прежний + CabinetBinderTest 10 + CabinetStringsTest 3 = 44.

- [ ] **Step 2: Полный прогон сервера**

Run (в `C:\projects\RSP`):
```
npm.cmd test
npx.cmd tsc --noEmit -p tsconfig.json
npm.cmd run build
```
Expected: 33/33, tsc/build чистые; `git status` чистый, `git log origin/main` содержит коммиты Task 1–4.

- [ ] **Step 3: Устройственная проверка UI (без сервера)**

Планшет подключить по USB; `adb install -r` свежего APK; Настройки → секция «Привязка к кабинету»: статус «Не привязан», поле кода, кнопка. Снять uia-dump (тексты присутствуют). Проверка: ввод мусора («1») → «Неверный или просроченный код» локально, без сети. В конце — `accelerometer_rotation=1`.

- [ ] **Step 4: Ledger**

Добавить в `progress.md` запись цикла: спека ревизии A, план, коммиты клиента (Task 5–7) и сервера (Task 1–4, push `RSP/main`), результаты тестов (клиент 44 / сервер 33), статус E2E (ожидает деплоя сервера — отдельное согласие пользователя).

- [ ] **Step 5: Push клиента**

Run: `git push origin kiosk-mode` (если есть незапушенные коммиты).
Expected: push принят.

- [ ] **Step 6: E2E-цикл (ТОЛЬКО по явному «да» пользователя)**

1) Деплой RSP на VPS (`deploy.sh` — side-effect, спросить!); примечание: **выданные до деплоя PIN невалидны** (хэш вместо plaintext) — перевыпустить коды; 2) локально снять БД/миграции (`docker compose` + `migrate dev` уже накатил pointName — на VPS при деплое `migrate deploy`); 3) dashboard: создать планшет → получить код; 4) планшет: ввод кода → «Привязан: Орг · Точка»; 5) «Перевыпустить код» → старый код → «Неверный или просроченный код». Визуал — пользователь.
