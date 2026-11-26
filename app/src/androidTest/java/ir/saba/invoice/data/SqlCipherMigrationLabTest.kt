package ir.saba.invoice.data

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.UUID

/** Isolated migration laboratory: synthetic files only, no production account access. */
@RunWith(AndroidJUnit4::class)
class SqlCipherMigrationLabTest {
    @Test fun exportPopulatedRoomDatabaseToEncryptedCopyAndReopen() = runBlocking {
        System.loadLibrary("sqlcipher")
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val prefix="sura_cipher_lab_"+UUID.randomUUID()
        val sourceName=prefix+"_plain.db"
        val targetName=prefix+"_cipher.db"
        val sourceFile=ctx.getDatabasePath(sourceName)
        val targetFile=ctx.getDatabasePath(targetName)
        val owner="11111111-1111-4111-8111-111111111111"
        val random=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val password=Base64.encode(random,Base64.NO_WRAP)
        random.fill(0)
        try {
            val source=Room.databaseBuilder(ctx,SabaDatabase::class.java,sourceName)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
            val customer:Long
            val invoice:Long
            try {
                source.companyDao().insert(Company(id=7,purchaserId=owner,name="Synthetic confidentiality fixture",nationalId="123"))
                customer=source.customerDao().insert(Customer(companyId=7,name="Synthetic buyer"))
                invoice=source.invoiceDao().insert(Invoice(companyId=7,customerId=customer,invoiceNumber="1000000001",
                    issueDate="1405/09/05",totalAmountRial=100,taxAmountRial=0,status="FINALIZED"))
                source.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=invoice,description="Synthetic line",quantity=1,unitPriceRial=100,taxRatePercent=0)))
                source.quotaDao().insertWallet(QuotaWallet(owner,80,60))
            } finally {source.close()}
            assertTrue(sourceFile.isFile)
            assertFalse(targetFile.exists())
            val db=SQLiteDatabase.openDatabase(sourceFile.absolutePath,ByteArray(0),null,SQLiteDatabase.OPEN_READWRITE,null)
            try {
                val version=db.query("PRAGMA user_version").use {check(it.moveToFirst());it.getInt(0)}
                check(version>0)
                db.execSQL("ATTACH DATABASE ? AS encrypted KEY ?",arrayOf(targetFile.absolutePath,String(password,Charsets.US_ASCII)))
                try {
                    db.query("SELECT sqlcipher_export('encrypted')").use {check(it.moveToFirst())}
                    db.execSQL("PRAGMA encrypted.user_version=$version")
                    db.query("PRAGMA encrypted.integrity_check").use {check(it.moveToFirst() && it.getString(0)=="ok")}
                } finally { db.execSQL("DETACH DATABASE encrypted") }
            } finally {db.close()}
            assertTrue(sourceFile.isFile)
            assertTrue(targetFile.isFile)
            val header=ByteArray(16).also { bytes -> java.io.DataInputStream(targetFile.inputStream()).use { it.readFully(bytes) } }
            assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)))
            assertFalse(String(targetFile.readBytes(),Charsets.ISO_8859_1).contains("Synthetic confidentiality fixture"))
            val encrypted=Room.databaseBuilder(ctx,SabaDatabase::class.java,targetName)
                .openHelperFactory(SupportOpenHelperFactory(password))
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
            try {
                assertEquals("Synthetic confidentiality fixture",encrypted.companyDao().byId(7)?.name)
                assertEquals("Synthetic buyer",encrypted.customerDao().byId(customer)?.name)
                assertEquals("1000000001",encrypted.invoiceDao().byId(invoice)?.invoiceNumber)
                assertEquals("FINALIZED",encrypted.invoiceDao().byId(invoice)?.status)
                assertEquals("Synthetic line",encrypted.invoiceLineDao().forInvoice(invoice).single().description)
                assertEquals(60L,encrypted.quotaDao().wallet(owner)?.available)
                encrypted.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
            } finally {encrypted.close()}
            // Do not touch the actual cipher file with a default corruption handler.
            val wrongKeyCopy=ctx.getDatabasePath(prefix+"_wrong.db")
            try {
                targetFile.copyTo(wrongKeyCopy)
                val failed=runCatching {
                    SQLiteDatabase.openDatabase(wrongKeyCopy.absolutePath,"wrong-lab-key",null,SQLiteDatabase.OPEN_READONLY,null)
                        .use {wrong -> wrong.query("SELECT count(*) FROM companies").use {it.moveToFirst()}}
                }.isFailure
                assertTrue("Wrong key must not read the encrypted database",failed)
            } finally {ctx.deleteDatabase(wrongKeyCopy.name)}
        } finally {
            password.fill(0)
            ctx.deleteDatabase(sourceName)
            ctx.deleteDatabase(targetName)
        }
    }
}
