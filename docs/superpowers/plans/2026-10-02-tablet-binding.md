# Tablet Binding (pairing-код → device API-key) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Привязать планшет с приложением RESPONDENT.PRO к Компании в кабинете my.respondent.pro через одноразовый pairing-код без хранения пароля человека на устройстве.

**Architecture:** Сервер (NestJS) выдаёт одноразовый 8-символьный код (TTL 15 мин, в БД только SHA-256) и обменивает его на постоянный device API-key (`X-API-Key`); планшет вводит код в Настройках и хранит ключ в EncryptedSharedPreferences. Две фазы: A — сервер (каталог без git), B — Android-приложение (ветка kiosk-mode, коммит на задачу).

**Tech Stack:** NestJS 10 + Prisma 5.8 + @nestjs/throttler + Jest/ts-jest/supertest; Android (Kotlin) — Retrofit 2.9, Hilt, androidx.security:security-crypto 1.1.0-alpha06, Compose, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-02-tablet-binding-design.md` (план аргументирует от спеки — читать оба).

## Global Constraints

- Сервер: `C:\Users\leonb\OneDrive\Документы\Default Project\server` — **не git-репозиторий, НЕ делайте `git init`** (каталог в OneDrive; решение о VCS принимает пользователь отдельно). Ворота каждой сервер-задачи: `npm test` и/или `npm run build`.
- Клиент: worktree `C:\projects\feedback-app\.worktrees\kiosk-mode`, все коммиты → push в `kiosk-mode` («одна задача — один коммит»).
- Сборка клиента: `$env:JAVA_HOME="C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug testDebugUnitTest` (норма — все зелёные).
- Новые UI-строки — только AppStrings (ru+en). Room/DataStore не трогать. Логи — `Log.i`. Мини-SDK 24.
- Визуальные проверки (скриншоты) — пользователь; агент верифицирует инструментально (тесты, dumpsys, logcat, uia-dump).
- Деструктивное (factory reset, dpm, деплой на VPS) — только по явному «да» пользователя.
- Безопасность (из спеки): единый ответ 400 `{"message":"invalid or expired code"}` на любую неудачу паринга; TTL 15 мин; в БД только SHA-256; throttler `POST /api/pair` = 10/мин на IP.

## Review Focus

Пять входов/условий, которые спека подразумевает, но не каждое покрыто тестами задач по умолчанию:

1. **Ввод кода с мусором** (пробелы, дефисы, строчные, кириллица) → нормализация до отправки; закреплено тестом нормализации в Task 7 (`CabinetBinderTest`).
2. **Двойной тап «Привязать»** (повторный POST во время первого) → guard повторного входа; тест с зависшим первым запросом в Task 7.
3. **Раскрытие причины неудачи сервером** (разные ошибки для «просрочен»/«уже использован») → единый 400; тесты в Task 2 (service) и Task 3 (HTTP e2e) — сообщение обязано совпадать байт в байт.
4. **401/403 vs сетевые ошибки**: отозванный ключ должен чистить локальное состояние, timeout — нет; тесты `isRevoked`/IOException в Task 7.
5. **rotate-key до погашения кода**: код обязан выдать НОВЫЙ активный ключ, не старый; тест в Task 2 (redeem читает `tablet.apiKey` актуальный, проверяет `isActive`).

---

## Фаза A — Сервер

### Task 1: Prisma — модель PairingCode и поле Tablet.pointName

**Files:**
- Modify: `C:\Users\leonb\OneDrive\Документы\Default Project\server\prisma\schema.prisma`

**Interfaces:**
- Produces: модель `PairingCode` (поля `id, tabletId@unique, codeHash, expiresAt, usedAt, createdAt`) и `Tablet.pointName: String?` — нужны Task 2–5.

- [ ] **Step 1: Дописать в model Tablet** (после `name String` добавить `pointName String?`, после `feedbacks Feedback[]` — связь):

```prisma
model Tablet {
  id             String   @id @default(uuid())
  organizationId String
  organization   Organization @relation(fields: [organizationId], references: [id], onDelete: Cascade)
  apiKeyId       String?  @unique
  apiKey         ApiKey?  @relation(fields: [apiKeyId], references: [id], onDelete: SetNull)
  name           String
  pointName      String?
  isActive       Boolean  @default(true)
  lastSeenAt     DateTime?
  createdAt      DateTime @default(now())

  feedbacks   Feedback[]
  pairingCode PairingCode?

  @@map("tablets")
}
```

- [ ] **Step 2: Добавить модель PairingCode** (в конец schema.prisma):

