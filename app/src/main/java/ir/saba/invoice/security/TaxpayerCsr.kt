package ir.saba.invoice.security

import java.io.ByteArrayOutputStream
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.util.Base64

/** PKCS#10 certificate request signed locally with the taxpayer's matching RSA key.
 * No private key bytes are exported in the CSR.
 */
object TaxpayerCsr {
    data class Subject(
        val commonName: String,
        val organization: String,
        val province: String,
        val city: String,
        val countryCode: String = "IR",
        val email: String
    )
    fun generate(privatePem: String, publicPem: String, subject: Subject): String {
        val privateKey = TaxpayerPrivateKey.parse(privatePem)
        return generate(privateKey, publicPem, subject)
    }
    fun generate(privateKey: RSAPrivateKey, publicPem: String, subject: Subject): String {
        fun checked(s: String, max: Int): String {
            val t = s.trim()
            require(t.isNotEmpty() && t.length <= max && t.none { it.code < 32 })
            return t
        }
        val name = checked(subject.commonName, 120)
        val org = checked(subject.organization, 120)
        val province = checked(subject.province, 120)
        val city = checked(subject.city, 120)
        val email = checked(subject.email, 254)
        require('@' in email && email.all { it.code in 33..126 })
        require(subject.countryCode.matches(Regex("[A-Z]{2}")))
        val publicDer = Base64.getMimeDecoder().decode(
            publicPem.substringAfter("-----BEGIN PUBLIC KEY-----")
                .substringBefore("-----END PUBLIC KEY-----").trim()
        )
        val dn = seq(
            rdn("2.5.4.6", printable(subject.countryCode)),
            rdn("2.5.4.8", utf8(province)),
            rdn("2.5.4.7", utf8(city)),
            rdn("2.5.4.10", utf8(org)),
            rdn("2.5.4.3", utf8(name)),
            rdn("1.2.840.113549.1.9.1", tagged(0x16, email.toByteArray(Charsets.US_ASCII)))
        )
        // CertificationRequestInfo ::= SEQUENCE { version, subject, subjectPKInfo, attributes [0] }
        val info = seq(tagged(0x02, byteArrayOf(0)), dn, publicDer, tagged(0xa0, byteArrayOf()))
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey); update(info); sign()
        }
        val verificationKey = java.security.KeyFactory.getInstance("RSA").generatePublic(
            java.security.spec.X509EncodedKeySpec(publicDer)
        )
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(verificationKey); update(info); verify(signature)
        }) { "Public and private keys do not match" }
        val alg = seq(oid("1.2.840.113549.1.1.11"), tagged(0x05, byteArrayOf()))
        val bytes = seq(info, alg, tagged(0x03, byteArrayOf(0) + signature))
        return "-----BEGIN CERTIFICATE REQUEST-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(bytes) +
            "\n-----END CERTIFICATE REQUEST-----\n"
    }
    private fun rdn(id: String, value: ByteArray) = tagged(0x31, seq(oid(id), value))
    private fun utf8(s: String) = tagged(0x0c, s.toByteArray(Charsets.UTF_8))
    private fun printable(s: String) = tagged(0x13, s.toByteArray(Charsets.US_ASCII))
    private fun seq(vararg parts: ByteArray) = tagged(0x30, concat(*parts))
    private fun tagged(tag: Int, data: ByteArray): ByteArray {
        val len = when {
            data.size < 128 -> byteArrayOf(data.size.toByte())
            data.size < 256 -> byteArrayOf(0x81.toByte(), data.size.toByte())
            else -> byteArrayOf(0x82.toByte(), (data.size shr 8).toByte(), data.size.toByte())
        }
        return byteArrayOf(tag.toByte()) + len + data
    }
    private fun oid(s: String): ByteArray {
        val n = s.split('.').map { it.toLong() }
        require(n.size >= 2 && n[0] in 0..2 && n[1] in 0..39)
        val out = ByteArrayOutputStream()
        out.write((n[0] * 40 + n[1]).toInt())
        n.drop(2).forEach { x ->
            require(x >= 0)
            val octets = mutableListOf((x and 127).toInt())
            var v = x ushr 7
            while (v > 0) { octets.add((v and 127).toInt() or 128); v = v ushr 7 }
            octets.asReversed().forEach(out::write)
        }
        return tagged(0x06, out.toByteArray())
    }
    private fun concat(vararg parts: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> parts.forEach(out::write) }.toByteArray()
}
