package ir.saba.invoice.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Local tamper-resistance only; not remote attestation. A modified APK can patch
 * local code. Never trust a client-reported digest as server authentication.
 * The public certificate pin matches the established signed APK workflow.
 */
internal object ReleaseIntegrity {
    internal const val OFFICIAL_PACKAGE = "ir.saba.invoice"
    internal const val OFFICIAL_SHA256 = "55a5c9f677f899630bc792330606161560a8dea94b7fe0eabe11d47a013d7b0b"

    @Suppress("DEPRECATION")
    fun isOfficial(context: Context): Boolean = try {
        val pm = context.packageManager
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        matches(context.packageName, current?.map { it.toByteArray() }.orEmpty())
    } catch (_: Exception) {
        false
    }

    internal fun matches(
        packageName: String,
        signers: List<ByteArray>,
        expected: String = OFFICIAL_SHA256
    ): Boolean {
        if (packageName != OFFICIAL_PACKAGE || signers.size != 1 || signers[0].isEmpty()) return false
        return sha256(signers[0]) == expected
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
