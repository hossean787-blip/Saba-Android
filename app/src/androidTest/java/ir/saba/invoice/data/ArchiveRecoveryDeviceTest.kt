package ir.saba.invoice.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic on-disk database test; no user account, payment or tax service is accessed. */
@RunWith(AndroidJUnit4::class)
class ArchiveRecoveryDeviceTest {
    @Test fun committedRestoreSurvivesReopenAndResumesWithoutDuplicateRowsOrCredit()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val owner=AccountScope.of(UUID.randomUUID().toString())
        val sourceName="restore-device-"+UUID.randomUUID()+".db"
        val targetName="restore-device-"+UUID.randomUUID()+".db"
        fun open(name:String)=Room.databaseBuilder(ctx,SabaDatabase::class.java,name)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
        val checkpoint=ArchiveRestoreCheckpoint(ctx,owner)
        val password="synthetic-device-recovery-123".toCharArray()
        val source=open(sourceName)
        var target=open(targetName)
        try {
            val company=source.companyDao().insert(Company(id=7,purchaserId=owner.ownerId,name="Synthetic taxpayer",nationalId="123"))
            val buyer=source.customerDao().insert(Customer(companyId=company,name="Synthetic buyer"))
            val invoice=source.invoiceDao().insert(Invoice(companyId=company,customerId=buyer,invoiceNumber="1000000001",
                issueDate="1405/09/05",totalAmountRial=100,taxAmountRial=0,status="FINALIZED"))
            source.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=invoice,description="Synthetic item",quantity=1,unitPriceRial=100,taxRatePercent=0)))
            val blob=AccountArchive(source,owner,{},{}).export(password,ArchiveCreditSnapshot(80,0,mapOf(company to 80L),1))
            var reserved=false
            var checksAfterReserve=0
            try {
                AccountArchive(target,owner,{
                    if(reserved && ++checksAfterReserve==2) error("synthetic stop after committed merge")
                },{reserved=true},checkpoint=checkpoint).restoreWithCredit(blob,password) {
                    assertEquals(setOf(company),it)
                    ArchiveCreditSnapshot(60,0,mapOf(company to 60L),2)
                }
                fail("interruption expected")
            } catch(e:IllegalStateException) {
                assertEquals("synthetic stop after committed merge",e.message)
            }
            assertTrue(checkpoint.exists())
            assertArrayEquals(blob,checkpoint.read())
            target.close()
            target=open(targetName)
            assertEquals("Synthetic taxpayer",target.companyDao().byId(company)?.name)
            assertEquals("RESTORED",target.invoiceDao().byId(invoice)?.status)
            assertEquals("Synthetic item",target.invoiceLineDao().forInvoice(invoice).single().description)
            target.quotaDao().insertWallet(QuotaWallet(owner.ownerId,60,60))
            val resumed=AccountArchive(target,owner,{}, {},checkpoint=ArchiveRestoreCheckpoint(ctx,owner))
                .restoreWithCredit(checkpoint.read(),password) {
                    ArchiveCreditSnapshot(60,0,mapOf(company to 60L),3)
                }
            assertEquals(ArchiveCounts(0,0,0),resumed.added)
            assertEquals(60L,resumed.credit.owner)
            assertEquals(60L,target.quotaDao().wallet(owner.ownerId)?.available)
            assertEquals(1,target.companyDao().all().size)
            assertEquals(1,target.invoiceLineDao().forInvoice(invoice).size)
            assertFalse(checkpoint.exists())
        } finally {
            password.fill(' ')
            source.close();target.close()
            checkpoint.complete()
            ctx.deleteDatabase(sourceName);ctx.deleteDatabase(targetName)
        }
    }
}
