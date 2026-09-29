package com.respondent.pro.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Таблица статических текстов интерфейса.
 * Два языка: ru / en. Выбранное значение хранится в AppSettings.language
 * и доставляется через [LocalAppStrings] из MainActivity.
 */
class AppStrings(
    // Настройки
    val settingsTitle: String,
    val languageLabel: String,
    val telegramTokenLabel: String,
    val chatIdLabel: String,
    val pinLabel: String,
    val orgNameLabel: String,
    val greetingLabel: String,
    val callToActionLabel: String,
    val commentHintLabel: String,
    val thankYouLabel: String,
    val resetTimeoutLabel: String,
    val sendIncompleteLabel: String,
    val androidSettingsLabel: String,
    val btnStart: String,
    // Способ отправки
    val sendMethodLabel: String,
    val sendMethodTelegram: String,
    val sendMethodEmail: String,
    // Telegram
    val detectChatIdBtn: String,
    val detectingChatId: String,
    val chatIdDetected: String,
    val chatIdNoMessages: String,
    val chatIdError: String,
    // Email
    val emailPresetLabel: String,
    val emailPresetGmail: String,
    val emailPresetYandex: String,
    val emailPresetCustom: String,
    val emailFromLabel: String,
    val emailPasswordLabel: String,
    val emailToLabel: String,
    val smtpHostLabel: String,
    val smtpPortLabel: String,
    val smtpSslLabel: String,
    // Инструкции
    val instructionsTitle: String,
    // Инфокиоск
    val kioskTitle: String,
    val kioskStatusOwner: String,
    val kioskStatusLock: String,
    val kioskStatusNoOwner: String,
    val kioskAdbSpoiler: String,
    val kioskAdbSteps: String,
    val kioskCmdHint: String,
    val kioskQrSpoiler: String,
    val kioskQrSteps: String,
    val kioskQrSsidLabel: String,
    val kioskQrPasswordLabel: String,
    val kioskQrShowButton: String,
    val kioskQrShareButton: String,
    // Стартовый экран
    val btnDone: String,
    val defaultCallToAction: String,
    val pinDialogTitle: String,
    val btnCancel: String,
    // Экран комментария
    val defaultCommentHint: String,
    val editTextHint: String,
    val btnSend: String,
    val btnNoComment: String,
    val errorSavePattern: String,
    val errorNavPattern: String,
    // Экран благодарности
    val defaultThankYou: String
)

val ruStrings = AppStrings(
    settingsTitle = "Настройки",
    languageLabel = "Язык интерфейса",
    telegramTokenLabel = "Токен Telegram Bot (необходимо получить по средствам BotFather, см. инструкцию в FAQ)",
    chatIdLabel = "Chat ID ( ID чата созданного бота для получения отзывов, см. инструкцию в FAQ)",
    pinLabel = "PIN-код для входа в настройки (4 цифры) из рабочего режима.",
    orgNameLabel = "Название Вашей компании (отображается на экране, можно оставить пустым)",
    greetingLabel = "Приветствие (отображается на экране, можно оставить пустым)",
    callToActionLabel = "Обращение к клиенту (отображается на экране, обязательно)",
    commentHintLabel = "Текст перед полем отзыва (отображается на экране, можно оставить пустым)",
    thankYouLabel = "Текст благодарности (отображается на экране, обязательно)",
    resetTimeoutLabel = "Время (сек) до автоматического возврата на стартовый экран с момента последнего касания экрана.",
    sendIncompleteLabel = "Отправлять незавершённый отзыв",
    androidSettingsLabel = "Настройки Android",
    btnStart = "Старт",
    sendMethodLabel = "Способ отправки отзывов",
    sendMethodTelegram = "Telegram",
    sendMethodEmail = "E-mail",
    detectChatIdBtn = "Определить Chat ID",
    detectingChatId = "Определяем...",
    chatIdDetected = "Chat ID определён: %s",
    chatIdNoMessages = "Напишите боту /start и повторите",
    chatIdError = "Ошибка: %s",
    emailPresetLabel = "Почтовый сервис",
    emailPresetGmail = "Gmail",
    emailPresetYandex = "Яндекс",
    emailPresetCustom = "Другой (вручную)",
    emailFromLabel = "Email приложения (адрес в котором получен пароль приложения)",
    emailPasswordLabel = "Пароль приложения",
    emailToLabel = "Email получателя (любой ваш адрес для получения отзывов, можно использовать Email приложения)",
    smtpHostLabel = "SMTP хост",
    smtpPortLabel = "SMTP порт",
    smtpSslLabel = "SSL",
    instructionsTitle = "Инструкции",
    kioskTitle = "ИНФОКИОСК",
    kioskStatusOwner = "✅ Device Owner выдан",
    kioskStatusLock = "Lock Task активен",
    kioskStatusNoOwner = "⚠️ Device Owner не выдан — полная блокировка недоступна",
    kioskAdbSpoiler = "Настройка через ADB (с ПК)",
    kioskAdbSteps = "1. Включите USB-отладку: Параметры → О телефоне → 7 касаний по «Номеру сборки».\n" +
        "2. Скачайте APK по ссылке ниже (нажатие копирует).\n" +
        "3. Подключите планшет к ПК и выполните команды по одной (нажатие копирует команду).\n" +
        "4. Вернитесь в приложение — статус должен стать ✅.",
    kioskCmdHint = "Нажмите на текст, чтобы скопировать",
    kioskQrSpoiler = "Настройка через QR-код (без ПК)",
    kioskQrSteps = "1. Введите Wi-Fi (или оставьте пустым — сеть выберут в мастере) и нажмите «Показать QR-код».\n" +
        "2. Сохраните/отправите QR изображение на второй устройство (телефон).\n" +
        "3. Сбросьте планшет до заводского настроек.\n" +
        "4. На приветственном экране: Android 7–9 — 6 касаний по экрану; Android 10+ — иконка доступности → камера.\n" +
        "5. Наведите камеру на QR: планшет скачает APK, станет Device Owner и подключится к Wi-Fi.\n" +
        "6. После запуска: выберите лаунчер (один раз) и выдайте разрешение «Поверх других окон».",
    kioskQrSsidLabel = "Wi-Fi сеть (SSID)",
    kioskQrPasswordLabel = "Пароль Wi-Fi",
    kioskQrShowButton = "Показать QR-код",
    kioskQrShareButton = "Поделиться",
    btnDone = "Готово",
    defaultCallToAction = "Пожалуйста оцените наши услуги",
    pinDialogTitle = "Введите PIN",
    btnCancel = "Отмена",
    defaultCommentHint = "Ваш комментарий (отзыв) к оценке",
    editTextHint = "Ваш отзыв...",
    btnSend = "Отправить",
    btnNoComment = "Без комментария",
    errorSavePattern = "Ошибка сохранения: %s",
    errorNavPattern = "Ошибка навигации: %s",
    defaultThankYou = "Спасибо за ваш отзыв!"
)

