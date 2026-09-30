# Установка с нуля: от APK до Device Owner и Lock Task

Полный путь тестовой установки RESPONDENT.PRO на чистое устройство:
установка APK → выдача Device Owner → активация Lock Task.

## Что нужно

- ПК с **adb** (Android platform-tools). Если adb не в PATH — подставляйте
  полный путь (в рабочей среде: `C:\platform-tools\adb.exe`)
- USB-кабель
- Устройство **Android 7.0+** (minSdk 24) с пройденной первичной настройкой
- APK: `app\build\outputs\apk\debug\app-debug.apk`
  - debug-сборка, подписана debug-ключом (release в проекте не подписывается)
  - версия: `1.0-test`
  - SHA-256 сборки от 30.09.2026: `5A8607AC5771D742803DB72E1CFEB54E44B5E57572F99C745D6BA21E05C36606`
    (при пересборке меняется)

## Предпосылки

`dpm set-device-owner` работает только на «чистом» устройстве:

1. **Нет другого** Device Owner / Profile Owner
2. **Нет учётных записей** (Google и др.) — Настройки → Учётные записи
   (раздел должен быть пуст)
3. Первичная настройка Android (мастер) пройдена
4. Устройство разблокировано и не спит

> Самый чистый способ получить состояние «с нуля» — **factory reset**.
> ⚠️ Деструктивная операция: стирает все данные. Выполняйте только
> осознанно и с явного согласия.

## Шаг A. Подготовка устройства

1. Настройки → О телефоне/планшете → «Номер сборки» 7 раз →
   появится пункт «Для разработчиков»
2. Настройки → Система → Для разработчиков → **USB-отладка** → включить
3. Подключить ПК кабелем → на экране устройства
   «Разрешить отладку по USB?» → **Разрешить** (галочка «Всегда для
   этого компьютера»)

Проверка:

```
adb devices
```

Устройство должно иметь статус `device`. Если `unauthorized` —
переподключите кабель и подтвердите RSA-запрос на экране.

## Шаг B. Установка APK

```
adb install "C:\путь\к\app-debug.apk"
```

Повторная установка поверх: `adb install -r "C:\путь\к\app-debug.apk"`

Проверка:

```
adb shell "pm list packages | grep respondent"
```

→ `package:com.respondent.pro`

## Шаг C. Первый запуск

```
adb shell am start -n com.respondent.pro/.MainActivity
```

Откроется главный экран оценки. Device Owner ещё не выдан — это нормально.

## Шаг D. Выдача Device Owner

```
adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver
```

Ожидаемый вывод — строка, содержащая `Success` и имя компонента.

Если команда завершилась ошибкой → [Troubleshooting](#troubleshooting).

## Шаг E. Перезапуск приложения (обязателен)

Политики применяются при **создании** активности, Lock Task стартует при
**возврате в foreground**. Поэтому после выдачи DO перезапустите приложение:

```
adb shell am force-stop com.respondent.pro
adb shell am start -n com.respondent.pro/.MainActivity
```

После перезапуска статус-бар скрыт, приложение находится в Lock Task.

## Шаг F. Проверки

### 1. Device Owner (adb)

```
adb shell "dumpsys device_policy | grep -A1 'Device Owner'"
```

Ожидаемо:

```
Device Owner:
    admin=ComponentInfo{com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}
```

### 2. Lock Task (adb)

```
adb shell "dumpsys activity activities | grep -i locktask"
```

Ключевая строка: `mLockTaskModeState=LOCKED`

### 3. Статус в приложении

Шестёрка в статус-строке → PIN `0000` → карточка **ИНФОКИОСК**:
зелёные строки «✅ Device Owner выдан» и «Lock Task активен».

### 4. Поведение

- [ ] Кнопка **Home** остаётся в приложении (оно же — лаунчер HOME)
- [ ] **Шторка** уведомений не вытягивается
- [ ] Кнопка **Обзоры** (Recents) не открывает список приложений
- [ ] **Статус-бар** скрыт
- [ ] Системные настройки открываются только через «Настройки Android»
      в настройках приложения (экскурсия с ярлыком возврата)

## Troubleshooting

### `adb devices` → `unauthorized`
Переподключите кабель, подтвердите «Разрешить отладку по USB» на экране
устройства.

### Ошибка при `set-device-owner` (упоминание accounts / учётных записей)
На устройстве есть учётные записи. Удалите их: Настройки → Учётные записи.
Либо factory reset (⚠️ деструктивно).

### Ошибка «уже есть device/profile owner»
Состояние выдано другому приложению (или нашему от прошлого теста).
На Android 10 штатных adb-команд снятия DO у **не-testOnly** приложения нет —
проверить список можно командой `adb shell dpm` (там только
`remove-active-admin`, и он работает лишь с `android:testOnly` в манифесте).
Варианты: factory reset (⚠️ деструктивно) или тестовая сборка с testOnly.

### DO выдан, но Lock Task не активен / политики не применились
Перезапустите приложение (Шаг E) — политики применяются в `onCreate`,
Lock Task — в `onResume`.

### Компонент не найден / unknown command
Убедитесь, что установлена именно эта сборка (пакет `com.respondent.pro`)
и имя компонента не изменено: `com.respondent.pro/.kiosk.KioskAdminReceiver`.

## Снятие DO (когда тест завершён)

Как сказано выше, для обычной (не-testOnly) сборки штатной adb-команды нет:
factory reset (⚠️) либо тестовая сборка с `android:testOnly=true` и
`adb shell dpm remove-active-admin com.respondent.pro/.kiosk.KioskAdminReceiver`.
