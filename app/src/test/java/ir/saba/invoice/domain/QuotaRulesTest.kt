package ir.saba.invoice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuotaRulesTest {
    @Test fun transferReducesMasterPoolAndCannotBeTransferredAgainFromMaster() {
        val initial = QuotaState(50, 50, emptyMap())
        val after = QuotaRules.transfer(initial, 1, 10)
        assertEquals(40L, after.masterAvailable)
        assertEquals(10L, after.companyAllocations[1L])
        val afterSecondCompany = QuotaRules.transfer(after, 2, 5)
        assertEquals(35L, afterSecondCompany.masterAvailable)
        assertEquals(10L, afterSecondCompany.companyAllocations[1L])
    }

    @Test fun rejectsAllocationTotalsThatExceedPurchasedQuota() {
        assertThrows(IllegalArgumentException::class.java) {
            QuotaState(10, 5, mapOf(1L to 6L))
        }
    }

    @Test fun rejectsOverflowInsteadOfWrappingQuotaTotals() {
        assertThrows(ArithmeticException::class.java) {
            QuotaState(Long.MAX_VALUE, Long.MAX_VALUE, mapOf(1L to 1L))
        }
    }

    @Test fun consumingQuotaDecreasesOnlyThatCompany() {
        val state = QuotaState(50, 40, mapOf(1L to 10L, 2L to 0L))
        val after = QuotaRules.consume(state, 1)
        assertEquals(9L, after.companyAllocations[1L])
        assertEquals(40L, after.masterAvailable)
    }
}