val enStrings = AppStrings(
    settingsTitle = "Settings",
    languageLabel = "Interface language",
    telegramTokenLabel = "Telegram Bot token (get it via BotFather, see FAQ)",
    chatIdLabel = "Chat ID (chat ID created by the bot to receive feedback, see FAQ)",
    pinLabel = "PIN to enter settings (4 digits) from running mode.",
    orgNameLabel = "Your company name (displayed on screen, may be left empty)",
    greetingLabel = "Greeting (displayed on screen, may be left empty)",
    callToActionLabel = "Call to action (displayed on screen, required)",
    commentHintLabel = "Text before the feedback field (displayed on screen, may be left empty)",
    thankYouLabel = "Thank-you text (displayed on screen, required)",
    resetTimeoutLabel = "Time (sec) until auto-return to the start screen after the last screen touch.",
    sendIncompleteLabel = "Send incomplete feedback",
    androidSettingsLabel = "Android Settings",
    btnStart = "Start",
    sendMethodLabel = "Feedback delivery method",
    sendMethodTelegram = "Telegram",
    sendMethodEmail = "E-mail",
    detectChatIdBtn = "Detect Chat ID",
    detectingChatId = "Detecting...",
    chatIdDetected = "Chat ID detected: %s",
    chatIdNoMessages = "Send /start to your bot and try again",
    chatIdError = "Error: %s",
    emailPresetLabel = "Email service",
    emailPresetGmail = "Gmail",
    emailPresetYandex = "Yandex",
    emailPresetCustom = "Other (manual)",
    emailFromLabel = "App email (address where the app password was created)",
    emailPasswordLabel = "App password",
    emailToLabel = "Recipient email (any your address to receive feedback, can be the app email)",
    smtpHostLabel = "SMTP host",
    smtpPortLabel = "SMTP port",
    smtpSslLabel = "SSL",
    instructionsTitle = "Instructions",
    kioskTitle = "KIOSK MODE",
    kioskStatusOwner = "✅ Device Owner granted",
    kioskStatusLock = "Lock Task active",
    kioskStatusNoOwner = "⚠️ Device Owner not granted — full lockdown unavailable",
    kioskAdbSpoiler = "Setup via ADB (from PC)",
    kioskAdbSteps = "1. Enable USB debugging: Settings → About tablet → tap \"Build number\" 7 times.\n" +
        "2. Download the APK via the link below (tap to copy).\n" +
        "3. Connect the tablet to a PC and run the commands one by one (tap to copy).\n" +
        "4. Return to the app — status should become ✅.",
    kioskCmdHint = "Tap text to copy",
    kioskQrSpoiler = "Setup via QR code (no PC)",
    kioskQrSteps = "1. Enter Wi-Fi (or leave empty — network is chosen in the wizard) and tap \"Show QR code\".\n" +
        "2. Save/share the QR image to another device (phone).\n" +
        "3. Factory-reset the tablet.\n" +
        "4. On the welcome screen: Android 7–9 — tap the screen 6 times; Android 10+ — accessibility icon → camera.\n" +
        "5. Point the camera at the QR: the tablet downloads the APK, becomes Device Owner and connects to Wi-Fi.\n" +
        "6. On first launch: choose the launcher (once) and grant \"Display over other apps\".",
    kioskQrSsidLabel = "Wi-Fi network (SSID)",
    kioskQrPasswordLabel = "Wi-Fi password",
    kioskQrShowButton = "Show QR code",
    kioskQrShareButton = "Share",
    btnDone = "Done",
    defaultCallToAction = "Please rate our services",
    pinDialogTitle = "Enter PIN",
    btnCancel = "Cancel",
    defaultCommentHint = "Your comment (feedback) for the rating",
    editTextHint = "Your feedback...",
    btnSend = "Send",
    btnNoComment = "No comment",
    errorSavePattern = "Save error: %s",
    errorNavPattern = "Navigation error: %s",
    defaultThankYou = "Thank you for your feedback!"
)

fun appStringsFor(language: String): AppStrings = if (language == "en") enStrings else ruStrings

val LocalAppStrings = staticCompositionLocalOf { ruStrings }
