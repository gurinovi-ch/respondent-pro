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
