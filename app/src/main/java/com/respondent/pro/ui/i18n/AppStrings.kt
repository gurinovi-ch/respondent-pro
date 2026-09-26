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
