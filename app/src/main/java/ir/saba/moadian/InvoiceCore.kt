package ir.saba.moadian

import java.math.BigDecimal
import java.math.RoundingMode

/** Independent implementation; amounts are in IRR. Not an official Moadian schema. */
data class MoadianLine(
    val productId: String,
    val description: String,
    val quantity: BigDecimal,
    val unitPriceRial: BigDecimal,
    val discountRial: BigDecimal = BigDecimal.ZERO,
    val vatPercent: BigDecimal = BigDecimal.ZERO
)
data class MoadianLineTotal(
    val grossRial: BigDecimal,
    val discountRial: BigDecimal,
    val netRial: BigDecimal,
    val vatRial: BigDecimal,
    val payableRial: BigDecimal
)
data class MoadianTotals(
    val grossRial: BigDecimal,
    val discountRial: BigDecimal,
    val netRial: BigDecimal,
    val vatRial: BigDecimal,
    val payableRial: BigDecimal
)

object MoadianCalculator {
    private fun rial(n: BigDecimal) = n.setScale(0, RoundingMode.HALF_UP)
    fun calculate(line: MoadianLine): MoadianLineTotal {
        require(line.productId.isNotBlank() && line.description.isNotBlank())
        require(line.quantity > BigDecimal.ZERO)
        require(line.unitPriceRial >= BigDecimal.ZERO && line.discountRial >= BigDecimal.ZERO)
        require(line.vatPercent >= BigDecimal.ZERO && line.vatPercent <= BigDecimal("100"))
        val gross = rial(line.quantity.multiply(line.unitPriceRial))
        val discount = rial(line.discountRial)
        require(discount <= gross)
        val net = gross.subtract(discount)
        val vat = rial(net.multiply(line.vatPercent).divide(BigDecimal("100")))
        return MoadianLineTotal(gross, discount, net, vat, net.add(vat))
    }
    fun total(lines: List<MoadianLine>): MoadianTotals {
        require(lines.isNotEmpty())
        val rows = lines.map(::calculate)
        fun sum(f: (MoadianLineTotal) -> BigDecimal) =
            rows.fold(BigDecimal.ZERO) { acc, row -> acc.add(f(row)) }
        return MoadianTotals(sum { it.grossRial }, sum { it.discountRial },
            sum { it.netRial }, sum { it.vatRial }, sum { it.payableRial })
    }
}

enum class MoadianSubmissionState { DRAFT, READY, SUBMITTING, SUBMITTED, ACCEPTED, REJECTED, UNKNOWN }

object MoadianSubmissionTransitions {
    private val allowed = mapOf(
        MoadianSubmissionState.DRAFT to setOf(MoadianSubmissionState.READY),
        MoadianSubmissionState.READY to setOf(MoadianSubmissionState.SUBMITTING, MoadianSubmissionState.DRAFT),
        MoadianSubmissionState.SUBMITTING to setOf(MoadianSubmissionState.SUBMITTED, MoadianSubmissionState.UNKNOWN),
        MoadianSubmissionState.SUBMITTED to setOf(MoadianSubmissionState.ACCEPTED, MoadianSubmissionState.REJECTED, MoadianSubmissionState.UNKNOWN),
        MoadianSubmissionState.UNKNOWN to setOf(MoadianSubmissionState.SUBMITTED, MoadianSubmissionState.ACCEPTED, MoadianSubmissionState.REJECTED),
        MoadianSubmissionState.REJECTED to setOf(MoadianSubmissionState.DRAFT),
        MoadianSubmissionState.ACCEPTED to emptySet()
    )
    fun advance(from: MoadianSubmissionState, to: MoadianSubmissionState): MoadianSubmissionState {
        require(to in allowed.getValue(from)) { "Illegal state transition: $from -> $to" }
        return to
    }
}
