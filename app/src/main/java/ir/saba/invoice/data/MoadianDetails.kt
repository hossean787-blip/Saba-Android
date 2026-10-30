package ir.saba.invoice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moadian_details")
data class MoadianDetails(
    @PrimaryKey val companyId: Long,
    val taxpayerType: String = "UNSPECIFIED",
    val registrationNumber: String = "",
    val invoiceSerialStart: String = "",
    val referenceSerialStart: String = "",
    val fax: String = "",
    val postalCode: String = "",
    val province: String = "",
    val county: String = "",
    val mobile: String = "",
    val logoUri: String = "",
    val certificateExpiry: String = "",
    val fiscalMemoryId: String = "",
    val keyCheckStatus: String = "UNCHECKED",
    val inactive: Boolean = false,
    val encryptedPrivateKey: String? = null,
    val nextInvoiceSerial: Long? = null,
    val nextReferenceSerial: Long? = null
)
