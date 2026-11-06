package ir.saba.invoice.data.identifiers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.zip.ZipInputStream

/** Catalog data stays independent from the financial Room database and its migrations. */
data class SuraIdentifier(
    val id: String,
    val description: String,
    val vat: String,
    val taxable: String,
    val kind: String,
    val expirationDate: String,
    val source: String
)

class SuraIdentifierStore(private val context: Context) {
    private val catalogFile get() = File(context.filesDir, "sura_identifiers.db")

    fun installed(): Boolean = catalogFile.isFile && catalogFile.length() > 1024L

    suspend fun count(): Long = withContext(Dispatchers.IO) {
        if (!installed()) return@withContext 0L
        SQLiteDatabase.openDatabase(catalogFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM metadata WHERE key='row_count'", null).use {
                if (it.moveToFirst()) it.getString(0)?.toLongOrNull() ?: 0L else 0L
            }
        }
    }

    suspend fun search(input: String, category: String?, specificity: String?, limit: Int = 50): List<SuraIdentifier> =
        withContext(Dispatchers.IO) {
            if (!installed()) return@withContext emptyList()
            val normalized = normalize(input)
            if (normalized.isBlank()) return@withContext emptyList()
            val numeric = normalized.all { it in '0'..'9' }
            val args = mutableListOf<String>()
            val predicates = mutableListOf<String>()
            val join = if (numeric) "" else "JOIN catalog_fts ON catalog_fts.docid = c.rowid"
            if (numeric) {
                predicates.add("c.id GLOB ?")
                args.add(normalized + "*")
            } else {
                val terms = normalized.split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.isNotBlank() }.take(6).joinToString(" ") { it + "*" }
                if (terms.isEmpty()) return@withContext emptyList()
                predicates.add("catalog_fts MATCH ?")
                args.add(terms)
            }
            when (category) {
                "کالا" -> predicates.add("c.kind NOT LIKE '%خدمت%'")
                "خدمت" -> predicates.add("c.kind LIKE '%خدمت%'")
            }
            when (specificity) {
                "عمومی" -> predicates.add("c.kind LIKE '%عموم%'")
                "اختصاصی" -> predicates.add("c.kind LIKE '%اختصاصی%'")
            }
            val sql = "SELECT c.id,c.description,c.vat,COALESCE(c.taxable,''),COALESCE(c.kind,'')," +
                "COALESCE(c.expiration_date,''),s.code FROM catalog c " + join +
                " JOIN sources s ON s.source_id=c.source_id WHERE " +
                predicates.joinToString(" AND ") + " LIMIT ?"
            args.add(limit.coerceIn(1, 100).toString())
            SQLiteDatabase.openDatabase(catalogFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                    val result = ArrayList<SuraIdentifier>()
                    while (cursor.moveToNext()) {
                        result.add(SuraIdentifier(
                            cursor.getString(0), cursor.getString(1),
                            cursor.getString(2).orEmpty(), cursor.getString(3).orEmpty(),
                            cursor.getString(4).orEmpty(), cursor.getString(5).orEmpty(),
                            cursor.getString(6).orEmpty()
                        ))
                    }
                    result
                }
            }
        }

    /**
     * Imports only either of the two verified, user-provided catalogs (raw .db or its ZIP).
     * Never executes imported SQL, and never overwrites the live database before verification.
     */
    suspend fun importVerified(uri: Uri): Long = withContext(Dispatchers.IO) {
        val staged = File(context.filesDir, "sura_identifiers.partial")
        val backup = File(context.filesDir, "sura_identifiers.db.backup")
        staged.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        fun copy(source: InputStream) {
            staged.outputStream().buffered().use { out ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    written += n
                    require(written <= MAX_DB_BYTES) { "اندازه بانک اطلاعاتی بیش از حد مجاز است." }
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
        }
        try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { stream ->
                stream.mark(4)
                val a = stream.read()
                val b = stream.read()
                stream.reset()
                if (a == 0x50 && b == 0x4B) {
                    ZipInputStream(stream).use { zip ->
                        var found = false
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (!entry.isDirectory && entry.name.endsWith(".db", ignoreCase = true)) {
                                require(!found) { "فایل ZIP باید فقط یک بانک اطلاعاتی داشته باشد." }
                                found = true
                                copy(zip)
                            }
                            zip.closeEntry()
                        }
                        require(found) { "بانک SQLite در فایل ZIP پیدا نشد." }
                    }
                } else copy(stream)
            } ?: error("فایل انتخاب‌شده قابل خواندن نیست.")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual == SHA256_LITE || actual == SHA256_FULL) {
                "این بانک با نسخه‌های کنترل‌شده سورا مطابقت ندارد."
            }
            val rows = SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA quick_check", null).use {
                    check(it.moveToFirst() && it.getString(0) == "ok") { "بانک اطلاعاتی سالم نیست." }
                }
                db.rawQuery("SELECT value FROM metadata WHERE key='schema'", null).use {
                    check(it.moveToFirst() && it.getString(0) == "sura-stuffid-catalog-v1") {
                        "ساختار بانک اطلاعاتی معتبر نیست."
                    }
                }
                db.rawQuery("SELECT value FROM metadata WHERE key='row_count'", null).use {
                    check(it.moveToFirst()) { "تعداد شناسه‌ها مشخص نیست." }
                    it.getString(0).toLong()
                }
            }
            // Keep the previously working database until the verified staged file is installed.
            if (backup.exists()) backup.delete()
            if (catalogFile.exists()) check(catalogFile.renameTo(backup)) { "ایجاد نسخه پشتیبان ناموفق بود." }
            if (!staged.renameTo(catalogFile)) {
                if (backup.exists()) backup.renameTo(catalogFile)
                error("جایگزینی بانک اطلاعاتی انجام نشد.")
            }
            backup.delete()
            rows
        } finally {
            staged.delete()
            if (!catalogFile.exists() && backup.exists()) backup.renameTo(catalogFile)
        }
    }

    companion object {
        const val SHA256_LITE = "02530f58da28ab0d7846473b8e460cfe9a13c016468e8cffc07d2b3e7bc06fd1"
        const val SHA256_FULL = "58c3c63064508892333bfdfcf7e262bf489318070920f1762e51010bf6659fc8"
        private const val MAX_DB_BYTES = 4_200_000_000L

        fun normalize(value: String): String {
            val persian = "۰۱۲۳۴۵۶۷۸۹"
            val arabic = "٠١٢٣٤٥٦٧٨٩"
            return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace('‌', ' ').replace('‍', ' ')
                .map { c ->
                    when {
                        persian.indexOf(c) >= 0 -> ('0'.code + persian.indexOf(c)).toChar()
                        arabic.indexOf(c) >= 0 -> ('0'.code + arabic.indexOf(c)).toChar()
                        else -> c
                    }
                }.joinToString("").trim().replace(Regex("\\s+"), " ")
        }
    }
}

