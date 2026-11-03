package ir.saba.invoice.security

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MoadianSignedReceiptTest {
    companion object { private val pair by lazy {KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()} }
    private val now=Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()
    private val keys=listOf(OrganizationKey("official",pair.public as RSAPublicKey,1))
    private fun header()=JSONObject().put("alg","RS256").put("sigT","2026-11-03T11:59:00Z")
    private fun payload()=JSONObject().put("clientId","A11216").put("taxId","TAX-ID").put("referenceNumber","REF").put("receivedDate","2026-11-03T11:58:00Z")
    private fun sign(h:JSONObject=header(),p:JSONObject=payload()):String {
        val encoder=Base64.getUrlEncoder().withoutPadding()
        val input=encoder.encodeToString(h.toString().toByteArray())+"."+encoder.encodeToString(p.toString().toByteArray())
        return input+"."+encoder.encodeToString(Signature.getInstance("SHA256withRSA").run{initSign(pair.private);update(input.toByteArray());sign()})
    }
    private fun verify(jws:String)=MoadianSignedReceipt.verify(jws,keys,"TAX-ID","A11216","REF",now)
    private fun reject(block:()->Unit){try{block();fail("Expected untrusted receipt rejection")}catch(_:IllegalArgumentException){}}
    @Test fun acceptsOnlyTheCorrectSignedIdentity() {
        verify(sign())
        for((field,value) in listOf("clientId" to "OTHER1","taxId" to "OTHER","referenceNumber" to "ANOTHER")) reject{verify(sign(p=payload().put(field,value)))}
    }
    @Test fun untrustedKeyIdAndUnsupportedCriticalHeadersAreRejected() {
        reject{verify(sign(h=header().put("kid","attacker")))}
        reject{verify(sign(h=header().put("crit",JSONArray().put("unknown"))))}
        reject{verify(sign(h=header().put("crit","invalid")))}
        reject{verify(sign(h=header().put("b64",false)))}
        reject{verify(sign(h=header().put("alg","none")))}
    }
    @Test fun missingOrFutureReceiptTimesAreRejected() {
        reject{verify(sign(h=header().put("sigT","2027-11-03T12:00:00Z")))}
        reject{verify(sign(p=payload().put("receivedDate","2027-11-03T12:00:00Z")))}
    }
}
