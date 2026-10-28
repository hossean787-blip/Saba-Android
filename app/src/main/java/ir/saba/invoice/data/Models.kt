package ir.saba.invoice.data

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
    indices = [Index("companyId"), Index("customerId"), Index(value = ["companyId", "invoiceNumber"], unique = true)]
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
    val trackingCode: String? = null
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
    val taxRatePercent: Int
)

@Entity(tableName = "quota_wallets")
data class QuotaWallet(
    @PrimaryKey val purchaserId: String,
    val purchasedTotal: Long,
    val available: Long
)

@Entity(
    tableName = "company_quotas",
    foreignKeys = [ForeignKey(entity = Company::class, parentColumns = ["id"], childColumns = ["companyId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("purchaserId")]
)
data class CompanyQuota(
    @PrimaryKey val companyId: Long,
    val purchaserId: String,
    val allocatedTotal: Long,
    val available: Long
)

@Entity(tableName = "quota_transfers", indices = [Index(value = ["operationId"], unique = true), Index("purchaserId"), Index("companyId")])
data class QuotaTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: String,
    val purchaserId: String,
    val companyId: Long,
    val amount: Long,
    val transferredAtEpochMillis: Long
)
