package ir.saba.invoice.data

import androidx.room.withTransaction

/** Local transactional allocation. Commercial entitlement must be independently verified. */
class QuotaRepository(private val db: SabaDatabase) {
    private val dao get() = db.quotaDao()

    suspend fun initializeWallet(purchaserId: String, purchasedCount: Long) {
        require(purchaserId.isNotBlank() && purchasedCount >= 0)
        db.withTransaction {
            require(dao.wallet(purchaserId) == null)
            dao.insertWallet(QuotaWallet(purchaserId, purchasedCount, purchasedCount))
        }
    }

    suspend fun transferFromMaster(
        purchaserId: String, companyId: Long, amount: Long,
        operationId: String, nowEpochMillis: Long = System.currentTimeMillis()
    ) {
        require(purchaserId.isNotBlank() && companyId > 0 && amount > 0 && operationId.isNotBlank())
        db.withTransaction {
            val existing = dao.transferByOperationId(operationId)
            if (existing != null) {
                require(existing.purchaserId == purchaserId && existing.companyId == companyId && existing.amount == amount)
                return@withTransaction
            }
            val company = db.companyDao().byId(companyId) ?: error("شرکت پیدا نشد.")
            require(company.purchaserId == purchaserId) { "فقط مالک اصلی می‌تواند سهمیه انتقال دهد." }
            val wallet = dao.wallet(purchaserId) ?: error("کیف سهمیه پیدا نشد.")
            require(wallet.available >= amount) { "سهمیه کافی نیست." }
            val current = dao.companyQuota(companyId)
            if (current == null) {
                dao.insertCompanyQuota(CompanyQuota(companyId, purchaserId, amount, amount))
            } else {
                require(current.purchaserId == purchaserId)
                dao.updateCompanyQuota(current.copy(
                    allocatedTotal = Math.addExact(current.allocatedTotal, amount),
                    available = Math.addExact(current.available, amount)
                ))
            }
            dao.updateWallet(wallet.copy(available = wallet.available - amount))
            dao.insertTransfer(QuotaTransfer(
                operationId = operationId, purchaserId = purchaserId, companyId = companyId,
                amount = amount, transferredAtEpochMillis = nowEpochMillis
            ))
        }
    }
}