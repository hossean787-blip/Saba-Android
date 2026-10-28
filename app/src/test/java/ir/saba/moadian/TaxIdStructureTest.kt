package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test

class TaxIdStructureTest {
    @Test fun parseShape() {
        val parts = TaxIdStructure.parse("ABC123ABCDE012345678901")
        assertEquals("ABC123", parts.memoryId)
        assertEquals("ABCDE", parts.dayHex)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidLength() { TaxIdStructure.parse("123") }
    @Test(expected = IllegalArgumentException::class)
    fun invalidHex() { TaxIdStructure.parse("ABC123ZZZZZ012345678901") }
    @Test fun preventUnknownRetry() {
        val attempt = SubmissionAttempt("req", "hash", MoadianSubmissionState.UNKNOWN)
        assertFalse(SubmissionSafety.canCreateNewAttempt(attempt))
    }
    @Test(expected = IllegalArgumentException::class)
    fun preventBlindRetry() {
        SubmissionSafety.requireNoBlindRetry(SubmissionAttempt("req", "hash", MoadianSubmissionState.SUBMITTED))
    }
}
