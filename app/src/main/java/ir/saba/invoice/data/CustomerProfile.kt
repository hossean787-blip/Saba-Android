package ir.saba.invoice.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Optional customer/supplier profile. Existing customer and invoice IDs remain unchanged. */
@Entity(tableName = "customer_profiles", indices = [Index("companyId")])
data class CustomerProfile(
    @PrimaryKey val customerId: Long,
    val companyId: Long,
    val accountType: String = "NATURAL",
    val active: Boolean = true,
    val isCustomer: Boolean = true,
    val isSupplier: Boolean = false,
    val mobile: String = "",
    val fax: String = "",
    val email: String = "",
    val province: String = "",
    val county: String = "",
    val registrationNumber: String = "",
    val birthCertificateNumber: String = "",
    val nationality: String = "IR"
)