```prisma
// --- Pairing codes (одноразовый код привязки планшета) ---
model PairingCode {
  id        String   @id @default(uuid())
  tabletId  String   @unique
  tablet    Tablet   @relation(fields: [tabletId], references: [id], onDelete: Cascade)
  codeHash  String
  expiresAt DateTime
  usedAt    DateTime?
  createdAt DateTime @default(now())

  @@map("pairing_codes")
}
```

- [ ] **Step 3: Сгенерировать клиент и накатить схему**

Run (в каталоге сервера):
```
npx prisma generate
npx prisma migrate dev --name tablet_binding
```
Expected: `Generated Prisma Client`. Если `migrate dev` завершится ошибкой дрейфа/базовой линии (в репо нет `prisma/migrations` — БД исторически создавалась через `db push`) — выполнить `npx prisma db push` и не прерываться.

- [ ] **Step 4: Проверка компиляции**

Run: `npm run build`
Expected: `BUILD SUCCESSFUL` / без ошибок TypeScript.

---

### Task 2: Jest-инфраструктура + PairingService (TDD)

**Files:**
- Create: `server\src\pairing\pairing.service.ts`
- Test: `server\src\pairing\pairing.service.spec.ts`
- Modify: `server\package.json` (devDependencies + блок `jest`)

**Interfaces:**
- Consumes: `PrismaService` (`prisma/tablet`, `prisma/pairingCode`), модель из Task 1.
- Produces: `PairingService.generateForTablet(organizationId: string, tabletId: string): Promise<{ code: string; expiresAt: Date }>`, `PairingService.redeem(input: unknown): Promise<PairResult>`; `interface PairResult { apiKey: string; tabletId: string; organizationId: string; organizationName: string; pointName: string | null }` — используют Task 3–4.

- [ ] **Step 1: Установить тестовый инструментарий**

Run:
```
npm install -D jest ts-jest @types/jest @nestjs/testing supertest @types/supertest
```
Expected: пакеты добавлены в devDependencies.

- [ ] **Step 2: Добавить конфиг jest в package.json** (после блока `"prisma"`):

```json
"jest": {
  "moduleFileExtensions": ["js", "json", "ts"],
  "rootDir": "src",
  "testRegex": ".*\\.spec\\.ts$",
  "transform": { "^.+\\.(t|j)s$": "ts-jest" },
  "testEnvironment": "node"
}
```

- [ ] **Step 3: Написать падающий тест PairingService**

Create `src\pairing\pairing.service.spec.ts`:

