package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test

class SubmissionReconciliationTest {
    private val hash = "b".repeat(64)
    private fun record(state: MoadianSubmissionState) =
        SubmissionRecord(2, 7, "req-7", hash, state)

    @Test fun pendingDoesNotResubmit() {
        val result = SubmissionReconciliation.reconcile(record(MoadianSubmissionState.SUBMITTED), RemoteResolution.PENDING)
        assertEquals(MoadianSubmissionState.UNKNOWN, result.state)
        assertEquals("req-7", result.requestId)
    }
    @Test fun acceptedNeedsReference() {
        val result = SubmissionReconciliation.reconcile(record(MoadianSubmissionState.UNKNOWN), RemoteResolution.ACCEPTED, "ref-7")
        assertEquals(MoadianSubmissionState.ACCEPTED, result.state)
    }
    @Test(expected = IllegalArgumentException::class) fun acceptedWithoutReferenceFails() {
        SubmissionReconciliation.reconcile(record(MoadianSubmissionState.UNKNOWN), RemoteResolution.ACCEPTED)
    }
    @Test(expected = IllegalArgumentException::class) fun draftCannotReconcile() {
        SubmissionReconciliation.reconcile(record(MoadianSubmissionState.DRAFT), RemoteResolution.PENDING)
    }
}
