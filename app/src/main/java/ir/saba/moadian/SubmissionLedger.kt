package ir.saba.moadian

/**
 * Local submission ledger rules. Persist records transactionally with the invoice.
 * No network operations are performed here.
 */
data class SubmissionRecord(
    val companyId: Long,
    val invoiceId: Long,
    val requestId: String,
    val payloadHash: String,
    val state: MoadianSubmissionState,
    val referenceNumber: String? = null
)

object SubmissionLedger {
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun validate(record: SubmissionRecord): SubmissionRecord {
        require(record.companyId > 0 && record.invoiceId > 0)
        require(record.requestId.isNotBlank())
        require(sha256Pattern.matches(record.payloadHash))
        if (record.state == MoadianSubmissionState.ACCEPTED) {
            require(!record.referenceNumber.isNullOrBlank()) {
                "Accepted invoice requires an external reference"
            }
        }
        return record
    }

    fun recordResponse(
        existing: SubmissionRecord,
        next: MoadianSubmissionState,
        reference: String? = null
    ): SubmissionRecord {
        validate(existing)
        MoadianSubmissionTransitions.advance(existing.state, next)
        val updated = existing.copy(
            state = next,
            referenceNumber = reference ?: existing.referenceNumber
        )
        return validate(updated)
    }

    /** Prevents accidental reuse of a request ID for another invoice or payload. */
    fun requireCompatible(existing: SubmissionRecord, attempt: SubmissionRecord) {
        validate(existing)
        validate(attempt)
        require(existing.requestId != attempt.requestId ||
            (existing.companyId == attempt.companyId &&
             existing.invoiceId == attempt.invoiceId &&
             existing.payloadHash == attempt.payloadHash)) {
            "Request ID already belongs to a different invoice or payload"
        }
    }
}
