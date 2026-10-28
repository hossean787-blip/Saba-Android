package ir.saba.invoice.domain

/** License rules shared by UI/server adapters. This is policy validation, not cryptographic authorization. */
enum class LicensePlan { MONTHLY, THREE_MONTHS, SIX_MONTHS, ANNUAL, FIXED_INVOICE_COUNT }

data class LicenseRequest(
    val plan: LicensePlan,
    val invoiceCount: Long? = null,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long
)

object LicensePolicy {
    fun validate(request: LicenseRequest) {
        require(request.startsAtEpochMillis >= 0) { "زمان شروع مجوز نامعتبر است." }
        require(request.endsAtEpochMillis > request.startsAtEpochMillis) { "تاریخ پایان باید بعد از تاریخ شروع باشد." }
        when (request.plan) {
            LicensePlan.FIXED_INVOICE_COUNT -> require((request.invoiceCount ?: 0L) > 0L) {
                "برای مجوز تعدادی، تعداد فاکتور باید بیشتر از صفر باشد."
            }
            LicensePlan.ANNUAL -> require(request.invoiceCount == null) {
                "مجوز با تعداد فاکتور آزاد فقط برای طرح سالانه تعریف می‌شود؛ تعداد را خالی بگذارید."
            }
            LicensePlan.MONTHLY, LicensePlan.THREE_MONTHS, LicensePlan.SIX_MONTHS -> require(request.invoiceCount == null) {
                "طرح اشتراکی تعداد فاکتور آزاد دارد و نباید تعداد ثابت دریافت کند."
            }
        }
    }

    fun isActive(request: LicenseRequest, nowEpochMillis: Long): Boolean =
        nowEpochMillis >= request.startsAtEpochMillis && nowEpochMillis < request.endsAtEpochMillis
}
