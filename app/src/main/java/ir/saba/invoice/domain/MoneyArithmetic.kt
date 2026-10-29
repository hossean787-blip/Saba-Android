package ir.saba.invoice.domain

/**
 * Exact integer arithmetic for amounts expressed in the smallest configured unit.
 * This utility deliberately does not implement tax legislation, rounding or currency conversion.
 */
object MoneyArithmetic {
    /** Amount for a non-negative quantity and unit price. Throws on overflow. */
    fun lineTotal(quantity: Long, unitPrice: Long): Long {
        require(quantity >= 0L) { "quantity must be non-negative" }
        require(unitPrice >= 0L) { "unitPrice must be non-negative" }
        return Math.multiplyExact(quantity, unitPrice)
    }

    /** Sums non-negative line amounts. Throws on overflow. */
    fun sumLines(amounts: Iterable<Long>): Long {
        var total = 0L
        for (amount in amounts) {
            require(amount >= 0L) { "line amount must be non-negative" }
            total = Math.addExact(total, amount)
        }
        return total
    }
}
