package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test

class SubmissionLedgerTest {
    private val hash = "a".repeat(64)
    private fun entry(state: MoadianSubmissionState) =
        SubmissionRecord(1, 3, "request-1", hash, state)

    @Test fun acceptsDraft() { assertEquals(3L, SubmissionLedger.validate(entry(MoadianSubmissionState.DRAFT)).invoiceId) }
    @Test(expected = IllegalArgumentException::class) fun rejectsBadHash() {
        SubmissionLedger.validate(entry(MoadianSubmissionState.DRAFT).copy(payloadHash = "xyz"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsAcceptedWithoutReference() {
        SubmissionLedger.validate(entry(MoadianSubmissionState.ACCEPTED))
    }
    @Test fun recordsAcceptedResponse() {
        val sent = entry(MoadianSubmissionState.SUBMITTED)
        val accepted = SubmissionLedger.recordResponse(sent, MoadianSubmissionState.ACCEPTED, "ref-1")
        assertEquals("ref-1", accepted.referenceNumber)
    }
    @Test(expected = IllegalArgumentException::class) fun blocksMismatchedRequestIdReuse() {
        SubmissionLedger.requireCompatible(entry(MoadianSubmissionState.DRAFT),
            entry(MoadianSubmissionState.DRAFT).copy(invoiceId = 9))
    }
    @Test(expected = IllegalArgumentException::class) fun blocksInvalidTransition() {
        SubmissionLedger.recordResponse(entry(MoadianSubmissionState.DRAFT), MoadianSubmissionState.ACCEPTED, "ref")
    }
}
