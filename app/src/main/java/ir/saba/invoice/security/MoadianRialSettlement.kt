package ir.saba.invoice.security

import ir.saba.invoice.data.Invoice
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.moadian.MoadianLineTotal
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * RC_IITP.IS_V7.9, tables 24-27 and 50-51 (pp.45-48, 70-71).
 *
 * The local invoice cashPaidRial is the cash component INCLUDING VAT.
 * The government header cap is cash EXCLUDING VAT. We solve for the
 * integer-rial base C so that the sum of legally truncated item-level
 * cash shares (cop) exactly matches the declared inclusive cash amount.
 *
 * Never approximate an impossible split or silently mark a credit invoice
 * as a cash invoice. All values are nonnegative integral rials.
 */
internal data class MoadianRialSettlement(
    val method: Int,
    val cashBase: Long,
    val creditBase: Long,
    val lineCashInclusive: List<Long>,
    val linePaidVat: List<Long>
) {
    val totalPaidVat: Long get() = linePaidVat.fold(0L, Math::addExact)
}

internal object MoadianRialSettlementCalculator {
    private val ZERO = BigDecimal.ZERO

    fun calculate(invoice: Invoice, rows: List<MoadianLineTotal>): MoadianRialSettlement {
        require(rows.isNotEmpty()) { "صورتحساب باید حداقل یک قلم داشته باشد." }
        val net = rows.fold(ZERO) { sum, row -> sum.add(row.netRial) }.longValueExact()
        val vat = rows.fold(ZERO) { sum, row -> sum.add(row.vatRial) }.longValueExact()
        val total = Math.addExact(net, vat)
        require(total == invoice.totalAmountRial && vat == invoice.taxAmountRial) {
            "جمع مبالغ اقلام با صورتحساب برای تسویه مطابقت ندارد."
        }
        require(net > 0L) { "مبلغ خالص صورتحساب باید مثبت باشد." }

        val shares = InvoiceSettlement.amounts(invoice)
        val divisor = BigDecimal.valueOf(net)
        fun sharesOf(base: Long): List<Long> {
            require(base in 0..net)
            val ratio = BigDecimal.valueOf(base)
            return rows.map { row ->
                row.payableRial.multiply(ratio).divide(divisor, 0, RoundingMode.DOWN).longValueExact()
            }
        }
        fun actualCash(base: Long): Long = sharesOf(base).fold(0L, Math::addExact)

        val method = when(invoice.settlementMode) {
            InvoiceSettlement.CASH -> 1
            InvoiceSettlement.CREDIT -> 2
            InvoiceSettlement.MIXED -> 3
            else -> throw IllegalArgumentException("روش تسویه مالیاتی نامعتبر است.")
        }
        val cashBase = when (method) {
            1 -> net
            2 -> 0L
            else -> {
                // Monotone binary search avoids floating point and works for mixed VAT rates.
                var low = 1L
                var high = net - 1L
                require(high >= low) { "مبلغ فاکتور برای تسویه ترکیبی کافی نیست." }
                while (low < high) {
                    val mid = low + (high - low) / 2L
                    if (actualCash(mid) < shares.cashRial) low = mid + 1L
                    else high = mid
                }
                require(actualCash(low) == shares.cashRial) {
                    "سهم نقدی با قواعد گردکردن مالیاتی در سطح اقلام قابل نمایش نیست؛ مبلغ نقدی را اصلاح کنید."
                }
                low
            }
        }
        val lineCash = sharesOf(cashBase)
        require(lineCash.fold(0L, Math::addExact) == shares.cashRial) {
            "سهم نقدی واقعی با محاسبه مالیاتی اقلام یکسان نیست."
        }
        val lineVat = rows.map { row ->
            row.vatRial.multiply(BigDecimal.valueOf(cashBase))
                .divide(divisor, 0, RoundingMode.DOWN).longValueExact()
        }
        require(lineVat.all { it >= 0L } && lineCash.all { it >= 0L }) {
            "محاسبه سهم نقدی یا مالیات منفی مجاز نیست."
        }
        val paidVat = lineVat.fold(0L, Math::addExact)
        require(Math.addExact(cashBase, paidVat) == shares.cashRial) {
            "سهم نقدی و مالیات پرداخت‌شده با تجمیع ردیف‌ها سازگار نیست؛ صورتحساب باید بازبینی شود."
        }
        return MoadianRialSettlement(
            method = method,
            cashBase = cashBase,
            creditBase = Math.subtractExact(net, cashBase),
            lineCashInclusive = lineCash,
            linePaidVat = lineVat
        )
    }
}
