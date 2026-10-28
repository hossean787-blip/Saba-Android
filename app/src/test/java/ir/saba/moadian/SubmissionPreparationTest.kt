package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test

class SubmissionPreparationTest {
    @Test fun knownSha256() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SubmissionPreparation.sha256Hex("abc".toByteArray())
        )
    }
    @Test fun stableHashAndCopy() {
        val bytes = "invoice".toByteArray()
        val prepared = SubmissionPreparation.prepare(bytes, "request-1")
        bytes[0] = 'X'.code.toByte()
        assertEquals("request-1", prepared.requestId)
        assertTrue(SubmissionPreparation.verifyUnchanged(prepared, "invoice".toByteArray()))
        assertFalse(SubmissionPreparation.verifyUnchanged(prepared, bytes))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyPayload() { SubmissionPreparation.prepare(byteArrayOf()) }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankRequestId() { SubmissionPreparation.prepare(byteArrayOf(1), " ") }
}
