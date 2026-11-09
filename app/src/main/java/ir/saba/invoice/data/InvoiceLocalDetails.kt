package ir.saba.invoice.data

import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.JalaliCalendar
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Read-only references retained through the Android document picker. No document is sent to a server. */
data class InvoiceDocumentRef(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
) {
    fun validated(): InvoiceDocumentRef {
        require(uri.startsWith("content://") && uri.length <= 1024) {
            "نشانی سند معتبر نیست؛ فقط سند انتخاب‌شده از فایل‌های دستگاه پذیرفته می‌شود."
        }
        require(name.isNotBlank() && name.length <= 140 && name.none { it.isISOControl() }) {
            "نام سند معتبر نیست."
        }
        require(mimeType in ALLOWED_MIME_TYPES) { "نوع سند پشتیبانی نمی‌شود." }
        require(sizeBytes in 1..MAX_BYTES) { "حجم هر سند باید حداکثر ۶ مگابایت باشد." }
        return copy(name = name.trim())
    }

    companion object {
        const val MAX_BYTES = 6L * 1024L * 1024L
        const val MAX_FILES = 5
        val ALLOWED_MIME_TYPES = setOf(
            "application/pdf", "image/jpeg", "image/png", "image/gif",
            "application/zip", "application/x-zip-compressed",
            "application/vnd.rar", "application/x-rar-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }
}

/**
 * Supplementary locally stored invoice information, not part of the signed tax payload.
 * Document references point to user-selected SAF documents with persistable read grants.
 */
data class InvoiceLocalDetails(
    val article17Subject: String = "",
    val paymentSwitchNumber: String = "",
    val paymentMethod: String = "",
    val paymentDate: String = "",
    val paymentTrackingNumber: String = "",
    val sellerBranchCode: String = "",
    val buyerBranchCode: String = "",
    val notes: String = "",
    val note1: String = "",
    val note2: String = "",
    val invoiceDescription: String = "",
    val article9Flag: Boolean = false,
    val sendLaterDate: String = "",
    val sendLaterTime: String = "",
    val documents: List<InvoiceDocumentRef> = emptyList()
) {
    fun validated(): InvoiceLocalDetails {
        val values = listOf(
            "موضوع مالیات بند ۱۷" to article17Subject,
            "شماره سوئیچ پرداخت" to paymentSwitchNumber,
            "روش پرداخت" to paymentMethod,
            "تاریخ پرداخت" to paymentDate,
            "شماره پیگیری" to paymentTrackingNumber,
            "کد شعبه فروشنده" to sellerBranchCode,
            "کد شعبه خریدار" to buyerBranchCode,
            "توضیحات صورتحساب" to notes,
            "یادداشت ۱" to note1,
            "یادداشت ۲" to note2,
            "شرح تکمیلی صورتحساب" to invoiceDescription,
            "تاریخ ارسال آینده" to sendLaterDate,
            "ساعت ارسال آینده" to sendLaterTime
        )
        values.forEach { (field, value) ->
            val max = when (field) {
                "توضیحات صورتحساب" -> 2000
                "یادداشت ۱", "یادداشت ۲" -> 1200
                else -> 120
            }
            require(value.length <= max) { "طول $field بیش از حد مجاز است." }
        }
        require(documents.size <= InvoiceDocumentRef.MAX_FILES) {
            "حداکثر پنج پیوست برای یک فاکتور مجاز است."
        }
        val safeDocuments = documents.map { it.validated() }
        require(safeDocuments.map { it.uri }.distinct().size == safeDocuments.size) {
            "این سند قبلاً به فاکتور پیوست شده است."
        }
        return copy(
            article17Subject = article17Subject.trim(),
            paymentSwitchNumber = paymentSwitchNumber.trim(),
            paymentMethod = paymentMethod.trim(),
            paymentDate = paymentDate.trim(),
            paymentTrackingNumber = paymentTrackingNumber.trim(),
            sellerBranchCode = sellerBranchCode.trim(),
            buyerBranchCode = buyerBranchCode.trim(),
            notes = notes.trim(), note1 = note1.trim(), note2 = note2.trim(),
            invoiceDescription = invoiceDescription.trim(),
            sendLaterDate = sendLaterDate.trim(), sendLaterTime = sendLaterTime.trim(),
            documents = safeDocuments
        )
    }

    /** Run only on save, not during text entry, so partial date/time typing cannot crash Compose. */
    fun requireValidSchedule() {
        val date = sendLaterDate.trim()
        val time = InvoiceInput.digits(sendLaterTime.trim())
        require(date.isBlank() == time.isBlank()) {
            "برای ارسال آینده باید تاریخ و ساعت هر دو تکمیل شوند."
        }
        if (date.isNotBlank()) {
            val parsedDate = InvoiceInput.date(date)
            require(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(time)) {
                "ساعت را به شکل ۲۴ ساعته HH:mm وارد کنید."
            }
            val h = time.substring(0, 2).toInt()
            val m = time.substring(3, 5).toInt()
            require(JalaliCalendar.toGregorian(parsedDate).atTime(h, m).isAfter(LocalDateTime.now())) {
                "تاریخ و ساعت ارسال آینده باید پس از زمان فعلی باشند."
            }
        }
    }

    fun toJson(): String {
        val v = validated()
        val documentArray = JSONArray()
        v.documents.forEach { ref ->
            documentArray.put(JSONObject()
                .put("uri", ref.uri).put("name", ref.name)
                .put("mimeType", ref.mimeType).put("sizeBytes", ref.sizeBytes))
        }
        val result = JSONObject()
            .put("article17Subject", v.article17Subject)
            .put("paymentSwitchNumber", v.paymentSwitchNumber)
            .put("paymentMethod", v.paymentMethod)
            .put("paymentDate", v.paymentDate)
            .put("paymentTrackingNumber", v.paymentTrackingNumber)
            .put("sellerBranchCode", v.sellerBranchCode)
            .put("buyerBranchCode", v.buyerBranchCode)
            .put("notes", v.notes)
            .put("note1", v.note1).put("note2", v.note2)
            .put("invoiceDescription", v.invoiceDescription)
            .put("article9Flag", v.article9Flag)
            .put("sendLaterDate", v.sendLaterDate).put("sendLaterTime", v.sendLaterTime)
            .put("documents", documentArray)
            .toString()
        require(result.length <= MAX_JSON_LENGTH) { "اطلاعات تکمیلی صورتحساب بیش از حد مجاز است." }
        return result
    }

    companion object {
        private const val MAX_JSON_LENGTH = 16000

        fun fromJson(source: String): InvoiceLocalDetails {
            if (source.isBlank()) return InvoiceLocalDetails()
            require(source.length <= MAX_JSON_LENGTH) { "اطلاعات تکمیلی صورتحساب بیش از حد مجاز است." }
            val json = JSONObject(source)
            val arr = json.optJSONArray("documents")
            require((arr?.length() ?: 0) <= InvoiceDocumentRef.MAX_FILES) { "تعداد پیوست‌ها بیش از حد مجاز است." }
            val documents = (0 until (arr?.length() ?: 0)).map { idx ->
                val obj = requireNotNull(arr).getJSONObject(idx)
                InvoiceDocumentRef(
                    uri = obj.getString("uri"),
                    name = obj.getString("name"),
                    mimeType = obj.getString("mimeType"),
                    sizeBytes = obj.getLong("sizeBytes")
                )
            }
            return InvoiceLocalDetails(
                article17Subject = json.optString("article17Subject"),
                paymentSwitchNumber = json.optString("paymentSwitchNumber"),
                paymentMethod = json.optString("paymentMethod"),
                paymentDate = json.optString("paymentDate"),
                paymentTrackingNumber = json.optString("paymentTrackingNumber"),
                sellerBranchCode = json.optString("sellerBranchCode"),
                buyerBranchCode = json.optString("buyerBranchCode"),
                notes = json.optString("notes"),
                note1 = json.optString("note1"), note2 = json.optString("note2"),
                invoiceDescription = json.optString("invoiceDescription"),
                article9Flag = json.optBoolean("article9Flag", false),
                sendLaterDate = json.optString("sendLaterDate"),
                sendLaterTime = json.optString("sendLaterTime"),
                documents = documents
            ).validated()
        }
    }
}
