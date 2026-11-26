package ir.saba.invoice.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import org.xml.sax.InputSource
import org.xml.sax.SAXException
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
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="SURA" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            // Format even empty cells as text so Excel preserves leading zeros when users fill the template.
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""")
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><cols><col min="1" max="${headers.size}" style="1" width="24" customWidth="1"/></cols><sheetData>""")
                (listOf(headers) + rows).forEachIndexed { i, row ->
                    append("<row r=\"").append(i + 1).append("\">")
                    for (col in headers.indices) {
                        val v = row.getOrElse(col) { "" }.take(32767)
                            .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
                            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            .replace("\"", "&quot;").replace("'", "&apos;")
                        val column = if (col < 26) ('A'.code + col).toChar().toString()
                            else "A" + ('A'.code + col - 26).toChar()
                        append("<c r=\"").append(column).append(i + 1)
                            .append("\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(v).append("</t></is></c>")
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
        var expanded = 0L
        var entries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 512) { "تعداد اجزای فایل اکسل بیش از حد مجاز است." }
                val wanted = !entry.isDirectory && entry.name in setOf("xl/worksheets/sheet1.xml", "xl/sharedStrings.xml")
                val buffer = if (wanted) ByteArrayOutputStream() else null
                val part = ByteArray(8192)
                while (true) {
                    val n = zip.read(part)
                    if (n < 0) break
                    expanded += n
                    require(expanded <= 32_000_000L) { "حجم بازشده فایل اکسل بیش از حد مجاز است." }
                    if (buffer != null) {
                        total += n
                        require(total <= 12_000_000) { "حجم محتوای فایل بیش از حد مجاز است." }
                        buffer.write(part, 0, n)
                    }
                }
                if (buffer != null) {
                    if (entry.name.endsWith("sheet1.xml")) {
                        require(sheet == null) { "کاربرگ تکراری در اکسل." }
                        sheet = buffer.toByteArray()
                    } else {
                        require(strings == null) { "جدول متن تکراری در اکسل." }
                        strings = buffer.toByteArray()
                    }
                }
                zip.closeEntry()
            }
        }
        fun parse(input: ByteArray): org.w3c.dom.Document {
            // Android's XML factory need not support the Apache/Xerces feature flags.
            // Decode before parsing; a Reader prevents alternate-encoding bypasses.
            val utf16le = input.size >= 2 && input[0] == 0xFF.toByte() && input[1] == 0xFE.toByte()
            val utf16be = input.size >= 2 && input[0] == 0xFE.toByte() && input[1] == 0xFF.toByte()
            val charset = when {
                utf16le -> Charsets.UTF_16LE
                utf16be -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            val xml = charset.newDecoder().decode(ByteBuffer.wrap(input)).toString().removePrefix("\uFEFF")
            require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)
                && !xml.contains('\u0000')) { "ساختار XML اکسل ایمن نیست." }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            return factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External XML entities are forbidden") }
            }.parse(InputSource(StringReader(xml)))
        }
        val shared = strings?.let { part ->
            val nodes = parse(part).getElementsByTagNameNS("*", "si")
            (0 until nodes.length).map { i -> nodes.item(i).textContent.orEmpty() }
        }.orEmpty()
        val doc = parse(sheet ?: error("کاربرگ اکسل پیدا نشد."))
        val nodes = doc.getElementsByTagNameNS("*", "row")
        require(nodes.length <= maxRows + 1) { "حداکثر تعداد سطر مجاز $maxRows است." }
        val rows = mutableListOf<List<String>>()
        for (i in 0 until nodes.length) {
            val row = nodes.item(i)
            val cells = row.childNodes
            val values = mutableListOf<String>()
            for (j in 0 until cells.length) {
                val cell = cells.item(j) as? Element ?: continue
                if (cell.localName != "c") continue
                val ref = cell.getAttribute("r")
                val letters = ref.takeWhile(Char::isLetter)
                val index = if (letters.isEmpty()) values.size else
                    letters.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
                require(index in 0..39) { "تعداد ستون‌ها بیش از حد مجاز است." }
                while (values.size <= index) values.add("")
                val type = cell.getAttribute("t")
                val raw = if (type == "inlineStr") cell.getElementsByTagNameNS("*", "is").item(0)?.textContent.orEmpty()
                    else cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                require(cell.getElementsByTagNameNS("*", "f").length == 0) {
                    "فرمول در ردیف " + (i + 1) + " مجاز نیست؛ مقدار ثابت وارد کنید."
                }
                values[index] = when {
                    type == "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1)
                        ?: error("ارجاع متن اکسل نامعتبر است.")
                    (type.isEmpty() || type == "n") && raw.contains('e', ignoreCase = true) && raw.length <= 128 -> {
                        val n = raw.toBigDecimalOrNull()
                        if (n != null && n.scale() in -100..100) n.stripTrailingZeros().toPlainString() else raw
                    }
                    else -> raw
                }
            }
            rows.add(values)
        }
        return rows
    }
}
