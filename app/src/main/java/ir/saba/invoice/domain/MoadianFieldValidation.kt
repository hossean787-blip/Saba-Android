package ir.saba.invoice.domain

/** Local format checks only; not a confirmation of tax registration or key ownership. */
object MoadianFieldValidation {
    fun postalCode(value: String): String? =
        if (value.matches(Regex("[0-9]{10}"))) null else "کدپستی باید دقیقاً ۱۰ رقم باشد."

    fun mobile(value: String): String? =
        if (value.matches(Regex("09[0-9]{9}"))) null else "شماره موبایل باید ۱۱ رقم و با ۰۹ شروع شود."

    fun serial(value: String, label: String): String? =
        if (value.isEmpty() || value.matches(Regex("[0-9]{10}"))) null
        else "$label باید دقیقاً ۱۰ رقم باشد."

    fun serialPair(primary: String, reference: String): String? =
        if (primary.isNotEmpty() && reference.isNotEmpty() && primary == reference)
            "سریال اصلی و ارجاعی باید متفاوت باشند."
        else null

    fun required(value: String, label: String): String? =
        if (value.isBlank()) "$label اجباری است." else null

    /** Intentionally avoids hard-coded A prefix pending authoritative rules verification. */
    fun fiscalMemoryId(value: String): String? =
        if (value.matches(Regex("[A-Za-z0-9]{6}"))) null
        else "شناسه یکتا باید ۶ کاراکتر لاتین یا رقم باشد."
}
