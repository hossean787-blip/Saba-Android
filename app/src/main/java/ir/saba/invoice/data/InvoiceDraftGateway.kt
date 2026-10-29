package ir.saba.invoice.data

/**
 * Thin gateway for Compose. This is NOT a second persistence implementation.
 * The only backing store is the existing SabaDatabase through InvoiceRepository.
 * All company-scoped reads require an explicit companyId.
 */
interface InvoiceDraftGateway {
    suspend fun saveDraft(
        companyId: Long,
        customerId: Long,
        invoiceNumber: String,
        issueDate: String,
        lines: List<InvoiceLine>,
        existingInvoiceId: Long? = null
    ): Long
    suspend fun invoicesForCompany(companyId: Long): List<Invoice>
    suspend fun detailsForCompany(companyId: Long, invoiceId: Long): InvoiceDetails
    suspend fun linesForInvoice(companyId: Long, invoiceId: Long): List<InvoiceLine>
    suspend fun deleteDraft(companyId: Long, invoiceId: Long)
    suspend fun finalizeDraft(companyId: Long, invoiceId: Long)
}

class RoomInvoiceDraftGateway(private val repository: InvoiceRepository) : InvoiceDraftGateway {
    override suspend fun saveDraft(companyId: Long, customerId: Long, invoiceNumber: String,
        issueDate: String, lines: List<InvoiceLine>, existingInvoiceId: Long?): Long =
        repository.saveDraft(companyId, customerId, invoiceNumber, issueDate, lines, existingInvoiceId)

    override suspend fun invoicesForCompany(companyId: Long): List<Invoice> =
        repository.invoicesForCompany(companyId)

    override suspend fun detailsForCompany(companyId: Long, invoiceId: Long): InvoiceDetails =
        repository.detailsForCompany(companyId, invoiceId)

    override suspend fun linesForInvoice(companyId: Long, invoiceId: Long): List<InvoiceLine> =
        repository.linesForInvoice(companyId, invoiceId)

    override suspend fun deleteDraft(companyId: Long, invoiceId: Long) =
        repository.deleteDraft(companyId, invoiceId)

    override suspend fun finalizeDraft(companyId: Long, invoiceId: Long) {
        repository.finalizeDraft(companyId, invoiceId)
    }
}
