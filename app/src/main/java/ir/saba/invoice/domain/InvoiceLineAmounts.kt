package ir.saba.invoice.domain

import ir.saba.invoice.data.InvoiceLine
import ir.saba.moadian.MoadianMeasurementUnits
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Retains exact decimal quantities and VAT rates without changing the already issued
 * integer-only invoice lines. Legacy fields remain authoritative ONLY when exact fields are blank.
 */
object InvoiceLineAmounts {
    private fun normalize(raw: String): String = InvoiceInput.digits(raw)
        .replace('٫', '.').replace('٬', ',').trim()
        .replace(",", "").replace(" ", "")

    fun quantity(value: String): BigDecimal {
        val input = normalize(value)
        require(input.matches(Regex("[0-9]+(?:\\.[0-9]{1,8})?"))) {
            "مقدار باید عدد مثبت با حداکثر ۸ رقم اعشار باشد."
        }
        val result = BigDecimal(input)
        require(result > BigDecimal.ZERO && result.precision() - result.scale() <= 15) {
            "مقدار از محدوده مجاز خارج است."
        }
        return result
    }

    fun taxPercent(value: String): BigDecimal {
        val input = normalize(value)
        require(input.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?"))) {
            "نرخ مالیات باید عدد با حداکثر ۲ رقم اعشار باشد."
        }
        val result = BigDecimal(input)
        require(result <= BigDecimal("100")) { "نرخ مالیات باید بین صفر تا صد باشد." }
        return result
    }

    fun effectiveQuantity(line: InvoiceLine): BigDecimal =
        if (line.quantityExact.isNotBlank()) quantity(line.quantityExact)
        else BigDecimal.valueOf(line.quantity)

    fun effectiveTaxPercent(line: InvoiceLine): BigDecimal =
        if (line.vatRateExact.isNotBlank()) taxPercent(line.vatRateExact)
        else BigDecimal.valueOf(line.taxRatePercent.toLong())

    fun isPrecise(line: InvoiceLine): Boolean =
        line.quantityExact.isNotBlank() || line.vatRateExact.isNotBlank()

    fun quantityText(line: InvoiceLine): String =
        effectiveQuantity(line).stripTrailingZeros().toPlainString()

    fun vatText(line: InvoiceLine): String =
        effectiveTaxPercent(line).stripTrailingZeros().toPlainString()

    fun grossRial(line: InvoiceLine): Long {
        val gross = effectiveQuantity(line).multiply(BigDecimal.valueOf(line.unitPriceRial))
        // Official line monetary fields are truncated to integer rials for precise rows.
        return gross.setScale(0, RoundingMode.DOWN).longValueExact()
    }

    fun fromUserInput(
        code: String,
        description: String,
        rawQuantity: String,
        price: Long,
        discount: Long,
        rawRate: String,
        unit: String
    ): InvoiceLine {
        val q = quantity(rawQuantity)
        val rate = taxPercent(rawRate)
        require(unit.isBlank() || MoadianMeasurementUnits.nameOf(unit) != null) {
            "کد واحد سنجش معتبر نیست."
        }
        val qIsFractional = q.stripTrailingZeros().scale() > 0
        val rateIsFractional = rate.stripTrailingZeros().scale() > 0
        val legacyQuantity = q.setScale(0, RoundingMode.CEILING).longValueExact()
        val legacyRate = rate.setScale(0, RoundingMode.DOWN).intValueExact()
        return InvoiceLine(
            invoiceId = 0, itemCode = InvoiceInput.digits(code.trim()),
            description = description.trim(), quantity = legacyQuantity,
            unitPriceRial = price, discountRial = discount, taxRatePercent = legacyRate,
            unitCode = unit,
            quantityExact = if (qIsFractional) q.stripTrailingZeros().toPlainString() else "",
            vatRateExact = if (rateIsFractional) rate.stripTrailingZeros().toPlainString() else ""
        )
    }
}
