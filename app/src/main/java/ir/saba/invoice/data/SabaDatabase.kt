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
    @Insert suspend fun insert(row: LegacyQuotaExemption)
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

@Database(entities = [Company::class, Customer::class, Invoice::class, InvoiceLine::class, QuotaWallet::class, CompanyQuota::class, QuotaTransfer::class, InvoiceQuotaUse::class, MoadianSimulation::class, MoadianDetails::class, LegacyQuotaExemption::class, CustomerProfile::class, InvoiceCreationClaim::class, OfficialSubmission::class, LegacyImportReceipt::class, CreditTransferClaim::class], version = 14, exportSchema = true)
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
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS invoice_creation_claims (requestId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, companyId INTEGER NOT NULL, creditKind TEXT NOT NULL, payloadJson TEXT NOT NULL, invoiceId INTEGER, activeCompanyId INTEGER, FOREIGN KEY(companyId) REFERENCES companies(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_creation_claims_companyId ON invoice_creation_claims(companyId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_creation_claims_activeCompanyId ON invoice_creation_claims(activeCompanyId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_creation_claims_invoiceId ON invoice_creation_claims(invoiceId)")
            }
        }
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS official_submissions (requestId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, companyId INTEGER NOT NULL, invoiceId INTEGER NOT NULL, fiscalId TEXT NOT NULL, taxId TEXT NOT NULL, payload TEXT NOT NULL, payloadHash TEXT NOT NULL, preparedAt INTEGER NOT NULL, state TEXT NOT NULL, referenceNumber TEXT, lastError TEXT, signedReceipt TEXT, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_official_submissions_invoiceId ON official_submissions(invoiceId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_official_submissions_taxId ON official_submissions(taxId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS legacy_import_receipts (companyId INTEGER NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, importedAt INTEGER NOT NULL)")
            }
        }
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS credit_transfer_claims (requestId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, companyId INTEGER NOT NULL, amount INTEGER NOT NULL, confirmed INTEGER NOT NULL, activeCompanyId INTEGER, receiptJson TEXT, FOREIGN KEY(companyId) REFERENCES companies(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_credit_transfer_claims_companyId ON credit_transfer_claims(companyId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_credit_transfer_claims_activeCompanyId ON credit_transfer_claims(activeCompanyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_companyId_customerId ON invoices(companyId,customerId)")
            }
        }
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Defaults preserve the monetary identity of existing invoice lines.
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN unitCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN quantityExact TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoice_lines ADD COLUMN vatRateExact TEXT NOT NULL DEFAULT ''")
            }
        }
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe for an interrupted prior migration or older upgrade fixtures.
                // Any mismatched existing column is still rejected by Room's schema verifier.
                val existing = mutableSetOf<String>()
                db.query("PRAGMA table_info(invoices)").use { columns ->
                    while (columns.moveToNext()) existing += columns.getString(1)
                }
                if ("settlementMode" !in existing) {
                    db.execSQL("ALTER TABLE invoices ADD COLUMN settlementMode TEXT NOT NULL DEFAULT 'CASH'")
                }
                if ("cashPaidRial" !in existing) {
                    db.execSQL("ALTER TABLE invoices ADD COLUMN cashPaidRial INTEGER")
                }
            }
        }
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existing = mutableSetOf<String>()
                db.query("PRAGMA table_info(invoices)").use { cursor ->
                    while (cursor.moveToNext()) existing += cursor.getString(1)
                }
                if ("subject" !in existing) {
                    db.execSQL("ALTER TABLE invoices ADD COLUMN subject TEXT NOT NULL DEFAULT 'ORIGINAL'")
                }
                if ("referenceTaxId" !in existing) {
                    db.execSQL("ALTER TABLE invoices ADD COLUMN referenceTaxId TEXT")
                }
            }
        }
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive and idempotent: do not rewrite historical financial records.
                fun hasColumn(table: String, column: String): Boolean {
                    db.query("PRAGMA table_info($table)").use { cursor ->
                        while (cursor.moveToNext()) {
                            if (cursor.getString(1) == column) return true
                        }
                    }
                    return false
                }
                if (!hasColumn("invoices", "localDetailsJson")) {
                    db.execSQL("ALTER TABLE invoices ADD COLUMN localDetailsJson TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn("invoice_lines", "itemTitle")) {
                    db.execSQL("ALTER TABLE invoice_lines ADD COLUMN itemTitle TEXT NOT NULL DEFAULT ''")
                }
            }
        }
        /** New commercial accounts never open or automatically claim the unowned legacy database. */
        fun openAccount(context: Context, account: ir.saba.invoice.security.AccountScope): SabaDatabase =
            Room.databaseBuilder(context.applicationContext, SabaDatabase::class.java, account.databaseName)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // An account-local AUTOINCREMENT must never reuse an existing server taxpayer ID.
                        db.execSQL("CREATE TRIGGER account_taxpayer_id_required BEFORE INSERT ON companies WHEN NEW.id < 1 BEGIN SELECT RAISE(ABORT, 'Explicit globally allocated taxpayer ID required'); END")
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Financial request IDs must reach durable storage before external operations.
                        // Room disables WAL here but SQLite/OEM defaults may still choose another journal.
                        db.query("PRAGMA journal_mode=TRUNCATE").use { result ->
                            check(result.moveToFirst() && result.getString(0).equals("truncate",ignoreCase=true))
                        }
                        db.execSQL("PRAGMA synchronous=FULL")
                        db.execSQL("CREATE TRIGGER IF NOT EXISTS account_taxpayer_id_required BEFORE INSERT ON companies WHEN NEW.id < 1 BEGIN SELECT RAISE(ABORT, 'Explicit globally allocated taxpayer ID required'); END")
                        // ownerId is a canonical UUID from AccountScope; it cannot contain SQL syntax.
                        db.execSQL("CREATE TRIGGER IF NOT EXISTS account_owner_insert BEFORE INSERT ON companies WHEN NEW.purchaserId != '${account.ownerId}' BEGIN SELECT RAISE(ABORT, 'Taxpayer owner differs from account database'); END")
                        db.execSQL("CREATE TRIGGER IF NOT EXISTS account_owner_update BEFORE UPDATE ON companies WHEN NEW.purchaserId != '${account.ownerId}' OR OLD.purchaserId != '${account.ownerId}' BEGIN SELECT RAISE(ABORT, 'Taxpayer owner differs from account database'); END")
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14).build()

        @Volatile private var INSTANCE: SabaDatabase? = null
        fun getInstance(context: Context): SabaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SabaDatabase::class.java,
                "saba_local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14).build().also { INSTANCE = it }
        }
    }
    abstract fun creditTransferClaimDao(): CreditTransferClaimDao
    abstract fun officialSubmissionDao(): OfficialSubmissionDao
    abstract fun legacyImportReceiptDao(): LegacyImportReceiptDao
    abstract fun invoiceCreationClaimDao(): InvoiceCreationClaimDao
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

