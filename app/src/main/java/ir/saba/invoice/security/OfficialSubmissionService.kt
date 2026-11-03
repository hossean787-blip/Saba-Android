package ir.saba.invoice.security

import androidx.room.withTransaction
import ir.saba.invoice.data.*
import ir.saba.moadian.SubmissionPreparation
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

/** Android outbox: one tax identity per invoice; ambiguous POST is never blindly retried. */
class OfficialSubmissionService(
    private val db: SabaDatabase,
    private val account: AccountScope,
    private val credentials: TaxpayerCredentialRepository,
    private val transport: OfficialTaxTransport = OfficialMoadianTransport(),
    private val sessionCheck: () -> Unit = { error("نشست معتبر حساب برای این عملیات لازم است.") },
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val signer = MoadianV2Signer(credentials)
    private suspend fun owner(companyId: Long): Company {
        val company = requireNotNull(db.companyDao().byId(companyId))
        require(company.purchaserId == account.ownerId) { "مؤدی متعلق به این حساب نیست." }
        require(db.moadianDetailsDao().byCompany(companyId)?.inactive != true) { "مؤدی غیرفعال است." }
        return company
    }
    private suspend fun row(id: String): OfficialSubmission = db.withTransaction {
        val row = requireNotNull(db.officialSubmissionDao().byId(id))
        owner(row.companyId)
        require(row.ownerId == account.ownerId)
        val invoice = requireNotNull(db.invoiceDao().byId(row.invoiceId))
        require(invoice.companyId == row.companyId && invoice.status == "FINALIZED")
        require(invoice.trackingCode.isNullOrBlank() || invoice.trackingCode == row.referenceNumber) { "کد پیگیری موجود با دفتر ارسال مطابقت ندارد؛ بررسی پشتیبانی لازم است." }
        require(db.moadianDetailsDao().byCompany(row.companyId)?.fiscalMemoryId == row.fiscalId) { "حافظه مالیاتی تغییر کرده است؛ حافظه اصلی درخواست را بازیابی کنید." }
        require(SubmissionPreparation.sha256Hex(row.payload.toByteArray(Charsets.UTF_8)) == row.payloadHash) { "محتوای محفوظ صورتحساب تغییر کرده یا خراب است؛ ارسال متوقف شد." }
        row
    }
    suspend fun history(companyId: Long): List<OfficialSubmission> = db.withTransaction {
        owner(companyId)
        db.officialSubmissionDao().forCompany(companyId).also { require(it.all { r -> r.ownerId == account.ownerId }) }
    }
    suspend fun prepare(companyId: Long, invoiceId: Long, issueTime: String, unit: String): OfficialSubmission = db.withTransaction {
        val company = owner(companyId)
        val invoice = requireNotNull(db.invoiceDao().byId(invoiceId))
        val customer = requireNotNull(db.customerDao().byId(invoice.customerId))
        val details = requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        val existing = db.officialSubmissionDao().forInvoice(invoiceId)
        require(invoice.trackingCode.isNullOrBlank() || (existing != null && existing.referenceNumber == invoice.trackingCode)) {
            "صورتحساب دارای کد پیگیری قدیمی است؛ ساخت درخواست تازه مجاز نیست و سابقه باید بررسی شود."
        }
        val payload = MoadianInvoiceMapper.map(company,customer,db.customerProfileDao().byCustomer(customer.id),details,invoice,
            db.invoiceLineDao().forInvoice(invoiceId),issueTime,unit,clock())
        require(payload.length <= 2_000_000) { "حجم صورتحساب بیش از حد مجاز است." }
        if (existing != null) {
            require(existing.ownerId == account.ownerId && existing.companyId == companyId && existing.payload == payload) { "صورتحساب قبلاً با محتوای ثابت آماده شده است." }
            return@withTransaction existing
        }
        val taxId = JSONObject(payload).getJSONObject("header").getString("taxid")
        val serial = ir.saba.moadian.TaxIdGenerator.validate(taxId).serialHex.toLong(16)
        require(db.officialSubmissionDao().forFiscalMemory(details.fiscalMemoryId).none {
            ir.saba.moadian.TaxIdGenerator.validate(it.taxId).serialHex.toLong(16) >= serial
        }) { "سریال مالیاتی باید از سریال‌های ثبت‌شده این حافظه بزرگ‌تر باشد؛ تغییر تاریخ یا صفرهای ابتدا مجوز تکرار نمی‌دهد." }
        OfficialSubmission(UUID.randomUUID().toString(),account.ownerId,companyId,invoiceId,details.fiscalMemoryId,taxId,payload,
            SubmissionPreparation.sha256Hex(payload.toByteArray(Charsets.UTF_8)),clock()).also { db.officialSubmissionDao().insert(it) }
    }
    suspend fun reviewPrepared(id: String): OfficialSubmission = row(id).also {
        require(it.state == "PREPARED")
        val payload=JSONObject(it.payload)
        require(payload.getJSONObject("header").getString("taxid")==it.taxId)
        require(payload.getJSONArray("body").length()>0)
    }
    suspend fun discardPrepared(id: String) = db.withTransaction {
        val existing=requireNotNull(db.officialSubmissionDao().byId(id))
        require(existing.ownerId==account.ownerId && db.companyDao().byId(existing.companyId)?.purchaserId==account.ownerId)
        // PREPARED has never reached POST; even a changed memory or corrupt preview can be discarded.
        require(db.officialSubmissionDao().discardPrepared(id) == 1) { "درخواست شروع شده و قابل حذف نیست؛ استعلام کنید." }
    }
    /** Keep the PDF/history tracking field and the outbox receipt in one local transaction. */
    private suspend fun resolve(id: String, state: String, reference: String?, error: String?, receipt: String? = null): Int = db.withTransaction {
        val current = requireNotNull(db.officialSubmissionDao().byId(id))
        require(current.ownerId == account.ownerId)
        val changed = db.officialSubmissionDao().resolve(id,state,reference,error,receipt)
        if (changed == 1 && reference != null) {
            val invoice = requireNotNull(db.invoiceDao().byId(current.invoiceId))
            require(invoice.companyId == current.companyId)
            require(invoice.trackingCode.isNullOrBlank() || invoice.trackingCode == reference)
            db.invoiceDao().update(invoice.copy(trackingCode=reference))
        }
        changed
    }
    private suspend fun authorization(row: OfficialSubmission): String {
        sessionCheck()
        val raw = JSONObject(transport.request("GET","nonce?timeToLive=30"))
        return signer.authorization(row.companyId,TaxpayerNonce(TaxpayerIdentity(account,row.companyId,row.fiscalId),
            raw.getString("nonce"),Instant.parse(raw.getString("expDate")).toEpochMilli()),clock())
    }
    private suspend fun keys(row: OfficialSubmission): List<OrganizationKey> = MoadianSignedReceipt.keys(
        transport.request("GET","server-information",authorization(row)))

    /** Caller must get explicit user confirmation before invoking this real financial document operation. */
    suspend fun submit(id: String): OfficialSubmission {
        sessionCheck()
        val initial = row(id)
        require(initial.state == "PREPARED") { "این درخواست ممکن است ارسال شده باشد؛ ابتدا استعلام کنید." }
        val keys = keys(initial)
        val encryption = keys.firstOrNull { it.purpose == 1 } ?: error("کلید رمزگذاری معتبر سازمان یافت نشد.")
        val identity = TaxpayerIdentity(account,initial.companyId,initial.fiscalId)
        val signed = signer.invoice(initial.companyId,identity,initial.payload,clock())
        val packet = JSONObject().put("payload",MoadianJwe.encrypt(signed,encryption.id,encryption.key))
            .put("header",JSONObject().put("requestTraceId",initial.requestId).put("fiscalId",initial.fiscalId))
        val token = authorization(initial)
        db.withTransaction {
            sessionCheck()
            row(id)
            check(db.officialSubmissionDao().claim(id) == 1) { "درخواست قبلاً شروع شده است؛ استعلام کنید." }
        }
        try {
            val result = JSONObject(transport.request("POST","invoice",token,JSONArray().put(packet).toString())).getJSONArray("result")
            require(result.length() == 1)
            val receipt = result.getJSONObject(0)
            require(receipt.getString("uid") == initial.requestId)
            val reference = receipt.getString("referenceNumber")
            require(reference.isNotBlank() && reference.length <= 200 && reference.none { it.isISOControl() })
            resolve(id,"SUBMITTED",reference,null)
        } catch (failure: Exception) {
            // This persisted state also covers cancellation/lost acknowledgement. Never assign a new UUID.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                resolve(id,"UNKNOWN",null,"پاسخ ارسال قطعی نیست؛ فقط استعلام همان درخواست مجاز است.")
            }
            if (failure is CancellationException) throw failure
        }
        return requireNotNull(db.officialSubmissionDao().byId(id))
    }
    suspend fun inquire(id: String): OfficialSubmission {
        sessionCheck()
        val initial = row(id)
        require(initial.state in setOf("SENDING","UNKNOWN","SUBMITTED")) { "درخواست در وضعیت قابل استعلام نیست." }
        fun encode(s: String) = URLEncoder.encode(s,"UTF-8")
        // Supply the original range explicitly; the official default only searches the last 24 hours.
        val start = Instant.ofEpochMilli(initial.preparedAt - 86_400_000).toString()
        val end = Instant.ofEpochMilli(clock() + 300_000).toString()
        val path = "inquiry-by-uid?uidList=${encode(initial.requestId)}&fiscalId=${encode(initial.fiscalId)}&start=${encode(start)}&end=${encode(end)}"
        try {
            val results = JSONArray(transport.request("GET",path,authorization(initial)))
            require(results.length() == 1)
            val result = results.getJSONObject(0)
            require(result.getString("uid") == initial.requestId && result.getString("fiscalId") == initial.fiscalId)
            val reference = result.optString("referenceNumber").takeIf { it.isNotBlank() && it != "null" }
            require(reference == null || (reference.length <= 200 && reference.none { it.isISOControl() }))
            require(initial.referenceNumber == null || reference == initial.referenceNumber)
            when (result.getString("status")) {
                "SUCCESS" -> {
                    require(!reference.isNullOrBlank())
                    val signedReceipt = result.getString("sign")
                    MoadianSignedReceipt.verify(signedReceipt,keys(initial),initial.taxId,initial.fiscalId,reference,clock())
                    resolve(id,"ACCEPTED",reference,null,signedReceipt)
                }
                "FAILED" -> {
                    val codes = result.optJSONObject("data")?.optJSONArray("error")
                    val message = if (codes == null) "صورتحساب در سامانه رد شد." else {
                        val safeCodes = (0 until minOf(codes.length(),20)).mapNotNull { i ->
                            codes.optJSONObject(i)?.optString("code")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,40}")) }
                        }
                        "صورتحساب رد شد. کد خطا: " + safeCodes.joinToString(", ")
                    }
                    resolve(id,"REJECTED",reference,message)
                }
                "IN_PROGRESS" -> resolve(id,"SUBMITTED",reference,"در صف اعتبارسنجی سامانه")
                else -> resolve(id,"UNKNOWN",reference,"نتیجه قطعی نیست؛ ارسال مجدد خودکار ممنوع است. بعداً استعلام کنید.")
            }
        } catch (failure: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                resolve(id,"UNKNOWN",null,"استعلام معتبر دریافت نشد؛ وضعیت پذیرش تأیید نشده است.")
            }
            if (failure is CancellationException) throw failure
        }
        return requireNotNull(db.officialSubmissionDao().byId(id))
    }
}
