package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class InvoiceCreationCoordinatorTest {
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val context get()=ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun database(name:String?=null)=if(name==null)Room.inMemoryDatabaseBuilder(context,SabaDatabase::class.java).build() else Room.databaseBuilder(context,SabaDatabase::class.java,name).build()
    private class Backend {
        val consumed=mutableSetOf<Pair<InvoiceCreditKind,String>>()
        val calls=mutableListOf<Pair<InvoiceCreditKind,String>>()
        var loseResponse=false
        suspend fun authorize(kind:InvoiceCreditKind,id:Long,key:String) {
            check(id>0);calls+=kind to key;consumed+=kind to key
            if(loseResponse){loseResponse=false;throw java.io.IOException("Response lost after debit")}
        }
    }
    private suspend fun seed(db:SabaDatabase):Pair<Long,Long> {
        val company=db.companyDao().insert(Company(purchaserId=account.ownerId,name="مودی",nationalId="1"))
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=company))
        val customer=db.customerDao().insert(Customer(companyId=company,name="خریدار"))
        return company to customer
    }
    private fun input(customer:Long,number:String="12")=InvoiceCreationSnapshot(customer,number,"1405/08/12",listOf(InvoiceLine(invoiceId=0,description="کالا",quantity=1,unitPriceRial=100,taxRatePercent=10)))
    private suspend fun expectRejected(action:suspend()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}}

    @Test fun lostDebitResponseSurvivesDatabaseReopenWithImmutableOriginalKeyAndKind()=runBlocking {
        val name="invoice-recovery-${UUID.randomUUID()}.db";var db=database(name)
        val backend=Backend();backend.loseResponse=true
        try {
            val (company,customer)=seed(db)
            var coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val original=coordinator.prepare(company,input(customer),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            try{coordinator.finish(original.requestId);fail("Expected ambiguous result")}catch(_:java.io.IOException){}
            assertTrue(db.invoiceDao().forCompany(company).isEmpty())
            db.close();db=database(name)
            coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val pending=coordinator.pending(company)!!
            assertEquals(original, pending)
            val retry=coordinator.prepare(company,coordinator.snapshot(pending),UUID.randomUUID().toString(),InvoiceCreditKind.GIFT)
            val id=coordinator.finish(retry.requestId)
            assertEquals(original.requestId,retry.requestId);assertEquals("PAID",retry.creditKind)
            assertEquals(1,backend.consumed.size);assertEquals(2,backend.calls.size)
            assertEquals(1,db.invoiceDao().forCompany(company).size)
            assertEquals(id,db.invoiceCreationClaimDao().byKey(original.requestId)!!.invoiceId)
            assertNull(coordinator.pending(company))
        }finally{db.close();context.deleteDatabase(name)}
    }
    @Test fun retryAfterLocalCommitDoesNotCallServerAgain()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val backend=Backend();val coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val claim=coordinator.prepare(company,input(customer),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            val id=coordinator.finish(claim.requestId)
            assertEquals(id,coordinator.finish(claim.requestId));assertEquals(1,backend.calls.size)
            InvoiceRepository(db).saveDraft(company,customer,"edited","1405/08/12",input(customer).lines,id)
            InvoiceRepository(db).finalizeDraft(company,id)
            assertEquals(1,backend.consumed.size)
        }finally{db.close()}
    }
    @Test fun pendingPayloadCannotBeChangedIntoAnotherInvoice()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val backend=Backend();val coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            coordinator.prepare(company,input(customer),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            expectRejected{coordinator.prepare(company,input(customer,"13"),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)}
            assertTrue(backend.calls.isEmpty());assertEquals("12",coordinator.snapshot(coordinator.pending(company)!!).number)
        }finally{db.close()}
    }
    @Test fun localWriteFailureRollsBackInvoiceAndRetainsRecoverableDebitKey()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val backend=Backend();val coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val claim=coordinator.prepare(company,input(customer),UUID.randomUUID().toString(),InvoiceCreditKind.GIFT)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_claim_test BEFORE INSERT ON invoice_quota_uses BEGIN SELECT RAISE(ABORT,'injected'); END")
            try{coordinator.finish(claim.requestId);fail("Expected rollback")}catch(_:RuntimeException){}
            assertTrue(db.invoiceDao().forCompany(company).isEmpty());assertNotNull(coordinator.pending(company))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_claim_test")
            coordinator.finish(claim.requestId)
            assertEquals(1,backend.consumed.size);assertEquals(1,db.invoiceDao().forCompany(company).size)
        }finally{db.close()}
    }
    @Test fun automaticNumberIsReservedOnceBeforeDebitAndReusedAfterRecovery()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val backend=Backend();val coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val first=coordinator.prepare(company,input(customer,""),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            val again=coordinator.prepare(company,input(customer,""),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            assertEquals(first,again);assertEquals("1000000001",coordinator.snapshot(first).number)
            assertEquals(1000000002L,db.moadianDetailsDao().byCompany(company)!!.nextInvoiceSerial)
            assertTrue(backend.calls.isEmpty())
        }finally{db.close()}
    }
    @Test fun foreignAccountCannotRecoverOrAuthorizeAnotherOwnersClaim()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val backend=Backend();val coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val claim=coordinator.prepare(company,input(customer),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            val other=InvoiceCreationCoordinator(db,AccountScope.of("22222222-2222-4222-8222-222222222222"),backend::authorize)
            expectRejected{other.finish(claim.requestId)};expectRejected{other.pending(company)}
            assertTrue(backend.calls.isEmpty())
        }finally{db.close()}
    }
    @Test fun activeSlotIsUniqueAndReleasedOnlyWithCommittedInvoice()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val backend=Backend();val coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val first=coordinator.prepare(company,input(customer),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            try{db.invoiceCreationClaimDao().insert(first.copy(requestId=UUID.randomUUID().toString()));fail("Expected unique active slot")}catch(_:RuntimeException){}
            coordinator.finish(first.requestId)
            val second=coordinator.prepare(company,input(customer,"13"),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            coordinator.finish(second.requestId)
            assertEquals(2,backend.consumed.size);assertEquals(2,db.invoiceDao().forCompany(company).size)
        }finally{db.close()}
    }
    @Test fun corruptedPendingPayloadIsRejectedBeforeRemoteDebit()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val backend=Backend();val coordinator=InvoiceCreationCoordinator(db,account,backend::authorize)
            val claim=coordinator.prepare(company,input(customer),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            db.openHelper.writableDatabase.execSQL("UPDATE invoice_creation_claims SET payloadJson='{}' WHERE requestId=?",arrayOf(claim.requestId))
            try{coordinator.finish(claim.requestId);fail("Expected corrupt snapshot rejection")}catch(_:Exception){}
            assertTrue("Malformed persisted invoice must not debit",backend.calls.isEmpty())
            assertTrue(db.invoiceDao().forCompany(company).isEmpty())
        }finally{db.close()}
    }
    @Test fun editingAnotherDraftCannotTakePendingInvoiceNumber()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val coordinator=InvoiceCreationCoordinator(db,account){_,_,_->}
            val id=InvoiceRepository(db).saveDraft(company,customer,"13","1405/08/12",input(customer).lines,serverPromoConfirmed=true)
            coordinator.prepare(company,input(customer,"12"),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            expectRejected{InvoiceRepository(db).saveDraft(company,customer,"۱۲","1405/08/12",input(customer).lines,id)}
            assertEquals("13",db.invoiceDao().byId(id)!!.invoiceNumber)
        }finally{db.close()}
    }
    @Test fun numericAliasesDoNotCreateTwoInvoiceNumbers()=runBlocking {
        val db=database();try{
            val (company,customer)=seed(db);val coordinator=InvoiceCreationCoordinator(db,account){_,_,_->}
            val claim=coordinator.prepare(company,input(customer,"00012"),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)
            coordinator.finish(claim.requestId)
            assertEquals("00012",db.invoiceDao().forCompany(company).single().invoiceNumber)
            expectRejected{coordinator.prepare(company,input(customer,"۱۲"),UUID.randomUUID().toString(),InvoiceCreditKind.PAID)}
            assertEquals(1,db.invoiceDao().forCompany(company).size)
        }finally{db.close()}
    }

}
