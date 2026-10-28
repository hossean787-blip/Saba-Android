package ir.saba.invoice.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensePolicyTest {
    @Test fun annualPlanAllowsUnlimitedCountOnlyWhenCountIsAbsent() {
        LicensePolicy.validate(LicenseRequest(LicensePlan.ANNUAL, null, 100, 200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unlimitedCountIsRejectedForMonthlyPlan() {
        LicensePolicy.validate(LicenseRequest(LicensePlan.MONTHLY, 100, 100, 200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun fixedCountMustBePositive() {
        LicensePolicy.validate(LicenseRequest(LicensePlan.FIXED_INVOICE_COUNT, 0, 100, 200))
    }

    @Test fun licenseExpiryIsExclusive() {
        val license = LicenseRequest(LicensePlan.ANNUAL, null, 100, 200)
        assertTrue(LicensePolicy.isActive(license, 100))
        assertFalse(LicensePolicy.isActive(license, 200))
    }
}
