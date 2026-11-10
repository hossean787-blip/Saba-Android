package ir.saba.invoice.data

import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.JalaliCalendar
import java.time.Instant
import java.time.ZoneId

/**
 * Local, opt-in future-send reminder. It NEVER posts invoices in the background.
 * User-initiated official submission requires confirmation and an eligible time.
 * The tax system's time zone is Tehran, independent of the handset time zone.
 */
object InvoiceSendSchedule {
    private val zone: ZoneId = ZoneId.of("Asia/Tehran")
    private val clockTime = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

    fun dueAt(details: InvoiceLocalDetails): Instant? {
        val date = details.sendLaterDate.trim()
        val time = InvoiceInput.digits(details.sendLaterTime.trim())
        require(date.isBlank() == time.isBlank()) {
            "برای ارسال آینده، تاریخ و ساعت هر دو باید تکمیل شوند."
        }
        if (date.isBlank()) return null
        require(clockTime.matches(time)) { "ساعت را به شکل ۲۴ ساعته HH:mm وارد کنید." }
        val datePart = JalaliCalendar.toGregorian(InvoiceInput.date(date))
        return datePart.atTime(time.substring(0, 2).toInt(), time.substring(3, 5).toInt())
            .atZone(zone).toInstant()
    }

    /** A new schedule must be in the future; an already stored, elapsed schedule remains readable. */
    fun requireFutureOnSave(details: InvoiceLocalDetails, now: Instant = Instant.now()) {
        val due = dueAt(details)
        require(due == null || due.isAfter(now)) {
            "تاریخ و ساعت ارسال آینده باید پس از زمان فعلی تهران باشند."
        }
    }

    fun isDue(details: InvoiceLocalDetails, now: Instant = Instant.now()): Boolean {
        return dueAt(details)?.let { !it.isAfter(now) } ?: true
    }

    fun requireDueForManualSubmission(details: InvoiceLocalDetails, now: Instant = Instant.now()) {
        require(isDue(details, now)) {
            "موعد ارسال انتخاب‌شده هنوز نرسیده است؛ این تنظیم ارسال خودکار نیست. پس از موعد با تأیید دستی ارسال کنید."
        }
    }

    fun label(details: InvoiceLocalDetails): String? {
        return dueAt(details)?.let {
            "${details.sendLaterDate.trim()}، ساعت ${InvoiceInput.digits(details.sendLaterTime.trim())} به وقت تهران"
        }
    }
}
