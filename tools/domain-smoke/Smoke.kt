import ir.saba.invoice.data.InvoiceLine
import ir.saba.invoice.domain.*

private var passed = 0
private var failed = 0
private fun test(name: String, block: () -> Unit) {
    try { block(); passed++; println("PASS: $name") }
    catch (t: Throwable) { failed++; System.err.println("FAIL: $name: ${t.message}") }
}
private inline fun <reified T: Throwable> expectThrows(noinline block: () -> Unit) {
    try { block() } catch (t: Throwable) { if (t is T) return; throw AssertionError("Expected ${T::class}, got ${t::class}") }
    throw AssertionError("Expected ${T::class} but no exception")
}
fun main() {
    test("net/tax/total") { val x=InvoiceCalculator.calculate(listOf(InvoiceLine("service",2,1000,0,10))); check(x.netAmountRial==2000L && x.taxAmountRial==200L && x.totalAmountRial==2200L) }
    test("HALF_UP tax rounding") { val x=InvoiceCalculator.calculate(listOf(InvoiceLine("x",1,5,0,10))); check(x.taxAmountRial==1L) }
    test("multiply overflow rejected") { expectThrows<ArithmeticException> { InvoiceCalculator.calculate(listOf(InvoiceLine("x",Long.MAX_VALUE,2,0,0))) } }
    test("zero quantity rejected") { expectThrows<IllegalArgumentException> { InvoiceCalculator.calculate(listOf(InvoiceLine("x",0,1,0,0))) } }
    test("empty invoice rejected") { expectThrows<IllegalArgumentException> { InvoiceCalculator.calculate(emptyList()) } }
    test("blank description rejected") { expectThrows<IllegalArgumentException> { InvoiceCalculator.calculate(listOf(InvoiceLine(" ",1,1,0,0))) } }
    test("negative price rejected") { expectThrows<IllegalArgumentException> { InvoiceCalculator.calculate(listOf(InvoiceLine("x",1,-1,0,0))) } }
    test("negative discount rejected") { expectThrows<IllegalArgumentException> { InvoiceCalculator.calculate(listOf(InvoiceLine("x",1,10,-1,0))) } }
    test("discount above row gross rejected") { expectThrows<IllegalArgumentException> { InvoiceCalculator.calculate(listOf(InvoiceLine("x",1,10,11,0))) } }
    test("tax rate above 100 rejected") { expectThrows<IllegalArgumentException> { InvoiceCalculator.calculate(listOf(InvoiceLine("x",1,10,0,101))) } }
    test("annual unlimited license accepted") { LicensePolicy.validate(LicenseRequest(LicensePlan.ANNUAL,null,1,2)) }
    test("monthly fixed count rejected") { expectThrows<IllegalArgumentException> { LicensePolicy.validate(LicenseRequest(LicensePlan.MONTHLY,5,1,2)) } }
    test("zero fixed count rejected") { expectThrows<IllegalArgumentException> { LicensePolicy.validate(LicenseRequest(LicensePlan.FIXED_INVOICE_COUNT,0,1,2)) } }
    test("license start inclusive") { val r=LicenseRequest(LicensePlan.ANNUAL,null,100,200); check(LicensePolicy.isActive(r,100)) }
    test("license end exclusive") { val r=LicenseRequest(LicensePlan.ANNUAL,null,100,200); check(!LicensePolicy.isActive(r,200)) }
    test("transfer decreases master balance") { val x=QuotaRules.transfer(QuotaState(50,50, emptyMap()),1,10); check(x.masterAvailable==40L && x.companyAllocations[1L]==10L) }
    test("transfer above available rejected") { expectThrows<IllegalArgumentException> { QuotaRules.transfer(QuotaState(50,5, emptyMap()),1,6) } }
    test("zero transfer rejected") { expectThrows<IllegalArgumentException> { QuotaRules.transfer(QuotaState(50,5, emptyMap()),1,0) } }
    test("allocation over purchased total rejected") { expectThrows<IllegalArgumentException> { QuotaState(50,41,mapOf(1L to 10L)) } }
    test("quota sum overflow rejected") { expectThrows<ArithmeticException> { QuotaState(Long.MAX_VALUE,1,mapOf(1L to Long.MAX_VALUE)) } }
    test("consume only selected company") { val x=QuotaRules.consume(QuotaState(50,35,mapOf(1L to 10L,2L to 5L)),1); check(x.companyAllocations[1L]==9L && x.companyAllocations[2L]==5L) }
    test("consume exhausted company rejected") { expectThrows<IllegalArgumentException> { QuotaRules.consume(QuotaState(10,10, emptyMap()),7) } }
    println("RESULT: $passed passed; $failed failed; ${passed+failed} total")
    if (failed > 0) error("Smoke tests failed")
}