/** Each authenticated account uses its own SQLite file; each taxpayer has separate rows. */
class SuraSavedIdentifierStore(context: Context, account: AccountScope) :
    SQLiteOpenHelper(context.applicationContext, "sura_identifier_saved_" + account.storageKey + ".db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE saved_identifiers (
            company_id INTEGER NOT NULL,
            id TEXT NOT NULL,
            description TEXT NOT NULL,
            vat TEXT NOT NULL,
            taxable TEXT NOT NULL,
            kind TEXT NOT NULL,
            expiration_date TEXT NOT NULL,
            source TEXT NOT NULL,
            saved_at INTEGER NOT NULL,
            PRIMARY KEY(company_id, id)
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun save(companyId: Long, identifier: SuraIdentifier) = withContext(Dispatchers.IO) {
        require(companyId > 0 && identifier.id.isNotBlank())
        val record = ContentValues().apply {
            put("company_id", companyId)
            put("id", identifier.id)
            put("description", identifier.description)
            put("vat", identifier.vat)
            put("taxable", identifier.taxable)
            put("kind", identifier.kind)
            put("expiration_date", identifier.expirationDate)
            put("source", identifier.source)
            put("saved_at", System.currentTimeMillis())
        }
        check(writableDatabase.insertWithOnConflict(
            "saved_identifiers", null, record, SQLiteDatabase.CONFLICT_REPLACE
        ) != -1L)
    }

    /** Import validated Excel rows atomically; existing taxpayer entries are never overwritten. */
    suspend fun importBatch(companyId: Long, identifiers: List<SuraIdentifier>): Int =
        withContext(Dispatchers.IO) {
            require(companyId > 0 && identifiers.isNotEmpty() && identifiers.size <= 1000)
            require(identifiers.all { it.id.matches(Regex("[0-9]{13,14}")) })
            require(identifiers.map { it.id }.distinct().size == identifiers.size) {
                "شناسه تکراری داخل فایل اکسل وجود دارد."
            }
            val db = writableDatabase
            var inserted = 0
            db.beginTransaction()
            try {
                identifiers.forEach { identifier ->
                    val values = ContentValues().apply {
                        put("company_id", companyId)
                        put("id", identifier.id)
                        put("description", identifier.description)
                        put("vat", identifier.vat)
                        put("taxable", identifier.taxable)
                        put("kind", identifier.kind)
                        put("expiration_date", identifier.expirationDate)
                        put("source", identifier.source)
                        put("saved_at", System.currentTimeMillis())
                    }
                    val row = db.insertWithOnConflict(
                        "saved_identifiers", null, values, SQLiteDatabase.CONFLICT_IGNORE
                    )
                    if (row != -1L) inserted++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            inserted
        }

    suspend fun remove(companyId: Long, identifierId: String) = withContext(Dispatchers.IO) {
        require(companyId > 0)
        writableDatabase.delete("saved_identifiers", "company_id=? AND id=?",
            arrayOf(companyId.toString(), identifierId))
    }

    suspend fun forTaxpayer(companyId: Long): List<SuraIdentifier> = withContext(Dispatchers.IO) {
        require(companyId > 0)
        readableDatabase.query(
            "saved_identifiers",
            arrayOf("id", "description", "vat", "taxable", "kind", "expiration_date", "source"),
            "company_id=?", arrayOf(companyId.toString()), null, null,
            "saved_at DESC, id ASC"
        ).use { cursor ->
            val result = ArrayList<SuraIdentifier>()
            while (cursor.moveToNext()) {
                result.add(SuraIdentifier(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6)
                ))
            }
            result
        }
    }
}
