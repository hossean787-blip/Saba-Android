package ir.saba.invoice.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyArithmeticTest {
    @Test fun computesExactLine() {
        assertEquals(120000L, MoneyArithmetic.lineTotal(3, 40000))
    }

    @Test fun sumsLines() {
        assertEquals(12L, MoneyArithmetic.sumLines(listOf(3L, 4L, 5L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeQuantity() {
        MoneyArithmetic.lineTotal(-1, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLine() {
        MoneyArithmetic.sumLines(listOf(1, -1))
    }

    @Test(expected = ArithmeticException::class)
    fun detectsMultiplicationOverflow() {
        MoneyArithmetic.lineTotal(Long.MAX_VALUE, 2)
    }

    @Test(expected = ArithmeticException::class)
    fun detectsSumOverflow() {
        MoneyArithmetic.sumLines(listOf(Long.MAX_VALUE, 1L))
    }
    @Test fun supportsZeroQuantity() {
        assertEquals(0L, MoneyArithmetic.lineTotal(0L, Long.MAX_VALUE))
    }

    @Test fun sumsEmptyInputToZero() {
        assertEquals(0L, MoneyArithmetic.sumLines(emptyList()))
    }

    @Test fun permitsMaximumRepresentableAmount() {
        assertEquals(Long.MAX_VALUE, MoneyArithmetic.lineTotal(1L, Long.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeUnitPrice() {
        MoneyArithmetic.lineTotal(1L, -1L)
    }
}
