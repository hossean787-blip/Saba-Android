package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.InvoiceCalculator

class InvoiceRepository(private val db: SabaDatabase) {
    suspend fun saveDraft(
        companyId: Long, customerId: Long, invoiceNumber: String,
        issueDate: String, lines: List<InvoiceLine>, existingInvoiceId: Long? = null
    ): Long {
        require(companyId > 0 && customerId > 0)
        require(invoiceNumber.isNotBlank() && issueDate.isNotBlank())
        val totals = InvoiceCalculator.calculate(lines)
        return db.withTransaction {
            require(db.companyDao().byId(companyId) != null) { "شرکت پیدا نشد." }
            require(db.customerDao().byId(customerId)?.companyId == companyId) { "مشتری متعلق به این شرکت نیست." }
            val id = if (existingInvoiceId == null) {
                db.invoiceDao().insert(Invoice(
                    companyId = companyId, customerId = customerId, invoiceNumber = invoiceNumber.trim(),
                    issueDate = issueDate.trim(), totalAmountRial = totals.totalAmountRial,
                    taxAmountRial = totals.taxAmountRial
                ))
            } else {
                val old = db.invoiceDao().byId(existingInvoiceId) ?: error("فاکتور پیدا نشد.")
                require(old.status == "DRAFT" && old.companyId == companyId)
                db.invoiceDao().update(old.copy(
                    customerId = customerId, invoiceNumber = invoiceNumber.trim(), issueDate = issueDate.trim(),
                    totalAmountRial = totals.totalAmountRial, taxAmountRial = totals.taxAmountRial
                ))
                db.invoiceLineDao().deleteForInvoice(existingInvoiceId)
                existingInvoiceId
            }
            db.invoiceLineDao().insertAll(lines.map { it.copy(id = 0, invoiceId = id) })
            id
        }
    }

    suspend fun finalizeDraft(invoiceId: Long, nowEpochMillis: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val invoice = db.invoiceDao().byId(invoiceId) ?: error("فاکتور پیدا نشد.")
            if (invoice.status == "FINALIZED") {
                require(db.quotaDao().useForInvoice(invoiceId) != null) { "سابقه مصرف سهمیه یافت نشد." }
                return@withTransaction
            }
            require(invoice.status == "DRAFT")
            val company = db.companyDao().byId(invoice.companyId) ?: error("شرکت پیدا نشد.")
            require(db.customerDao().byId(invoice.customerId)?.companyId == company.id)
            val totals = InvoiceCalculator.calculate(db.invoiceLineDao().forInvoice(invoiceId))
            require(db.quotaDao().useForInvoice(invoiceId) == null)
            val quota = db.quotaDao().companyQuota(company.id) ?: error("سهمیه شرکت پیدا نشد.")
            require(quota.purchaserId == company.purchaserId && quota.available > 0)
            db.invoiceDao().update(invoice.copy(
                totalAmountRial = totals.totalAmountRial, taxAmountRial = totals.taxAmountRial,
                status = "FINALIZED"
            ))
            db.quotaDao().updateCompanyQuota(quota.copy(available = quota.available - 1))
            db.quotaDao().insertQuotaUse(InvoiceQuotaUse(invoiceId, company.purchaserId, company.id, nowEpochMillis))
        }
    }
    suspend fun invoicesForCompany(companyId: Long) = db.invoiceDao().forCompany(companyId)
}