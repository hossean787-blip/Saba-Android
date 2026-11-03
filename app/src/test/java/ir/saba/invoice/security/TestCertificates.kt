package ir.saba.invoice.security

import java.security.KeyPair
import java.security.Signature
import java.util.Base64

/** Ephemeral, synthetic self-signed certificates. No production keys/certificates are committed. */
internal object TestCertificates {
    fun pem(pair: KeyPair) = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----"
    fun certificate(pair: KeyPair, expired: Boolean = false): ByteArray {
        val algorithm = byteArrayOf(0x30,0x0d,0x06,0x09,0x2a,0x86.toByte(),0x48,0x86.toByte(),0xf7.toByte(),0x0d,0x01,0x01,0x0b,0x05,0x00)
        val name = der(0x30,der(0x31,der(0x30,byteArrayOf(0x06,0x03,0x55,0x04,0x03) + der(0x0c,"SURA synthetic test".toByteArray()))))
        val validity = der(0x30,der(0x17,"200101000000Z".toByteArray()) + der(0x17,(if(expired) "210101000000Z" else "300101000000Z").toByteArray()))
        val body = der(0x30,byteArrayOf(0x02,0x01,0x01) + algorithm + name + validity + name + pair.public.encoded)
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private);update(body);sign() }
        return der(0x30,body + algorithm + der(0x03,byteArrayOf(0) + signature))
    }
    private fun der(tag: Int, content: ByteArray): ByteArray {
        val n=content.size
        val length=when { n<128 -> byteArrayOf(n.toByte()); n<256 -> byteArrayOf(0x81.toByte(),n.toByte()); else -> byteArrayOf(0x82.toByte(),(n shr 8).toByte(),n.toByte()) }
        return byteArrayOf(tag.toByte()) + length + content
    }
}
