package ir.saba.invoice.integration

/** Minimal boundary for a future official Taxpayer System adapter. */
interface TaxpayerGateway {
    suspend fun submit(invoiceId: Long): TaxpayerSubmission
    suspend fun queryStatus(trackingCode: String): TaxpayerSubmission
}

enum class TaxpayerState { NOT_CONFIGURED, QUEUED, SENT, ACCEPTED, REJECTED, UNKNOWN }

data class TaxpayerSubmission(
    val state: TaxpayerState,
    val trackingCode: String? = null,
    val message: String? = null
)

/** Safe default: never pretends that an invoice was sent to the real government service. */
class UnconfiguredTaxpayerGateway : TaxpayerGateway {
    override suspend fun submit(invoiceId: Long): TaxpayerSubmission {
        require(invoiceId > 0) { "شناسه فاکتور نامعتبر است." }
        return TaxpayerSubmission(
            state = TaxpayerState.NOT_CONFIGURED,
            message = "اتصال رسمی سامانه مؤدیان هنوز پیکربندی نشده است؛ فاکتور ارسال نشد."
        )
    }

    override suspend fun queryStatus(trackingCode: String): TaxpayerSubmission {
        require(trackingCode.isNotBlank()) { "کد رهگیری الزامی است." }
        return TaxpayerSubmission(
            state = TaxpayerState.NOT_CONFIGURED,
            trackingCode = trackingCode,
            message = "استعلام واقعی درگاه هنوز پیکربندی نشده است."
        )
    }
}
