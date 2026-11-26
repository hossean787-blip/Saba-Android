package ir.saba.invoice.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.saba.invoice.BuildConfig
import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinancialStorageDeviceTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()
    private fun scope()=AccountScope.of(UUID.randomUUID().toString())
    private fun header(name:String):ByteArray=ByteArray(16).also {
        java.io.DataInputStream(ctx.getDatabasePath(name).inputStream()).use {input->input.readFully(it)}
    }
    private fun plain(name:String)=header(name).contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
    private fun hash(name:String)=MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
        .joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun cleanup(name:String) {
        ctx.deleteDatabase(name)
        val base=File(ctx.noBackupFilesDir,"financial-db-keys-v1/"+hash(name))
        for(suffix in listOf(".wrapped",".wrapped.bak",".wrapped.new",".ready",".ready.bak",".ready.new"))
            File(base.path+suffix).delete()
        KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("sura.financial.db.v1."+hash(name))}
    }
    @Test fun upgradeExistingRoomAndPreserveDataThroughInterruptedMigration()=runBlocking {
        assertFalse(BuildConfig.SURA_PLAINTEXT_JVM_FIXTURES)
        val account=scope();val name=account.databaseName
        val source=Room.databaseBuilder(ctx,SabaDatabase::class.java,name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
        val company:Long;val customer:Long;val invoice:Long
        try {
            company=source.companyDao().insert(Company(id=71,purchaserId=account.ownerId,name="مؤدی آزمون",nationalId="123"))
            source.moadianDetailsDao().upsert(MoadianDetails(companyId=company,fiscalMemoryId="A11216",encryptedPrivateKey="synthetic-encrypted-key"))
            customer=source.customerDao().insert(Customer(companyId=company,name="مشتری آزمون"))
            invoice=source.invoiceDao().insert(Invoice(companyId=company,customerId=customer,invoiceNumber="1000000001",issueDate="1405/09/05",totalAmountRial=100,taxAmountRial=0,status="FINALIZED"))
            source.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=invoice,description="کالای آزمون",quantity=1,unitPriceRial=100,taxRatePercent=0)))
            source.quotaDao().insertWallet(QuotaWallet(account.ownerId,80,60))
            source.officialSubmissionDao().insert(OfficialSubmission("request",account.ownerId,company,invoice,"A11216","taxid","{}","hash",1,state="ACCEPTED",referenceNumber="ref",signedReceipt="receipt"))
            source.openHelper.writableDatabase.execSQL("CREATE TRIGGER synthetic_preservation_trigger AFTER UPDATE ON companies BEGIN SELECT 1; END")
        } finally {source.close()}
        try {
            assertTrue(plain(name))
            try {
                FinancialStorage.migrateForDeviceTest(ctx,name){throw IOException("synthetic interruption immediately before atomic replacement")}
                fail("interruption expected")
            } catch(e:IOException){assertTrue(e.message!!.contains("synthetic interruption"))}
            assertTrue("Source remains plaintext and readable after failed attempt",plain(name))
            PlainDatabase.openDatabase(ctx.getDatabasePath(name).path,null,PlainDatabase.OPEN_READONLY).use {old ->
                old.rawQuery("SELECT name FROM companies WHERE id=71",null).use {assertTrue(it.moveToFirst());assertEquals("مؤدی آزمون",it.getString(0))}
            }
            // Simulate a staging artifact left by abrupt process death.
            File(ctx.getDatabasePath(name).path+".sura-cipher-stage").writeText("incomplete-stage")
            var target=SabaDatabase.openAccount(ctx,account)
            try {
                assertEquals("مؤدی آزمون",target.companyDao().byId(company)?.name)
                assertEquals("مشتری آزمون",target.customerDao().byId(customer)?.name)
                assertEquals("1000000001",target.invoiceDao().byId(invoice)?.invoiceNumber)
                assertEquals("FINALIZED",target.invoiceDao().byId(invoice)?.status)
                assertEquals("کالای آزمون",target.invoiceLineDao().forInvoice(invoice).single().description)
                assertEquals("synthetic-encrypted-key",target.moadianDetailsDao().byCompany(company)?.encryptedPrivateKey)
                assertEquals(60L,target.quotaDao().wallet(account.ownerId)?.available)
                assertEquals("taxid",target.officialSubmissionDao().forInvoice(invoice)?.taxId)
                target.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE name='synthetic_preservation_trigger'").use {assertTrue(it.moveToFirst())}
                target.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                assertFalse(plain(name))
                assertFalse(File(ctx.getDatabasePath(name).path+".sura-cipher-stage").exists())
            } finally {target.close()}
            target=SabaDatabase.openAccount(ctx,account)
            try {assertEquals("1000000001",target.invoiceDao().byId(invoice)?.invoiceNumber)}
            finally {target.close()}
            // Losing the wrapping key must not recreate a blank database or delete ciphertext.
            val file=ctx.getDatabasePath(name);val before=file.readBytes()
            KeyStore.getInstance("AndroidKeyStore").apply {load(null);deleteEntry("sura.financial.db.v1."+hash(name))}
            assertTrue(runCatching {
                val db=SabaDatabase.openAccount(ctx,account)
                try {db.companyDao().all()} finally {db.close()}
            }.isFailure)
            assertArrayEquals(before,file.readBytes())
        } finally {cleanup(name)}
    }
    @Test fun privateIdentifierAndAllocatorUseEncryptedFilesAndPreserveExistingValues()=runBlocking {
        val account=scope();val idsName="sura_identifier_saved_"+account.storageKey+".db"
        val seqName="sura_local_taxpayer_ids.db"
        val legacyName="saba_local.db"
        val accountName=account.databaseName
        try {
            // Independent fixture data; do not access real account data or any network service.
            ctx.openOrCreateDatabase(idsName,0,null).use {db ->
                db.execSQL("CREATE TABLE saved_identifiers(company_id INTEGER NOT NULL,id TEXT NOT NULL,description TEXT NOT NULL,vat TEXT NOT NULL,taxable TEXT NOT NULL,kind TEXT NOT NULL,expiration_date TEXT NOT NULL,source TEXT NOT NULL,saved_at INTEGER NOT NULL,PRIMARY KEY(company_id,id))")
                db.execSQL("INSERT INTO saved_identifiers VALUES(71,'1234567890123','old item','10','yes','goods','','manual',123)")
                db.version=1
            }
            ctx.openOrCreateDatabase(seqName,0,null).use {db ->
                db.execSQL("CREATE TABLE taxpayer_sequence(id INTEGER PRIMARY KEY CHECK(id=1),next_id INTEGER NOT NULL)")
                db.execSQL("INSERT INTO taxpayer_sequence VALUES(1,900)");db.version=1
            }
            val old=Room.databaseBuilder(ctx,SabaDatabase::class.java,legacyName).build()
            try {old.companyDao().insert(Company(id=800,purchaserId=account.ownerId,name="legacy fixture",nationalId="800"))} finally {old.close()}
            FinancialStorage.prepareExisting(ctx)
            assertFalse(plain(idsName));assertFalse(plain(seqName));assertFalse(plain(legacyName))
            SuraSavedIdentifierStore(ctx,account).use {store ->
                val existing=store.forTaxpayer(71).single();assertEquals("old item",existing.description)
                store.save(71,SuraIdentifier("1234567890124","new item","0","","","","manual"))
                assertEquals(2,store.forTaxpayer(71).size)
            }
            assertEquals(900L,TaxpayerIdAllocator(ctx).use{it.reserve()})
            assertEquals(901L,TaxpayerIdAllocator(ctx).use{it.reserve()})
            assertEquals(800L,FinancialStorage.maxCompanyId(ctx,legacyName))
            val db=SabaDatabase.openAccount(ctx,account)
            try {
                db.companyDao().insert(Company(id=902,purchaserId=account.ownerId,name="new account",nationalId="902"))
                assertFalse(plain(accountName))
            } finally {db.close()}
        } finally {for(name in listOf(idsName,seqName,legacyName,accountName))cleanup(name)}
    }
}
