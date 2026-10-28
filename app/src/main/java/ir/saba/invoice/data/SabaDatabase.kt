package ir.saba.invoice.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao interface CompanyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: Company): Long
    @Update suspend fun update(item: Company)
    @Query("SELECT * FROM companies ORDER BY name") suspend fun all(): List<Company>
    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1") suspend fun byId(id: Long): Company?
}

@Dao interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: Customer): Long
    @Update suspend fun update(item: Customer)
    @Query("SELECT * FROM customers WHERE companyId = :companyId ORDER BY name") suspend fun forCompany(companyId: Long): List<Customer>
    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1") suspend fun byId(id: Long): Customer?
}

@Dao interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: Invoice): Long
    @Update suspend fun update(item: Invoice)
    @Query("SELECT * FROM invoices WHERE companyId = :companyId ORDER BY issueDate DESC, id DESC") suspend fun forCompany(companyId: Long): List<Invoice>
    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1") suspend fun byId(id: Long): Invoice?
}

@Dao interface InvoiceLineDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(items: List<InvoiceLine>): List<Long>
    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY id") suspend fun forInvoice(invoiceId: Long): List<InvoiceLine>
    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId") suspend fun deleteForInvoice(invoiceId: Long)
}

@Database(entities = [Company::class, Customer::class, Invoice::class, InvoiceLine::class, QuotaWallet::class, CompanyQuota::class, QuotaTransfer::class, InvoiceQuotaUse::class, MoadianSimulation::class], version = 2, exportSchema = true)
abstract class SabaDatabase : RoomDatabase() {
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `moadian_simulations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `companyId` INTEGER NOT NULL, `invoiceId` INTEGER NOT NULL, `requestId` TEXT NOT NULL, `payloadHash` TEXT NOT NULL, `localStatus` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moadian_simulations_companyId` ON `moadian_simulations` (`companyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moadian_simulations_invoiceId` ON `moadian_simulations` (`invoiceId`)")
            }
        }
        @Volatile private var INSTANCE: SabaDatabase? = null
        fun getInstance(context: Context): SabaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SabaDatabase::class.java,
                "saba_local.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
    abstract fun companyDao(): CompanyDao
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceLineDao(): InvoiceLineDao
    abstract fun quotaDao(): QuotaDao
    abstract fun moadianSimulationDao(): MoadianSimulationDao
}
