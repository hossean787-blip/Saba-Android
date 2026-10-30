package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.MoadianFieldValidation

/** Reservations survive draft deletion and are serialized with invoice creation by Room. */
class TaxpayerSerialAllocator(private val db: SabaDatabase) {
    suspend fun reserve(companyId: Long, reference: Boolean = false): String = db.withTransaction {
        val details = db.moadianDetailsDao().byCompany(companyId)
            ?: throw IllegalArgumentException("شماره فاکتور الزامی است.")
        require(!details.inactive) { "مودی غیرفعال است." }
        val configured = if (reference) details.referenceSerialStart else details.invoiceSerialStart
        val defaultStart = if (reference) 2000000001L else 1000000001L
        val start = if (configured.isBlank()) defaultStart else {
            require(MoadianFieldValidation.serial(configured, "سریال") == null)
            MoadianFieldValidation.digits(configured).toLong()
        }
        val previous = if (reference) details.nextReferenceSerial else details.nextInvoiceSerial
        var next = maxOf(start, previous ?: start)
        // Existing numeric invoice numbers share one namespace per taxpayer.
        val used = db.invoiceDao().forCompany(companyId).mapNotNull {
            val number = MoadianFieldValidation.digits(it.invoiceNumber)
            if (number.length == 10) number.toLongOrNull() else null
        }.toSet()
        while (next in used) next++
        require(next <= 9999999999L) { "ظرفیت سریال ۱۰ رقمی تمام شده است." }
        // Do not allow the primary sequence to enter the reference reservation range.
        val otherStartText = if (reference) details.invoiceSerialStart else details.referenceSerialStart
        val otherStart = otherStartText.toLongOrNull() ?: if (reference) 1000000001L else 2000000001L
        val otherNext = (if (reference) details.nextInvoiceSerial else details.nextReferenceSerial) ?: otherStart
        require(next !in otherStart until otherNext) { "سریال با شماره‌گذاری ارجاعی یا اصلی تداخل دارد." }
        db.moadianDetailsDao().upsert(if (reference) details.copy(nextReferenceSerial = next + 1)
            else details.copy(nextInvoiceSerial = next + 1))
        next.toString().padStart(10, '0')
    }
}