```ts
import { PairingService } from './pairing.service';
import { BadRequestException, NotFoundException } from '@nestjs/common';

describe('PairingService', () => {
  const NOW = Date.now();
  let prisma: any;
  let service: PairingService;

  const futureRow = {
    id: 'pc1',
    tabletId: 't1',
    codeHash: 'irrelevant',
    expiresAt: new Date(NOW + 60_000),
    usedAt: null as Date | null,
  };

  const tabletActive = {
    id: 't1',
    organizationId: 'o1',
    pointName: 'Точка у входа',
    apiKey: { key: 'rpro_new', isActive: true },
    organization: { name: 'ООО Ромашка' },
  };

  beforeEach(() => {
    prisma = {
      tablet: { findFirst: jest.fn(), findUnique: jest.fn() },
      pairingCode: { upsert: jest.fn(), findFirst: jest.fn(), updateMany: jest.fn() },
    };
    service = new PairingService(prisma as any);
  });

  // redeem ищет строку ПО ХЭШУ: если findFirst вернул строку — хэш совпал.
  // Поэтому в тестах findFirst мокается напрямую, без реконструкции кода.
  const fail = async (fn: () => Promise<unknown>) =>
    (await fn().catch((e) => e)) as BadRequestException;

  it('generate: код формата XXXX-XXXX из алфавита, в БД уходит только SHA-256', async () => {
    prisma.tablet.findFirst.mockResolvedValue({ id: 't1', organizationId: 'o1' });
    const out = await service.generateForTablet('o1', 't1');

    expect(out.code).toMatch(/^[2-9ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[2-9ABCDEFGHJKMNPQRSTUVWXYZ]{4}$/);
    const arg = prisma.pairingCode.upsert.mock.calls[0][0];
    expect(arg.create.codeHash).toHaveLength(64);
    // сам код в БД не попадает — только его SHA-256
    expect(JSON.stringify(arg)).not.toContain(out.code.replace('-', ''));
    expect(arg.create.expiresAt.getTime()).toBeGreaterThan(NOW + 14 * 60 * 1000);
    expect(arg.update.usedAt).toBeNull();
  });

  it('generate: планшет не из организации → NotFound', async () => {
    prisma.tablet.findFirst.mockResolvedValue(null);
    await expect(service.generateForTablet('o1', 'nope')).rejects.toThrow(NotFoundException);
  });

  it('redeem: happy path — текущий активный ключ + метаданные, код погашается', async () => {
    prisma.pairingCode.findFirst.mockResolvedValue({ ...futureRow });
    prisma.tablet.findUnique.mockResolvedValue({ ...tabletActive });
    prisma.pairingCode.updateMany.mockResolvedValue({ count: 1 });

    const res = await service.redeem('ABCD-EFGH');
    expect(res).toEqual({
      apiKey: 'rpro_new',
      tabletId: 't1',
      organizationId: 'o1',
      organizationName: 'ООО Ромашка',
      pointName: 'Точка у входа',
    });
    expect(prisma.pairingCode.updateMany).toHaveBeenCalledWith({
      where: { id: 'pc1', usedAt: null },
      data: { usedAt: expect.any(Date) },
    });
  });

  it('redeem: неверный / просроченный / использованный / не-строка → единый 400 байт в байт', async () => {
    prisma.pairingCode.findFirst.mockResolvedValue(null);
    const wrong = await fail(() => service.redeem('ZZZZ-ZZZZ'));

    prisma.pairingCode.findFirst.mockResolvedValue({ ...futureRow, expiresAt: new Date(NOW - 1000) });
    const expired = await fail(() => service.redeem('ZZZZ-ZZZZ'));

    prisma.pairingCode.findFirst.mockResolvedValue({ ...futureRow, usedAt: new Date() });
    const used = await fail(() => service.redeem('ZZZZ-ZZZZ'));

    const notString = await fail(() => service.redeem(12345 as unknown));

    const messages = [wrong, expired, used, notString].map((e) => e.message);
    expect(new Set(messages).size).toBe(1);
    expect(messages[0]).toBe('invalid or expired code');
    expect(wrong).toBeInstanceOf(BadRequestException);
  });

  it('redeem: ротация до погашения → код выдаёт НОВЫЙ активный ключ', async () => {
    prisma.pairingCode.findFirst.mockResolvedValue({ ...futureRow });
    prisma.tablet.findUnique.mockResolvedValue({
      ...tabletActive,
      apiKey: { key: 'rpro_rotated', isActive: true },
    });
    prisma.pairingCode.updateMany.mockResolvedValue({ count: 1 });

    const res = await service.redeem('ABCD-EFGH');
    expect(res.apiKey).toBe('rpro_rotated');
  });

  it('redeem: неактивный ключ → 400; гонка погашения (count=0) → 400', async () => {
    prisma.pairingCode.findFirst.mockResolvedValue({ ...futureRow });
    prisma.tablet.findUnique.mockResolvedValue({ ...tabletActive, apiKey: { key: 'rpro_old', isActive: false } });
    const inactive = await fail(() => service.redeem('ABCD-EFGH'));
    expect(inactive.message).toBe('invalid or expired code');

    prisma.tablet.findUnique.mockResolvedValue({ ...tabletActive });
    prisma.pairingCode.updateMany.mockResolvedValue({ count: 0 });
    const race = await fail(() => service.redeem('ABCD-EFGH'));
    expect(race.message).toBe('invalid or expired code');
  });
});
```

- [ ] **Step 4: Запустить — тест должен упасть «module not found»**

Run: `npm test -- pairing.service`
Expected: FAIL (`Cannot find module './pairing.service'`).

- [ ] **Step 5: Реализовать PairingService**

Create `src\pairing\pairing.service.ts`:

