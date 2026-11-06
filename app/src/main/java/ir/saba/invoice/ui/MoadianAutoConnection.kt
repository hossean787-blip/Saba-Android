package ir.saba.invoice.ui

import ir.saba.invoice.data.SabaDatabase
import ir.saba.invoice.security.MoadianLegacyConnectionProbe
import ir.saba.invoice.security.TaxpayerCredentialRepository
import kotlinx.coroutines.CancellationException

/** Saves and checks credentials only AFTER the taxpayer record is committed.
 * A network failure never rolls back or removes a saved taxpayer or encrypted key. */
internal class MoadianAutoConnection(
    private val db: SabaDatabase,
    private val credentials: TaxpayerCredentialRepository,
    private val checkConnection: suspend (Long, String) -> Unit = { id, memory ->
        MoadianLegacyConnectionProbe(credentials).check(id, memory)
    }
) {
    data class Result(val status: String, val message: String)

    suspend fun complete(companyId: Long, memoryId: String, importedPrivateKey: String): Result {
        if (importedPrivateKey.isNotBlank()) {
            try {
                credentials.stagePrivateKey(companyId, importedPrivateKey)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                return update(companyId, "KEY_STORE_FAILED",
                    "مشخصات مؤدی ذخیره شد اما کلید خصوصی ذخیره نشد؛ از مدیریت کلید دوباره امتحان کنید.")
            }
        }
        val hasKey = try { credentials.hasPendingPrivateKey(companyId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        if (!hasKey) return Result("UNCHECKED",
            "مشخصات مؤدی ذخیره شد؛ برای بررسی اتصال، کلید خصوصی و شناسه یکتا لازم است.")
        if (memoryId.isBlank()) return update(companyId, "KEY_STORED",
            "مؤدی و کلید خصوصی ذخیره شدند؛ پس از ثبت شناسه یکتا اتصال بررسی می‌شود.")
        return try {
            checkConnection(companyId, memoryId)
            update(companyId, "LEGACY_OK",
                "مؤدی ذخیره شد؛ آخرین استعلام اتصال بدون گواهی موفق بود. ارسال فاکتور هنوز آزمایش نشده است.")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            update(companyId, "LEGACY_FAILED",
                "مؤدی و کلید خصوصی ذخیره شدند؛ اتصال تأیید نشد. ثبت کلید عمومی و شناسه یکتا را بررسی کنید.")
        }
    }

    private suspend fun update(id: Long, status: String, message: String): Result {
        db.moadianDetailsDao().byCompany(id)?.let { current ->
            db.moadianDetailsDao().upsert(current.copy(keyCheckStatus = status))
        }
        return Result(status, message)
    }
}
