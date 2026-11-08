package ir.saba.invoice.data

import org.json.JSONObject

/**
 * Additional business/payment details for a locally saved invoice.
 *
 * Never interpret this document as part of the signed tax submission payload.
 * The currently approved official path supports type 1 / pattern 1, cash only.
 */
data class InvoiceLocalDetails(
    val article17Subject: String = "",
    val paymentSwitchNumber: String = "",
    val paymentMethod: String = "",
    val paymentDate: String = "",
    val paymentTrackingNumber: String = "",
    val sellerBranchCode: String = "",
    val buyerBranchCode: String = "",
    val notes: String = ""
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
            "توضیحات صورتحساب" to notes
        )
        values.forEach { (field, value) ->
            require(value.length <= if (field == "توضیحات صورتحساب") 2000 else 120) {
                "طول $field بیش از حد مجاز است."
            }
        }
        return copy(
            article17Subject = article17Subject.trim(),
            paymentSwitchNumber = paymentSwitchNumber.trim(),
            paymentMethod = paymentMethod.trim(),
            paymentDate = paymentDate.trim(),
            paymentTrackingNumber = paymentTrackingNumber.trim(),
            sellerBranchCode = sellerBranchCode.trim(),
            buyerBranchCode = buyerBranchCode.trim(),
            notes = notes.trim()
        )
    }

    fun toJson(): String {
        val v = validated()
        return JSONObject()
            .put("article17Subject", v.article17Subject)
            .put("paymentSwitchNumber", v.paymentSwitchNumber)
            .put("paymentMethod", v.paymentMethod)
            .put("paymentDate", v.paymentDate)
            .put("paymentTrackingNumber", v.paymentTrackingNumber)
            .put("sellerBranchCode", v.sellerBranchCode)
            .put("buyerBranchCode", v.buyerBranchCode)
            .put("notes", v.notes)
            .toString()
    }

    companion object {
        fun fromJson(source: String): InvoiceLocalDetails {
            if (source.isBlank()) return InvoiceLocalDetails()
            require(source.length <= 4000) { "اطلاعات تکمیلی صورتحساب بیش از حد مجاز است." }
            val json = JSONObject(source)
            return InvoiceLocalDetails(
                article17Subject = json.optString("article17Subject"),
                paymentSwitchNumber = json.optString("paymentSwitchNumber"),
                paymentMethod = json.optString("paymentMethod"),
                paymentDate = json.optString("paymentDate"),
                paymentTrackingNumber = json.optString("paymentTrackingNumber"),
                sellerBranchCode = json.optString("sellerBranchCode"),
                buyerBranchCode = json.optString("buyerBranchCode"),
                notes = json.optString("notes")
            ).validated()
        }
    }
}
