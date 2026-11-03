package ir.saba.invoice.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TaxSubmissionPreflightTest {
    private fun request(id: Long = 1) = TaxSubmissionRequest(id, 2, "A11216", "A1121604C220002F095011", "{}")

    @Test fun rejectsInvalidTaxIdChecksumBeforeGatewayPreparation() {
        assertTrue(TaxSubmissionPreflight.check(request().copy(taxId="A1121604C220002F095010")) is TaxPreflightResult.Invalid)
    }
    @Test fun rejectsTaxIdFromDifferentFiscalMemory() {
        assertTrue(TaxSubmissionPreflight.check(request().copy(fiscalMemoryId="ABC123")) is TaxPreflightResult.Invalid)
    }
    @Test fun rejectsInvoiceIdentityMismatch() = runBlocking {
        val gateway = PreflightOnlyTaxpayerGateway { request(99) }
        assertEquals(TaxpayerState.REJECTED, gateway.submit(1).state)
    }

    @Test fun validPreflightNeverReportsOfficialSuccess() = runBlocking {
        val gateway = PreflightOnlyTaxpayerGateway { request() }
        assertEquals(TaxpayerState.NOT_CONFIGURED, gateway.submit(1).state)
        assertEquals(TaxpayerState.NOT_CONFIGURED, gateway.queryStatus("reference").state)
    }

    @Test fun missingInvoiceIsRejected() = runBlocking {
        assertEquals(TaxpayerState.REJECTED, PreflightOnlyTaxpayerGateway { null }.submit(1).state)
    }

    @Test fun invalidInvoiceIdDoesNotLoadData() = runBlocking {
        val gateway = PreflightOnlyTaxpayerGateway { error("Must not load an invalid identity") }
        assertEquals(TaxpayerState.REJECTED, gateway.submit(0).state)
    }
}
