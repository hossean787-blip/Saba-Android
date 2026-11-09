package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.domain.InvoiceSettlement
import ir.saba.invoice.domain.InvoiceSubject
import ir.saba.invoice.security.AccountScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class InvoiceCreditKind { PAID, GIFT }
data class InvoiceCreationSnapshot(
    val customerId: Long, val number: String, val date: String, val lines: List<InvoiceLine>,
    val settlementMode: String = InvoiceSettlement.CASH, val cashPaidRial: Long? = null,
    val subject: String = InvoiceSubject.ORIGINAL, val referenceTaxId: String? = null,
    val localDetailsJson: String = ""
)

/** Durable, immutable request before remote debit; invoice and local completion receipt commit together.
 * Server authorization is required on every pending attempt, always with the original key/kind.
 */
class InvoiceCreationCoordinator(
    private val db:SabaDatabase,
    private val account:AccountScope,
    private val authorize:suspend(InvoiceCreditKind,Long,String)->Unit
) {
    private suspend fun requireOwner(companyId:Long,active:Boolean=true) {
        require(db.companyDao().byId(companyId)?.purchaserId==account.ownerId){"مودی متعلق به این حساب نیست."}
        if(active) require(db.moadianDetailsDao().byCompany(companyId)?.inactive!=true){"مودی غیرفعال است."}
    }
    suspend fun pending(companyId:Long):InvoiceCreationClaim?=db.withTransaction {
        requireOwner(companyId,false)
        db.invoiceCreationClaimDao().pending(companyId)?.also{require(it.ownerId==account.ownerId)}
    }
    fun snapshot(claim:InvoiceCreationClaim):InvoiceCreationSnapshot {
        require(claim.ownerId==account.ownerId)
        require(claim.payloadJson.length in 1..2_000_000)
        val root=JSONObject(claim.payloadJson)
        val rows=root.getJSONArray("lines")
        val lines=(0 until rows.length()).map { index ->
            val item=rows.getJSONObject(index)
            InvoiceLine(invoiceId=0,itemCode=item.getString("code"),description=item.getString("description"),
                quantity=item.getString("quantity").toLong(),unitPriceRial=item.getString("price").toLong(),
                discountRial=item.getString("discount").toLong(),taxRatePercent=item.getString("tax").toInt(),
                unitCode=item.optString("unitCode"), quantityExact=item.optString("quantityExact"),
                vatRateExact=item.optString("vatRateExact"))
        }
        val mode = root.optString("settlementMode", InvoiceSettlement.CASH)
        val cash = if (root.has("cashPaidRial") && !root.isNull("cashPaidRial"))
            root.getLong("cashPaidRial") else null
        return InvoiceCreationSnapshot(root.getString("customer").toLong(),root.getString("number"),root.getString("date"),lines,mode,cash,
            root.optString("subject", InvoiceSubject.ORIGINAL), root.optString("referenceTaxId").takeIf { it.isNotBlank() },
            root.optString("localDetailsJson"))
    }
    private fun encode(snapshot:InvoiceCreationSnapshot):String {
        val rows=JSONArray()
        snapshot.lines.forEach { rows.put(JSONObject().put("code",it.itemCode).put("description",it.description)
            .put("quantity",it.quantity.toString()).put("price",it.unitPriceRial.toString())
            .put("discount",it.discountRial.toString()).put("tax",it.taxRatePercent.toString())
            .put("unitCode",it.unitCode).put("quantityExact",it.quantityExact)
            .put("vatRateExact",it.vatRateExact)) }
        val root = JSONObject().put("customer",snapshot.customerId.toString()).put("number",snapshot.number)
            .put("date",snapshot.date).put("lines",rows).put("settlementMode",snapshot.settlementMode)
        snapshot.cashPaidRial?.let { root.put("cashPaidRial", it) }
        root.put("subject", snapshot.subject)
        snapshot.referenceTaxId?.let { root.put("referenceTaxId", it) }
        root.put("localDetailsJson", snapshot.localDetailsJson)
        return root.toString().also { require(it.length<=2_000_000) }
    }
    suspend fun prepare(companyId:Long,input:InvoiceCreationSnapshot,requestId:String,kind:InvoiceCreditKind):InvoiceCreationClaim = db.withTransaction {
        requireOwner(companyId)
        val key=UUID.fromString(requestId).toString()
        require(key==requestId.lowercase(java.util.Locale.ROOT) && UUID.fromString(key).version() in 1..8 && UUID.fromString(key).variant()==2)
        require(db.customerDao().byId(input.customerId)?.companyId==companyId){"مشتری متعلق به این مؤدی نیست."}
        val lines=input.lines.map{it.copy(id=0,invoiceId=0)}
        val totals = InvoiceCalculator.calculate(lines)
        InvoiceSettlement.amounts(input.settlementMode,input.cashPaidRial,totals.totalAmountRial)
        if(input.localDetailsJson.isNotBlank())
            InvoiceLocalDetails.fromJson(input.localDetailsJson).requireValidSchedule()
        InvoiceSubject.requireLocalAcceptedReference(db, account.ownerId, companyId,
            input.customerId, input.subject, input.referenceTaxId, lines,
            newIssueDate = input.date)
        val existing=db.invoiceCreationClaimDao().byKey(key) ?: db.invoiceCreationClaimDao().pending(companyId)
        if(existing!=null) {
            require(existing.ownerId==account.ownerId && existing.companyId==companyId){"درخواست متعلق به حساب یا مؤدی دیگری است."}
            val original=snapshot(existing)
            val normalized=input.copy(number=InvoiceInput.digits(input.number).trim().ifBlank{original.number},date=InvoiceInput.date(input.date),lines=lines)
            require(normalized==original){"درخواست قبلی ناتمام است؛ همان فاکتور را بازیابی و تکمیل کنید."}
            return@withTransaction existing
        }
        val number=InvoiceInput.digits(input.number).trim().ifBlank { TaxpayerSerialAllocator(db).reserve(companyId) }
        require(db.invoiceDao().forCompany(companyId).none{InvoiceInput.number(it.invoiceNumber)==InvoiceInput.number(number)}){"شماره صورتحساب قبلاً ثبت شده است."}
        val normalized=input.copy(number=number,date=InvoiceInput.date(input.date),lines=lines)
        InvoiceCreationClaim(key,account.ownerId,companyId,kind.name,encode(normalized)).also{db.invoiceCreationClaimDao().insert(it)}
    }
    private suspend fun validatePending(claim: InvoiceCreationClaim): InvoiceCreationSnapshot {
        val content = snapshot(claim)
        InvoiceCreditKind.valueOf(claim.creditKind)
        InvoiceInput.date(content.date)
        val totals=InvoiceCalculator.calculate(content.lines)
        InvoiceSettlement.amounts(content.settlementMode,content.cashPaidRial,totals.totalAmountRial)
        InvoiceSubject.requireLocalAcceptedReference(db, account.ownerId, claim.companyId,
            content.customerId, content.subject, content.referenceTaxId, content.lines,
            newIssueDate = content.date)
        require(InvoiceInput.number(content.number).isNotEmpty()) { "شماره صورتحساب محفوظ معتبر نیست." }
        require(db.customerDao().byId(content.customerId)?.companyId == claim.companyId) { "مشتری درخواست محفوظ متعلق به این مؤدی نیست." }
        require(db.invoiceDao().forCompany(claim.companyId).none {
            InvoiceInput.number(it.invoiceNumber) == InvoiceInput.number(content.number)
        }) { "شماره درخواست محفوظ قبلاً در فاکتور دیگری ثبت شده است." }
        return content
    }
    suspend fun finish(requestId:String):Long {
        val key=UUID.fromString(requestId).toString()
        val initial=db.withTransaction {
            val claim=db.invoiceCreationClaimDao().byKey(key)?:error("درخواست ایجاد فاکتور یافت نشد.")
            requireOwner(claim.companyId,claim.invoiceId==null)
            require(claim.ownerId==account.ownerId)
            if (claim.invoiceId == null) validatePending(claim)
            claim
        }
        initial.invoiceId?.let {
            require(db.invoiceDao().byId(it)?.companyId==initial.companyId)
            return it
        }
        // No persisted flag or balance can substitute for this authenticated server call.
        authorize(InvoiceCreditKind.valueOf(initial.creditKind),initial.companyId,initial.requestId)
        return db.withTransaction {
            val claim=requireNotNull(db.invoiceCreationClaimDao().byKey(key))
            require(claim.ownerId==initial.ownerId && claim.companyId==initial.companyId &&
                claim.creditKind==initial.creditKind && claim.payloadJson==initial.payloadJson)
            claim.invoiceId?.let{return@withTransaction it}
            requireOwner(claim.companyId)
            val content=validatePending(claim)
            val id=InvoiceRepository(db).saveDraft(claim.companyId,content.customerId,content.number,content.date,
                content.lines,serverPromoConfirmed=true,settlementMode=content.settlementMode,cashPaidRial=content.cashPaidRial,
                subject=content.subject,referenceTaxId=content.referenceTaxId,
                localDetailsJson=content.localDetailsJson)
            check(db.invoiceCreationClaimDao().complete(claim.requestId,id)==1)
            id
        }
    }
}
