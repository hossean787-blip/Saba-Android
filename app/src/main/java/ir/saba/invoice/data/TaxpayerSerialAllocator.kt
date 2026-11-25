package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.MoadianFieldValidation

/** All subjects share one monotonic sequence; preserve historical reservation floors. */
class TaxpayerSerialAllocator(private val db:SabaDatabase) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun suggest(companyId:Long,reference:Boolean=false):String=db.withTransaction {
        nextAvailable(companyId).toString().padStart(10,'0')
    }
    @Suppress("UNUSED_PARAMETER")
    suspend fun reserve(companyId:Long,reference:Boolean=false,minimum:Long=1L):String=db.withTransaction {
        val next=nextAvailable(companyId,minimum)
        val details=requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        db.moadianDetailsDao().upsert(details.copy(nextInvoiceSerial=next+1,nextReferenceSerial=next+1))
        next.toString().padStart(10,'0')
    }
    /** Fiscal floor includes pending/unknown and rejected identities, not only accepted receipts. */
    suspend fun minimumOfficialSerial(companyId:Long):Long {
        val details=requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        require(details.fiscalMemoryId.matches(Regex("[A-Z0-9]{6}"))){"شناسه حافظه مالیاتی معتبر نیست."}
        val ids=db.officialSubmissionDao().forFiscalMemory(details.fiscalMemoryId).map{it.taxId}+
            db.rejectedSubmissionHistoryDao().forFiscalMemory(details.fiscalMemoryId).map{it.taxId}
        return (ids.maxOfOrNull{ir.saba.moadian.TaxIdGenerator.validate(it).serialHex.toLong(16)}?:0L)+1
    }
    private suspend fun nextAvailable(companyId:Long,minimum:Long=1L):Long {
        val details=requireNotNull(db.moadianDetailsDao().byCompany(companyId)){"ابتدا مشخصات مؤدی را تکمیل کنید."}
        require(!details.inactive){"مودی غیرفعال است."}
        val start=details.invoiceSerialStart.takeIf{it.isNotBlank()}?.let {
            require(MoadianFieldValidation.serial(it,"سریال")==null)
            MoadianFieldValidation.digits(it).toLong()
        } ?: 1000000001L
        val used=db.invoiceDao().forCompany(companyId).mapNotNull {
            ir.saba.invoice.domain.InvoiceInput.number(it.invoiceNumber).toLongOrNull()
        }.toMutableList()
        db.invoiceCreationClaimDao().pending(companyId)?.let {
            ir.saba.invoice.domain.InvoiceInput.number(org.json.JSONObject(it.payloadJson).getString("number")).toLongOrNull()?.let(used::add)
        }
        db.rejectedSubmissionHistoryDao().forCompany(companyId).mapNotNull {
            ir.saba.invoice.domain.InvoiceInput.number(it.invoiceNumber).toLongOrNull()
        }.forEach(used::add)
        if(details.fiscalMemoryId.matches(Regex("[A-Z0-9]{6}"))) {
            val taxIds=db.officialSubmissionDao().forFiscalMemory(details.fiscalMemoryId).map{it.taxId}+
                db.rejectedSubmissionHistoryDao().forFiscalMemory(details.fiscalMemoryId).map{it.taxId}
            taxIds.forEach { id -> used.add(ir.saba.moadian.TaxIdStructure.parse(id).serialHex.toLong(16)) }
        }
        val next=maxOf(start,details.nextInvoiceSerial?:start,details.nextReferenceSerial?:start,minimum,(used.maxOrNull()?:0L)+1)
        require(next<=9999999999L){"ظرفیت سریال ۱۰ رقمی تمام شده است."}
        return next
    }
}
