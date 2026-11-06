package ir.saba.invoice.domain

import ir.saba.invoice.data.Invoice

/**
 * Amounts are always in rials, including tax, and are never inferred from a
 * payment processor response. The application records a declared settlement
 * plan; it does not certify that any actual bank payment occurred.
 */
data class InvoiceSettlementAmounts(val cashRial: Long, val creditRial: Long)

object InvoiceSettlement {
    const val CASH = "CASH"
    const val CREDIT = "CREDIT"
    const val MIXED = "MIXED"

    fun label(mode: String): String = when (mode) {
        CASH -> "نقدی"
        CREDIT -> "نسیه"
        MIXED -> "نقدی و نسیه"
        else -> "نامعتبر"
    }

    fun cashFromUserInput(mode: String, totalRial: Long, text: String): Long? {
        require(totalRial > 0) { "مبلغ فاکتور باید مثبت باشد." }
        return when (mode) {
            CASH -> null // Legacy and new cash invoices both mean the entire amount is due in cash.
            CREDIT -> 0L
            MIXED -> InvoiceInput.wholeNumber(text, "مبلغ نقدی").also {
                require(it > 0 && it < totalRial) {
                    "در پرداخت ترکیبی، مبلغ نقدی باید بیشتر از صفر و کمتر از جمع فاکتور باشد."
                }
            }
            else -> throw IllegalArgumentException("روش تسویه انتخاب‌شده معتبر نیست.")
        }
    }

    fun amounts(mode: String, paidCashRial: Long?, totalRial: Long): InvoiceSettlementAmounts {
        require(totalRial > 0) { "جمع فاکتور باید مثبت باشد." }
        val cash = when (mode) {
            CASH -> {
                require(paidCashRial == null || paidCashRial == totalRial) {
                    "مبلغ نقدی صورتحساب نقدی باید برابر جمع کل باشد."
                }
                totalRial
            }
            CREDIT -> {
                require(paidCashRial == 0L) { "سهم نقدی فاکتور نسیه باید صفر باشد." }
                0L
            }
            MIXED -> {
                require(paidCashRial != null && paidCashRial > 0 && paidCashRial < totalRial) {
                    "مبلغ نقدی فاکتور ترکیبی باید بین صفر و جمع کل باشد."
                }
                paidCashRial
            }
            else -> throw IllegalArgumentException("روش تسویه ناشناخته است؛ صدور متوقف شد.")
        }
        return InvoiceSettlementAmounts(cash, Math.subtractExact(totalRial, cash))
    }

    fun amounts(invoice: Invoice): InvoiceSettlementAmounts =
        amounts(invoice.settlementMode, invoice.cashPaidRial, invoice.totalAmountRial)

    fun requireSupportedForOfficialCash(invoice: Invoice) {
        require(invoice.settlementMode == CASH) {
            "ارسال رسمی نسیه یا ترکیبی هنوز فعال نیست؛ فاکتور ذخیره می‌شود اما ارسال رسمی مجاز نیست."
        }
        val amount = amounts(invoice)
        require(amount.creditRial == 0L) { "فاکتور در مسیر نقدی تسویه کامل ندارد." }
    }
}
