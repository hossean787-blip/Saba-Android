package ir.saba.invoice.ui

import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CreditTransferJournalTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val a=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val b=AccountScope.of("22222222-2222-4222-8222-222222222222")
    private lateinit var db:SabaDatabase
    @Before fun setup()=runBlocking {
        context.deleteDatabase(a.databaseName)
        db=SabaDatabase.openAccount(context,a)
        db.companyDao().insert(Company(id=17,purchaserId=a.ownerId,name="one",nationalId="17"))
        db.companyDao().insert(Company(id=18,purchaserId=a.ownerId,name="two",nationalId="18"))
        Unit
    }
    @After fun close(){db.close();context.deleteDatabase(a.databaseName)}
    private fun journal(account:AccountScope=a,id:Long=17)=CreditTransferJournal(db,account,id)
    private fun receipt(id:String,amount:Long)=JSONObject().put("status","transferred").put("receipt",JSONObject().put("id",id).put("credits",amount))
    @Test fun lostResponseReopensWithSameImmutableRequestAndTenantScope()=runBlocking {
        val first=journal().begin(3)
        db.close();db=SabaDatabase.openAccount(context,a)
        assertEquals(first,journal().pending());assertEquals(first,journal().begin(3))
        assertNull(journal(id=18).pending())
        try{journal(b).pending();fail("Expected foreign owner rejection")}catch(_:IllegalArgumentException){}
        try{journal().begin(4);fail("Expected immutable amount")}catch(_:IllegalArgumentException){}
    }
    @Test fun receiptForAnotherRequestCannotClearPendingTransfer()=runBlocking {
        val pending=journal().begin(3)
        assertFalse(journal().complete(pending,receipt("other",3)))
        assertFalse(journal().complete(pending,receipt(pending.requestId,2)))
        assertEquals(pending,journal().pending())
        assertTrue(journal().complete(pending,receipt(pending.requestId,3)));assertNull(journal().pending())
        assertTrue(db.creditTransferClaimDao().byId(pending.requestId)!!.confirmed)
        assertEquals(pending,journal().begin(3,pending.requestId)) // stale SavedState retains original identity
        val next=journal().begin(3)
        assertNotEquals(pending.requestId,next.requestId)
    }
    @Test fun concurrentUiInstancesReserveOnlyOneOperation()=runBlocking {
        val claims=(1..8).map{async(Dispatchers.IO){journal().begin(5)}}.awaitAll()
        assertEquals(1,claims.map{it.requestId}.toSet().size)
    }
    @Test fun initialDiskWriteFailureDoesNotLeaveAnInMemoryOnlyClaim()=runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER transfer_insert_failure BEFORE INSERT ON credit_transfer_claims BEGIN SELECT RAISE(ABORT,'disk failure'); END")
        try{journal().begin(3);fail("Expected failed durable reservation")}catch(_:RuntimeException){}
        assertNull(journal().pending())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER transfer_insert_failure")
        assertEquals(journal().begin(3),journal().pending())
    }
    @Test fun failedReceiptCommitKeepsOriginalRequestAcrossDatabaseReopen()=runBlocking {
        val pending=journal().begin(3)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER transfer_confirm_failure BEFORE UPDATE ON credit_transfer_claims BEGIN SELECT RAISE(ABORT,'disk failure'); END")
        try{journal().complete(pending,receipt(pending.requestId,3));fail("Expected rollback")}catch(_:RuntimeException){}
        db.close();db=SabaDatabase.openAccount(context,a)
        assertEquals(pending,journal().pending());assertFalse(db.creditTransferClaimDao().byId(pending.requestId)!!.confirmed)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER transfer_confirm_failure")
        assertTrue(journal().complete(pending,receipt(pending.requestId,3)));assertNull(journal().pending())
    }
}
