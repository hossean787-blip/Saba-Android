package ir.saba.invoice.security

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import ir.saba.invoice.data.SabaDatabase
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class TaxpayerCredentialProfile(val identity: TaxpayerIdentity, val certificateSha256: String, val expiresAtEpochMillis: Long) {
    override fun toString() = "TaxpayerCredentialProfile([redacted])"
}
internal class TaxpayerSigningMaterial(val identity: TaxpayerIdentity, val key: PrivateKey, val certificate: X509Certificate) {
    override fun toString() = "TaxpayerSigningMaterial([redacted])"
}

/** One store per authenticated account; each envelope also binds taxpayer and fiscal memory with AEAD. */
class TaxpayerCredentialRepository internal constructor(
    private val db: SabaDatabase,
    private val account: AccountScope,
    private val preferences: SharedPreferences,
    private val vault: TaxpayerKeyVault
) {
    constructor(context: Context, db: SabaDatabase, account: AccountScope) : this(db,account,
        context.applicationContext.getSharedPreferences("sura_tax_credentials_${account.storageKey}",Context.MODE_PRIVATE),TaxpayerKeyVault())

    private suspend fun identity(companyId: Long): TaxpayerIdentity {
        val company = db.companyDao().byId(companyId) ?: error("مودی یافت نشد.")
        require(company.purchaserId == account.ownerId) { "مودی متعلق به حساب واردشده نیست." }
        val details = db.moadianDetailsDao().byCompany(companyId) ?: error("اطلاعات حافظه مالیاتی ثبت نشده است.")
        require(!details.inactive) { "مودی غیرفعال است." }
        return TaxpayerIdentity(account,companyId,details.fiscalMemoryId)
    }
    /** Keep a valid private key encrypted until the taxpayer has an official memory ID
     *  and a matching signing certificate. A pending key can never authorize submission. */
    suspend fun stagePrivateKey(companyId: Long, privatePem: String) = db.withTransaction {
        requireOwnedActiveCompany(companyId)
        val encrypted = vault.encryptPending(account, companyId, privatePem)
        require(preferences.edit().putString("pending_company_$companyId", encrypted).commit()) {
            "ذخیره امن کلید خصوصی انجام نشد."
        }
    }

    suspend fun hasPendingPrivateKey(companyId: Long): Boolean = db.withTransaction {
        requireOwnedActiveCompany(companyId)
        preferences.contains("pending_company_$companyId")
    }

    suspend fun saveWithPendingPrivateKey(
        companyId: Long, certificateBytes: ByteArray, nowEpochMillis: Long
    ): TaxpayerCredentialProfile {
        val bytes = db.withTransaction {
            requireOwnedActiveCompany(companyId)
            val encrypted = preferences.getString("pending_company_$companyId", null)
                ?: error("کلید خصوصی ذخیره‌شده برای این مؤدی پیدا نشد.")
            vault.decryptPending(account, companyId, encrypted)
        }
        val pem = try {
            "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(bytes) +
                "\n-----END PRIVATE KEY-----"
        } finally { bytes.fill(0) }
        return save(companyId, pem, certificateBytes, nowEpochMillis)
    }

    private suspend fun requireOwnedActiveCompany(companyId: Long) {
        require(companyId > 0)
        val company = db.companyDao().byId(companyId) ?: error("مؤدی پیدا نشد.")
        require(company.purchaserId == account.ownerId) { "مؤدی متعلق به حساب واردشده نیست." }
        require(db.moadianDetailsDao().byCompany(companyId)?.inactive != true) { "مؤدی غیرفعال است." }
    }

    suspend fun save(companyId: Long, privatePem: String, certificateBytes: ByteArray, nowEpochMillis: Long): TaxpayerCredentialProfile = db.withTransaction {
        val scoped = identity(companyId)
        val cert = TaxpayerSigningCertificate.parse(certificateBytes)
        val key = TaxpayerPrivateKey.parse(privatePem)
        TaxpayerSigningCertificate.requireMatch(cert,key,nowEpochMillis)
        val record = JSONObject().put("version",2).put("fiscalMemoryId",scoped.fiscalMemoryId)
            .put("key",vault.encrypt(scoped,privatePem)).put("certificate",Base64.getEncoder().encodeToString(cert.encoded))
        require(preferences.edit().putString("company_$companyId",record.toString())
            .remove("pending_company_$companyId").commit()) { "ذخیره امن اطلاعات انجام نشد." }
        profile(scoped,cert)
    }
    suspend fun profile(companyId: Long, nowEpochMillis: Long): TaxpayerCredentialProfile? =
        material(companyId,nowEpochMillis)?.let { profile(it.identity,it.certificate) }

    internal suspend fun material(companyId: Long, nowEpochMillis: Long): TaxpayerSigningMaterial? = db.withTransaction {
        val scoped = identity(companyId)
        val raw = preferences.getString("company_$companyId",null) ?: return@withTransaction null
        require(raw.length <= 200000)
        val record = JSONObject(raw)
        require(record.getInt("version") == 2 && record.getString("fiscalMemoryId") == scoped.fiscalMemoryId) {
            "شناسه حافظه تغییر کرده است؛ کلید و گواهی را برای این حافظه دوباره ثبت کنید."
        }
        val cert = TaxpayerSigningCertificate.parse(Base64.getDecoder().decode(record.getString("certificate")))
        val bytes = vault.decrypt(scoped,record.getString("key"))
        val key = try { KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes)) } finally { bytes.fill(0) }
        TaxpayerSigningCertificate.requireMatch(cert,key,nowEpochMillis)
        TaxpayerSigningMaterial(scoped,key,cert)
    }
    suspend fun remove(companyId: Long) = db.withTransaction {
        require(db.companyDao().byId(companyId)?.purchaserId == account.ownerId) { "مودی متعلق به این حساب نیست." }
        require(preferences.edit().remove("company_$companyId").remove("pending_company_$companyId").commit()) { "حذف اطلاعات اتصال انجام نشد." }
    }
    private fun profile(identity: TaxpayerIdentity, cert: X509Certificate) = TaxpayerCredentialProfile(identity,
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(java.util.Locale.ROOT,it.toInt() and 255) },cert.notAfter.time)
}
