package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test

class IntegrityObservationStateTest {
    @Test fun httpOutcomesNeverTreatRateLimitOrNetworkAsCounterfeit() {
        assertEquals(IntegrityOutcome.VERIFIED,integrityHttpOutcome(200,"verified"))
        assertEquals(IntegrityOutcome.PROOF_REJECTED,integrityHttpOutcome(422,"not_verified"))
        assertEquals(IntegrityOutcome.RATE_LIMITED,integrityHttpOutcome(429,""))
        assertEquals(IntegrityOutcome.SESSION_INVALID,integrityHttpOutcome(401,""))
        for(code in listOf(200,403,404,422,500,503)) {
            assertEquals(IntegrityOutcome.UNAVAILABLE,integrityHttpOutcome(code,""))
        }
        assertEquals(IntegrityOutcome.UNAVAILABLE,integrityHttpOutcome(503,"verified"))
    }
    @Test fun repeatedClicksReuseVerifiedResultAndDoNotExtendItsLifetime() {
        val state=IntegrityObservationState()
        val verified=state.record("session-A",IntegrityOutcome.VERIFIED,100,1000)
        assertEquals(verified.copy(reused=true),state.recent("session-A",101))
        assertEquals(verified.copy(reused=true),state.recent("session-A",30_099))
        assertNull(state.recent("session-A",30_100))
        val limited=state.record("session-A",IntegrityOutcome.RATE_LIMITED,30_101,31_000)
        assertEquals(IntegrityOutcome.RATE_LIMITED,limited.outcome)
        assertEquals(1000L,limited.lastVerifiedAt)
        val network=state.record("session-A",IntegrityOutcome.UNAVAILABLE,30_102,32_000)
        assertEquals(1000L,network.lastVerifiedAt)
        assertTrue(network.displayText().contains("وضعیت جدید هنوز مشخص نیست"))
        assertFalse(network.reused)
    }
    @Test fun oldSuccessDoesNotOverrideNewRejectedProofOrDifferentSession() {
        val state=IntegrityObservationState()
        state.record("session-A",IntegrityOutcome.VERIFIED,100,1000)
        val rejected=state.record("session-A",IntegrityOutcome.PROOF_REJECTED,40_000,41_000)
        assertNull(rejected.lastVerifiedAt)
        assertNull(state.recent("session-A",40_001))
        state.record("session-A",IntegrityOutcome.VERIFIED,50_000,51_000)
        assertNull(state.recent("session-B",50_001))
        assertNull(state.record("session-B",IntegrityOutcome.UNAVAILABLE,50_002,51_002).lastVerifiedAt)
    }
}