```ts
import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { createHash, randomInt } from 'crypto';

/** Алфавит без 0/O/1/I/L — ровно 32 символа (совпадает с клиентской проверкой). */
export const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const CODE_LEN = 8;
const TTL_MS = 15 * 60 * 1000;
export const INVALID_CODE_MSG = 'invalid or expired code';

export interface PairResult {
  apiKey: string;
  tabletId: string;
  organizationId: string;
  organizationName: string;
  pointName: string | null;
}

@Injectable()
export class PairingService {
  constructor(private prisma: PrismaService) {}

  /** Одноразовый код для планшета: в ответе — открытый код, в БД — только SHA-256. */
  async generateForTablet(organizationId: string, tabletId: string) {
    const tablet = await this.prisma.tablet.findFirst({
      where: { id: tabletId, organizationId },
    });
    if (!tablet) throw new NotFoundException('Tablet not found');

    let code = '';
    for (let i = 0; i < CODE_LEN; i++) code += ALPHABET[randomInt(ALPHABET.length)];
    const expiresAt = new Date(Date.now() + TTL_MS);

    await this.prisma.pairingCode.upsert({
      where: { tabletId },
      create: { tabletId, codeHash: this.hash(code), expiresAt },
      update: { codeHash: this.hash(code), expiresAt, usedAt: null },
    });

    return { code: `${code.slice(0, 4)}-${code.slice(4)}`, expiresAt };
  }

  /** Обмен кода на текущий активный ключ планшета. Любая неудача → единый 400. */
  async redeem(input: unknown): Promise<PairResult> {
    const invalid = () => new BadRequestException(INVALID_CODE_MSG);

    if (typeof input !== 'string') throw invalid();
    const normalized = input.replace(/[^0-9A-Za-z]/g, '').toUpperCase();

    const pc = await this.prisma.pairingCode.findFirst({
      where: { codeHash: this.hash(normalized) },
    });
    if (!pc || pc.usedAt || pc.expiresAt.getTime() <= Date.now()) throw invalid();

    const tablet = await this.prisma.tablet.findUnique({
      where: { id: pc.tabletId },
      include: { apiKey: true, organization: true },
    });
    if (!tablet || !tablet.apiKey || !tablet.apiKey.isActive) throw invalid();

    // Погашение атомарно: проигравший гонку получает тот же 400
    const claimed = await this.prisma.pairingCode.updateMany({
      where: { id: pc.id, usedAt: null },
      data: { usedAt: new Date() },
    });
    if (claimed.count === 0) throw invalid();

    return {
      apiKey: tablet.apiKey.key,
      tabletId: tablet.id,
      organizationId: tablet.organizationId,
      organizationName: tablet.organization.name,
      pointName: tablet.pointName,
    };
  }

  private hash(code: string): string {
    return createHash('sha256').update(code).digest('hex');
  }
}
```

- [ ] **Step 6: Запустить — тесты должны пройти**

Run: `npm test -- pairing.service`
Expected: PASS (все `it` зелёные).

- [ ] **Step 7: Гейт**

Run: `npm test`
Expected: PASS (первый рабочий тест-прогон в репо).

---

### Task 3: POST /api/pair — controller, модуль, HTTP e2e (TDD)

**Files:**
- Create: `server\src\pairing\pairing.controller.ts`
- Create: `server\src\pairing\pairing.module.ts`
- Test: `server\src\pairing\pairing-http.spec.ts`
- Modify: `server\src\app.module.ts`

**Interfaces:**
- Consumes: `PairingService.redeem` (Task 2).
- Produces: `POST /api/pair` → 200 `PairResult` | 400 `{"message":"invalid or expired code"}`; `PairingModule` (export) — импортируется TabletsModule в Task 4.

- [ ] **Step 1: Написать падающий HTTP-тест**

Create `src\pairing\pairing-http.spec.ts`:

```ts
import { BadRequestException, INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import * as request from 'supertest';
import { PairingModule } from './pairing.module';
import { PairingService } from './pairing.service';
import { PrismaService } from '../prisma/prisma.service';
import { ConfigModule } from '@nestjs/config';

describe('POST /api/pair (HTTP)', () => {
  let app: INestApplication;
  const redeem = jest.fn();

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [ConfigModule.forRoot({ isGlobal: true }), PairingModule],
    })
      .overrideProvider(PairingService).useValue({ redeem })
      .overrideProvider(PrismaService).useValue({})
      .compile();

    app = moduleRef.createNestApplication();
    app.setGlobalPrefix('api');
    app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
    await app.init();
  });

  afterAll(() => app.close());

  beforeEach(() => redeem.mockReset());

  it('200: возвращает результат сервиса', async () => {
    const payload = {
      apiKey: 'rpro_x', tabletId: 't1', organizationId: 'o1',
      organizationName: 'Org', pointName: null,
    };
    redeem.mockResolvedValue(payload);
    const res = await request(app.getHttpServer()).post('/api/pair').send({ code: 'ABCD-EFGH' });
    expect(res.status).toBe(200);
    expect(res.body).toEqual(payload);
    expect(redeem).toHaveBeenCalledWith('ABCD-EFGH');
  });

  it('400: тело ошибки — ровно {"message":"invalid or expired code"}', async () => {
    redeem.mockRejectedValue(new BadRequestException('invalid or expired code'));
    const res = await request(app.getHttpServer()).post('/api/pair').send({ code: 'WRONG123' });
    expect(res.status).toBe(400);
    expect(res.body).toEqual({ message: 'invalid or expired code' });
  });
});
```

- [ ] **Step 2: Запустить — упасть**

Run: `npm test -- pairing-http`
Expected: FAIL (`Cannot find module './pairing.module'`).

- [ ] **Step 3: Controller + Module**

Create `src\pairing\pairing.controller.ts`:

