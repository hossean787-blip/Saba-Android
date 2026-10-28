package ir.saba.moadian

/**
 * Pure reconciliation of an existing request. Never creates a second submission.
 * External responses must be authenticated and mapped into these states by a separate adapter.
 */
enum class RemoteResolution { PENDING, ACCEPTED, REJECTED, INDETERMINATE }

object SubmissionReconciliation {
    fun reconcile(
        current: SubmissionRecord,
        resolution: RemoteResolution,
        reference: String? = null
    ): SubmissionRecord {
        SubmissionLedger.validate(current)
        require(current.state == MoadianSubmissionState.SUBMITTED ||
            current.state == MoadianSubmissionState.UNKNOWN) {
            "Only previously submitted/unknown requests can be reconciled"
        }
        return when (resolution) {
            RemoteResolution.PENDING, RemoteResolution.INDETERMINATE ->
                if (current.state == MoadianSubmissionState.UNKNOWN) current else
                    SubmissionLedger.recordResponse(current, MoadianSubmissionState.UNKNOWN)
            RemoteResolution.ACCEPTED ->
                SubmissionLedger.recordResponse(current, MoadianSubmissionState.ACCEPTED, reference)
            RemoteResolution.REJECTED ->
                SubmissionLedger.recordResponse(current, MoadianSubmissionState.REJECTED, reference)
        }
    }
}
