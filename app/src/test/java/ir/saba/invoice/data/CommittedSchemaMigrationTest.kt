package ir.saba.invoice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CommittedSchemaMigrationTest {
    @Test fun actualCommittedVersionsTwoSevenEightAndNineMigrateWithoutLosingInvoiceRelations()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
        val schemas=listOf(File("schemas/ir.saba.invoice.data.SabaDatabase"),File("app/schemas/ir.saba.invoice.data.SabaDatabase")).first{it.isDirectory}
        for(version in listOf(2,7,8,9)) {
            val name=account.databaseName
            context.deleteDatabase(name)
            val schema=JSONObject(File(schemas,"$version.json").readText()).getJSONObject("database")
            context.getDatabasePath(name).parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use { sql ->
                val entities=schema.getJSONArray("entities")
                for(i in 0 until entities.length()) {
                    val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                    fun statement(value:String)=value.replace("\${TABLE_NAME}",table)
                    sql.execSQL(statement(entity.getString("createSql")))
                    val indexes=entity.getJSONArray("indices")
                    for(j in 0 until indexes.length()) sql.execSQL(statement(indexes.getJSONObject(j).getString("createSql")))
                }
                sql.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                sql.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema.getString("identityHash")))
                sql.execSQL("INSERT INTO companies VALUES(17,?,'seller','national','economic','address','phone')",arrayOf(account.ownerId))
                sql.execSQL("INSERT INTO customers VALUES(25,17,'buyer','national','economic','postal','address','phone')")
                sql.execSQL("INSERT INTO invoices VALUES(31,17,25,'00012','1405/08/12',110,10,'DRAFT',NULL)")
                sql.execSQL("INSERT INTO invoice_lines VALUES(42,31,'code','item',1,100,0,10)")
                sql.execSQL("INSERT INTO invoice_quota_uses VALUES(1,31,?,17,1)",arrayOf(account.ownerId))
                if(version>=7) sql.execSQL("INSERT INTO customer_profiles VALUES(25,17,'LEGAL',1,1,0,'mobile','fax','email','province','county','reg','birth','IR')")
                if(version>=8) {
                    val key=UUID.randomUUID().toString()
                    sql.execSQL("INSERT INTO invoice_creation_claims VALUES(?,?,17,'PAID','immutable-history',31,NULL)",arrayOf(key,account.ownerId))
                }
                sql.version=version
            }
            val upgraded=SabaDatabase.openAccount(context,account)
            try {
                val invoice=upgraded.invoiceDao().byId(31)!!
                assertEquals("00012",invoice.invoiceNumber);assertEquals(25L,invoice.customerId)
                assertEquals(42L,upgraded.invoiceLineDao().forInvoice(31).single().id)
                assertEquals(account.ownerId,upgraded.quotaDao().useForInvoice(31)!!.purchaserId)
                if(version>=7) assertEquals("email",upgraded.customerProfileDao().byCustomer(25)!!.email)
                if(version>=8) assertEquals("immutable-history",upgraded.invoiceCreationClaimDao().forCompany(17).single().payloadJson)
                upgraded.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
                try{upgraded.companyDao().insert(Company(purchaserId=account.ownerId,name="auto",nationalId="new"));fail("Expected ID trigger on upgraded database")}catch(_:RuntimeException){}
                InvoiceRepository(upgraded).finalizeDraft(17,31)
                assertEquals("FINALIZED",upgraded.invoiceDao().byId(31)!!.status)
            }finally{upgraded.close();context.deleteDatabase(name)}
        }
    }
}
