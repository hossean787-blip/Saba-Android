package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.domain.InvoiceSubject

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
        existingInvoiceId: Long? = null,
        serverPromoConfirmed: Boolean = false,
        settlementMode: String = InvoiceSettlement.CASH,
        cashPaidRial: Long? = null,
        subject: String = InvoiceSubject.ORIGINAL,
        referenceTaxId: String? = null,
        localDetailsJson: String = ""
    ): Long {
        require(companyId > 0) { "شرکت را انتخاب کنید." }
        require(customerId > 0) { "مشتری را انتخاب کنید." }
        require(issueDate.isNotBlank()) { "تاریخ فاکتور الزامی است." }
        require(lines.isNotEmpty()) { "حداقل یک ردیف فاکتور لازم است." }
        val normalizedDate = InvoiceInput.date(issueDate)
        val requestedNumber = InvoiceInput.digits(invoiceNumber).trim()
        val totals = InvoiceCalculator.calculate(lines)
        InvoiceSettlement.amounts(settlementMode, cashPaidRial, totals.totalAmountRial)
        InvoiceSubject.validate(subject, referenceTaxId)
        val businessDetails = if (localDetailsJson.isBlank()) "" else InvoiceLocalDetails.fromJson(localDetailsJson).let {
            it.requireValidSchedule()
            it.toJson()
        }

        return db.withTransaction {
            val issuer = db.companyDao().byId(companyId)
                ?: throw IllegalArgumentException("شرکت پیدا نشد.")
            InvoiceSubject.requireLocalAcceptedReference(
                db, issuer.purchaserId, companyId, customerId, subject, referenceTaxId,
                lines, existingInvoiceId, normalizedDate
            )
            require(db.moadianDetailsDao().byCompany(companyId)?.inactive != true) { "مودی غیرفعال است." }
            val normalizedNumber = if (requestedNumber.isNotEmpty()) requestedNumber
                else if (existingInvoiceId != null) db.invoiceDao().byId(existingInvoiceId)?.invoiceNumber.orEmpty()
                else TaxpayerSerialAllocator(db).reserve(companyId)
            require(normalizedNumber.isNotEmpty()) { "شماره فاکتور الزامی است." }
            require(db.rejectedSubmissionHistoryDao().forCompany(companyId).none { InvoiceInput.number(it.invoiceNumber)==InvoiceInput.number(normalizedNumber) }) { "این شماره در سابقه ارسال ناموفق استفاده شده؛ شماره جدید را حفظ کنید." }
            val customer = db.customerDao().byId(customerId) ?: throw IllegalArgumentException("مشتری پیدا نشد.")
            require(customer.companyId == companyId) { "مشتری به شرکت انتخاب‌شده تعلق ندارد." }
            val duplicate = db.invoiceDao().byNumber(companyId, normalizedNumber)
                ?: db.invoiceDao().forCompany(companyId).firstOrNull {
                    InvoiceInput.number(it.invoiceNumber) == InvoiceInput.number(normalizedNumber)
                }
            require(duplicate == null || duplicate.id == existingInvoiceId) { "این شماره فاکتور برای شرکت قبلاً ثبت شده است." }
            if (existingInvoiceId != null) {
                val pending = db.invoiceCreationClaimDao().pending(companyId)
                require(pending == null || InvoiceInput.number(org.json.JSONObject(pending.payloadJson).getString("number")) != InvoiceInput.number(normalizedNumber)) {
                    "شماره فاکتور در یک درخواست صدور ناتمام رزرو شده است."
                }
            }

            // A quota unit is consumed when a NEW invoice is first created, not when sent or finalized.
            // Keep consumption and invoice insertion in the same Room transaction.
            val newQuota = if (existingInvoiceId == null && !serverPromoConfirmed) {
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
                        status = "DRAFT",
                        settlementMode = settlementMode,
                        cashPaidRial = cashPaidRial,
                        subject = subject,
                        referenceTaxId = referenceTaxId,
                        localDetailsJson = businessDetails
                    )
                )
            } else {
                val old = db.invoiceDao().byId(existingInvoiceId) ?: throw IllegalArgumentException("فاکتور پیدا نشد.")
                require(old.status == "DRAFT") { "فقط پیش‌نویس قابل ویرایش است." }
                require(old.companyId == companyId) { "شرکت فاکتور قابل تغییر نیست." }
                db.officialSubmissionDao().forInvoice(old.id)?.let { reserved ->
                    require(reserved.state == "RESERVED" && reserved.ownerId==issuer.purchaserId &&
                        reserved.companyId==companyId && reserved.referenceNumber.isNullOrBlank()) { "این سند برای ارسال محفوظ است." }
                    require(normalizedNumber==old.invoiceNumber && normalizedDate==old.issueDate) {
                        "بعد از تخصیص شماره مالیاتی، سریال و تاریخ صدور قابل تغییر نیستند."
                    }
                }
                db.invoiceDao().update(
                    old.copy(
                        customerId = customerId,
                        invoiceNumber = normalizedNumber,
                        issueDate = normalizedDate,
                        totalAmountRial = totals.totalAmountRial,
                        taxAmountRial = totals.taxAmountRial,
                        settlementMode = settlementMode,
                        cashPaidRial = cashPaidRial,
                        subject = subject,
                        referenceTaxId = referenceTaxId,
                        localDetailsJson = businessDetails
                    )
                )
                db.invoiceLineDao().deleteForInvoice(existingInvoiceId)
                existingInvoiceId
            }
            if (serverPromoConfirmed && existingInvoiceId == null) {
                val owner = db.companyDao().byId(companyId)!!.purchaserId
                db.quotaDao().insertQuotaUse(InvoiceQuotaUse(
                    invoiceId = invoiceId,
                    purchaserId = owner,
                    companyId = companyId,
                    usedAtEpochMillis = System.currentTimeMillis()
                ))
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

    /** Reopen only an explicitly rejected invoice, atomically retaining its immutable attempt.
     * Same local invoice ID and quota use; a fresh fiscal serial prevents reuse on resubmission. */
    suspend fun reopenRejectedForEdit(companyId:Long, invoiceId:Long, ownerId:String) = db.withTransaction {
        require(db.companyDao().byId(companyId)?.purchaserId == ownerId)
        val profile=requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        require(!profile.inactive)
        val invoice=requireNotNull(db.invoiceDao().byId(invoiceId))
        require(invoice.companyId==companyId && invoice.status=="FINALIZED")
        val row=requireNotNull(db.officialSubmissionDao().forInvoice(invoiceId))
        require(row.state=="REJECTED" && row.ownerId==ownerId && row.companyId==companyId &&
            row.fiscalId==profile.fiscalMemoryId && row.signedReceipt.isNullOrBlank()) {
            "فقط سند صریحاً ردشده با همین حافظه مالیاتی قابل اصلاح است."
        }
        require(db.quotaDao().useForInvoice(invoiceId)!=null || db.legacyQuotaExemptionDao().contains(invoiceId)==1) {
            "سابقه اعتبار این سند باید بررسی شود."
        }
        require(invoice.trackingCode.isNullOrBlank() || invoice.trackingCode==row.referenceNumber)
        val allTaxIds=db.officialSubmissionDao().forFiscalMemory(row.fiscalId).map{it.taxId}+
            db.rejectedSubmissionHistoryDao().forFiscalMemory(row.fiscalId).map{it.taxId}
        val minSerial=allTaxIds.maxOf{ir.saba.moadian.TaxIdGenerator.validate(it).serialHex.toLong(16)}+1
        val nextNumber=TaxpayerSerialAllocator(db).reserve(companyId,invoice.subject!=InvoiceSubject.ORIGINAL,minSerial)
        db.rejectedSubmissionHistoryDao().insert(RejectedSubmissionHistory(row.requestId,ownerId,companyId,invoiceId,
            row.fiscalId,row.taxId,invoice.invoiceNumber,row.payload,row.payloadHash,row.preparedAt,row.referenceNumber,
            row.lastError,row.signedReceipt,row.protocol,System.currentTimeMillis()))
        check(db.officialSubmissionDao().archiveRejected(row.requestId,ownerId)==1)
        val local=InvoiceLocalDetails.fromJson(invoice.localDetailsJson).copy(sendLaterDate="",sendLaterTime="")
        db.invoiceDao().update(invoice.copy(status="DRAFT",invoiceNumber=nextNumber,trackingCode=null,
            localDetailsJson=local.toJson()))
    }

    /** Explicit edit of a finalized but never posted invoice. The credit record is retained. */
    suspend fun reopenUnsentForEdit(companyId: Long, invoiceId: Long, ownerId: String) = db.withTransaction {
        require(db.companyDao().byId(companyId)?.purchaserId == ownerId) { "مودی متعلق به این حساب نیست." }
        require(db.moadianDetailsDao().byCompany(companyId)?.inactive != true) { "مودی غیرفعال است." }
        val invoice = requireNotNull(db.invoiceDao().byId(invoiceId))
        require(invoice.companyId == companyId && invoice.status == "FINALIZED" && invoice.trackingCode.isNullOrBlank()) {
            "فقط فاکتور نهایی‌شده و ارسال‌نشده قابل بازگشایی است."
        }
        require(db.moadianSimulationDao().latestForInvoice(invoiceId) == null) { "سند دارای سابقه آزمایش است و باید محفوظ بماند." }
        val row = db.officialSubmissionDao().forInvoice(invoiceId)
        if (row != null) {
            require(row.ownerId == ownerId && row.companyId == companyId && row.state in setOf("PREPARED","RESERVED") &&
                row.referenceNumber.isNullOrBlank() && row.signedReceipt.isNullOrBlank()) { "سند ارسال شده؛ استعلام کنید." }
            if(row.state=="PREPARED") check(db.officialSubmissionDao().reservePreparedIdentity(row.requestId) == 1) { "درخواست در حال ارسال است." }
        }
        db.invoiceDao().update(invoice.copy(status = "DRAFT"))
    }

    /** Finalization does not consume charge again. Older drafts have explicit migration exemptions. */
    suspend fun finalizeDraft(companyId: Long, invoiceId: Long, nowEpochMillis: Long = System.currentTimeMillis()) {
        require(companyId > 0) { "شرکت نامعتبر است." }
        require(invoiceId > 0) { "شناسه فاکتور نامعتبر است." }
        db.withTransaction {
            val invoice = db.invoiceDao().byId(invoiceId) ?: throw IllegalArgumentException("فاکتور پیدا نشد.")
            require(invoice.companyId == companyId) { "فاکتور متعلق به شرکت انتخاب‌شده نیست." }
            if (invoice.status == "FINALIZED") return@withTransaction
            require(invoice.status == "DRAFT") { "وضعیت فاکتور اجازه نهایی‌سازی نمی‌دهد." }
            require(db.moadianDetailsDao().byCompany(companyId)?.inactive != true) { "مودی غیرفعال است." }
            val normalizedDate = InvoiceInput.date(invoice.issueDate)
            val lines = db.invoiceLineDao().forInvoice(invoiceId)
            val totals = InvoiceCalculator.calculate(lines)
            InvoiceSettlement.amounts(invoice.settlementMode, invoice.cashPaidRial, totals.totalAmountRial)
            InvoiceSubject.requireLocalAcceptedReference(
                db, db.companyDao().byId(companyId)?.purchaserId
                    ?: throw IllegalArgumentException("مودی پیدا نشد."),
                companyId, invoice.customerId, invoice.subject, invoice.referenceTaxId,
                lines, invoiceId, normalizedDate
            )
            val company = db.companyDao().byId(invoice.companyId) ?: throw IllegalArgumentException("شرکت پیدا نشد.")
            require(db.customerDao().byId(invoice.customerId)?.companyId == company.id) {
                "مشتری به شرکت این فاکتور تعلق ندارد."
            }
            require(db.quotaDao().useForInvoice(invoiceId) != null || db.legacyQuotaExemptionDao().contains(invoiceId) == 1) {
                "این پیش‌نویس سابقه مصرف شارژ ندارد؛ برای بررسی به پشتیبانی مراجعه کنید."
            }
            db.invoiceDao().update(invoice.copy(
                issueDate = normalizedDate,
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

    /** Cancel only an unposted document; retain financial identity and consumed credit. */
    suspend fun cancelNeverSent(companyId:Long,invoiceId:Long,ownerId:String)=db.withTransaction {
        require(db.companyDao().byId(companyId)?.purchaserId==ownerId)
        val invoice=requireNotNull(db.invoiceDao().byId(invoiceId))
        require(invoice.companyId==companyId && invoice.status in setOf("DRAFT","FINALIZED") && invoice.trackingCode.isNullOrBlank()) {
            "فقط سند ارسال‌نشده قابل لغو محلی است."
        }
        require(db.moadianSimulationDao().latestForInvoice(invoiceId)==null)
        val receipt=db.officialSubmissionDao().forInvoice(invoiceId)
        if(receipt!=null) {
            require(receipt.ownerId==ownerId && receipt.companyId==companyId &&
                receipt.state in setOf("PREPARED","RESERVED") && receipt.referenceNumber.isNullOrBlank() && receipt.signedReceipt.isNullOrBlank()) {
                "وضعیت ارسال قطعی نیست؛ لغو ممنوع است."
            }
            if(receipt.state=="PREPARED") check(db.officialSubmissionDao().reservePreparedIdentity(receipt.requestId)==1)
        }
        db.invoiceDao().update(invoice.copy(status="CANCELLED"))
        db.submissionAuditDao().insert(SubmissionAudit(java.util.UUID.randomUUID().toString(),ownerId,companyId,
            invoiceId,receipt?.requestId.orEmpty(),System.currentTimeMillis(),"LOCAL_CANCEL","CANCELLED","سند ارسال‌نشده لغو شد."))
    }

    suspend fun deleteDraft(companyId: Long, invoiceId: Long) = db.withTransaction {
        val details = detailsForCompany(companyId, invoiceId)
        require(details.invoice.status == "DRAFT") { "فقط پیش‌نویس قابل حذف است؛ فاکتور نهایی قابل حذف نیست." }
        require(db.officialSubmissionDao().forInvoice(invoiceId)==null) { "سند دارای شماره مالیاتی محفوظ است؛ از مدیریت ارسال لغو محلی کنید." }
        require(details.invoice.trackingCode.isNullOrBlank())
        require(db.moadianSimulationDao().latestForInvoice(invoiceId) == null) { "فاکتور دارای سابقه آزمایش قابل حذف نیست." }
        if (db.quotaDao().useForInvoice(invoiceId) != null || db.legacyQuotaExemptionDao().contains(invoiceId) == 1) {
            // Keep a permanent invoice and consumption record; creating it already used one unit.
            db.invoiceDao().update(details.invoice.copy(status = "CANCELLED"))
        } else {
            require(db.invoiceDao().deleteDraft(companyId, invoiceId) == 1) { "فاکتور حذف نشد." }
        }
    }
}

