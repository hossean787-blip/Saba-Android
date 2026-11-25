package ir.saba.invoice.security

import androidx.room.withTransaction
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.moadian.SubmissionPreparation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
    private val directOnly: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val signer = MoadianV2Signer(credentials)
    /** Injectable only inside this module for deterministic fake-server tests. */
    internal var legacyGateway: LegacyInvoiceGateway = LegacyMoadianInvoiceGateway(credentials)
    suspend fun checkLegacySigningReadiness(companyId: Long) {
        sessionCheck()
        val company = owner(companyId)
        require(company.id == companyId)
        val fiscal = requireNotNull(db.moadianDetailsDao().byCompany(companyId)).fiscalMemoryId
        require(fiscal.matches(Regex("[A-Z0-9]{6}")))
        require(credentials.hasPendingPrivateKey(companyId)) {
            "کلید خصوصی این مؤدی ثبت نشده است؛ از تنظیمات مؤدی، کلید را به‌صورت امن ذخیره کنید."
        }
    }

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
    /**
     * Local credential preflight: validates ownership, fiscal memory, private key and
     * matching unexpired certificate. No HTTP calls and no invoice is submitted.
     */
    suspend fun checkLocalSigningReadiness(companyId: Long): TaxpayerCredentialProfile {
        sessionCheck()
        owner(companyId)
        val fiscalId = requireNotNull(db.moadianDetailsDao().byCompany(companyId)).fiscalMemoryId
        require(fiscalId.matches(Regex("[A-Z0-9]{6}"))) {
            "شناسه یکتای حافظه مالیاتی باید ۶ کاراکتر معتبر باشد."
        }
        val profile = credentials.profile(companyId, clock())
            ?: throw IllegalStateException(
                "کلید خصوصی و گواهی معتبر برای این مؤدی ثبت نشده است؛ ابتدا اتصال نسخه ۲ را تکمیل کنید."
            )
        require(profile.identity.fiscalMemoryId == fiscalId) {
            "شناسه حافظه با گواهی امضای مؤدی یکسان نیست."
        }
        return profile
    }

    /**
     * Read-only online connectivity preflight. Performs two GET requests and
     * checks the returned organization encryption key; it never creates a tax
     * outbox entry or POSTs an invoice. A successful result is NOT tax acceptance.
     */
    suspend fun checkOnlineReadiness(companyId: Long) {
        val profile = checkLocalSigningReadiness(companyId)
        val nonceJson = JSONObject(transport.request("GET", "nonce?timeToLive=30"))
        val challenge = TaxpayerNonce(profile.identity,
            nonceJson.getString("nonce"),
            Instant.parse(nonceJson.getString("expDate")).toEpochMilli())
        val authorization = signer.authorization(companyId, challenge, clock())
        sessionCheck()
        val keys = MoadianSignedReceipt.keys(
            transport.request("GET", "server-information", authorization))
        require(keys.any { it.purpose == 1 }) {
            "سرور پاسخ داد، اما کلید عمومی معتبر برای رمزگذاری صورتحساب ارائه نشد."
        }
    }

    suspend fun history(companyId: Long): List<OfficialSubmission> = db.withTransaction {
        sessionCheck()
        owner(companyId)
        db.officialSubmissionDao().forCompany(companyId).also { require(it.all { r -> r.ownerId == account.ownerId }) }
    }
    /** Recheck immutable source evidence at preparation and again before sending.
     *  Same-day referrals are allowed only when their actual issue time is later.
     *  A mutable customer profile must not silently change the original buyer. */
    private suspend fun requireOfficialReference(invoice: Invoice, header: JSONObject) {
        if (invoice.subject == InvoiceSubject.ORIGINAL) return
        val source = db.officialSubmissionDao().forCompany(invoice.companyId)
            .firstOrNull { it.taxId == invoice.referenceTaxId }
            ?: throw IllegalArgumentException("سند مرجع در دفتر ارسال پیدا نشد.")
        require(source.ownerId == account.ownerId && source.companyId == invoice.companyId &&
            OfficialReferenceEvidence.eligible(source)) { "پذیرش مرجع باید معتبر و قطعی باشد." }
        require(SubmissionPreparation.sha256Hex(source.payload.toByteArray(Charsets.UTF_8)) == source.payloadHash) {
            "محتوای محفوظ سند مرجع تغییر کرده است."
        }
        val previous = JSONObject(source.payload).getJSONObject("header")
        require(previous.getString("taxid") == source.taxId &&
            previous.getInt("inty") == header.getInt("inty") &&
            previous.getInt("inp") == header.getInt("inp") &&
            previous.getString("tins") == header.getString("tins")) {
            "نوع، الگو یا فروشنده با سند مرجع مطابقت ندارد."
        }
        require(header.getLong("indatim") > previous.getLong("indatim")) {
            "زمان صدور سند ارجاعی باید بعد از زمان واقعی صدور مرجع باشد."
        }
        if (invoice.subject != InvoiceSubject.CANCELLATION) {
            require(listOf("tob", "bid", "tinb").all { previous.getString(it) == header.getString(it) }) {
                "اطلاعات هویتی خریدار در اصلاحیه یا برگشت نباید تغییر کند؛ مسیر ابطال را بررسی کنید."
            }
        }
    }

    suspend fun prepare(companyId: Long, invoiceId: Long, issueTime: String, unit: String, protocol: String = "V2"): OfficialSubmission = db.withTransaction {
        require(protocol in setOf("V2", "LEGACY")) { "روش ارسال معتبر نیست." }
        require(!directOnly || protocol == "LEGACY") { "فقط اتصال مستقیم با شناسه یکتا و کلید خصوصی مجاز است." }
        sessionCheck()
        val company = owner(companyId)
        val invoice = requireNotNull(db.invoiceDao().byId(invoiceId))
        val customer = requireNotNull(db.customerDao().byId(invoice.customerId))
        val details = requireNotNull(db.moadianDetailsDao().byCompany(companyId))
        val sourceLines = db.invoiceLineDao().forInvoice(invoiceId)
        if (protocol == "LEGACY") {
            require(InvoiceSubject.supportsLegacy(invoice.subject)) {
                "مسیر بدون گواهی فعلاً برای اصلی، اصلاحی و ابطالی نوع یک، الگوی فروش یک فعال است."
            }
            checkLegacySigningReadiness(companyId)
        }

        // Validate the accepted source again inside the immutable outbox transaction.
        InvoiceSubject.requireLocalAcceptedReference(db, account.ownerId, companyId,
            customer.id, invoice.subject, invoice.referenceTaxId, sourceLines,
            invoiceId, invoice.issueDate)
        val existing = db.officialSubmissionDao().forInvoice(invoiceId)
        require(invoice.trackingCode.isNullOrBlank() || (existing != null && existing.referenceNumber == invoice.trackingCode)) {
            "صورتحساب دارای کد پیگیری قدیمی است؛ ساخت درخواست تازه مجاز نیست و سابقه باید بررسی شود."
        }
        // Preserve the originally issued timestamp and tax ID for an explicitly reopened, never-posted request.
        if(existing!=null) require(existing.ownerId==account.ownerId && existing.companyId==companyId &&
            existing.fiscalId==details.fiscalMemoryId) { "حافظه یا مالک شماره مالیاتی قابل تغییر نیست." }
        val reservedTime = existing?.takeIf { it.state=="RESERVED" }?.let {
            require(SubmissionPreparation.sha256Hex(it.payload.toByteArray(Charsets.UTF_8))==it.payloadHash)
            Instant.ofEpochMilli(JSONObject(it.payload).getJSONObject("header").getLong("indatim")).toString()
        }
        val payload = MoadianInvoiceMapper.map(company,customer,db.customerProfileDao().byCustomer(customer.id),details,invoice,
            sourceLines,reservedTime ?: issueTime,unit,clock())
        requireOfficialReference(invoice, JSONObject(payload).getJSONObject("header"))
        require(payload.length <= 2_000_000) { "حجم صورتحساب بیش از حد مجاز است." }
        if (existing != null) {
            if(existing.state=="RESERVED") {
                require(invoice.status=="FINALIZED" && existing.referenceNumber.isNullOrBlank() && existing.signedReceipt.isNullOrBlank())
                require(JSONObject(payload).getJSONObject("header").getString("taxid")==existing.taxId) {
                    "شماره مالیاتی محفوظ باید بدون تغییر باقی بماند."
                }
                check(db.officialSubmissionDao().refreshReserved(existing.requestId,payload,
                    SubmissionPreparation.sha256Hex(payload.toByteArray(Charsets.UTF_8)),protocol)==1)
                return@withTransaction requireNotNull(db.officialSubmissionDao().byId(existing.requestId))
            }
            require(existing.protocol == protocol && existing.payload == payload) { "صورتحساب قبلاً با محتوای ثابت آماده شده است." }
            return@withTransaction existing
        }
        val taxId = JSONObject(payload).getJSONObject("header").getString("taxid")
        val serial = ir.saba.moadian.TaxIdGenerator.validate(taxId).serialHex.toLong(16)
        require((db.officialSubmissionDao().forFiscalMemory(details.fiscalMemoryId).map { it.taxId } +
            db.rejectedSubmissionHistoryDao().forFiscalMemory(details.fiscalMemoryId).map { it.taxId }).none {
            ir.saba.moadian.TaxIdGenerator.validate(it).serialHex.toLong(16) >= serial
        }) { "سریال مالیاتی باید از سریال‌های ثبت‌شده این حافظه بزرگ‌تر باشد؛ تغییر تاریخ یا صفرهای ابتدا مجوز تکرار نمی‌دهد." }
        OfficialSubmission(UUID.randomUUID().toString(),account.ownerId,companyId,invoiceId,details.fiscalMemoryId,taxId,payload,
            SubmissionPreparation.sha256Hex(payload.toByteArray(Charsets.UTF_8)),clock(),protocol=protocol).also { db.officialSubmissionDao().insert(it) }
    }
    suspend fun reviewPrepared(id: String): OfficialSubmission {
        sessionCheck()
        return row(id).also {
            require(it.state == "PREPARED")
            require(it.protocol in setOf("LEGACY","V2"))
            if (it.protocol == "LEGACY") require(
                InvoiceSubject.supportsLegacy(requireNotNull(db.invoiceDao().byId(it.invoiceId)).subject))
            val payload=JSONObject(it.payload)
            val header=payload.getJSONObject("header")
            require(header.getString("taxid")==it.taxId)
            val invoice = requireNotNull(db.invoiceDao().byId(it.invoiceId))
            val company = owner(it.companyId)
            val expectedSubject = InvoiceSubject.officialSubjectCode(invoice)
            require(header.getInt("ins") == expectedSubject) {
                "موضوع سند محفوظ با موضوع صورتحساب نهایی‌شده یکسان نیست."
            }
            require(header.getString("tins") == company.economicId) {
                "شماره اقتصادی فروشنده در سند محفوظ متفاوت است."
            }
            require(header.getInt("inty") == 1 && header.getInt("inp") == 1) {
                "نوع یا الگوی این سند برای ارسال رسمی پشتیبانی نمی‌شود."
            }
            if (expectedSubject == 1) require(!header.has("irtaxid"))
            else require(header.getString("irtaxid") == invoice.referenceTaxId) {
                "شناسه مرجع سند محفوظ با سند ارجاعی یکسان نیست."
            }
            if (expectedSubject != 1) {
                InvoiceSubject.requireLocalAcceptedReference(db, account.ownerId, it.companyId,
                    invoice.customerId, invoice.subject, invoice.referenceTaxId,
                    db.invoiceLineDao().forInvoice(invoice.id), invoice.id, invoice.issueDate)
                requireOfficialReference(invoice, header)
            }
            val body=payload.getJSONArray("body")
            if (header.getInt("ins")==3) {
                // Fail closed on cancellation: no buyer, amount or sale lines.
                require(body.length()==0 && payload.getJSONArray("payments").length()==0)
                require(header.getString("irtaxid").matches(Regex("[A-Z0-9]{22}")))
                require(!header.has("bid") && !header.has("tbill") && !header.has("setm"))
            } else require(body.length()>0)
        }
    }
    suspend fun discardPrepared(id: String) = db.withTransaction {
        sessionCheck()
        val existing=requireNotNull(db.officialSubmissionDao().byId(id))
        require(existing.ownerId==account.ownerId && db.companyDao().byId(existing.companyId)?.purchaserId==account.ownerId)
        // Cancel the prepared content, not the allocated fiscal identity.
        require(db.officialSubmissionDao().reservePreparedIdentity(id) == 1) { "درخواست شروع شده و قابل لغو نیست؛ استعلام کنید." }
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

    /**
     * User-confirmed foreground operation: one POST at most, then immediate
     * inquiry of the same durable request. A pending or ambiguous response
     * never generates another invoice, UUID, or debit. The existing inquiry
     * paths persist each result atomically with the invoice tracking code.
     */
    suspend fun submitAndInquire(
        id: String, onUpdate: (OfficialSubmission) -> Unit = {}
    ): OfficialSubmission {
        sessionCheck()
        val initial = row(id)
        require(initial.state == "PREPARED") {
            "درخواست قبلاً آغاز شده است؛ فقط استعلام همان درخواست مجاز است."
        }
        val sent = when (initial.protocol) {
            "LEGACY" -> submitLegacy(id)
            "V2" -> submit(id)
            else -> error("روش ارسال معتبر نیست.")
        }
        onUpdate(sent)
        val awaiting = setOf("SENDING", "UNKNOWN", "SUBMITTED")
        if (sent.state !in awaiting) return sent

        // The timeout is for the foreground inquiry budget, not the invoice POST.
        // Network cancellation retains the durable ambiguous state. No WorkManager,
        // background polling, or automatic resubmission is started.
        withTimeoutOrNull(30_000L) {
            repeat(3) { attempt ->
                if (attempt > 0) delay(if (attempt == 1) 1_500L else 3_000L)
                sessionCheck()
                val current = row(id)
                if (current.state !in awaiting) return@withTimeoutOrNull
                val result = when (current.protocol) {
                    "LEGACY" -> inquireLegacy(id)
                    "V2" -> inquire(id)
                    else -> error("روش استعلام معتبر نیست.")
                }
                onUpdate(result)
                if (result.state !in awaiting) return@withTimeoutOrNull
            }
        }
        return row(id)
    }

    /** Caller must get explicit user confirmation before invoking this real financial document operation. */
    suspend fun submit(id: String): OfficialSubmission {
        sessionCheck()
        require(!directOnly) { "ارسال جدید فقط با اتصال مستقیم مجاز است." }
        // Revalidate the immutable official payload (including cancellation shape)
        // on the actual send path; the UI review step can be bypassed or become stale.
        val initial = reviewPrepared(id)
        require(initial.protocol == "V2") { "برای این درخواست باید از مسیر ارسال بدون گواهی استفاده شود." }
        require(initial.state == "PREPARED") { "این درخواست ممکن است ارسال شده باشد؛ ابتدا استعلام کنید." }
        // Fail closed before any HTTP, signing or outbox transition when a future
        // local send-time was selected. This is not an unattended scheduler.
        val sourceInvoice = requireNotNull(db.invoiceDao().byId(initial.invoiceId))
        InvoiceSendSchedule.requireDueForManualSubmission(
            InvoiceLocalDetails.fromJson(sourceInvoice.localDetailsJson),
            Instant.ofEpochMilli(clock())
        )
        checkLocalSigningReadiness(initial.companyId)
        val keys = keys(initial)
        val encryption = keys.firstOrNull { it.purpose == 1 } ?: error("کلید رمزگذاری معتبر سازمان یافت نشد.")
        val identity = TaxpayerIdentity(account,initial.companyId,initial.fiscalId)
        val signed = signer.invoice(initial.companyId,identity,initial.payload,clock())
        val packet = JSONObject().put("payload",MoadianJwe.encrypt(signed,encryption.id,encryption.key))
            .put("header",JSONObject().put("requestTraceId",initial.requestId).put("fiscalId",initial.fiscalId))
        val token = authorization(initial)
        db.withTransaction {
            sessionCheck()
            require(row(id)==initial) { "محتوای سند تغییر کرده؛ دوباره بررسی کنید." }
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
    /** Certificate-free self-TSP: preflight completes before claiming the immutable outbox. */
    suspend fun submitLegacy(id: String): OfficialSubmission {
        sessionCheck()
        val initial = reviewPrepared(id)
        require(initial.protocol == "LEGACY")
        val invoice = requireNotNull(db.invoiceDao().byId(initial.invoiceId))
        InvoiceSendSchedule.requireDueForManualSubmission(
            InvoiceLocalDetails.fromJson(invoice.localDetailsJson), Instant.ofEpochMilli(clock()))
        val prepared = try {
            checkLegacySigningReadiness(initial.companyId)
            legacyGateway.prepare(initial.companyId, initial.fiscalId, initial.requestId, initial.payload)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val safe = if (e is LegacyPreflightFailure) e
                else LegacyPreflightFailure("بررسی کلید و آماده‌سازی اتصال", e)
            db.officialSubmissionDao().recordPreflightError(id, account.ownerId, requireNotNull(safe.message))
            throw safe
        }
        db.withTransaction {
            sessionCheck()
            require(row(id)==initial) { "محتوای سند تغییر کرده؛ دوباره بررسی کنید." }
            check(db.officialSubmissionDao().claim(id) == 1) {
                "ارسال این درخواست آغاز شده است؛ ابتدا نتیجه را استعلام کنید."
            }
        }
        try {
            val ack = legacyGateway.send(prepared, initial.requestId)
            require(ack.uid == initial.requestId)
            resolve(id, "SUBMITTED", ack.referenceNumber, null)
        } catch (failure: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                resolve(id, "UNKNOWN", null,
                    "پاسخ قطعی دریافت نشد؛ ارسال مجدد ممنوع است. فقط شناسه همین درخواست را استعلام کنید.")
            }
            if (failure is CancellationException) throw failure
        }
        return requireNotNull(db.officialSubmissionDao().byId(id))
    }

    /** Read-only reconciliation; SUCCESS from the legacy API is not a v2 signed receipt. */
    suspend fun inquireLegacy(id: String): OfficialSubmission {
        sessionCheck()
        val initial = row(id)
        require(initial.protocol == "LEGACY")
        require(initial.state in setOf("SENDING", "UNKNOWN", "SUBMITTED"))
        try {
            val result = legacyGateway.inquire(initial.companyId, initial.fiscalId, initial.requestId, initial.taxId)
            require(result.uid == initial.requestId)
            require(result.taxId == null || result.taxId == initial.taxId)
            require(initial.referenceNumber == null || result.referenceNumber == initial.referenceNumber)
            when (result.status) {
                "SUCCESS" -> {
                    // The legacy endpoint reports success over authenticated TLS, but the
                    // result is not a cryptographically verifiable v2 signed receipt.
                    require(!result.referenceNumber.isNullOrBlank())
                    val evidence = if (legacyIdentityBound(initial, result))
                        OfficialReferenceEvidence.LEGACY_BOUND else OfficialReferenceEvidence.LEGACY_UNBOUND
                    resolve(id, "ACCEPTED", result.referenceNumber, evidence)
                }
                "FAILED" -> resolve(id, "REJECTED", result.referenceNumber,
                    taxRejectionMessage(result.errorDetails.ifEmpty { result.errorCodes }))
                "PENDING", "IN_PROGRESS" -> resolve(id, "SUBMITTED", result.referenceNumber,
                    "صورتحساب در صف اعتبارسنجی سامانه است.")
                else -> resolve(id, "UNKNOWN", result.referenceNumber, "نتیجه قطعی نیست؛ دوباره استعلام کنید.")
            }
        } catch (failure: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                resolve(id, "UNKNOWN", null, "استعلام معتبر دریافت نشد؛ ارسال مجدد نکنید.")
            }
            if (failure is CancellationException) throw failure
        }
        return requireNotNull(db.officialSubmissionDao().byId(id))
    }

    private fun legacyIdentityBound(row: OfficialSubmission, result: LegacyInvoiceInquiry): Boolean =
        result.status == "SUCCESS" && result.uid == row.requestId &&
            result.fiscalId == row.fiscalId && result.taxId == row.taxId &&
            !result.referenceNumber.isNullOrBlank() &&
            (row.referenceNumber == null || row.referenceNumber == result.referenceNumber)

    /** Read-only recheck for older accepted legacy records; no new invoice POST.
     * A missing identifier is not replaced with the request's identifier. */
    suspend fun verifyLegacyReference(id: String): OfficialSubmission {
        sessionCheck()
        val initial = row(id)
        require(initial.protocol == "LEGACY" && initial.state == "ACCEPTED")
        val result = legacyGateway.inquire(initial.companyId, initial.fiscalId, initial.requestId, initial.taxId)
        require(legacyIdentityBound(initial, result)) {
            "تطبیق کامل شماره مالیاتی، حافظه و پیگیری مرجع در پاسخ استعلام ممکن نشد؛ سند ارجاعی فعلاً مجاز نیست. این نتیجه به معنی رد فاکتور نیست؛ ارسال مجدد نکنید."
        }
        db.withTransaction {
            sessionCheck()
            row(id)
            check(db.officialSubmissionDao().confirmLegacyReference(id,
                requireNotNull(result.referenceNumber), OfficialReferenceEvidence.LEGACY_BOUND) == 1)
        }
        return row(id)
    }

    suspend fun inquire(id: String): OfficialSubmission {
        sessionCheck()
        val initial = row(id)
        require(initial.protocol == "V2") { "استعلام این درخواست از مسیر بدون گواهی انجام می‌شود." }
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
                    val message = taxRejectionMessage(taxErrorDetails(codes))
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
