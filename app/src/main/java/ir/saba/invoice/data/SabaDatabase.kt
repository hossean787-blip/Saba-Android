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

@Dao interface LegacyQuotaExemptionDao {
    @Query("SELECT COUNT(*) FROM legacy_quota_exemptions WHERE invoiceId = :invoiceId")
    suspend fun contains(invoiceId: Long): Int
}

@Dao interface CustomerProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: CustomerProfile)
    @Query("SELECT * FROM customer_profiles WHERE companyId = :companyId")
    suspend fun forCompany(companyId: Long): List<CustomerProfile>
    @Query("SELECT * FROM customer_profiles WHERE customerId = :id LIMIT 1")
    suspend fun byCustomer(id: Long): CustomerProfile?
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

@Database(entities = [Company::class, Customer::class, Invoice::class, InvoiceLine::class, QuotaWallet::class, CompanyQuota::class, QuotaTransfer::class, InvoiceQuotaUse::class, MoadianSimulation::class, MoadianDetails::class, LegacyQuotaExemption::class, CustomerProfile::class], version = 7, exportSchema = true)
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
                    taxpayerType TEXT NOT NULL,
                    registrationNumber TEXT NOT NULL,
                    invoiceSerialStart TEXT NOT NULL,
                    referenceSerialStart TEXT NOT NULL,
                    fax TEXT NOT NULL,
                    postalCode TEXT NOT NULL,
                    province TEXT NOT NULL,
                    county TEXT NOT NULL,
                    mobile TEXT NOT NULL,
                    logoUri TEXT NOT NULL,
                    certificateExpiry TEXT NOT NULL,
                    fiscalMemoryId TEXT NOT NULL,
                    keyCheckStatus TEXT NOT NULL,
                    inactive INTEGER NOT NULL
                )""".trimIndent())
            }
        }
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moadian_details ADD COLUMN encryptedPrivateKey TEXT")
            }
        }
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE moadian_details ADD COLUMN nextInvoiceSerial INTEGER")
                db.execSQL("ALTER TABLE moadian_details ADD COLUMN nextReferenceSerial INTEGER")
            }
        }
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS legacy_quota_exemptions (invoiceId INTEGER NOT NULL PRIMARY KEY)")
                // Record pre-policy drafts explicitly; do not fabricate consumption or debit retrospectively.
                db.execSQL("INSERT INTO legacy_quota_exemptions (invoiceId) SELECT id FROM invoices WHERE status = 'DRAFT' AND id NOT IN (SELECT invoiceId FROM invoice_quota_uses)")
            }
        }
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS customer_profiles (
                    customerId INTEGER NOT NULL PRIMARY KEY, companyId INTEGER NOT NULL,
                    accountType TEXT NOT NULL, active INTEGER NOT NULL,
                    isCustomer INTEGER NOT NULL, isSupplier INTEGER NOT NULL,
                    mobile TEXT NOT NULL, fax TEXT NOT NULL, email TEXT NOT NULL,
                    province TEXT NOT NULL, county TEXT NOT NULL,
                    registrationNumber TEXT NOT NULL, birthCertificateNumber TEXT NOT NULL,
                    nationality TEXT NOT NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_profiles_companyId ON customer_profiles(companyId)")
            }
        }
        @Volatile private var INSTANCE: SabaDatabase? = null
        fun getInstance(context: Context): SabaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SabaDatabase::class.java,
                "saba_local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { INSTANCE = it }
        }
    }
    abstract fun legacyQuotaExemptionDao(): LegacyQuotaExemptionDao
    abstract fun moadianDetailsDao(): MoadianDetailsDao
    abstract fun companyDao(): CompanyDao
    abstract fun customerProfileDao(): CustomerProfileDao
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceLineDao(): InvoiceLineDao
    abstract fun quotaDao(): QuotaDao
    abstract fun moadianSimulationDao(): MoadianSimulationDao
}

