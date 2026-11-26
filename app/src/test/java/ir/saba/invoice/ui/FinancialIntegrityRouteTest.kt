package ir.saba.invoice.ui
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
class FinancialIntegrityRouteTest {
    @Test fun ordinaryAccountPreservesLegacyRoute()=runBlocking {
        var calls=0
        assertEquals("legacy",financialIntegrityRoute(legacy={"legacy"},protected={calls++;"secure"}))
        assertEquals(0,calls)
    }
    @Test fun onlyExplicitPreMutationRefusalUsesProtectedRoute()=runBlocking {
        var calls=0
        assertEquals("secure",financialIntegrityRoute(legacy={throw CreditHttpException(403,"integrity_required")},protected={calls++;"secure"}))
        assertEquals(1,calls)
    }
    @Test fun otherFailuresNeverSwitchRoutes()=runBlocking {
        for(e in listOf(CreditHttpException(403,"forbidden"),CreditHttpException(409,"idempotency_conflict"),CreditHttpException(429,"rate_limited"),CreditHttpException(503,"integrity_unavailable"),java.net.SocketTimeoutException())) {
            var calls=0
            val result=runCatching { financialIntegrityRoute(legacy={throw e},protected={calls++;"secure"}) }
            assertSame(e,result.exceptionOrNull());assertEquals(0,calls)
        }
    }
    @Test fun protectedFailureNeverFallsBack()=runBlocking {
        var calls=0
        val failure=CreditHttpException(503,"integrity_unavailable")
        val result=runCatching {financialIntegrityRoute(legacy={calls++;throw CreditHttpException(403,"integrity_required")},protected={throw failure})}
        assertSame(failure,result.exceptionOrNull());assertEquals(1,calls)
    }
}
