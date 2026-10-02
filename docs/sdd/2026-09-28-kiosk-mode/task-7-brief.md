### Task 7: GitHub-репозиторий и Release APK (KioskConfig)

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/KioskConfig.kt`
- Modify: ничего больше; используется существующий git (`master` уже закоммичен)

**Interfaces:**
- Consumes: собранная колонка kiosk-компонентов (Task 1–6) — APK в Release должен уметь быть DO.
- Produces: `object KioskConfig { const val APK_DOWNLOAD_URL: String; const val OWNER_REPO: String; fun adbCommands(): List<String> }` — `APK_DOWNLOAD_URL` используется Task 9 (QR payload), `adbCommands()` — Task 8 (инструкция ADB).

**ВНИМАНИЕ: задача интерактивная — потребуется учётная запись GitHub партнёра.**

- [ ] **Step 1: Проверить git-remote и доступ к GitHub**

```powershell
cd C:\projects\feedback-app
git remote -v
gh auth status
```
- Если remote уже есть — перейти к Step 3 (URL взять из remote).
- Если `gh` не установлен или не авторизован — остановиться и попросить партнёра выполнить `gh auth login` (интерактивно) либо сообщить имя GitHub-пользователя/организации для ручного создания репозитория.

- [ ] **Step 2: Создать публичный репозиторий и запушить**

```powershell
gh repo create respondent-pro --public --source . --push
```
(Имя `respondent-pro` — по умолчанию; если партнёр назначил другое — использовать его и зафиксировать в `OWNER_REPO`.)
Expected: создан `https://github.com/<owner>/respondent-pro`, ветка `master` запушена.

- [ ] **Step 3: Собрать APK и опубликовать Release с ассетом `RESPONDENT.PRO.apk`**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
Copy-Item app\build\outputs\apk\debug\app-debug.apk app\build\outputs\apk\debug\RESPONDENT.PRO.apk
gh release create v1.0-test app\build\outputs\apk\debug\RESPONDENT.PRO.apk --title "1.0-test" --notes "Инфокиоск: DO + Lock Task + автозапуск"
```
Expected: релиз `v1.0-test`, ассет `RESPONDENT.PRO.apk`.
Проверка URL (он должен отдаваться напрямую):
```powershell
(Invoke-WebRequest -Uri "https://github.com/<owner>/respondent-pro/releases/latest/download/RESPONDENT.PRO.apk" -Method Head -UseBasicParsing).StatusCode
```
Expected: `200`. Реальный `<owner>` подставить из `gh repo view --json url -q .url`.

- [ ] **Step 4: Создать `KioskConfig.kt` с реальными значениями**

```kotlin
package com.respondent.pro.kiosk

/**
 * Константы инфокиоска: откуда планшет скачивает APK (QR-провижининг,
 * инструкция ADB) и команды настройки. URL — GitHub Releases (spec §13.5).
 */
object KioskConfig {

    /** Организация/репозиторий: заполняется после создания репозитория (Step 3). */
    const val OWNER_REPO = "<owner>/respondent-pro"   // ← заменить на реальный owner из Step 3

    const val APK_DOWNLOAD_URL =
        "https://github.com/$OWNER_REPO/releases/latest/download/RESPONDENT.PRO.apk"

    /** Команды инструкции ADB — по одной, нажатие копирует (Task 8). */
    fun adbCommands(): List<String> = listOf(
        "adb install -r RESPONDENT.PRO.apk",
        "adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver",
        "adb shell cmd package set-home-activity com.respondent.pro/.MainActivity",
        "adb shell appops set com.respondent.pro SYSTEM_ALERT_WINDOW allow"
    )
}
```

`<owner>` в Step 4 — **единственное допустимое место «подстановки после выполнения шага»**: значение известно из Step 3 в той же задаче и подставляется немедленно; в следующем шаге проверяется, что плейсхолдеров в файле не осталось.

- [ ] **Step 5: Проверить, что плейсхолдеров не осталось**

```powershell
Select-String -Path app\src\main\java\com\respondent\pro\kiosk\KioskConfig.kt -Pattern "<owner>"
```
Expected: пусто (все `<owner>` заменены).

- [ ] **Step 6: Собрать и закоммитить**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/respondent/pro/kiosk/KioskConfig.kt
git commit -m "feat(kiosk): KioskConfig with GitHub Releases APK url and adb commands"
git push
```

---

