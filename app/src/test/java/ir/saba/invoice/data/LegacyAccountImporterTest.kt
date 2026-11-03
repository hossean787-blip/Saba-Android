package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class LegacyAccountImporterTest {
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private lateinit var source:SabaDatabase
    private lateinit var target:SabaDatabase
    private lateinit var importer:LegacyAccountImporter
    @Before fun setup()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        source=Room.inMemoryDatabaseBuilder(context,SabaDatabase::class.java).build()
        target=Room.inMemoryDatabaseBuilder(context,SabaDatabase::class.java).build()
        importer=LegacyAccountImporter(source,target,account)
        source.companyDao().insert(Company(id=17,purchaserId=account.ownerId,name="قدیمی",nationalId="old"))
        source.moadianDetailsDao().upsert(MoadianDetails(companyId=17,fiscalMemoryId="A11216",encryptedPrivateKey="legacy-secret",keyCheckStatus="VALID",nextInvoiceSerial=14))
        source.customerDao().insert(Customer(id=25,companyId=17,name="مشتری"))
        source.customerProfileDao().upsert(CustomerProfile(customerId=25,companyId=17,email="test@example.invalid"))
        source.invoiceDao().insert(Invoice(id=31,companyId=17,customerId=25,invoiceNumber="12",issueDate="1405/08/12",totalAmountRial=110,taxAmountRial=10,status="FINALIZED"))
        source.invoiceLineDao().insertAll(listOf(InvoiceLine(id=42,invoiceId=31,description="کالا",quantity=1,unitPriceRial=100,taxRatePercent=10)))
        source.quotaDao().insertQuotaUse(InvoiceQuotaUse(id=19,invoiceId=31,purchaserId=account.ownerId,companyId=17,usedAtEpochMillis=100))
        source.quotaDao().insertWallet(QuotaWallet(account.ownerId,500,499))
        source.quotaDao().insertCompanyQuota(CompanyQuota(17,account.ownerId,20,19))
        source.invoiceCreationClaimDao().insert(InvoiceCreationClaim("old-claim",account.ownerId,17,"PAID","original",31,null))
        source.moadianSimulationDao().insert(MoadianSimulation(companyId=17,invoiceId=31,requestId="simulation",payloadHash="hash",createdAtMillis=1))
        Unit
    }
    @After fun close(){source.close();target.close()}
    private suspend fun reject(block:suspend()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    @Test fun copiesRelationsAndHistoryExactlyOnceWithoutCopyingKeysOrCreditBalances()=runBlocking {
        assertTrue(importer.importCompany(17,1000));assertFalse(importer.importCompany(17,2000))
        assertEquals(listOf(17L),target.companyDao().all().map{it.id})
        assertEquals(25L,target.invoiceDao().byId(31)!!.customerId)
        assertEquals(42L,target.invoiceLineDao().forInvoice(31).single().id)
        assertEquals("test@example.invalid",target.customerProfileDao().byCustomer(25)!!.email)
        assertEquals(source.quotaDao().useForInvoice(31),target.quotaDao().useForInvoice(31))
        assertEquals(source.invoiceCreationClaimDao().byKey("old-claim"),target.invoiceCreationClaimDao().byKey("old-claim"))
        assertEquals(1,target.moadianSimulationDao().forCompany(17).size)
        assertEquals(14L,target.moadianDetailsDao().byCompany(17)!!.nextInvoiceSerial)
        assertNull(target.moadianDetailsDao().byCompany(17)!!.encryptedPrivateKey)
        assertEquals("UNCHECKED",target.moadianDetailsDao().byCompany(17)!!.keyCheckStatus)
        assertNull(target.quotaDao().wallet(account.ownerId));assertNull(target.quotaDao().companyQuota(17))
        assertEquals(1000L,target.legacyImportReceiptDao().byCompany(17)!!.importedAt)
        assertNotNull(source.invoiceDao().byId(31));assertEquals("legacy-secret",source.moadianDetailsDao().byCompany(17)!!.encryptedPrivateKey)
    }
    @Test fun unknownAndForeignOwnersAreNeverListedOrImported()=runBlocking {
        for((id,owner) in listOf(18L to "owner",19L to "22222222-2222-4222-8222-222222222222",20L to "")) {
            source.companyDao().insert(Company(id=id,purchaserId=owner,name="خصوصی",nationalId="n$id"))
            reject{importer.importCompany(id)}
        }
        assertEquals(listOf(17L),importer.eligible().map{it.id});assertTrue(target.companyDao().all().isEmpty())
    }
    @Test fun existingTargetCompanyIsNeverOverwritten()=runBlocking {
        target.companyDao().insert(Company(id=17,purchaserId=account.ownerId,name="جدید",nationalId="new"))
        reject{importer.importCompany(17)}
        assertEquals("جدید",target.companyDao().byId(17)!!.name)
        assertNull(target.legacyImportReceiptDao().byCompany(17))
    }
    @Test fun midImportFailureRollsBackAllRowsAndRetryIsSafe()=runBlocking {
        target.openHelper.writableDatabase.execSQL("CREATE TRIGGER import_failure BEFORE INSERT ON invoice_lines BEGIN SELECT RAISE(ABORT,'injected'); END")
        try{importer.importCompany(17);fail("Expected rollback")}catch(_:RuntimeException){}
        assertTrue(target.companyDao().all().isEmpty());assertNull(target.customerDao().byId(25));assertNull(target.invoiceDao().byId(31))
        assertNull(target.legacyImportReceiptDao().byCompany(17))
        target.openHelper.writableDatabase.execSQL("DROP TRIGGER import_failure")
        assertTrue(importer.importCompany(17));assertEquals(1,target.invoiceLineDao().forInvoice(31).size)
    }
    @Test fun historicalExemptionIsPreservedWithoutInventingConsumption()=runBlocking {
        source.invoiceDao().insert(Invoice(id=32,companyId=17,customerId=25,invoiceNumber="13",issueDate="1405/08/12",totalAmountRial=110,taxAmountRial=10))
        source.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=32,description="کالا",quantity=1,unitPriceRial=100,taxRatePercent=10)))
        source.legacyQuotaExemptionDao().insert(LegacyQuotaExemption(32))
        importer.importCompany(17)
        assertEquals(1,target.legacyQuotaExemptionDao().contains(32));assertNull(target.quotaDao().useForInvoice(32))
        InvoiceRepository(target).finalizeDraft(17,32)
        assertEquals("FINALIZED",target.invoiceDao().byId(32)!!.status)
    }
    @Test fun inconsistentConsumptionOwnerPreventsWholeImport()=runBlocking {
        source.openHelper.writableDatabase.execSQL("UPDATE invoice_quota_uses SET purchaserId='owner'")
        reject{importer.importCompany(17)};assertTrue(target.companyDao().all().isEmpty())
    }
    @Test fun pendingTransferIdentityIsPreservedDuringOwnedLegacyRecovery()=runBlocking {
        val pending=CreditTransferClaim("11111111-1111-4111-8111-111111111111",account.ownerId,17,3)
        source.creditTransferClaimDao().insert(pending)
        importer.importCompany(17)
        assertEquals(pending,target.creditTransferClaimDao().pending(17))
        assertEquals(pending,source.creditTransferClaimDao().pending(17))
    }

}
