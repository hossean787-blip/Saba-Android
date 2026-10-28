package ir.saba.invoice.data

import androidx.room.*

@Dao interface QuotaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWallet(wallet: QuotaWallet)
    @Query("SELECT * FROM quota_wallets WHERE purchaserId = :purchaserId LIMIT 1") suspend fun wallet(purchaserId: String): QuotaWallet?
    @Update suspend fun updateWallet(wallet: QuotaWallet)
    @Query("SELECT * FROM company_quotas WHERE companyId = :companyId LIMIT 1") suspend fun companyQuota(companyId: Long): CompanyQuota?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCompanyQuota(quota: CompanyQuota)
    @Update suspend fun updateCompanyQuota(quota: CompanyQuota)
    @Query("SELECT * FROM quota_transfers WHERE operationId = :operationId LIMIT 1") suspend fun transferByOperationId(operationId: String): QuotaTransfer?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransfer(transfer: QuotaTransfer): Long
    @Query("SELECT * FROM invoice_quota_uses WHERE invoiceId = :invoiceId LIMIT 1") suspend fun useForInvoice(invoiceId: Long): InvoiceQuotaUse?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertQuotaUse(use: InvoiceQuotaUse): Long
}