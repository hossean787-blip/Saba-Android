package ir.saba.invoice.domain

/** Placeholder for a real Taxpayer System integration. No network submission is implemented yet. */
enum class SubmissionStatus { DRAFT, QUEUED, SENT, ACCEPTED, REJECTED, UNKNOWN }

data class SubmissionResult(
    val invoiceId: Long,
    val status: SubmissionStatus,
    val trackingCode: String? = null,
    val message: String? = null
)
