package ir.saba.invoice.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

/** Three fixed worksheets using the shared, hardened XLSX reader/writer. */
internal object InvoiceBatchWorkbook {
    val invoiceHeaders = listOf("شماره صورتحساب", "تاریخ شمسی", "شناسه مؤدی", "شناسه مشتری",
        "شناسه کالا/خدمت", "شرح کالا/خدمت", "کد واحد سنجش", "تعداد", "مبلغ واحد (ریال)",
        "تخفیف (ریال)", "نرخ مالیات (%)", "روش تسویه", "مبلغ نقدی (ریال)")
    val taxpayerHeaders = listOf("شناسه مؤدی", "نام مؤدی", "شماره اقتصادی")
    val customerHeaders = listOf("شناسه مشتری", "نام مشتری", "نوع شخص", "شماره اقتصادی", "کد پستی", "آدرس", "تلفن")
    private val names = listOf("صورتحساب‌ها", "مودیان", "مشتریان")
    private val headers = listOf(invoiceHeaders, taxpayerHeaders, customerHeaders)

    private fun unpack(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size <= 5_000_000) { "حجم فایل اکسل بیش از ۵ مگابایت است." }
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(bytes.inputStream()).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 512)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 32_000_000L) { "حجم بازشده اکسل بیش از حد مجاز است." }
                    out.write(buffer, 0, n)
                }
                if (!entry.isDirectory) require(entries.put(entry.name, out.toByteArray()) == null) {
                    "اجزای تکراری در فایل اکسل وجود دارد."
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun pack(parts: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { out ->
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        out.toByteArray()
    }

    fun write(sheets: List<List<List<String>>>): ByteArray {
        require(sheets.size == 3)
        val parts = unpack(ExcelWorkbook.write(headers[0], sheets[0])).toMutableMap()
        for (i in 1..2) {
            val sheet = unpack(ExcelWorkbook.write(headers[i], sheets[i])).getValue("xl/worksheets/sheet1.xml")
            parts["xl/worksheets/sheet${i + 1}.xml"] = sheet
        }
        fun modify(name: String, transform: (String) -> String) {
            parts[name] = transform(parts.getValue(name).toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
        }
        modify("xl/workbook.xml") { xml ->
            xml.replace("<sheet name=\"SURA\" sheetId=\"1\" r:id=\"rId1\"/>",
                names.mapIndexed { i, name -> "<sheet name=\"$name\" sheetId=\"${i+1}\" r:id=\"rId${if(i==0) 1 else i+2}\"/>" }.joinToString(""))
        }
        modify("xl/_rels/workbook.xml.rels") { xml ->
            xml.replace("</Relationships>", (2..3).joinToString("") { i ->
                "<Relationship Id=\"rId${i+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>"
            } + "</Relationships>")
        }
        modify("[Content_Types].xml") { xml ->
            xml.replace("</Types>", (2..3).joinToString("") { i ->
                "<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            } + "</Types>")
        }
        return pack(parts)
    }

    fun template(company: Company): ByteArray = write(listOf(
        List(10) { List(invoiceHeaders.size) { "" } },
        listOf(listOf(company.nationalId, company.name, company.economicId)),
        List(10) { List(customerHeaders.size) { "" } }
    ))

    fun read(bytes: ByteArray): List<List<List<String>>> {
        val parts = unpack(bytes)
        return headers.mapIndexed { i, header ->
            val sheet = parts["xl/worksheets/sheet${i+1}.xml"]
                ?: error("کاربرگ ${names[i]} در فایل وجود ندارد.")
            val minimal = linkedMapOf("xl/worksheets/sheet1.xml" to sheet)
            parts["xl/sharedStrings.xml"]?.let { minimal["xl/sharedStrings.xml"] = it }
            val rows = ExcelWorkbook.read(pack(minimal), maxRows = 1000)
            require(rows.firstOrNull()?.take(header.size)?.map(String::trim) == header) {
                "ستون‌های کاربرگ ${names[i]} معتبر نیست؛ از قالب سورا استفاده کنید."
            }
            rows.drop(1).mapIndexedNotNull { index, row ->
                require(row.drop(header.size).all(String::isBlank)) { "کاربرگ ${names[i]}، ردیف ${index+2}: ستون اضافه دارد." }
                if (row.all(String::isBlank)) null else List(header.size) { col -> row.getOrElse(col) { "" }.trim() }
            }
        }
    }
}
