package ir.saba.invoice.security

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/** Structural/self-signature validation only; never proves fiscal-memory ownership. */
object TaxpayerPrivateKey {
    const val MAX_BYTES = 65536
    fun parse(pem: String): RSAPrivateCrtKey {
        require(pem.length <= MAX_BYTES) { "فایل کلید بیش از حد بزرگ است." }
        val text = pem.trim()
        val pkcs1 = text.startsWith("-----BEGIN RSA PRIVATE KEY-----")
        val label = if (pkcs1) "RSA PRIVATE KEY" else "PRIVATE KEY"
        val begin = "-----BEGIN $label-----"
        val end = "-----END $label-----"
        require(text.startsWith(begin) && text.endsWith(end)) { "کلید باید PEM خصوصی RSA و بدون رمز باشد." }
        try {
            val body = text.removePrefix(begin).removeSuffix(end).filterNot { it.isWhitespace() }
            val raw = Base64.getDecoder().decode(body)
            val encoded = if (pkcs1) der(0x30, byteArrayOf(2, 1, 0) +
                byteArrayOf(0x30, 0x0d, 6, 9, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 1, 1, 1, 5, 0) +
                der(4, raw)) else raw
            val factory = KeyFactory.getInstance("RSA")
            val key = factory.generatePrivate(PKCS8EncodedKeySpec(encoded)) as RSAPrivateCrtKey
            require(key.modulus.bitLength() >= 2048)
            val publicKey = factory.generatePublic(RSAPublicKeySpec(key.modulus, key.publicExponent))
            val challenge = "SURA local key structure check".toByteArray()
            val signature = Signature.getInstance("SHA256withRSA").run { initSign(key); update(challenge); sign() }
            require(Signature.getInstance("SHA256withRSA").run { initVerify(publicKey); update(challenge); verify(signature) })
            return key
        } catch (failure: Exception) {
            throw IllegalArgumentException("ساختار کلید RSA معتبر نیست؛ کلید حداقل ۲۰۴۸ بیت لازم است. (" + failure.javaClass.simpleName + ")")
        }
    }
    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val length = bytes.size
        val size = when {
            length < 128 -> byteArrayOf(length.toByte())
            length < 256 -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        }
        return byteArrayOf(tag.toByte()) + size + bytes
    }
}
