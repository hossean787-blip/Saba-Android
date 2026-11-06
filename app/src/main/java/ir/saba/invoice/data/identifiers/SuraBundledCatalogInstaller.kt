package ir.saba.invoice.data.identifiers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Installs the signed-APK's complete offline catalog, when install-time assets exist.
 * Financial/account SQLite databases are not touched. Never downloads data from a server.
 */
class SuraBundledCatalogInstaller(private val context: Context) {
    private val destination = File(context.filesDir, "sura_identifiers.db")
    private val pending = File(context.filesDir, "sura_identifiers.bundled.partial")
    private val backup = File(context.filesDir, "sura_identifiers.bundled.backup")
    private val catalog = SuraIdentifierStore(context.applicationContext)

    suspend fun installIfNeeded(onProgress: suspend (Int) -> Unit = {}): Long? =
        withContext(Dispatchers.IO) {
            val assets = context.assets.list("catalog")?.toSet().orEmpty()
            if ("manifest.json" !in assets) return@withContext null
            val document = context.assets.open("catalog/manifest.json").bufferedReader().use { it.readText() }
            require(document.length <= 32768) { "فهرست بانک اطلاعاتی نامعتبر است." }
            val manifest = JSONObject(document)
            require(manifest.getString("schema") == "sura-offline-catalog-bundle-v1")
            require(manifest.getLong("record_count") == FULL_RECORD_COUNT)
            require(manifest.getLong("database_bytes") == FULL_DATABASE_BYTES)
            require(manifest.getString("database_sha256") == FULL_DATABASE_SHA256)
            val parts = manifest.getJSONArray("parts")
            require(parts.length() in 1..32) { "تعداد بخش‌های بانک نامعتبر است." }

            // A previously installed full catalog, including the original unoptimized one,
            // remains usable; avoid consuming storage twice.
            if (destination.exists() && runCatching { catalog.count() }.getOrDefault(0L) == FULL_RECORD_COUNT)
                return@withContext FULL_RECORD_COUNT

            if (backup.exists() && !destination.exists()) {
                check(backup.renameTo(destination)) { "بازیابی بانک قبلی ناموفق بود." }
            }
            val free = StatFs(context.filesDir.absolutePath).availableBytes
            check(free >= FULL_DATABASE_BYTES + MINIMUM_SPARE_BYTES) {
                "برای آماده‌سازی بانک کامل، دست‌کم ۲ گیگابایت فضای خالی داخلی لازم است."
            }

            pending.delete()
            val overall = MessageDigest.getInstance("SHA-256")
            var written = 0L
            try {
                BufferedOutputStream(pending.outputStream(), 128 * 1024).use { output ->
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val name = part.getString("file")
                        require(name == "full-%02d.gz".format(i)) {
                            "نام بخش اطلاعاتی معتبر نیست."
                        }
                        check(name in assets) { "یکی از بخش‌های بانک کامل در فایل نصب وجود ندارد." }
                        val declaredLength = part.getLong("bytes")
                        require(declaredLength > 0L && declaredLength <= 268_435_456L)
                        val partDigest = MessageDigest.getInstance("SHA-256")
                        var partLength = 0L
                        context.assets.open("catalog/$name").use { raw ->
                            DigestInputStream(raw, partDigest).use { compressed ->
                                GZIPInputStream(BufferedInputStream(compressed, 128 * 1024), 128 * 1024).use { uncompressed ->
                                    val block = ByteArray(128 * 1024)
                                    while (true) {
                                        val n = uncompressed.read(block)
                                        if (n < 0) break
                                        if (n == 0) continue
                                        partLength += n
                                        written += n
                                        check(partLength <= declaredLength && written <= FULL_DATABASE_BYTES) {
                                            "حجم داده خارج از محدوده مجاز است."
                                        }
                                        output.write(block, 0, n)
                                        overall.update(block, 0, n)
                                    }
                                }
                            }
                        }
                        check(partLength == declaredLength) { "حجم بخش اطلاعاتی صحیح نیست." }
                        check(hex(partDigest.digest()) == part.getString("compressed_sha256")) {
                            "صحت بخش اطلاعاتی تأیید نشد."
                        }
                        withContext(Dispatchers.Main) {
                            onProgress(((i + 1) * 100) / parts.length())
                        }
                    }
                }
                check(written == FULL_DATABASE_BYTES && hex(overall.digest()) == FULL_DATABASE_SHA256) {
                    "اطلاعات استخراج‌شده با بانک کامل کنترل‌شده مطابقت ندارد."
                }
                // Contentless FTS4 indexes cannot run PRAGMA quick_check against a duplicated
                // content table. SHA-256 verified above is an exact check of the entire DB.
                SQLiteDatabase.openDatabase(
                    pending.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    fun scalar(sql: String): String = db.rawQuery(sql, null).use {
                        check(it.moveToFirst())
                        it.getString(0)
                    }
                    check(scalar("SELECT value FROM metadata WHERE key='schema'") == "sura-stuffid-catalog-v1")
                    check(scalar("SELECT value FROM metadata WHERE key='fts_engine'") == "fts4-contentless-v1")
                    check(scalar("SELECT COUNT(*) FROM catalog").toLong() == FULL_RECORD_COUNT)
                    check(scalar("SELECT COUNT(*) FROM catalog_fts_docsize").toLong() == FULL_RECORD_COUNT)
                    check(scalar("SELECT c.id FROM catalog c JOIN catalog_fts ON catalog_fts.docid=c.rowid WHERE catalog_fts MATCH 'حمل*' LIMIT 1").isNotBlank())
                }
                if (backup.exists()) backup.delete()
                if (destination.exists()) {
                    check(destination.renameTo(backup)) { "ایجاد نسخه بازگشت از بانک قبلی ناموفق بود." }
                }
                if (!pending.renameTo(destination)) {
                    if (backup.exists()) backup.renameTo(destination)
                    error("انتقال بانک کامل به فضای برنامه انجام نشد.")
                }
                backup.delete()
                FULL_RECORD_COUNT
            } finally {
                pending.delete()
                if (backup.exists() && !destination.exists()) backup.renameTo(destination)
            }
        }

    companion object {
        const val FULL_RECORD_COUNT = 4_227_218L
        const val FULL_DATABASE_BYTES = 1_830_764_544L
        const val FULL_DATABASE_SHA256 = "567fbba0e54a863879c790a848866a7e16d48ad272c66e286172f87b67fd0e9f"
        private const val MINIMUM_SPARE_BYTES = 256L * 1024L * 1024L
        private fun hex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
