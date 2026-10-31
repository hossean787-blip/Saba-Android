package ir.saba.invoice.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Bounded, dependency-free XLSX transfer. All cells are literal text (never formulas). */
object ExcelWorkbook {
    fun write(headers: List<String>, rows: List<List<String>>): ByteArray {
        require(headers.isNotEmpty() && headers.size <= 40 && rows.size <= 10000)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="SURA" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                (listOf(headers) + rows).forEachIndexed { i, row ->
                    append("<row r=\"").append(i + 1).append("\">")
                    for (col in headers.indices) {
                        val v = row.getOrElse(col) { "" }.take(32767)
                            .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
                            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            .replace("\"", "&quot;").replace("'", "&apos;")
                        append("<c t=\"inlineStr\"><is><t>").append(v).append("</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray, maxRows: Int = 5000): List<List<String>> {
        require(bytes.size <= 5_000_000) { "فایل اکسل بیش از اندازه بزرگ است." }
        var sheet: ByteArray? = null
        var strings: ByteArray? = null
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in setOf("xl/worksheets/sheet1.xml", "xl/sharedStrings.xml")) {
                    val buffer = ByteArrayOutputStream()
                    val part = ByteArray(8192)
                    while (true) {
                        val n = zip.read(part)
                        if (n < 0) break
                        total += n
                        require(total <= 12_000_000) { "حجم محتوای فایل بیش از حد مجاز است." }
                        buffer.write(part, 0, n)
                    }
                    if (entry.name.endsWith("sheet1.xml")) sheet = buffer.toByteArray()
                    else strings = buffer.toByteArray()
                }
                zip.closeEntry()
            }
        }
        fun parse(input: ByteArray) = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(input))
        val shared = strings?.let { part ->
            val nodes = parse(part).getElementsByTagName("si")
            (0 until nodes.length).map { i -> nodes.item(i).textContent.orEmpty() }
        }.orEmpty()
        val doc = parse(sheet ?: error("کاربرگ اکسل پیدا نشد."))
        val nodes = doc.getElementsByTagName("row")
        require(nodes.length <= maxRows + 1) { "حداکثر تعداد سطر مجاز $maxRows است." }
        val rows = mutableListOf<List<String>>()
        for (i in 0 until nodes.length) {
            val row = nodes.item(i)
            val cells = row.childNodes
            val values = mutableListOf<String>()
            for (j in 0 until cells.length) {
                val cell = cells.item(j) as? Element ?: continue
                if (cell.tagName != "c") continue
                val ref = cell.getAttribute("r")
                val letters = ref.takeWhile(Char::isLetter)
                val index = if (letters.isEmpty()) values.size else
                    letters.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
                require(index in 0..39) { "تعداد ستون‌ها بیش از حد مجاز است." }
                while (values.size <= index) values.add("")
                val type = cell.getAttribute("t")
                val raw = if (type == "inlineStr") cell.getElementsByTagName("is").item(0)?.textContent.orEmpty()
                    else cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                values[index] = if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
            }
            rows.add(values)
        }
        return rows
    }
}
