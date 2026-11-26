package ir.saba.invoice.security
import org.junit.Assert.*
import org.junit.Test
class ProtectedBalanceResultTest {
    @Test fun strictSuccessRequiresEnforcedModeAndValidInteger() {
        assertEquals(ProtectedBalanceReport(ProtectedBalanceOutcome.VERIFIED,3),protectedBalanceResult(200,"enforce",3,""))
        assertEquals(ProtectedBalanceReport(ProtectedBalanceOutcome.VERIFIED,0),protectedBalanceResult(200,"enforce",0L,""))
        for(value in listOf(null,"3",3.0,-1,Long.MAX_VALUE,true)) {
            assertEquals(ProtectedBalanceOutcome.UNAVAILABLE,protectedBalanceResult(200,"enforce",value,"").outcome)
        }
        assertEquals(ProtectedBalanceOutcome.UNAVAILABLE,protectedBalanceResult(200,"observe",3,"").outcome)
        assertEquals(ProtectedBalanceOutcome.UNAVAILABLE,protectedBalanceResult(200,"enforce",3,"error").outcome)
    }
    @Test fun rejectedOrUnavailableNeverShowsBalance() {
        for((code,error,outcome) in listOf(
            Triple(403,"integrity_rejected",ProtectedBalanceOutcome.REJECTED),
            Triple(403,"challenge_invalid_or_used",ProtectedBalanceOutcome.REJECTED),
            Triple(429,"rate_limited",ProtectedBalanceOutcome.RATE_LIMITED),
            Triple(401,"unauthorized",ProtectedBalanceOutcome.SESSION_INVALID),
            Triple(503,"integrity_unavailable",ProtectedBalanceOutcome.UNAVAILABLE)
        )) {
            val result=protectedBalanceResult(code,"enforce",3,error)
            assertEquals(outcome,result.outcome)
            assertNull(result.remaining)
        }
    }
}
