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

    private fun pem(label: String, bytes: ByteArray): String =
        "-----BEGIN $label-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes) +
            "\n-----END $label-----\n"
}