```ts
import { Body, Controller, HttpCode, Logger, Post, Req } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { PairingService } from './pairing.service';

@ApiTags('Pairing')
@Controller('pair')
export class PairingController {
  private readonly logger = new Logger(PairingController.name);

  constructor(private readonly pairingService: PairingService) {}

  @Post()
  @HttpCode(200)
  @ApiOperation({ summary: 'Exchange one-time pairing code for tablet API key (tablet)' })
  async pair(@Req() req: { ip?: string }, @Body() body: { code?: unknown }) {
    try {
      return await this.pairingService.redeem(body?.code);
    } catch (e) {
      // Диагностика попыток: IP + результат (спека §5); ответ клиенту не меняется
      this.logger.warn(`pairing rejected ip=${req.ip} error=${(e as Error).message}`);
      throw e;
    }
  }
}
```

Create `src\pairing\pairing.module.ts`:

```ts
import { Module } from '@nestjs/common';
import { PairingController } from './pairing.controller';
import { PairingService } from './pairing.service';

@Module({
  controllers: [PairingController],
  providers: [PairingService],
  exports: [PairingService],
})
export class PairingModule {}
```

- [ ] **Step 4: Зарегистрировать в AppModule**

`src\app.module.ts` — добавить импорт и в `imports`:
```ts
import { PairingModule } from './pairing/pairing.module';
```
```ts
    TabletsModule,
    PairingModule,
```

- [ ] **Step 5: Запустить HTTP-тесты**

Run: `npm test -- pairing-http`
Expected: PASS.

- [ ] **Step 6: Гейт**

Run: `npm test && npm run build`
Expected: PASS + без ошибок компиляции.

---

### Task 4: Admin-endpoint выдачи кода (TDD)

**Files:**
- Modify: `server\src\tablets\tablets.controller.ts` (новый маршрут)
- Modify: `server\src\tablets\tablets.service.ts` (делегирование — НЕТ: генерация в PairingService; контроллер получает PairingService)
- Modify: `server\src\tablets\tablets.module.ts` (imports PairingModule)
- Test: `server\src\tablets\tablets-http.spec.ts`

**Interfaces:**
- Consumes: `PairingService.generateForTablet` (Task 2), `PairingModule` (Task 3).
- Produces: `POST /api/tablets/:id/pairing-code` (Bearer) → 200 `{ code, expiresAt }`; 404 если планшет не найден/чужой.

- [ ] **Step 1: Падающий HTTP-тест**

Create `src\tablets\tablets-http.spec.ts`:

```ts
import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import * as request from 'supertest';
import { TabletsModule } from './tablets.module';
import { PairingService } from '../pairing/pairing.service';
import { PrismaService } from '../prisma/prisma.service';
import { ConfigModule } from '@nestjs/config';

describe('POST /api/tablets/:id/pairing-code (HTTP)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [ConfigModule.forRoot({ isGlobal: true }), TabletsModule],
    })
      .overrideProvider(PairingService).useValue({ generateForTablet: jest.fn() })
      .overrideProvider(PrismaService).useValue({})
      .compile();

    app = moduleRef.createNestApplication();
    app.setGlobalPrefix('api');
    await app.init();
  });

  afterAll(() => app.close());

  it('маршрут существует: без токена → 401 от JwtAuthGuard (несуществующий маршрут дал бы 404)', async () => {
    const res = await request(app.getHttpServer()).post('/api/tablets/t1/pairing-code');
    expect(res.status).toBe(401);
  });

  it('некорректный id не роняет сервер (guard отвечает до сервиса)', async () => {
    const res = await request(app.getHttpServer())
      .post('/api/tablets/%2E%2E/pairing-code');
    expect(res.status).not.toBe(500);
  });
});
```

*Логика генерации (org-принадлежность, TTL, хэш) покрыта тестами `PairingService` в Task 2; здесь фиксируется наличие маршрута и guard'а.*

- [ ] **Step 2: Запустить — упасть**

