package ir.saba.invoice.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaRulesTest {
    @Test fun masterTransfersOnlyAvailableQuota() {
        val initial = QuotaState(50, 50, emptyMap())
        val after = QuotaRules.transfer(initial, 1, 10)
        assertEquals(40L, after.masterAvailable)
        assertEquals(10L, after.companyAllocations[1])
        val next = QuotaRules.transfer(after, 2, 20)
        assertEquals(20L, next.masterAvailable)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverspending() {
        QuotaRules.transfer(QuotaState(50, 40, mapOf(1L to 10L)), 2, 41)
    }
    @Test fun consumptionOnlyUsesCompanyQuota() {
        val after = QuotaRules.consume(QuotaState(50, 40, mapOf(1L to 10L)), 1)
        assertEquals(9L, after.companyAllocations[1])
        assertEquals(40L, after.masterAvailable)
    }
}