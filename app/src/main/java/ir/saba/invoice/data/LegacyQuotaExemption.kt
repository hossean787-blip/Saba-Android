package ir.saba.invoice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Historical compatibility marker; not a payment or quota-use record. */
@Entity(tableName = "legacy_quota_exemptions")
data class LegacyQuotaExemption(@PrimaryKey val invoiceId: Long)
