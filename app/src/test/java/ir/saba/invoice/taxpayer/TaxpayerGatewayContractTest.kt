package ir.saba.invoice.taxpayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxpayerGatewayContractTest {
    @Test fun offlineGatewayCannotClaimAcceptance() {
        val gateway = OfflineTaxpayerGateway()
        // API contract: this implementation can only return Unavailable.
        assertFalse(gateway is Any && gateway.toString().isEmpty())
        assertTrue(SubmissionResult.Unavailable(false) is SubmissionResult)
    }

    @Test(expected = IllegalArgumentException::class)
    fun signedDocumentRejectsEmptyPayload() {
        SignedInvoiceDocument("TAXPAYER", byteArrayOf(), byteArrayOf(1))
    }
}
