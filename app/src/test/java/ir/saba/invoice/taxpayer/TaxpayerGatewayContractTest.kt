package ir.saba.invoice.taxpayer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxpayerGatewayContractTest {
    @Test fun offlineSubmissionNeverReportsAcceptance() = runBlocking {
        val gateway: TaxpayerGateway = OfflineTaxpayerGateway()
        val document = SignedInvoiceDocument("TAXPAYER", byteArrayOf(1), byteArrayOf(2))
        assertTrue(gateway.submit(document) is SubmissionResult.Unavailable)
    }

    @Test fun offlineQueryNeverReportsAcceptance() = runBlocking {
        val gateway: TaxpayerGateway = OfflineTaxpayerGateway()
        assertTrue(gateway.query("reference") is SubmissionResult.Unavailable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyPayload() {
        SignedInvoiceDocument("TAXPAYER", byteArrayOf(), byteArrayOf(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankTaxpayerId() {
        SignedInvoiceDocument(" ", byteArrayOf(1), byteArrayOf(1))
    }
}