Run: `npm test -- tablets-http`
Expected: FAIL (маршрута ещё нет: Nest вернёт 404 «Cannot POST», а тест ожидает 401 от guard'а).

- [ ] **Step 3: Маршрут в TabletsController**

`src\tablets\tablets.controller.ts` — импорт + новый метод + `constructor`:

```ts
import { PairingService } from '../pairing/pairing.service';
```
```ts
  constructor(
    private tabletsService: TabletsService,
    private pairingService: PairingService,
  ) {}
```
```ts
  @Post(':id/pairing-code')
  @ApiOperation({ summary: 'Issue a one-time pairing code for the tablet' })
  async pairingCode(@Param('id') id: string, @Req() req) {
    return this.pairingService.generateForTablet(req.user.organizationId, id);
  }
```

`src\tablets\tablets.module.ts` — добавить `PairingModule` в `imports`:

```ts
import { PairingModule } from '../pairing/pairing.module';
```
```ts
@Module({
  imports: [PairingModule],
  ...
})
```

- [ ] **Step 4: Запустить тесты**

Run: `npm test -- tablets-http`
Expected: PASS (маршрут больше не 404; guard отвечает 401 без токена).

- [ ] **Step 5: Гейт**

Run: `npm test && npm run build`
Expected: PASS.

---

### Task 5: pointName в создании/обновлении планшета (TDD)

**Files:**
- Modify: `server\src\tablets\tablets.service.ts`
- Modify: `server\src\tablets\tablets.controller.ts` (тип body)
- Test: `server\src\tablets\tablets.service.spec.ts`

**Interfaces:**
- Consumes: `Tablet.pointName` (Task 1).
- Produces: `create(orgId, { name, pointName? })`, `update(id, orgId, { name?, isActive?, pointName? })` — кабинет передаёт имя Точки; ответы содержат `pointName` (Task 3 pairing читает его уже сейчас).

- [ ] **Step 1: Падающий тест**

Create `src\tablets\tablets.service.spec.ts`:

```ts
import { TabletsService } from './tablets.service';

describe('TabletsService pointName', () => {
  let prisma: any;
  let service: TabletsService;

  beforeEach(() => {
    prisma = {
      tablet: { create: jest.fn(), findFirst: jest.fn(), update: jest.fn(), delete: jest.fn() },
    };
    service = new TabletsService(prisma as any);
  });

  it('create: pointName уходит в БД, без него — null', async () => {
    prisma.tablet.create.mockResolvedValue({});
    await service.create('o1', { name: 'Планшет 1', pointName: 'Точка №2' } as any);
    expect(prisma.tablet.create.mock.calls[0][0].data.pointName).toBe('Точка №2');

    await service.create('o1', { name: 'Планшет 2' } as any);
    expect(prisma.tablet.create.mock.calls[1][0].data.pointName).toBeNull();
  });

  it('update: pointName передаётся дальше, отсутствие не затирает (prisma игнорирует undefined)', async () => {
    prisma.tablet.findFirst.mockResolvedValue({ id: 't1' });
    prisma.tablet.update.mockResolvedValue({});
    await service.update('t1', 'o1', { pointName: 'Новое имя' });
    expect(prisma.tablet.update.mock.calls[0][0].data).toEqual({ pointName: 'Новое имя' });

    await service.update('t1', 'o1', { name: 'X' });
    expect(prisma.tablet.update.mock.calls[1][0].data).toEqual({ name: 'X' });
  });
});
```

- [ ] **Step 2: Запустить — упасть**

Run: `npm test -- tablets.service`
Expected: FAIL (`data.pointName` undefined / `create` не принимает поле).

- [ ] **Step 3: Реализация**

`src\tablets\tablets.service.ts`:

```ts
  async create(organizationId: string, data: { name: string; pointName?: string }) {
    const key = `rpro_${uuidv4().replace(/-/g, '')}`;

    return this.prisma.tablet.create({
      data: {
        organizationId,
        name: data.name,
        pointName: data.pointName ?? null,
        apiKey: {
          create: {
            organizationId,
            key,
            tabletName: data.name,
          },
        },
      },
      include: { apiKey: true },
    });
  }
```

Сигнатуру `update` расширить: `data: { name?: string; isActive?: boolean; pointName?: string }` (тело пробрасывается в `prisma.tablet.update` без изменений — undefined поля Prisma игнорирует).

`src\tablets\tablets.controller.ts` — типы body: `@Body() body: { name: string; pointName?: string }` (create) и `@Body() body: { name?: string; isActive?: boolean; pointName?: string }` (update).

- [ ] **Step 4: Запустить — пройти**

Run: `npm test -- tablets.service`
Expected: PASS.

- [ ] **Step 5: Гейт**

Run: `npm test && npm run build`
Expected: PASS.

---

### Task 6: Rate-limit на /api/pair + README

**Files:**
- Modify: `server\package.json` (зависимость)
- Modify: `server\src\app.module.ts` (ThrottlerModule + APP_GUARD)
- Modify: `server\src\pairing\pairing.controller.ts` (@Throttle)
- Modify: `server\src\pairing\pairing-http.spec.ts` (тест 429)
- Modify: `server\README.md` (таблицы эндпоинтов)

**Interfaces:**
- Consumes: Task 3.
- Produces: `POST /api/pair` ограничен 10 req/min/IP (429 сверх лимита); дефолт для остальных — 100/min (дашборд не заденет).

- [ ] **Step 1: Установить throttler**

Run: `npm install @nestjs/throttler`
Expected: зависимость в `dependencies`.

- [ ] **Step 2: Падающий тест 429**

Добавить в конец `src\pairing\pairing-http.spec.ts`:

```ts
describe('rate limit POST /api/pair', () => {
  it('11-й запрос за минуту → 429', async () => {
    redeem.mockResolvedValue({ apiKey: 'k', tabletId: 't', organizationId: 'o', organizationName: 'O', pointName: null });
    let last = 0;
    for (let i = 0; i < 11; i++) {
      last = (await request(app.getHttpServer()).post('/api/pair').send({ code: 'ABCD-EFGH' })).status;
    }
    expect(last).toBe(429);
  });
});
```

Run: `npm test -- pairing-http`
Expected: FAIL (11-й запрос — 200, throttler не подключён).

- [ ] **Step 3: Подключить throttler**

`src\app.module.ts`:

```ts
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { APP_GUARD } from '@nestjs/core';
```
```ts
  imports: [
    ThrottlerModule.forRoot({ throttlers: [{ ttl: 60_000, limit: 100 }] }),
    ConfigModule.forRoot({ isGlobal: true }),
    ...
  ],
  providers: [{ provide: APP_GUARD, useClass: ThrottlerGuard }],
```

`src\pairing\pairing.controller.ts`:

```ts
import { Throttle } from '@nestjs/throttler';
```
```ts
  @Post()
  @HttpCode(200)
  @Throttle({ default: { limit: 10, ttl: 60000 } })
```

- [ ] **Step 4: Запустить — пройти**

Run: `npm test -- pairing-http`
Expected: PASS (11-й → 429).

- [ ] **Step 5: README**

`README.md` — в таблицу Tablets добавить строку:
```
| POST | `/api/tablets/:id/pairing-code` | Bearer | Issue one-time pairing code |
```
Новый блок после таблицы Tablets:
```
### Pairing
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/pair` | — | Exchange pairing code → tablet API key (rate-limited 10/min) |
```

- [ ] **Step 6: Гейт**

Run: `npm test && npm run build`
Expected: PASS — фаза A завершена.

---

## Фаза B — Android-приложение

База: `C:\projects\feedback-app\.worktrees\kiosk-mode` (ветка `kiosk-mode`).

### Task 7: Ядро привязки клиента (TDD)

**Files:**
- Create: `app\src\main\java\com\respondent\pro\cabinet\KabinetConfig.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\CabinetApi.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\BindingStorage.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\CabinetBinder.kt`
- Create: `app\src\main\java\com\respondent\pro\cabinet\CabinetState.kt`
- Modify: `app\build.gradle.kts` (зависимость security-crypto)
- Test: `app\src\test\java\com\respondent\pro\cabinet\CabinetBinderTest.kt`

**Interfaces:**
- Produces (используют Task 8–9): `normalizePairingCode(raw: String): String?`; `class CabinetBinder(storage: BindingStorage, api: CabinetApi)` с `suspend fun pair(rawCode: String): PairOutcome`, `fun currentBinding(): StoredBinding?`, `fun unbindLocal()`, `fun isRevoked(httpCode: Int): Boolean`; `enum class PairOutcome { PAIRED, BUSY, INVALID_CODE, NETWORK_ERROR, REVOKED }`; `sealed class CabinetState` (`Unbound`, `Binding`, `Bound(organizationName, pointName)`, `Failed(kind: FailKind)`); `enum class FailKind { INVALID_CODE, NETWORK, REVOKED }`; `interface CabinetApi { @POST("pair") suspend fun pair(@Body body: PairRequest): PairResponse }`; `data class PairResponse(apiKey, tabletId, organizationId, organizationName, pointName)`; `interface BindingStorage { read/write/clear }` + `data class StoredBinding`.

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
        override suspend fun pair(body: PairRequest): PairResponse { calls++; return respond(body) }
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

    @Test fun `pair success stores binding`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { ok }
        val outcome = CabinetBinder(storage, api).pair("abcd-efgh")
        assertEquals(PairOutcome.PAIRED, outcome)
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
        storage.data = ok.let { StoredBinding(it.apiKey, it.tabletId, it.organizationId, it.organizationName, it.pointName) }
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
    @POST("pair")
    suspend fun pair(@Body body: PairRequest): PairResponse
}

data class PairRequest(val code: String)

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

/** Состояние экрана «Кабинет» (UI + ViewModel). */
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
 * Алфавит обязан совпадать с серверным PairingService.ALPHABET
 * (2-9 A-H J K M N P-Z — без 0/O/1/I/L).
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
Expected: PASS.

- [ ] **Step 6: Коммит**

```powershell
git add app/src/main/java/com/respondent/pro/cabinet app/build.gradle.kts app/src/test/java/com/respondent/pro/cabinet
git commit -m "feat(cabinet): pairing-code normalization, encrypted binding storage, binder"
```

---

### Task 8: DI и состояние в SettingsViewModel

**Files:**
- Modify: `app\src\main\java\com\respondent\pro\di\AppModule.kt`
- Modify: `app\src\main\java\com\respondent\pro\viewmodel\SettingsViewModel.kt`

**Interfaces:**
- Consumes: всё из Task 7.
- Produces: Hilt-провайдеры `CabinetApi`, `BindingStorage`; `SettingsViewModel.cabinetState: StateFlow<CabinetState>`, `fun pairCabinet(rawCode: String)`, `fun unbindCabinet()` — использует Task 9.

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

*Почему без юнит-теста ViewModel: логика живёт в `CabinetBinder` (Task 7, покрыта 10 тестами); SettingsViewModel в проекте никогда не тестировался — консистентность кодовой базы.*

- [ ] **Step 4: Коммит**

```powershell
git add app/src/main/java/com/respondent/pro/di/AppModule.kt app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt
git commit -m "feat(cabinet): DI providers and binding state in SettingsViewModel"
```

---

### Task 9: UI «Кабинет» + строки (TDD строк)

**Files:**
- Modify: `app\src\main\java\com\respondent\pro\ui\i18n\AppStrings.kt` (11 строк ru+en)
- Create: `app\src\test\java\com\respondent\pro\ui\i18n\CabinetStringsTest.kt`
- Modify: `app\src\main\java\com\respondent\pro\ui\screens\SettingsScreen.kt` (CabinetCard)

**Interfaces:**
- Consumes: `cabinetState/pairCabinet/unbindCabinet` (Task 8), `CabinetState` (Task 7).

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
    kabinetHint = "Код: в кабинете → Планшеты → «Получить код привязки». Действует 15 минут, одноразовый.",
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
    kabinetHint = "Code: in the cabinet → Tablets → \"Get pairing code\". Valid for 15 minutes, single-use.",
    kabinetErrorInvalid = "Invalid or expired code",
    kabinetErrorNetwork = "Server unreachable",
    kabinetRevoked = "Binding revoked",
```

*Примечание: `kabinetStatusBoundPoint` в ru/en содержит литерал `$s` — в Kotlin-строке экранируется `\$`. В тесте плейсхолдеры проверяются как `%1$s`/`%2$s`.*

- [ ] **Step 4: Запустить — пройти**

Run: `.\gradlew.bat testDebugUnitTest --tests "*CabinetStringsTest*"`
Expected: PASS.

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

В call-site, сразу после `KioskCard(...)`:

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

- [ ] **Step 6: Гейт**

Run: `$env:JAVA_HOME="C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 7: Коммит**

```powershell
git add app/src/main/java/com/respondent/pro/ui app/src/test/java/com/respondent/pro/ui/i18n/CabinetStringsTest.kt
git commit -m "feat(cabinet): binding section in settings with status, code input and unbind"
```

---

### Task 10: Финальная верификация и ledger

**Files:**
- Modify: `.superpowers\sdd\2026-09-28-kiosk-mode\progress.md` (запись цикла)

**Interfaces:**
- Consumes: Task 1–9.

- [ ] **Step 1: Полный прогон клиента**

Run: `$env:JAVA_HOME="C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, все тесты зелёные (31 прежний + CabinetBinderTest 10 + CabinetStringsTest 3).

- [ ] **Step 2: Полный прогон сервера**

Run (каталог сервера): `npm test && npm run build`
Expected: PASS.

- [ ] **Step 3: Устройственная проверка UI (без сервера)**

Планшет подключить по USB; `adb install -r` свежего APK; пройти Настройки → секция «Привязка к кабинету»: статус «Не привязан», поле кода, кнопка. Снять uia-dump (проверка присутствия текстов). Ожидание: ввод мусора → «Неверный или просроченный код» (локально, без сети). Автоповорот восстановить (`accelerometer_rotation=1`).

- [ ] **Step 4: Ledger**

Добавить в `progress.md` запись цикла: спека, план, коммиты Task 7–9, результаты тестов (клиент/сервер), статус E2E (ожидает деплоя сервера — отдельное согласие пользователя).

- [ ] **Step 5: Push**

Run: `git push origin kiosk-mode`
Expected: `... kiosk-mode -> kiosk-mode`.

- [ ] **Step 6: E2E-цикл (ТОЛЬКО по явному «да» пользователя)**

1) Деплой сервера: `./deploy.sh` на VPS (сторонний side-effect — спросить!); 2) создать планшет+код (curl с JWT); 3) ввод кода на планшете → «Привязан: …»; 4) `rotate-key` в кабинете → следующий авторизованный запрос → состояние «Привязка отменена» (в v1 авторизованных запросов ещё нет — триггер проверяется unit-тестом Task 7 `isRevoked`; живой триггер появится в этапе 2 спеки). Визуал — пользователь.
