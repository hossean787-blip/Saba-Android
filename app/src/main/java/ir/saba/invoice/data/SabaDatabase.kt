package ir.saba.invoice.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Delete
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

@Dao interface MoadianDetailsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: MoadianDetails)
    @Query("SELECT * FROM moadian_details WHERE companyId = :companyId LIMIT 1")
    suspend fun byCompany(companyId: Long): MoadianDetails?
    @Query("SELECT * FROM moadian_details ORDER BY companyId")
    suspend fun all(): List<MoadianDetails>
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
    @Query("SELECT * FROM invoices WHERE companyId = :companyId AND invoiceNumber = :number LIMIT 1")
    suspend fun byNumber(companyId: Long, number: String): Invoice?
    @Query("DELETE FROM invoices WHERE id = :id AND companyId = :companyId AND status = 'DRAFT'")
    suspend fun deleteDraft(companyId: Long, id: Long): Int
}

@Dao interface InvoiceLineDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(items: List<InvoiceLine>): List<Long>
    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY id") suspend fun forInvoice(invoiceId: Long): List<InvoiceLine>
    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId") suspend fun deleteForInvoice(invoiceId: Long)
}

@Database(entities = [Company::class, Customer::class, Invoice::class, InvoiceLine::class, QuotaWallet::class, CompanyQuota::class, QuotaTransfer::class, InvoiceQuotaUse::class, MoadianSimulation::class, MoadianDetails::class], version = 3, exportSchema = true)
abstract class SabaDatabase : RoomDatabase() {
    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `moadian_simulations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `companyId` INTEGER NOT NULL, `invoiceId` INTEGER NOT NULL, `requestId` TEXT NOT NULL, `payloadHash` TEXT NOT NULL, `localStatus` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_moadian_simulations_companyId` ON `moadian_simulations` (`companyId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_moadian_simulations_invoiceId` ON `moadian_simulations` (`invoiceId`)")
            }
        }
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS moadian_details (
                    companyId INTEGER NOT NULL PRIMARY KEY,
                    taxpayerType TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                    registrationNumber TEXT NOT NULL DEFAULT '',
                    invoiceSerialStart TEXT NOT NULL DEFAULT '',
                    referenceSerialStart TEXT NOT NULL DEFAULT '',
                    fax TEXT NOT NULL DEFAULT '',
                    postalCode TEXT NOT NULL DEFAULT '',
                    province TEXT NOT NULL DEFAULT '',
                    county TEXT NOT NULL DEFAULT '',
                    mobile TEXT NOT NULL DEFAULT '',
                    logoUri TEXT NOT NULL DEFAULT '',
                    certificateExpiry TEXT NOT NULL DEFAULT '',
                    fiscalMemoryId TEXT NOT NULL DEFAULT '',
                    keyCheckStatus TEXT NOT NULL DEFAULT 'UNCHECKED',
                    inactive INTEGER NOT NULL DEFAULT 0
                )""".trimIndent())
            }
        }
        @Volatile private var INSTANCE: SabaDatabase? = null
        fun getInstance(context: Context): SabaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SabaDatabase::class.java,
                "saba_local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }
    abstract fun moadianDetailsDao(): MoadianDetailsDao
    abstract fun companyDao(): CompanyDao
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceLineDao(): InvoiceLineDao
    abstract fun quotaDao(): QuotaDao
    abstract fun moadianSimulationDao(): MoadianSimulationDao
}

