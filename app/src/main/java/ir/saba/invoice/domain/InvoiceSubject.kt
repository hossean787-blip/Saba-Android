package ir.saba.invoice.domain

import ir.saba.invoice.data.Invoice
import ir.saba.invoice.data.InvoiceLine
import ir.saba.invoice.data.OfficialSubmission
import ir.saba.invoice.data.SabaDatabase

/**
 * Invoice subject is independent from local draft/finalized state.
 * A locally stored correction is NOT a government-accepted correction.
 *
 * Only original type-one cash sales currently have an audited official mapper.
 */
object InvoiceSubject {
    const val ORIGINAL = "ORIGINAL"
    const val CORRECTION = "CORRECTION"
    const val CANCELLATION = "CANCELLATION"
    const val RETURN = "RETURN"

    fun label(subject: String): String = when (subject) {
        ORIGINAL -> "اصلی"
        CORRECTION -> "اصلاحی"
        CANCELLATION -> "ابطالی"
        RETURN -> "برگشت از فروش"
        else -> "نامعتبر"
    }

    /** Only original, local corrections and local partial returns are implemented. */
    fun validate(subject: String, referenceTaxId: String?) {
        when (subject) {
            ORIGINAL -> require(referenceTaxId.isNullOrBlank()) {
                "صورتحساب اصلی نباید شماره مالیاتی مرجع داشته باشد."
            }
            CORRECTION, RETURN -> require(referenceTaxId != null &&
                    referenceTaxId.matches(Regex("[A-Z0-9]{22}"))) {
                "برای صورتحساب ارجاعی، شماره مالیاتی ۲۲ کاراکتری مرجع لازم است."
            }
            else -> throw IllegalArgumentException(
                "ثبت موضوع صورتحساب انتخاب‌شده هنوز پشتیبانی نمی‌شود."
            )
        }
    }

    /** Recheck source inside the same Room transaction that creates or edits a draft. */
    suspend fun requireLocalAcceptedReference(
        db: SabaDatabase, ownerId: String, companyId: Long, customerId: Long,
        subject: String, referenceTaxId: String?, lines: List<InvoiceLine>,
        currentInvoiceId: Long? = null, newIssueDate: String? = null
    ) {
        validate(subject, referenceTaxId)
        if (subject == ORIGINAL) return
        val reference = db.officialSubmissionDao().forCompany(companyId)
            .firstOrNull { it.taxId == referenceTaxId }
            ?: throw IllegalArgumentException("صورتحساب مرجع در پرونده همین مؤدی پیدا نشد.")
        require(reference.ownerId == ownerId && reference.companyId == companyId &&
            reference.state == "ACCEPTED" && !reference.signedReceipt.isNullOrBlank() &&
            !reference.referenceNumber.isNullOrBlank()) {
            "فقط صورتحساب اصلی پذیرفته‌شده در سامانه، با رسید معتبر، قابل ارجاع است."
        }
        val original = db.invoiceDao().byId(reference.invoiceId)
            ?: throw IllegalArgumentException("صورتحساب مرجع محلی پیدا نشد.")
        require(original.companyId == companyId && original.status == "FINALIZED" &&
            original.customerId == customerId && original.subject == ORIGINAL) {
            "موضوع، مشتری یا مؤدی صورتحساب مرجع معتبر نیست."
        }
        if (newIssueDate != null) require(InvoiceInput.date(newIssueDate) > InvoiceInput.date(original.issueDate)) {
            "تاریخ سند ارجاعی باید بعد از تاریخ صورتحساب مرجع باشد."
        }
        if (subject == RETURN) {
            val originals = db.invoiceLineDao().forInvoice(original.id)
            require(originals.size == lines.size) {
                "برگشت از فروش محلی باید همه اقلام باقی‌مانده مرجع را داشته باشد."
            }
            var decreased = false
            for ((newLine, previousLine) in lines.zip(originals)) {
                require(newLine.itemCode == previousLine.itemCode &&
                    newLine.description == previousLine.description &&
                    newLine.unitCode == previousLine.unitCode &&
                    newLine.unitPriceRial == previousLine.unitPriceRial &&
                    InvoiceLineAmounts.effectiveTaxPercent(newLine) == InvoiceLineAmounts.effectiveTaxPercent(previousLine)) {
                    "شناسه، شرح، واحد، مبلغ فی و نرخ مالیاتِ اقلام مرجع در برگشت از فروش نباید تغییر کنند."
                }
                val amount = InvoiceLineAmounts.effectiveQuantity(newLine)
                val oldAmount = InvoiceLineAmounts.effectiveQuantity(previousLine)
                require(amount > java.math.BigDecimal.ZERO && amount <= oldAmount) {
                    "مقدار باقی‌مانده هر قلم باید مثبت و حداکثر برابر مقدار صورتحساب مرجع باشد."
                }
                if (amount < oldAmount) decreased = true
            }
            require(decreased) { "حداقل مقدار یک قلم باید نسبت به مرجع کاهش یابد." }
            require(InvoiceCalculator.calculate(lines).totalAmountRial <
                InvoiceCalculator.calculate(originals).totalAmountRial) {
                "مبلغ باقی‌مانده برگشت از فروش باید کمتر از مبلغ صورتحساب مرجع باشد."
            }
        }
        // A corrective invoice may adjust prices and quantities, but cannot silently
        // replace the original goods/services with different identifiers. Changes to
        // the item identity require a separate sale or another legally supported subject.
        if (subject == CORRECTION) {
            val sourceCodes = db.invoiceLineDao().forInvoice(original.id).map { it.itemCode }.sorted()
            val correctionCodes = lines.map { it.itemCode }.sorted()
            require(sourceCodes.isNotEmpty() && correctionCodes == sourceCodes) {
                "شناسه و تعداد اقلام کالا/خدمت در اصلاحیه باید با صورتحساب مرجع یکسان باشد؛ تغییر کالا یا خدمت با اصلاحیه مجاز نیست."
            }
        }
        // Avoid conflicting pending/local corrections against one accepted reference.
        require(db.invoiceDao().forCompany(companyId).none {
            it.id != currentInvoiceId && it.subject in setOf(CORRECTION, RETURN) &&
                it.referenceTaxId == referenceTaxId && it.status != "CANCELLED"
        }) { "برای این صورتحساب مرجع قبلاً سند ارجاعی فعال ثبت شده است؛ همان پیش‌نویس را ویرایش کنید." }
    }

    fun requireSupportedForOfficialSubmission(invoice: Invoice) {
        require(invoice.subject == ORIGINAL && invoice.referenceTaxId.isNullOrBlank()) {
            "ارسال رسمی صورتحساب اصلاحی/ارجاعی در این نسخه فعال نیست؛ فقط ثبت محلی انجام شده است."
        }
    }
}
