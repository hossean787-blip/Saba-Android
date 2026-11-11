package ir.saba.invoice.security

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date

/** Local validity/key-match checks; does not prove trusted issuance or fiscal-memory registration. */
object TaxpayerSigningCertificate {
    const val MAX_BYTES = 65536
    fun read(input: InputStream, filename: String): ByteArray {
        // Android document providers sometimes omit the filename suffix.
        // Validate the actual X.509 bytes, not the provider's display name.
        val bytes = input.readBytesBounded(MAX_BYTES)
        require(bytes.isNotEmpty()) { "فایل انتخاب‌شده خالی است." }
        val header = String(bytes, 0, minOf(bytes.size, 160), Charsets.US_ASCII)
        require(!header.contains("BEGIN CERTIFICATE REQUEST") &&
            !header.contains("BEGIN NEW CERTIFICATE REQUEST")) {
            "این فایل درخواست CSR است، نه گواهی صادرشده؛ فایل گواهی CER / CRT / PEM را انتخاب کنید."
        }
        require(!header.contains("BEGIN PRIVATE KEY") &&
            !header.contains("BEGIN RSA PRIVATE KEY")) {
            "فایل کلید خصوصی را نباید در قسمت گواهی وارد کنید."
        }
        return bytes
    }
    fun parse(bytes: ByteArray): X509Certificate {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "اندازه فایل گواهی معتبر نیست." }
        try {
            val stream = ByteArrayInputStream(bytes)
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
            require(stream.readBytes().all { it.toInt().toChar().isWhitespace() })
            val public = cert.publicKey as? RSAPublicKey ?: error("RSA certificate required")
            require(public.modulus.bitLength() >= 2048 && cert.basicConstraints < 0)
            val usage = cert.keyUsage
            require(usage == null || usage.getOrElse(0) { false } || usage.getOrElse(1) { false })
            return cert
        } catch (_: Exception) { throw IllegalArgumentException("گواهی امضای RSA معتبر نیست؛ گواهی شخص/شرکت را انتخاب کنید.") }
    }
    fun requireMatch(certificate: X509Certificate, key: PrivateKey, nowEpochMillis: Long) {
        certificate.checkValidity(Date(nowEpochMillis))
        val challenge = "SURA taxpayer credential binding v2".toByteArray(Charsets.US_ASCII)
        val proof = Signature.getInstance("SHA256withRSA").run { initSign(key); update(challenge); sign() }
        require(Signature.getInstance("SHA256withRSA").run { initVerify(certificate.publicKey); update(challenge); verify(proof) }) {
            "گواهی به کلید خصوصی انتخاب‌شده تعلق ندارد."
        }
    }
    private fun InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer,0,minOf(buffer.size,limit + 1 - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer,0,count)
            require(output.size() <= limit) { "فایل گواهی بیش از حد بزرگ است." }
        }
        return output.toByteArray()
    }
}
