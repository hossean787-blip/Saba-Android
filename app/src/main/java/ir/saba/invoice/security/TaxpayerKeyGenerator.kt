package ir.saba.invoice.security

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Generates a taxpayer-specific 2048-bit RSA keypair locally.
 * The caller MUST encrypt the private PEM with TaxpayerKeyVault before storage.
 * Public key export is safe; possession alone never proves registration with the tax authority.
 */
object TaxpayerKeyGenerator {
    data class Generated(val privatePem: String, val publicPem: String)

    fun generate(): Generated {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = pair.public as RSAPublicKey
        require(publicKey.modulus.bitLength() >= 2048)
        val verified = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(publicKey.modulus, publicKey.publicExponent)
        )
        require(verified.encoded.contentEquals(pair.public.encoded))
        return Generated(pem("PRIVATE KEY", pair.private.encoded), pem("PUBLIC KEY", pair.public.encoded))
    }

    /** Re-create the public PEM from an encrypted-at-rest PKCS#8 RSA CRT private key.
     *  Call only after the vault has decrypted the correct taxpayer's key.
     */
    fun publicPemFromPrivatePkcs8(pkcs8: ByteArray): String {
        // Parse the two public integers from PKCS#8/PKCS#1, avoiding
        // provider-specific RSAPrivateCrtKey casts and key-spec conversions.
        val outer = DerCursor(pkcs8).read(0x30).let(::DerCursor)
        outer.read(0x02) // PKCS#8 version
        outer.read(0x30) // RSA algorithm identifier
        val inner = DerCursor(outer.read(0x04)).read(0x30).let(::DerCursor)
        inner.read(0x02) // PKCS#1 version
        val modulus = java.math.BigInteger(1, inner.read(0x02))
        val exponent = java.math.BigInteger(1, inner.read(0x02))
        require(modulus.bitLength() >= 2048 && exponent >= java.math.BigInteger.valueOf(3))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(modulus, exponent)
        )
        return pem("PUBLIC KEY", publicKey.encoded)
    }

    private class DerCursor(private val bytes: ByteArray) {
        private var offset = 0
        fun read(tag: Int): ByteArray {
            require(offset < bytes.size && (bytes[offset++].toInt() and 255) == tag)
            require(offset < bytes.size)
            var size = bytes[offset++].toInt() and 255
            if (size >= 128) {
                val count = size and 127
                require(count in 1..3 && offset + count <= bytes.size)
                size = 0
                repeat(count) { size = (size shl 8) or (bytes[offset++].toInt() and 255) }
            }
            require(size > 0 && size <= bytes.size - offset)
            return bytes.copyOfRange(offset, offset + size).also { offset += size }
        }
    }

    private fun pem(label: String, bytes: ByteArray): String =
        "-----BEGIN $label-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes) +
            "\n-----END $label-----\n"
}
