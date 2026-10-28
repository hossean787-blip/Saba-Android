package ir.saba.invoice.data

import androidx.room.*

@Entity(tableName = "invoice_quota_uses", indices = [Index("companyId"), Index("purchaserId")],
    foreignKeys = [ForeignKey(entity = Invoice::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)])
data class InvoiceQuotaUse(
    @PrimaryKey val invoiceId: Long,
    val purchaserId: String,
    val companyId: Long,
    val usedAtEpochMillis: Long
)