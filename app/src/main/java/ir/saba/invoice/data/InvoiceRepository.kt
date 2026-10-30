package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceInput

data class InvoiceDetails(val invoice: Invoice, val lines: List<InvoiceLine>)

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
        require(issueDate.isNotBlank()) { "تاریخ فاکتور الزامی است." }
        require(lines.isNotEmpty()) { "حداقل یک ردیف فاکتور لازم است." }
        val normalizedDate = InvoiceInput.date(issueDate)
        val requestedNumber = InvoiceInput.digits(invoiceNumber).trim()
        val totals = InvoiceCalculator.calculate(lines)

        return db.withTransaction {
            require(db.companyDao().byId(companyId) != null) { "شرکت پیدا نشد." }
            require(db.moadianDetailsDao().byCompany(companyId)?.inactive != true) { "مودی غیرفعال است." }
            val normalizedNumber = if (requestedNumber.isNotEmpty()) requestedNumber
                else if (existingInvoiceId != null) db.invoiceDao().byId(existingInvoiceId)?.invoiceNumber.orEmpty()
                else TaxpayerSerialAllocator(db).reserve(companyId)
            require(normalizedNumber.isNotEmpty()) { "شماره فاکتور الزامی است." }
            val customer = db.customerDao().byId(customerId) ?: throw IllegalArgumentException("مشتری پیدا نشد.")
            require(customer.companyId == companyId) { "مشتری به شرکت انتخاب‌شده تعلق ندارد." }
            val duplicate = db.invoiceDao().byNumber(companyId, normalizedNumber)
                ?: db.invoiceDao().forCompany(companyId).firstOrNull {
                    InvoiceInput.digits(it.invoiceNumber).trim() == normalizedNumber
                }
            require(duplicate == null || duplicate.id == existingInvoiceId) { "این شماره فاکتور برای شرکت قبلاً ثبت شده است." }

            // A quota unit is consumed when a NEW invoice is first created, not when sent or finalized.
            // Keep consumption and invoice insertion in the same Room transaction.
            val newQuota = if (existingInvoiceId == null) {
                val company = db.companyDao().byId(companyId)!!
                val quota = db.quotaDao().companyQuota(companyId)
                    ?: throw IllegalArgumentException("شارژ مودی صفر است؛ از تنظیمات، انتقال شارژ انجام دهید.")
                require(quota.purchaserId == company.purchaserId) { "مالک شارژ با مودی مطابقت ندارد." }
                require(quota.available > 0) { "شارژ مودی صفر است؛ از تنظیمات، انتقال شارژ انجام دهید." }
                quota
            } else null
            val invoiceId = if (existingInvoiceId == null) {
                db.invoiceDao().insert(
                    Invoice(
                        companyId = companyId,
                        customerId = customerId,
                        invoiceNumber = normalizedNumber,
                        issueDate = normalizedDate,
                        totalAmountRial = totals.totalAmountRial,
                        taxAmountRial = totals.taxAmountRial,
                        status = "DRAFT"
                    )
                )
            } else {
                val old = db.invoiceDao().byId(existingInvoiceId) ?: throw IllegalArgumentException("فاکتور پیدا نشد.")
                require(old.status == "DRAFT") { "فقط پیش‌نویس قابل ویرایش است." }
                require(old.companyId == companyId) { "شرکت فاکتور قابل تغییر نیست." }
                db.invoiceDao().update(
                    old.copy(
                        customerId = customerId,
                        invoiceNumber = normalizedNumber,
                        issueDate = normalizedDate,
                        totalAmountRial = totals.totalAmountRial,
                        taxAmountRial = totals.taxAmountRial
                    )
                )
                db.invoiceLineDao().deleteForInvoice(existingInvoiceId)
                existingInvoiceId
            }
            if (newQuota != null) {
                db.quotaDao().updateCompanyQuota(newQuota.copy(available = newQuota.available - 1))
                db.quotaDao().insertQuotaUse(InvoiceQuotaUse(
                    invoiceId = invoiceId,
                    purchaserId = newQuota.purchaserId,
                    companyId = companyId,
                    usedAtEpochMillis = System.currentTimeMillis()
                ))
            }
            db.invoiceLineDao().insertAll(lines.map { it.copy(id = 0, invoiceId = invoiceId) })
            invoiceId
        }
    }

    /** Finalization and quota consumption are one database transaction. */
    suspend fun finalizeDraft(companyId: Long, invoiceId: Long, nowEpochMillis: Long = System.currentTimeMillis()) {
        require(companyId > 0) { "شرکت نامعتبر است." }
        require(invoiceId > 0) { "شناسه فاکتور نامعتبر است." }
        db.withTransaction {
            val invoice = db.invoiceDao().byId(invoiceId) ?: throw IllegalArgumentException("فاکتور پیدا نشد.")
            require(invoice.companyId == companyId) { "فاکتور متعلق به شرکت انتخاب‌شده نیست." }
            if (invoice.status == "FINALIZED") return@withTransaction
            require(invoice.status == "DRAFT") { "وضعیت فاکتور اجازه نهایی‌سازی نمی‌دهد." }
            val lines = db.invoiceLineDao().forInvoice(invoiceId)
            val totals = InvoiceCalculator.calculate(lines)
            val company = db.companyDao().byId(invoice.companyId) ?: throw IllegalArgumentException("شرکت پیدا نشد.")
            require(db.customerDao().byId(invoice.customerId)?.companyId == company.id) {
                "مشتری به شرکت این فاکتور تعلق ندارد."
            }
            require(db.quotaDao().useForInvoice(invoiceId) != null) {
                "این پیش‌نویس سابقه مصرف شارژ ندارد؛ برای بررسی به پشتیبانی مراجعه کنید."
            }
            db.invoiceDao().update(invoice.copy(
                totalAmountRial = totals.totalAmountRial,
                taxAmountRial = totals.taxAmountRial,
                status = "FINALIZED"
            ))
        }
    }

    suspend fun invoicesForCompany(companyId: Long): List<Invoice> = db.invoiceDao().forCompany(companyId)
    suspend fun linesForInvoice(companyId: Long, invoiceId: Long): List<InvoiceLine> =
        detailsForCompany(companyId, invoiceId).lines

    suspend fun detailsForCompany(companyId: Long, invoiceId: Long): InvoiceDetails = db.withTransaction {
        val invoice = db.invoiceDao().byId(invoiceId) ?: throw IllegalArgumentException("فاکتور پیدا نشد.")
        require(invoice.companyId == companyId) { "فاکتور متعلق به شرکت انتخاب‌شده نیست." }
        InvoiceDetails(invoice, db.invoiceLineDao().forInvoice(invoiceId))
    }

    suspend fun deleteDraft(companyId: Long, invoiceId: Long) = db.withTransaction {
        val details = detailsForCompany(companyId, invoiceId)
        require(details.invoice.status == "DRAFT") { "فقط پیش‌نویس قابل حذف است؛ فاکتور نهایی قابل حذف نیست." }
        require(db.moadianSimulationDao().latestForInvoice(invoiceId) == null) { "فاکتور دارای سابقه آزمایش قابل حذف نیست." }
        if (db.quotaDao().useForInvoice(invoiceId) != null) {
            // Keep a permanent invoice and consumption record; creating it already used one unit.
            db.invoiceDao().update(details.invoice.copy(status = "CANCELLED"))
        } else {
            require(db.invoiceDao().deleteDraft(companyId, invoiceId) == 1) { "فاکتور حذف نشد." }
        }
    }
}

