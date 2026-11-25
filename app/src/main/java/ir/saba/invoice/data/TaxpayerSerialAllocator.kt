package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.MoadianFieldValidation
import ir.saba.invoice.domain.InvoiceInput

/** Business document counters are separate; fiscal identity is allocated at official preparation. */
class TaxpayerSerialAllocator(private val db:SabaDatabase) {
    suspend fun suggest(companyId:Long,reference:Boolean=false):String=db.withTransaction {
        nextAvailable(companyId,reference).toString().padStart(10,'0')
    }
    suspend fun reserve(companyId:Long,reference:Boolean=false,minimum:Long=1L):String=db.withTransaction {
        val details=requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        val primary=nextAvailable(companyId,false)
        val referred=nextAvailable(companyId,true)
        val next=nextAvailable(companyId,reference,minimum)
        db.moadianDetailsDao().upsert(details.copy(
            nextInvoiceSerial=if(reference) primary else next+1,
            nextReferenceSerial=if(reference) next+1 else referred))
        next.toString().padStart(10,'0')
    }
    /** Official serial is independent, including pending and rejected immutable identities. */
    suspend fun minimumOfficialSerial(companyId:Long):Long {
        val details=requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        require(details.fiscalMemoryId.matches(Regex("[A-Z0-9]{6}"))){"شناسه حافظه مالیاتی معتبر نیست."}
        val ids=db.officialSubmissionDao().forFiscalMemory(details.fiscalMemoryId).map{it.taxId}+
            db.rejectedSubmissionHistoryDao().forFiscalMemory(details.fiscalMemoryId).map{it.taxId}
        return (ids.maxOfOrNull{ir.saba.moadian.TaxIdGenerator.validate(it).serialHex.toLong(16)}?:0L)+1
    }
    private suspend fun nextAvailable(companyId:Long,reference:Boolean,minimum:Long=1L):Long {
        val details=requireNotNull(db.moadianDetailsDao().byCompany(companyId)){"ابتدا مشخصات مؤدی را تکمیل کنید."}
        require(!details.inactive){"مودی غیرفعال است."}
        fun start(raw:String,default:Long):Long=raw.takeIf{it.isNotBlank()}?.let {
            require(MoadianFieldValidation.serial(it,"سریال")==null)
            MoadianFieldValidation.digits(it).toLong()
        }?:default
        val primaryStart=start(details.invoiceSerialStart,1000000001L)
        val referenceStart=start(details.referenceSerialStart,2000000001L)
        require(primaryStart<referenceStart){"شروع سریال اصلی باید کمتر از سریال ارجاعی باشد."}
        val lower=if(reference)referenceStart else primaryStart
        val upper=if(reference)9999999999L else referenceStart-1
        val used=db.invoiceDao().forCompany(companyId).mapNotNull{InvoiceInput.number(it.invoiceNumber).toLongOrNull()}.toMutableList()
        db.invoiceCreationClaimDao().pending(companyId)?.let {
            InvoiceInput.number(org.json.JSONObject(it.payloadJson).getString("number")).toLongOrNull()?.let(used::add)
        }
        db.rejectedSubmissionHistoryDao().forCompany(companyId).mapNotNull{InvoiceInput.number(it.invoiceNumber).toLongOrNull()}.forEach(used::add)
        val stored=if(reference)details.nextReferenceSerial else details.nextInvoiceSerial
        // 0.99–0.103 stored the same high-water mark twice. Keep it only in its actual range.
        val shared=details.nextInvoiceSerial!=null && details.nextInvoiceSerial==details.nextReferenceSerial
        val floor=stored?.takeIf{!shared || it in lower..(upper+1)} ?:lower
        val next=maxOf(lower,floor,minimum,(used.filter{it in lower..upper}.maxOrNull()?:0L)+1)
        require(next<=upper){"ظرفیت بازه شماره‌گذاری صورتحساب تمام شده است."}
        return next
    }
}
