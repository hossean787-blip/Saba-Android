package ir.saba.moadian

/** Persistent caller must store the request ID, payload hash and server response atomically. */
data class SubmissionAttempt(
    val requestId: String,
    val payloadSha256: String,
    val state: MoadianSubmissionState
)

object SubmissionSafety {
    fun canCreateNewAttempt(previous: SubmissionAttempt?): Boolean =
        previous == null || previous.state == MoadianSubmissionState.DRAFT ||
            previous.state == MoadianSubmissionState.READY

    fun requireNoBlindRetry(previous: SubmissionAttempt?) {
        require(canCreateNewAttempt(previous)) {
            "Submission might already have reached the server: reconcile status before retry"
        }
    }
}
