package ir.saba.invoice.taxpayer

/** Safe offline placeholder; never claims an invoice has been sent. */
class OfflineTaxpayerGateway : TaxpayerGateway {
    override suspend fun submit(document: SignedInvoiceDocument): SubmissionResult =
        SubmissionResult.Unavailable(retryable = false)

    override suspend fun query(referenceId: String): SubmissionResult =
        SubmissionResult.Unavailable(retryable = false)
}
