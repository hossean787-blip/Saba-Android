package ir.saba.invoice.domain

/** Pure rules only. Persistent quota transfers must be performed transactionally in the database. */
data class QuotaState(val purchased: Long, val masterAvailable: Long, val companyAllocations: Map<Long, Long>) {
    init {
        require(purchased >= 0 && masterAvailable >= 0)
        require(companyAllocations.values.all { it >= 0 })
        val allocated = companyAllocations.values.fold(0L) { total, amount -> Math.addExact(total, amount) }
        require(Math.addExact(masterAvailable, allocated) <= purchased) {
            "مجموع سهمیه تخصیص‌یافته و موجودی اصلی از سهمیه خریداری‌شده بیشتر است."
        }
    }
}

object QuotaRules {
    fun transfer(state: QuotaState, companyId: Long, amount: Long): QuotaState {
        require(amount > 0) { "مقدار انتقال باید بیشتر از صفر باشد." }
        require(state.masterAvailable >= amount) { "سهمیه آزاد حساب اصلی کافی نیست." }
        val updated = state.companyAllocations.toMutableMap()
        updated[companyId] = Math.addExact(updated[companyId] ?: 0L, amount)
        return state.copy(masterAvailable = state.masterAvailable - amount, companyAllocations = updated)
    }

    fun consume(state: QuotaState, companyId: Long): QuotaState {
        val available = state.companyAllocations[companyId] ?: 0L
        require(available > 0) { "سهمیه شرکت برای صدور فاکتور کافی نیست." }
        val updated = state.companyAllocations.toMutableMap()
        updated[companyId] = available - 1
        return state.copy(companyAllocations = updated)
    }
}