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

/**
 * Reference provenance is stored in the existing status message for legacy
 * receipts, not the signedReceipt column. Old or incomplete TLS responses are
 * never promoted to signed evidence and cannot unlock a referred invoice.
 */
object OfficialReferenceEvidence {
    const val LEGACY_BOUND = "پذیرش مسیر بدون گواهی با تطبیق شناسه درخواست، حافظه، شماره مالیاتی و پیگیری؛ رسید امضاشده نسخه ۲ موجود نیست."
    const val LEGACY_UNBOUND = "پذیرش از مسیر بدون گواهی گزارش شد؛ رسید امضاشده نسخه ۲ موجود نیست."

    fun eligible(row: OfficialSubmission): Boolean =
        row.state == "ACCEPTED" && !row.referenceNumber.isNullOrBlank() &&
            when (row.protocol) {
                "V2" -> !row.signedReceipt.isNullOrBlank()
                "LEGACY" -> row.signedReceipt.isNullOrBlank() && row.lastError == LEGACY_BOUND
                else -> false
            }
}

@Dao interface OfficialSubmissionDao {
    @Insert suspend fun insert(row: OfficialSubmission)
    @Query("SELECT * FROM official_submissions WHERE requestId=:id") suspend fun byId(id: String): OfficialSubmission?
    @Query("SELECT * FROM official_submissions WHERE invoiceId=:id") suspend fun forInvoice(id: Long): OfficialSubmission?
    @Query("SELECT * FROM official_submissions WHERE fiscalId=:id") suspend fun forFiscalMemory(id: String): List<OfficialSubmission>
    @Query("SELECT * FROM official_submissions WHERE companyId=:id ORDER BY preparedAt DESC") suspend fun forCompany(id: Long): List<OfficialSubmission>
    @Query("UPDATE official_submissions SET lastError=:evidence WHERE requestId=:id AND protocol='LEGACY' AND state='ACCEPTED' AND referenceNumber=:reference AND signedReceipt IS NULL")
    suspend fun confirmLegacyReference(id: String, reference: String, evidence: String): Int
    @Query("DELETE FROM official_submissions WHERE requestId=:id AND state='PREPARED'") suspend fun discardPrepared(id: String): Int
    @Query("DELETE FROM official_submissions WHERE requestId=:id AND ownerId=:ownerId AND state='REJECTED'") suspend fun archiveRejected(id: String, ownerId: String): Int
    @Query("UPDATE official_submissions SET state='SENDING', lastError=NULL WHERE requestId=:id AND state='PREPARED'")
    suspend fun claim(id: String): Int
    @Query("UPDATE official_submissions SET lastError=:message WHERE requestId=:id AND ownerId=:ownerId AND state='PREPARED'")
    suspend fun recordPreflightError(id: String, ownerId: String, message: String): Int
    @Query("UPDATE official_submissions SET state=:state, referenceNumber=COALESCE(:reference,referenceNumber), lastError=:error, signedReceipt=:receipt WHERE requestId=:id AND state IN ('SENDING','UNKNOWN','SUBMITTED')")
    suspend fun resolve(id: String, state: String, reference: String?, error: String?, receipt: String? = null): Int
}

@Entity(tableName = "legacy_import_receipts")
data class LegacyImportReceipt(@PrimaryKey val companyId: Long, val ownerId: String, val importedAt: Long)
@Dao interface LegacyImportReceiptDao {
    @Insert suspend fun insert(row: LegacyImportReceipt)
    @Query("SELECT * FROM legacy_import_receipts WHERE companyId=:id") suspend fun byCompany(id: Long): LegacyImportReceipt?
}

/** A definitively rejected attempt is retained verbatim before the same invoice is reopened. */
@Entity(tableName="rejected_submission_history", foreignKeys=[
    ForeignKey(entity=Invoice::class,parentColumns=["id"],childColumns=["invoiceId"],onDelete=ForeignKey.RESTRICT)
], indices=[Index("invoiceId"),Index("fiscalId"),Index(value=["taxId"],unique=true)])
data class RejectedSubmissionHistory(
    @PrimaryKey val requestId:String, val ownerId:String, val companyId:Long, val invoiceId:Long,
    val fiscalId:String, val taxId:String, val invoiceNumber:String, val payload:String,
    val payloadHash:String, val preparedAt:Long, val referenceNumber:String?, val lastError:String?,
    val signedReceipt:String?, val protocol:String, val archivedAt:Long
)
@Dao interface RejectedSubmissionHistoryDao {
    @Insert suspend fun insert(row:RejectedSubmissionHistory)
    @Query("SELECT * FROM rejected_submission_history WHERE invoiceId=:id ORDER BY archivedAt DESC") suspend fun forInvoice(id:Long):List<RejectedSubmissionHistory>
    @Query("SELECT * FROM rejected_submission_history WHERE fiscalId=:id") suspend fun forFiscalMemory(id:String):List<RejectedSubmissionHistory>
    @Query("SELECT * FROM rejected_submission_history WHERE companyId=:id") suspend fun forCompany(id:Long):List<RejectedSubmissionHistory>
}
