package ir.saba.invoice.data

import androidx.room.withTransaction
import ir.saba.invoice.security.AccountScope

/** Legacy ownership is evidence, not a user-editable claim. Unknown/foreign owners remain quarantined. */
class LegacyAccountImporter(private val source: SabaDatabase, private val target: SabaDatabase, private val account: AccountScope) {
    private fun owns(owner: String) = runCatching { AccountScope.of(owner) == account }.getOrDefault(false)
    suspend fun eligible(): List<Company> = source.companyDao().all().filter { owns(it.purchaserId) }

    private data class Snapshot(val company: Company, val details: MoadianDetails?, val customers: List<Customer>,
        val profiles: List<CustomerProfile>, val invoices: List<Invoice>, val lines: List<InvoiceLine>,
        val uses: List<InvoiceQuotaUse>, val exemptions: List<Long>, val simulations: List<MoadianSimulation>,
        val claims: List<InvoiceCreationClaim>, val transfers: List<CreditTransferClaim>)

    /** Source stays intact; target imports all eligible selected-company records or none. IDs stay stable. */
    suspend fun importCompany(companyId: Long, now: Long = System.currentTimeMillis()): Boolean {
        require(source !== target)
        val snapshot = source.withTransaction {
            val company = requireNotNull(source.companyDao().byId(companyId))
            require(owns(company.purchaserId)) { "مالک معتبر این سابقه با حساب واردشده مطابق نیست؛ انتقال مجاز نیست." }
            val invoices = source.invoiceDao().forCompany(companyId)
            val uses = invoices.mapNotNull { source.quotaDao().useForInvoice(it.id) }
            require(uses.all { owns(it.purchaserId) && it.companyId == companyId })
            val transfers = source.creditTransferClaimDao().forCompany(companyId)
            require(transfers.all { owns(it.ownerId) })
            val claims = source.invoiceCreationClaimDao().forCompany(companyId)
            require(claims.all { owns(it.ownerId) && (it.invoiceId == null || invoices.any { invoice -> invoice.id == it.invoiceId }) })
            claims.filter { it.invoiceId == null }.forEach {
                val customerId = org.json.JSONObject(it.payloadJson).getString("customer").toLong()
                require(source.customerDao().byId(customerId)?.companyId == companyId)
            }
            // Never accidentally move a real in-flight official submission between stores.
            require(source.officialSubmissionDao().forCompany(companyId).isEmpty()) { "پرونده دارای درخواست رسمی است؛ انتقال نیازمند بررسی پشتیبانی است." }
            Snapshot(company,source.moadianDetailsDao().byCompany(companyId),source.customerDao().forCompany(companyId),
                source.customerProfileDao().forCompany(companyId),invoices,invoices.flatMap { source.invoiceLineDao().forInvoice(it.id) },
                uses,invoices.filter { source.legacyQuotaExemptionDao().contains(it.id) == 1 }.map { it.id },
                source.moadianSimulationDao().forCompany(companyId),claims,transfers)
        }
        return target.withTransaction {
            val receipt = target.legacyImportReceiptDao().byCompany(companyId)
            if (receipt != null) {
                require(receipt.ownerId == account.ownerId)
                return@withTransaction false
            }
            // ABORT on every ID/national-ID collision; no REPLACE can overwrite existing work.
            require(target.companyDao().byId(companyId) == null) { "شناسه مؤدی در مقصد وجود دارد؛ هیچ اطلاعاتی بازنویسی نشد." }
            require(target.moadianDetailsDao().byCompany(companyId) == null)
            target.companyDao().insert(snapshot.company.copy(purchaserId=account.ownerId))
            snapshot.details?.let { target.moadianDetailsDao().upsert(it.copy(encryptedPrivateKey=null,keyCheckStatus="UNCHECKED")) }
            snapshot.customers.forEach { target.customerDao().insert(it) }
            snapshot.profiles.forEach {
                require(snapshot.customers.any { c -> c.id == it.customerId })
                require(target.customerProfileDao().byCustomer(it.customerId) == null)
                target.customerProfileDao().upsert(it)
            }
            snapshot.invoices.forEach { target.invoiceDao().insert(it) }
            target.invoiceLineDao().insertAll(snapshot.lines)
            snapshot.uses.forEach { target.quotaDao().insertQuotaUse(it.copy(purchaserId=account.ownerId)) }
            snapshot.exemptions.forEach { target.legacyQuotaExemptionDao().insert(LegacyQuotaExemption(it)) }
            snapshot.simulations.forEach { target.moadianSimulationDao().insert(it) }
            snapshot.claims.forEach { target.invoiceCreationClaimDao().insert(it.copy(ownerId=account.ownerId)) }
            snapshot.transfers.forEach { target.creditTransferClaimDao().insert(it.copy(ownerId=account.ownerId)) }
            // Cached wallet/allocation balances are not proof of paid credits and are never imported.
            target.legacyImportReceiptDao().insert(LegacyImportReceipt(companyId,account.ownerId,now))
            true
        }
    }
}
