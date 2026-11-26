package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class InvoiceExcelServiceTest {
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private fun db()=Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<android.content.Context>(),SabaDatabase::class.java).build()
    private suspend fun seed(db:SabaDatabase):Long {
        val id=db.companyDao().insert(Company(purchaserId=account.ownerId,name="مودی",nationalId="0012345678"))
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=id))
        return id
    }
    private fun file(seller:String="0012345678", buyer:String="0123456789", includeBuyer:Boolean=true):ByteArray {
        fun row(number:String, description:String)=listOf(number,"1405/09/05",seller,buyer,"1234567890123",description,"1627","2","1000","0","10","نقدی","")
        return InvoiceBatchWorkbook.write(listOf(
            listOf(row("1000000001","خدمت اول"),row("1000000001","خدمت دوم"),row("1000000002","خدمت سوم")),
            listOf(listOf(seller,"مودی","")),
            if(includeBuyer) listOf(listOf(buyer,"مشتری","حقیقی","","","","")) else emptyList()
        ))
    }
    @Test fun groupedInvoicesCreateCustomerOnceAndReplayWithoutDoubleDebit()=runBlocking {
        val db=db()
        try {
            val company=seed(db)
            val debits=mutableSetOf<String>()
            var calls=0
            val service=InvoiceExcelService(db,account,company,InvoiceCreationCoordinator(db,account){_,_,key->calls++;debits.add(key);Unit})
            val batch=service.parse(file())
            assertEquals(2,batch.invoices.size)
            assertEquals(4400L,batch.invoices.first().total)
            assertEquals(2,service.preview(batch).count{it.newBuyer})
            assertTrue(db.customerDao().forCompany(company).isEmpty())
            assertEquals(0,calls)
            assertEquals(2,service.register(batch,InvoiceCreditKind.PAID){_,_->})
            assertEquals(1,db.customerDao().forCompany(company).size)
            assertEquals(2,db.invoiceDao().forCompany(company).size)
            assertTrue(db.invoiceDao().forCompany(company).all{it.status=="DRAFT"})
            assertEquals(2,debits.size)
            assertEquals(0,service.register(batch,InvoiceCreditKind.PAID){_,_->})
            assertEquals(2,calls)
        }finally{db.close()}
    }
    @Test fun missingBuyerOrForeignSellerFailsBeforeAnyDebitOrWrite()=runBlocking {
        val db=db()
        try {
            val company=seed(db)
            val service=InvoiceExcelService(db,account,company,InvoiceCreationCoordinator(db,account){_,_,_->error("Debit forbidden")})
            try{service.parse(file(seller="9999999999"));fail("Foreign taxpayer accepted")}catch(_:IllegalArgumentException){}
            val batch=service.parse(file(includeBuyer=false))
            try{service.preview(batch);fail("Missing buyer accepted")}catch(_:IllegalArgumentException){}
            assertTrue(db.customerDao().forCompany(company).isEmpty())
            assertTrue(db.invoiceDao().forCompany(company).isEmpty())
        }finally{db.close()}
    }
    @Test fun lostDebitResponseResumesSameRequestWithoutDuplicatingCustomer()=runBlocking {
        val db=db()
        try {
            val company=seed(db)
            val keys=mutableSetOf<String>()
            var failOnce=true
            val coordinator=InvoiceCreationCoordinator(db,account){_,_,key->
                keys.add(key)
                if(failOnce){failOnce=false;throw java.io.IOException("lost response")}
            }
            val service=InvoiceExcelService(db,account,company,coordinator)
            val batch=service.parse(file())
            try{service.register(batch,InvoiceCreditKind.PAID){_,_->};fail("Expected interrupted debit")}catch(_:java.io.IOException){}
            assertNotNull(coordinator.pending(company))
            assertEquals(1,service.preview(batch).count{it.pending})
            assertEquals(2,service.register(batch,InvoiceCreditKind.GIFT){_,_->})
            assertEquals(2,keys.size)
            assertEquals(1,db.customerDao().forCompany(company).size)
            assertNull(coordinator.pending(company))
        }finally{db.close()}
    }
}
