package ir.saba.invoice.data

import androidx.room.*

/** Durable immutable payload. SENDING survives process death and must be reconciled, never re-posted. */
@Entity(tableName = "official_submissions", foreignKeys = [
    ForeignKey(entity = Invoice::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["invoiceId"], unique = true), Index(value = ["taxId"], unique = true)])
data class OfficialSubmission(
    @PrimaryKey val requestId: String,
    val ownerId: String,
    val companyId: Long,
    val invoiceId: Long,
    val fiscalId: String,
    val taxId: String,
    val payload: String,
    val payloadHash: String,
    val preparedAt: Long,
    val state: String = "PREPARED",
    val referenceNumber: String? = null,
    val lastError: String? = null,
    val signedReceipt: String? = null,
    @ColumnInfo(defaultValue = "'V2'") val protocol: String = "V2"
)

@Dao interface OfficialSubmissionDao {
    @Insert suspend fun insert(row: OfficialSubmission)
    @Query("SELECT * FROM official_submissions WHERE requestId=:id") suspend fun byId(id: String): OfficialSubmission?
    @Query("SELECT * FROM official_submissions WHERE invoiceId=:id") suspend fun forInvoice(id: Long): OfficialSubmission?
    @Query("SELECT * FROM official_submissions WHERE fiscalId=:id") suspend fun forFiscalMemory(id: String): List<OfficialSubmission>
    @Query("SELECT * FROM official_submissions WHERE companyId=:id ORDER BY preparedAt DESC") suspend fun forCompany(id: Long): List<OfficialSubmission>
    @Query("DELETE FROM official_submissions WHERE requestId=:id AND state='PREPARED'") suspend fun discardPrepared(id: String): Int
    @Query("UPDATE official_submissions SET state='SENDING', lastError=NULL WHERE requestId=:id AND state='PREPARED'")
    suspend fun claim(id: String): Int
    @Query("UPDATE official_submissions SET state=:state, referenceNumber=COALESCE(:reference,referenceNumber), lastError=:error, signedReceipt=:receipt WHERE requestId=:id AND state IN ('SENDING','UNKNOWN','SUBMITTED')")
    suspend fun resolve(id: String, state: String, reference: String?, error: String?, receipt: String? = null): Int
}

@Entity(tableName = "legacy_import_receipts")
data class LegacyImportReceipt(@PrimaryKey val companyId: Long, val ownerId: String, val importedAt: Long)
@Dao interface LegacyImportReceiptDao {
    @Insert suspend fun insert(row: LegacyImportReceipt)
    @Query("SELECT * FROM legacy_import_receipts WHERE companyId=:id") suspend fun byCompany(id: Long): LegacyImportReceipt?
}
