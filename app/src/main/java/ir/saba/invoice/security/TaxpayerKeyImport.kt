package ir.saba.invoice.security

import java.io.InputStream
import java.util.Locale

/** No temporary file, persisted URI grant, or plaintext logging. Caller owns/ closes the stream. */
object TaxpayerKeyImport {
    fun read(input: InputStream, filename: String): String {
        require(filename.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("txt", "pem", "key")) {
            "پسوند فایل کلید باید txt، pem یا key باشد."
        }
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= TaxpayerPrivateKey.MAX_BYTES) { "فایل کلید حداکثر ۶۴ کیلوبایت باشد." }
            out.write(buffer, 0, n)
        }
        val bytes = out.toByteArray()
        try {
            val pem = bytes.toString(Charsets.UTF_8)
            TaxpayerPrivateKey.parse(pem)
            return pem
        } finally { bytes.fill(0); buffer.fill(0); out.reset() }
    }
}
