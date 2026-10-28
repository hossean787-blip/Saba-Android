package ir.saba.invoice.data

import androidx.room.withTransaction

/**
 * Transactional local quota operations.
 * The purchaser/master account is the only account allowed to transfer quota.
 * This is local app logic; commercial authorization must also be enforced by a trusted server.
 */
class QuotaRepository(private val db: SabaDatabase) {
    private val dao get() = db.quotaDao()

    suspend fun initializeWallet(purchaserId: String, purchasedCount: Long) {
        require(purchaserId.isNotBlank()) { "شناسه خریدار الزامی است." }
        require(purchasedCount >= 0) { "تعداد سهمیه نمی‌تواند منفی باشد." }
        db.withTransaction {
            require(dao.wallet(purchaserId) == null) { "کیف سهمیه این خریدار قبلاً ایجاد شده است." }
            dao.insertWallet(QuotaWallet(purchaserId, purchasedCount, purchasedCount))
        }
    }

    /** Idempotent for the same operationId and same request. */
    suspend fun transferFromMaster(
        purchaserId: String,
        companyId: Long,
        amount: Long,
        operationId: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ) {
        require(purchaserId.isNotBlank()) { "شناسه خریدار الزامی است." }
        require(companyId > 0) { "شرکت معتبر نیست." }
        require(amount > 0) { "تعداد انتقال باید بیشتر از صفر باشد." }
        require(operationId.isNotBlank()) { "شناسه عملیات الزامی است." }

        db.withTransaction {
            val existing = dao.transferByOperationId(operationId)
            if (existing != null) {
                require(existing.purchaserId == purchaserId && existing.companyId == companyId && existing.amount == amount) {
                    "شناسه عملیات قبلاً برای درخواست دیگری استفاده شده است."
                }
                return@withTransaction
            }

            val company = db.companyDao().byId(companyId) ?: error("شرکت پیدا نشد.")
            require(company.purchaserId == purchaserId) { "فقط خریدار اصلی همین شرکت می‌تواند سهمیه تخصیص دهد." }
            val wallet = dao.wallet(purchaserId) ?: error("کیف سهمیه خریدار پیدا نشد.")
            require(wallet.available >= amount) { "سهمیه آزاد حساب اصلی کافی نیست." }

            val current = dao.companyQuota(companyId)
            if (current == null) {
                dao.insertCompanyQuota(CompanyQuota(companyId, purchaserId, amount, amount))
            } else {
                require(current.purchaserId == purchaserId) { "مالک سهمیه شرکت با خریدار مطابقت ندارد." }
                dao.updateCompanyQuota(current.copy(
                    allocatedTotal = Math.addExact(current.allocatedTotal, amount),
                    available = Math.addExact(current.available, amount)
                ))
            }
            dao.updateWallet(wallet.copy(available = wallet.available - amount))
            dao.insertTransfer(QuotaTransfer(
                operationId = operationId,
                purchaserId = purchaserId,
                companyId = companyId,
                amount = amount,
                transferredAtEpochMillis = nowEpochMillis
            ))
        }
    }

}
