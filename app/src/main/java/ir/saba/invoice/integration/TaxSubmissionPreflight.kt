package ir.saba.invoice.integration

/** Pure, offline preflight. Never transmits data or loads private keys. */
data class TaxSubmissionRequest(
    val invoiceId: Long,
    val companyId: Long,
    val fiscalMemoryId: String,
    val taxId: String,
    val payloadJson: String
)

sealed class TaxPreflightResult {
    data class Ready(val request: TaxSubmissionRequest) : TaxPreflightResult()
    data class Invalid(val errors: List<String>) : TaxPreflightResult()
}

object TaxSubmissionPreflight {
    fun check(request: TaxSubmissionRequest): TaxPreflightResult {
        val errors = mutableListOf<String>()
        if (request.invoiceId <= 0) errors += "شناسه صورتحساب نامعتبر است"
        if (request.companyId <= 0) errors += "شناسه مؤدی نامعتبر است"
        if (request.fiscalMemoryId.isBlank()) errors += "شناسه حافظه مالیاتی الزامی است"
        if (request.taxId.isBlank()) errors += "شماره منحصر‌به‌فرد مالیاتی الزامی است"
        if (request.payloadJson.isBlank()) errors += "محتوای صورتحساب خالی است"
        if (request.payloadJson.isNotBlank() && !request.payloadJson.trimStart().startsWith("{")) {
            errors += "ساختار اولیه JSON صورتحساب نامعتبر است"
        }
        return if (errors.isEmpty()) TaxPreflightResult.Ready(request) else TaxPreflightResult.Invalid(errors)
    }
}

/** Deliberately offline until an audited SDK adapter and authorized credentials exist. */
class PreflightOnlyTaxpayerGateway(
    private val loadInvoice: suspend (Long) -> TaxSubmissionRequest?
) : TaxpayerGateway {
    override suspend fun submit(invoiceId: Long): TaxpayerSubmission {
        if (invoiceId <= 0) return TaxpayerSubmission(TaxpayerState.REJECTED, message = "شناسه نامعتبر")
        val invoice = loadInvoice(invoiceId)
            ?: return TaxpayerSubmission(TaxpayerState.REJECTED, message = "صورتحساب یافت نشد")
        return when (val result = TaxSubmissionPreflight.check(invoice)) {
            is TaxPreflightResult.Invalid -> TaxpayerSubmission(TaxpayerState.REJECTED, message = result.errors.joinToString("؛ "))
            is TaxPreflightResult.Ready -> TaxpayerSubmission(
                TaxpayerState.NOT_CONFIGURED,
                message = "اعتبارسنجی اولیه موفق بود؛ ارسال رسمی هنوز فعال نیست"
            )
        }
    }

    override suspend fun queryStatus(trackingCode: String): TaxpayerSubmission = TaxpayerSubmission(
        TaxpayerState.NOT_CONFIGURED,
        trackingCode = trackingCode.takeIf { it.isNotBlank() },
        message = "استعلام واقعی فعال نیست"
    )
}
