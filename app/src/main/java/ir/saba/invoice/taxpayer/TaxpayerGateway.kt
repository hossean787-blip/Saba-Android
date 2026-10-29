package ir.saba.invoice.taxpayer

/** Protocol boundary: transport and signing are deliberately supplied by independently audited implementations. */
interface TaxpayerGateway {
    suspend fun submit(document: SignedInvoiceDocument): SubmissionResult
    suspend fun query(referenceId: String): SubmissionResult
}

/** Never store signing keys or raw personal credentials in this structure. */
data class SignedInvoiceDocument(
    val taxpayerId: String,
    val payload: ByteArray,
    val signature: ByteArray
) {
    init {
        require(taxpayerId.isNotBlank())
        require(payload.isNotEmpty())
        require(signature.isNotEmpty())
    }
}

sealed class SubmissionResult {
    data class Accepted(val referenceId: String) : SubmissionResult()
    data class Rejected(val reason: String) : SubmissionResult()
    data class Unavailable(val retryable: Boolean) : SubmissionResult()
}
