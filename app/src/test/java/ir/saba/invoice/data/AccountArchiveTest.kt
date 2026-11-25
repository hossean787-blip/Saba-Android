package ir.saba.invoice.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AccountArchiveTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val other=AccountScope.of("22222222-2222-4222-8222-222222222222")
    private val pass="Sura-test-only-1234".toCharArray()
    private fun db()=Room.inMemoryDatabaseBuilder(ctx,SabaDatabase::class.java).build()
    @Test fun encryptedRoundTripAndNoOverwriteOrCrossAccount()=runBlocking {
        val source=db();val dest=db();val foreign=db()
        try {
            val c=source.companyDao().insert(Company(id=7,purchaserId=account.ownerId,name="مؤدی نمونه",nationalId="123"))
            source.moadianDetailsDao().upsert(MoadianDetails(c,encryptedPrivateKey="DO-NOT-EXPORT",fiscalMemoryId="A11216"))
            val customer=source.customerDao().insert(Customer(companyId=c,name="مشتری نمونه"))
            val i=source.invoiceDao().insert(Invoice(companyId=c,customerId=customer,invoiceNumber="1",
                issueDate="1405/08/12",totalAmountRial=100,taxAmountRial=0,status="FINALIZED"))
            source.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=i,description="کالا",quantity=1,unitPriceRial=100,taxRatePercent=0)))
            source.quotaDao().insertWallet(QuotaWallet(account.ownerId,50,50))
            source.officialSubmissionDao().insert(OfficialSubmission("request",account.ownerId,c,i,"A11216","taxid","{}", "hash",1,state="ACCEPTED",referenceNumber="ref",signedReceipt="receipt"))
            val blob=AccountArchive(source,account,{},{}).export(pass)
            val plain=String(AccountArchiveCrypto.open(blob,pass),Charsets.UTF_8)
            assertFalse(plain.contains("DO-NOT-EXPORT"))
            assertFalse(plain.contains("quota_wallets"))
            assertFalse(String(blob,Charsets.UTF_8).contains("مؤدی نمونه"))
            var reserved=0L
            val archive=AccountArchive(dest,account,{},{reserved=it})
            val result=archive.restore(blob,pass)
            assertEquals(ArchiveCounts(1,1,1),result)
            assertEquals(7L,reserved)
            assertEquals("مؤدی نمونه",dest.companyDao().byId(c)?.name)
            assertTrue(dest.moadianDetailsDao().byCompany(c)!!.inactive)
            assertNull(dest.moadianDetailsDao().byCompany(c)?.encryptedPrivateKey)
            assertEquals("RESTORED",dest.invoiceDao().byId(i)?.status)
            val row=dest.officialSubmissionDao().forInvoice(i)!!
            assertEquals("RESTORED",row.state)
            assertEquals("taxid",row.taxId)
            assertFalse(OfficialReferenceEvidence.eligible(row))
            assertNull(dest.quotaDao().wallet(account.ownerId))
            // A file saved at 80 credits must never replace the live 60.
            dest.quotaDao().insertWallet(QuotaWallet(account.ownerId,80,60))
            dest.quotaDao().insertCompanyQuota(CompanyQuota(c,account.ownerId,80,60))
            val newer=dest.customerDao().insert(Customer(companyId=c,name="مشتری جدید بعد از پشتیبان"))
            val old=AccountArchive(source,account,{},{}).export(pass,
                ArchiveCreditSnapshot(80,0,mapOf(c to 80),1))
            val restored=archive.restoreWithCredit(old,pass) { ids ->
                assertEquals(setOf(c),ids)
                ArchiveCreditSnapshot(60,0,mapOf(c to 60),2)
            }
            assertEquals(ArchiveCounts(0,0,0),restored.added)
            assertEquals(60L,restored.credit.owner)
            assertEquals(60L,restored.credit.taxpayers[c])
            assertEquals(60L,dest.quotaDao().wallet(account.ownerId)?.available)
            assertEquals(60L,dest.quotaDao().companyQuota(c)?.available)
            assertNotNull(dest.customerDao().byId(newer))
            try {
                archive.restoreWithCredit(old,pass) { error("offline") }
                fail("offline restore must fail")
            } catch(_:IllegalStateException) {}
            assertEquals(60L,dest.quotaDao().wallet(account.ownerId)?.available)
            try { AccountArchive(foreign,other,{},{}).restore(blob,pass);fail("foreign account") } catch(_:IllegalArgumentException) {}
            assertTrue(foreign.companyDao().all().isEmpty())
            val corrupt=blob.copyOf().apply { this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte() }
            try { AccountArchiveCrypto.open(corrupt,pass);fail("tamper") } catch(_:java.security.GeneralSecurityException) {}
        } finally { source.close();dest.close();foreign.close() }
    }
}
