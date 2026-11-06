package ir.saba.invoice.domain

import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraIdentifierStore

/** Values entered into the invoice editor from the selected taxpayer's saved identifiers.
 * A missing or fractional source VAT is NEVER silently replaced by a general VAT rate.
 * Selection does not certify that a source tax code or tax rate is currently valid.
 */
data class InvoiceIdentifierPrefill(
    val code: String,
    val description: String,
    val vatRatePercent: String,
    val requiresVatReview: Boolean
) {
    companion object {
        fun from(identifier: SuraIdentifier): InvoiceIdentifierPrefill {
            val code = SuraIdentifierStore.normalize(identifier.id).replace(" ", "")
            require(code.matches(Regex("[0-9]{13,14}"))) {
                "شناسه انتخاب‌شده باید ۱۳ یا ۱۴ رقم باشد."
            }
            val description = identifier.description.trim()
            require(description.isNotEmpty()) { "شرح شناسه انتخاب‌شده خالی است." }
            val rate = SuraIdentifierStore.normalize(identifier.vat)
                .replace("٪", "").replace("%", "").trim()
            val integerRate = rate.toIntOrNull()?.takeIf { it in 0..100 }?.toString().orEmpty()
            return InvoiceIdentifierPrefill(code, description, integerRate, integerRate.isEmpty())
        }
    }
}
