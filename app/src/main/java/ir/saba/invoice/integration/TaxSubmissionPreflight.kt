package ir.saba.invoice.integration

import ir.saba.moadian.TaxIdGenerator

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
        if (!request.fiscalMemoryId.matches(Regex("[A-Z0-9]{6}"))) errors += "شناسه حافظه مالیاتی معتبر نیست"
        val parts = try { TaxIdGenerator.validate(request.taxId) } catch (_: IllegalArgumentException) { null }
        if (parts == null) errors += "ساختار یا رقم کنترلی شماره مالیاتی معتبر نیست"
        else if (parts.memoryId != request.fiscalMemoryId) errors += "شماره مالیاتی متعلق به حافظه مالیاتی انتخاب‌شده نیست"
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
        if (invoice.invoiceId != invoiceId) return TaxpayerSubmission(
            TaxpayerState.REJECTED, message = "شناسه صورتحساب با درخواست مطابقت ندارد"
        )
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
