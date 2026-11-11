package ir.saba.invoice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfficialSubmissionProtocolMigrationTest {
    @Test fun realVersion14OutboxRowsKeepV2RouteReceiptAndIdentityOnUpgrade() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="sura-outbox-v14-"+UUID.randomUUID()+".db"
        var db=Room.databaseBuilder(context,SabaDatabase::class.java,name).build()
        val owner="11111111-1111-4111-8111-111111111111"
        val company=db.companyDao().insert(Company(purchaserId=owner,name="test",nationalId="1"))
        val buyer=db.customerDao().insert(Customer(companyId=company,name="buyer"))
        val invoice=db.invoiceDao().insert(Invoice(companyId=company,customerId=buyer,
            invoiceNumber="123",issueDate="1405/08/20",status="FINALIZED"))
        val original=OfficialSubmission(UUID.randomUUID().toString(),owner,company,invoice,
            "ABC123","ABC123"+"0".repeat(16),"payload","hash",123456L,
            state="SUBMITTED",referenceNumber="server-reference")
        db.officialSubmissionDao().insert(original)
        db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,
            SQLiteDatabase.OPEN_READWRITE).use { sql ->
            sql.execSQL("""CREATE TABLE official_submissions_v14 (
                requestId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL,
                companyId INTEGER NOT NULL, invoiceId INTEGER NOT NULL,
                fiscalId TEXT NOT NULL, taxId TEXT NOT NULL, payload TEXT NOT NULL,
                payloadHash TEXT NOT NULL, preparedAt INTEGER NOT NULL, state TEXT NOT NULL,
                referenceNumber TEXT, lastError TEXT, signedReceipt TEXT,
                FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""")
            sql.execSQL("""INSERT INTO official_submissions_v14
                SELECT requestId, ownerId, companyId, invoiceId, fiscalId, taxId,
                    payload, payloadHash, preparedAt, state, referenceNumber, lastError, signedReceipt
                FROM official_submissions""")
            sql.execSQL("DROP TABLE official_submissions")
            sql.execSQL("ALTER TABLE official_submissions_v14 RENAME TO official_submissions")
            sql.execSQL("CREATE UNIQUE INDEX index_official_submissions_invoiceId ON official_submissions(invoiceId)")
            sql.execSQL("CREATE UNIQUE INDEX index_official_submissions_taxId ON official_submissions(taxId)")
            sql.version=14
        }
        db=Room.databaseBuilder(context,SabaDatabase::class.java,name)
            .addMigrations(SabaDatabase.MIGRATION_14_15).build()
        try {
            val persisted=db.officialSubmissionDao().byId(original.requestId)!!
            assertEquals("V2",persisted.protocol)
            assertEquals(original.referenceNumber,persisted.referenceNumber)
            assertEquals(original.payloadHash,persisted.payloadHash)
            assertEquals("SUBMITTED",persisted.state)
            assertEquals(invoice,persisted.invoiceId)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
                assertFalse(it.moveToFirst())
            }
        } finally {db.close();context.deleteDatabase(name)}
    }
}
