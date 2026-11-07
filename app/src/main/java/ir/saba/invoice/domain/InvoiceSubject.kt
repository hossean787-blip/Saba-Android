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

    /** Only original and local corrections are implemented; prevent unsupported claims. */
    fun validate(subject: String, referenceTaxId: String?) {
        when (subject) {
            ORIGINAL -> require(referenceTaxId.isNullOrBlank()) {
                "صورتحساب اصلی نباید شماره مالیاتی مرجع داشته باشد."
            }
            CORRECTION -> require(referenceTaxId != null &&
                    referenceTaxId.matches(Regex("[A-Z0-9]{22}"))) {
                "برای صورتحساب اصلاحی، شماره مالیاتی ۲۲ کاراکتری مرجع لازم است."
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
        currentInvoiceId: Long? = null
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
        // A corrective invoice may adjust prices and quantities, but cannot silently
        // replace the original goods/services with different identifiers. Changes to
        // the item identity require a separate sale or another legally supported subject.
        val sourceCodes = db.invoiceLineDao().forInvoice(original.id).map { it.itemCode }.sorted()
        val correctionCodes = lines.map { it.itemCode }.sorted()
        require(sourceCodes.isNotEmpty() && correctionCodes == sourceCodes) {
            "شناسه و تعداد اقلام کالا/خدمت در اصلاحیه باید با صورتحساب مرجع یکسان باشد؛ تغییر کالا یا خدمت با اصلاحیه مجاز نیست."
        }
        // Avoid conflicting pending/local corrections against one accepted reference.
        require(db.invoiceDao().forCompany(companyId).none {
            it.id != currentInvoiceId && it.subject == CORRECTION &&
                it.referenceTaxId == referenceTaxId && it.status != "CANCELLED"
        }) { "برای این صورتحساب مرجع قبلاً اصلاحیه فعال ثبت شده است؛ همان پیش‌نویس را ویرایش کنید." }
    }

    fun requireSupportedForOfficialSubmission(invoice: Invoice) {
        require(invoice.subject == ORIGINAL && invoice.referenceTaxId.isNullOrBlank()) {
            "ارسال رسمی صورتحساب اصلاحی/ارجاعی در این نسخه فعال نیست؛ فقط ثبت محلی انجام شده است."
        }
    }
}
