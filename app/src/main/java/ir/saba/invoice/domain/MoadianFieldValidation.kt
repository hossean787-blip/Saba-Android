package ir.saba.invoice.domain

/** Local format checks only; not a confirmation of tax registration or key ownership. */
object MoadianFieldValidation {
    fun digits(value: String): String = value.trim().map {
        when (it) {
            in '۰'..'۹' -> '0' + (it - '۰')
            in '٠'..'٩' -> '0' + (it - '٠')
            else -> it
        }
    }.joinToString("")

    /** Local form conventions. Live tax registration remains an independent check. */
    fun identity(type: String, national: String, economic: String): Map<String, String> = buildMap {
        val n = digits(national)
        val e = digits(economic)
        val nationalLength = when(type) {
            "شخص حقوقی", "مشارکت مدنی" -> 11
            "شخص حقیقی" -> 14 // This app's requested field stores the economic identity.
            "اتباع غیرایرانی" -> 12
            else -> 0
        }
        val economicLength = if (type in listOf("شخص حقوقی", "مشارکت مدنی")) 11 else 14
        if (nationalLength == 0 || !n.matches(Regex("[0-9]{$nationalLength}")))
            put("nationalId", "شناسه برای این نوع مودی باید $nationalLength رقم باشد.")
        if (!e.matches(Regex("[0-9]{$economicLength}")))
            put("economicId", "شماره اقتصادی برای این نوع مودی باید $economicLength رقم باشد.")
        if (type in listOf("شخص حقوقی", "مشارکت مدنی", "شخص حقیقی") && n != e)
            put("nationalId", "شناسه این فرم باید با شماره اقتصادی واردشده یکسان باشد.")
    }

    fun postalCode(value: String): String? =
        if (digits(value).matches(Regex("[0-9]{10}"))) null else "کدپستی باید دقیقاً ۱۰ رقم باشد."

    fun mobile(value: String): String? =
        if (digits(value).matches(Regex("09[0-9]{9}"))) null else "شماره موبایل باید ۱۱ رقم و با ۰۹ شروع شود."

    fun serial(value: String, label: String): String? =
        if (value.isEmpty() || digits(value).matches(Regex("[0-9]{10}"))) null
        else "$label باید دقیقاً ۱۰ رقم باشد."

    fun serialPair(primary: String, reference: String): String? =
        if (primary.isNotEmpty() && reference.isNotEmpty() && digits(primary) == digits(reference))
            "سریال اصلی و ارجاعی باید متفاوت باشند."
        else null

    fun required(value: String, label: String): String? =
        if (value.isBlank()) "$label اجباری است." else null

    /** Intentionally avoids hard-coded A prefix pending authoritative rules verification. */
    fun fiscalMemoryId(value: String): String? =
        if (digits(value).matches(Regex("[A-Za-z0-9]{6}"))) null
        else "شناسه یکتا باید ۶ کاراکتر لاتین یا رقم باشد."
}
