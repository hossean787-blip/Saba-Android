package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.InvoiceCalculator

/** Local invoice workflow. Production licensing and authorization must also be enforced by a server. */
class InvoiceRepository(private val db: SabaDatabase) {
    /** Creates a new draft or replaces the lines of an existing draft. */
    suspend fun saveDraft(
        companyId: Long,
        customerId: Long,
        invoiceNumber: String,
        issueDate: String,
        lines: List<InvoiceLine>,
        existingInvoiceId: Long? = null
    ): Long {
        require(companyId > 0) { "شرکت را انتخاب کنید." }
        require(customerId > 0) { "مشتری را انتخاب کنید." }
        require(invoiceNumber.isNotBlank()) { "شماره فاکتور الزامی است." }
        require(issueDate.isNotBlank()) { "تاریخ فاکتور الزامی است." }
        require(lines.isNotEmpty()) { "حداقل یک ردیف فاکتور لازم است." }
        val totals = InvoiceCalculator.calculate(lines)

        return db.withTransaction {
            require(db.companyDao().byId(companyId) != null) { "شرکت پیدا نشد." }
            val customer = db.customerDao().byId(customerId) ?: error("مشتری پیدا نشد.")
            require(customer.companyId == companyId) { "مشتری به شرکت انتخاب‌شده تعلق ندارد." }

            val invoiceId = if (existingInvoiceId == null) {
                db.invoiceDao().insert(
                    Invoice(
                        companyId = companyId,
                        customerId = customerId,
                        invoiceNumber = invoiceNumber.trim(),
                        issueDate = issueDate.trim(),
                        totalAmountRial = totals.totalAmountRial,
                        taxAmountRial = totals.taxAmountRial,
                        status = "DRAFT"
                    )
                )
            } else {
                val old = db.invoiceDao().byId(existingInvoiceId) ?: error("فاکتور پیدا نشد.")
                require(old.status == "DRAFT") { "فقط پیش‌نویس قابل ویرایش است." }
                require(old.companyId == companyId) { "شرکت فاکتور قابل تغییر نیست." }
                db.invoiceDao().update(
                    old.copy(
                        customerId = customerId,
                        invoiceNumber = invoiceNumber.trim(),
                        issueDate = issueDate.trim(),
                        totalAmountRial = totals.totalAmountRial,
                        taxAmountRial = totals.taxAmountRial
                    )
                )
                db.invoiceLineDao().deleteForInvoice(existingInvoiceId)
                existingInvoiceId
            }
            db.invoiceLineDao().insertAll(lines.map { it.copy(id = 0, invoiceId = invoiceId) })
            invoiceId
        }
    }

    /** Finalization and quota consumption are one database transaction. */
    suspend fun finalizeDraft(invoiceId: Long, nowEpochMillis: Long = System.currentTimeMillis()) {
        require(invoiceId > 0) { "شناسه فاکتور نامعتبر است." }
        db.withTransaction {
            val invoice = db.invoiceDao().byId(invoiceId) ?: error("فاکتور پیدا نشد.")
            if (invoice.status == "FINALIZED") {
                require(db.quotaDao().useForInvoice(invoiceId) != null) {
                    "فاکتور قبلاً نهایی شده ولی سابقه مصرف سهمیه ندارد؛ نیاز به بررسی دارد."
                }
                return@withTransaction
            }
            require(invoice.status == "DRAFT") { "وضعیت فاکتور اجازه نهایی‌سازی نمی‌دهد." }
            val lines = db.invoiceLineDao().forInvoice(invoiceId)
            val totals = InvoiceCalculator.calculate(lines)
            val company = db.companyDao().byId(invoice.companyId) ?: error("شرکت پیدا نشد.")
            require(db.customerDao().byId(invoice.customerId)?.companyId == company.id) {
                "مشتری به شرکت این فاکتور تعلق ندارد."
            }
            require(db.quotaDao().useForInvoice(invoiceId) == null) { "سهمیه این فاکتور قبلاً مصرف شده است." }
            val quota = db.quotaDao().companyQuota(company.id) ?: error("برای این شرکت سهمیه تخصیص داده نشده است.")
            require(quota.purchaserId == company.purchaserId) { "مالک سهمیه با مالک شرکت مطابقت ندارد." }
            require(quota.available > 0) { "سهمیه شرکت تمام شده است." }

            db.invoiceDao().update(invoice.copy(
                totalAmountRial = totals.totalAmountRial,
                taxAmountRial = totals.taxAmountRial,
                status = "FINALIZED"
            ))
            db.quotaDao().updateCompanyQuota(quota.copy(available = quota.available - 1))
            db.quotaDao().insertQuotaUse(InvoiceQuotaUse(
                invoiceId = invoice.id,
                purchaserId = company.purchaserId,
                companyId = company.id,
                usedAtEpochMillis = nowEpochMillis
            ))
        }
    }

    suspend fun invoicesForCompany(companyId: Long): List<Invoice> = db.invoiceDao().forCompany(companyId)
    suspend fun linesForInvoice(invoiceId: Long): List<InvoiceLine> = db.invoiceLineDao().forInvoice(invoiceId)
}
