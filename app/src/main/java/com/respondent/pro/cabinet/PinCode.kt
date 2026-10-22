package com.respondent.pro.cabinet

private val PIN_RE = Regex("^\\d{4}$")

/** PIN валиден только как ровно 4 цифры; null/мусор не трогают сохранённое значение. */
fun normalizePinCode(value: String?): String? = value?.takeIf { PIN_RE.matches(it) }
