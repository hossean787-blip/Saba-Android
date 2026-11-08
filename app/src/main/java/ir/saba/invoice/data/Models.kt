package ir.saba.invoice.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "companies", indices = [Index(value = ["nationalId"], unique = true)])
data class Company(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaserId: String,
    val name: String,
    val nationalId: String,
    val economicId: String = "",
    val address: String = "",
    val phone: String = ""
)

@Entity(
    tableName = "customers",
    foreignKeys = [ForeignKey(entity = Company::class, parentColumns = ["id"], childColumns = ["companyId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["companyId", "id"], unique = true), Index("companyId")]
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: Long,
    val name: String,
    val nationalId: String = "",
    val economicId: String = "",
    val postalCode: String = "",
    val address: String = "",
    val phone: String = ""
)

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(entity = Company::class, parentColumns = ["id"], childColumns = ["companyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Customer::class, parentColumns = ["companyId", "id"], childColumns = ["companyId", "customerId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("companyId"), Index("customerId"), Index(value = ["companyId", "customerId"]), Index(value = ["companyId", "invoiceNumber"], unique = true)]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: Long,
    val customerId: Long,
    val invoiceNumber: String,
    val issueDate: String,
    val totalAmountRial: Long,
    val taxAmountRial: Long,
    val status: String = "DRAFT",
    val trackingCode: String? = null,
    /** Declared settlement at creation; does not represent verified bank transfer. */
    @ColumnInfo(defaultValue = "'CASH'") val settlementMode: String = "CASH",
    /** Null means legacy or current all-cash; explicit 0 is used for credit sales. */
    val cashPaidRial: Long? = null,
    /** A local correction must reference an accepted original invoice from this taxpayer. */
    @ColumnInfo(defaultValue = "'ORIGINAL'") val subject: String = "ORIGINAL",
    val referenceTaxId: String? = null,
    /** Extra local invoice/payment fields; not automatically included in tax payloads. */
    @ColumnInfo(defaultValue = "''") val localDetailsJson: String = ""
)

@Entity(
    tableName = "invoice_lines",
    foreignKeys = [ForeignKey(entity = Invoice::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("invoiceId")]
)
data class InvoiceLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemCode: String = "",
    val description: String,
    val quantity: Long,
    val unitPriceRial: Long,
    val discountRial: Long = 0,
    val taxRatePercent: Int,
    /** Empty on legacy rows; each newly issued line may have its own official unit code. */
    @ColumnInfo(defaultValue = "''") val unitCode: String = "",
    /** Empty on old rows: exact decimal quantity, not a floating point number. */
    @ColumnInfo(defaultValue = "''") val quantityExact: String = "",
    /** Empty on old rows: exact VAT percent, preserving fractional rates. */
    @ColumnInfo(defaultValue = "''") val vatRateExact: String = "",
    /** Optional item title, kept separately from the official line description. */
    @ColumnInfo(defaultValue = "''") val itemTitle: String = ""
)

/** Master purchaser's remaining invoice quota. One row per purchaser account. */
@Entity(tableName = "quota_wallets")
data class QuotaWallet(
    @androidx.room.PrimaryKey val purchaserId: String,
    val purchasedTotal: Long,
    val available: Long
)

/** Allocation belongs to one company and cannot be transferred onward by that company. */
@Entity(
    tableName = "company_quotas",
    foreignKeys = [ForeignKey(entity = Company::class, parentColumns = ["id"], childColumns = ["companyId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("purchaserId")]
)
data class CompanyQuota(
    @androidx.room.PrimaryKey val companyId: Long,
    val purchaserId: String,
    val allocatedTotal: Long,
    val available: Long
)

@Entity(tableName = "quota_transfers", indices = [Index(value = ["operationId"], unique = true), Index("purchaserId"), Index("companyId")])
data class QuotaTransfer(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: String,
    val purchaserId: String,
    val companyId: Long,
    val amount: Long,
    val transferredAtEpochMillis: Long
)

@Entity(tableName = "invoice_quota_uses", indices = [Index(value = ["invoiceId"], unique = true), Index("purchaserId"), Index("companyId")])
data class InvoiceQuotaUse(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val purchaserId: String,
    val companyId: Long,
    val usedAtEpochMillis: Long
)
