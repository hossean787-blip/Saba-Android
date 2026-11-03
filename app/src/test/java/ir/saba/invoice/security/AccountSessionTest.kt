package ir.saba.invoice.security

import ir.saba.invoice.ui.ServerInvoiceCreditGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSessionTest {
    private val owner=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val other="22222222-2222-4222-8222-222222222222"
    private val now=1_800_000_000_000L
    private fun token(exp:Any?, sub:String=owner.ownerId)= "e30."+Base64.getUrlEncoder().withoutPadding().encodeToString(JSONObject().put("sub",sub).put("exp",exp ?: JSONObject.NULL).toString().toByteArray())+".synthetic"
    @Test fun acceptsOnlyUnexpiredIntegerDeadlineAndCorrectOwner() {
        val t=token((now+2000)/1000)
        assertEquals(owner,AccountSession.active(t,now).account)
        AccountSession.requireOwner(t,owner,now)
        assertThrows(IllegalArgumentException::class.java){AccountSession.requireOwner(t,AccountScope.of(other),now)}
        assertThrows(IllegalArgumentException::class.java){AccountSession.active(t,now+2000)}
        for(exp in listOf(null,"1800000002",1800000002.5,Long.MAX_VALUE,-1L))
            assertThrows(IllegalArgumentException::class.java){AccountSession.active(token(exp),now)}
    }
    @Test fun timerLocksAtDeadlineWithoutWaitingForAnotherNetworkRequest()=runTest {
        val events=mutableListOf<AccountScope?>()
        val job=launch {watchAccountSession(token((now+2000)/1000),{now+testScheduler.currentTime}){events+=it}}
        runCurrent();assertEquals(owner,events.single())
        advanceTimeBy(2000);runCurrent()
        assertNull(events.last());assertTrue(job.isCompleted)
    }
    @Test fun cancellingPreviousSessionDoesNotLockANewerSession()=runTest {
        val events=mutableListOf<AccountScope?>()
        val job=launch {watchAccountSession(token((now+2000)/1000),{now+testScheduler.currentTime}){events+=it}}
        runCurrent();job.cancelAndJoin()
        advanceTimeBy(3000);runCurrent();assertEquals(listOf(owner),events)
    }
    @Test fun creditGatewayRejectsForeignOrExpiredTokensBeforeAnyHttpCall()=runBlocking {
        val gateway=ServerInvoiceCreditGateway(owner)
        val invalid=listOf(token(System.currentTimeMillis()/1000-1),token(System.currentTimeMillis()/1000+60,other))
        for(t in invalid) {
            suspend fun rejected(action:suspend()->Unit){try{action();fail("Expected session rejection")}catch(_:IllegalArgumentException){}}
            rejected{gateway.paidBalance(t,1)};rejected{gateway.giftBalance(t)}
            rejected{gateway.consumePaid(t,1,"11111111-1111-4111-8111-111111111111")}
            rejected{gateway.consumeGift(t,"11111111-1111-4111-8111-111111111111")}
        }
    }
}
