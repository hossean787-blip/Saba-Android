package ir.saba.invoice.data.identifiers

import android.content.Context
import android.net.Uri
import ir.saba.invoice.data.ExcelWorkbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/** Small, optional, manually maintained taxpayer catalog. No full catalog dependency. */
object SuraIdentifierInputService {
    val headers = listOf(
        "شناسه کالا/خدمت",
        "شرح کالا یا خدمت",
        "نرخ مالیات (%)",
        "نوع (کالا/خدمت)",
        "طبقه (عمومی/اختصاصی)"
    )

    /** Produces an empty, importable workbook, not a spreadsheet containing sample tax codes. */
    fun template(): ByteArray =
        ExcelWorkbook.write(headers, List(10) { List(headers.size) { "" } })

    fun item(
        rawId: String,
        rawDescription: String,
        rawVat: String,
        rawType: String,
        rawSpecificity: String,
        source: String = "ثبت دستی"
    ): SuraIdentifier {
        val code = SuraIdentifierStore.normalize(rawId).replace(" ", "")
        require(code.matches(Regex("[0-9]{13,14}"))) {
            "شناسه کالا یا خدمت باید ۱۳ یا ۱۴ رقم باشد."
        }
        val description = rawDescription.trim().replace(Regex("\\s+"), " ")
        require(description.isNotBlank() && description.length <= 500) {
            "شرح کالا یا خدمت الزامی و حداکثر ۵۰۰ نویسه است."
        }
        val vat = SuraIdentifierStore.normalize(rawVat).replace("٫", ".").trim()
        if (vat.isNotEmpty()) {
            require(vat.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,2})?"))) {
                "نرخ مالیات باید عدد بین صفر تا صد باشد."
            }
            require(BigDecimal(vat) <= BigDecimal("100")) {
                "نرخ مالیات باید بین صفر تا صد باشد."
            }
        }
        val type = rawType.trim()
        val specificity = rawSpecificity.trim()
        require(type.isEmpty() || type in listOf("کالا", "خدمت")) {
            "نوع باید کالا یا خدمت باشد."
        }
        require(specificity.isEmpty() || specificity in listOf("عمومی", "اختصاصی")) {
            "طبقه باید عمومی یا اختصاصی باشد."
        }
        return SuraIdentifier(
            id = code,
            description = description,
            vat = vat,
            taxable = "",
            kind = listOf(specificity, type).filter { it.isNotBlank() }.joinToString(" "),
            expirationDate = "",
            source = source
        )
    }

    fun parseXlsx(bytes: ByteArray): List<SuraIdentifier> {
        val rows = ExcelWorkbook.read(bytes, maxRows = 1000)
        require(rows.isNotEmpty() && rows.first().take(headers.size).map(String::trim) == headers) {
            "ستون‌های اکسل صحیح نیست؛ از الگوی اختصاصی شناسه‌های سورا استفاده کنید."
        }
        val records = rows.drop(1)
            .mapIndexedNotNull { index, row ->
                val values = headers.indices.map { row.getOrElse(it) { "" }.trim() }
                if (values.all { it.isBlank() }) null
                else try {
                    item(values[0], values[1], values[2], values[3], values[4], "ورود از اکسل")
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("ردیف " + (index + 2) + ": " + e.message)
                }
            }
        require(records.isNotEmpty()) { "فایل اکسل هیچ شناسه‌ای برای ثبت ندارد." }
        require(records.size <= 1000) { "حداکثر ۱۰۰۰ شناسه در هر بار ورود مجاز است." }
        val seen = mutableSetOf<String>()
        records.forEachIndexed { index, row ->
            require(seen.add(row.id)) {
                "شناسه " + row.id + " در فایل تکراری است (ردیف داده‌ای " + (index + 2) + ")."
            }
        }
        return records
    }

    /** Read only bounded XLSX input from Android Storage Access Framework. */
    suspend fun readXlsx(context: Context, uri: Uri): List<SuraIdentifier> =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("فایل اکسل باز نشد.")
            val bytes = input.use { source ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(32 * 1024)
                while (true) {
                    val n = source.read(chunk)
                    if (n < 0) break
                    if (n == 0) continue
                    require(output.size() + n <= 5_000_000) {
                        "فایل اکسل بیش از اندازه بزرگ است."
                    }
                    output.write(chunk, 0, n)
                }
                output.toByteArray()
            }
            parseXlsx(bytes)
        }
}
