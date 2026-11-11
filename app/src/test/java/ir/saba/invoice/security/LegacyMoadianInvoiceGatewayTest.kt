package ir.saba.invoice.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyMoadianInvoiceGatewayTest {
    @Test fun authenticatesSignsEncryptsAndQueriesWithoutAnyCertificateOrPrematureInvoicePost() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val owner = AccountScope.of("11111111-1111-4111-8111-111111111111")
        val taxpayerKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val orgKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val wrapping = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val db=Room.inMemoryDatabaseBuilder(ctx,SabaDatabase::class.java).build()
        try {
            val company = db.companyDao().insert(Company(purchaserId=owner.ownerId,name="Test",nationalId="1"))
            db.moadianDetailsDao().upsert(MoadianDetails(companyId=company,fiscalMemoryId="A11216"))
            val credentials=TaxpayerCredentialRepository(db,owner,
                ctx.getSharedPreferences("selftsp-test-" + UUID.randomUUID(),0),TaxpayerKeyVault { wrapping })
            credentials.stagePrivateKey(company,TestCertificates.pem(taxpayerKeys))
            assertNull(credentials.profile(company,System.currentTimeMillis()))
            val uid="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
            val taxId="A11216" + "0".repeat(16)
            val invoice=JSONObject().put("header",
                JSONObject().put("taxid",taxId).put("inty",1).put("inp",1).put("ins",1))
                .put("body",JSONArray().put(JSONObject().put("sstid","2710000138624")))
                .put("payments",JSONArray())
            val calls=mutableListOf<String>()
            val transport=object:LegacyInvoiceTransport {
                override suspend fun post(operation:String,envelope:JSONObject,headers:Map<String,String>):JSONObject {
                    calls += operation
                    assertTrue(headers["requestTraceId"].orEmpty().isNotBlank())
                    assertTrue(headers["timestamp"].orEmpty().isNotBlank())
                    val toVerify=if(operation=="INVOICE.V01")
                        JSONObject().put("packets",JSONArray().put(JSONArray().put(envelope.getJSONArray("packets").getJSONObject(0))))
                    else envelope.getJSONObject("packet")
                    val canonical=LegacyMoadianCanonical.normalize(toVerify,headers)
                    assertTrue(Signature.getInstance("SHA256withRSA").run {
                        initVerify(taxpayerKeys.public)
                        update(canonical.toByteArray(Charsets.UTF_8))
                        verify(Base64.getDecoder().decode(envelope.getString("signature")))
                    })
                    return when(operation) {
                        "GET_TOKEN" -> JSONObject().put("result",JSONObject()
                            .put("errorCode",JSONObject.NULL).put("data",JSONObject().put("token","demo-token")))
                        "GET_SERVER_INFORMATION" -> JSONObject().put("result",JSONObject()
                            .put("data",JSONObject().put("serverTime",1794384000000L)
                                .put("publicKeys",JSONArray().put(JSONObject()
                                    .put("id","org-key").put("purpose",1).put("algorithm","RSA")
                                    .put("key",Base64.getEncoder().encodeToString(orgKeys.public.encoded))))))
                        "INVOICE.V01" -> {
                            assertEquals("Bearer demo-token",headers["Authorization"])
                            val packet=envelope.getJSONArray("packets").getJSONObject(0)
                            assertEquals(uid,packet.getString("uid"))
                            assertEquals("A11216",packet.getString("fiscalId"))
                            val rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
                            rsa.init(Cipher.DECRYPT_MODE,orgKeys.private,
                                OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT))
                            val hex=String(rsa.doFinal(Base64.getDecoder().decode(packet.getString("symmetricKey"))),Charsets.US_ASCII)
                            val aesKey=ByteArray(32){i->hex.substring(i*2,i*2+2).toInt(16).toByte()}
                            val ivText=packet.getString("iv")
                            val iv=ByteArray(16){i->ivText.substring(i*2,i*2+2).toInt(16).toByte()}
                            val aes=Cipher.getInstance("AES/GCM/NoPadding")
                            aes.init(Cipher.DECRYPT_MODE,SecretKeySpec(aesKey,"AES"),GCMParameterSpec(128,iv))
                            val plaintext=aes.doFinal(Base64.getDecoder().decode(packet.getString("data")))
                            for(i in plaintext.indices) plaintext[i]=(plaintext[i].toInt() xor aesKey[i%32].toInt()).toByte()
                            val decoded=JSONObject(String(plaintext,Charsets.UTF_8))
                            assertEquals(invoice.toString(),decoded.toString())
                            assertTrue(Signature.getInstance("SHA256withRSA").run {
                                initVerify(taxpayerKeys.public)
                                update(LegacyMoadianCanonical.normalize(decoded).toByteArray(Charsets.UTF_8))
                                verify(Base64.getDecoder().decode(packet.getString("dataSignature")))
                            })
                            plaintext.fill(0);aesKey.fill(0)
                            JSONObject().put("result",JSONArray().put(JSONObject()
                                .put("uid",uid).put("referenceNumber","legacy-ack")))
                        }
                        "INQUIRY_BY_UID" -> {
                            assertEquals(uid,envelope.getJSONObject("packet").getJSONObject("data")
                                .getJSONArray("uidList").getString(0))
                            JSONObject().put("result",JSONObject().put("data",JSONArray().put(
                                JSONObject().put("uid",uid).put("taxId",taxId)
                                    .put("fiscalId","A11216").put("referenceNumber","legacy-ack")
                                    .put("status","SUCCESS"))))
                        }
                        else -> error("Unexpected operation")
                    }
                }
            }
            val gateway=LegacyMoadianInvoiceGateway(credentials,transport,{1794384000000L})
            val prepared=gateway.prepare(company,"A11216",uid,invoice.toString())
            assertEquals(listOf("GET_TOKEN","GET_SERVER_INFORMATION"),calls)
            assertFalse(prepared.toString().contains("demo-token"))
            val receipt=gateway.send(prepared,uid)
            assertEquals("legacy-ack",receipt.referenceNumber)
            val result=gateway.inquire(company,"A11216",uid,taxId)
            assertEquals("SUCCESS",result.status)
            assertEquals(listOf("GET_TOKEN","GET_SERVER_INFORMATION","INVOICE.V01",
                "GET_TOKEN","INQUIRY_BY_UID"),calls)
        } finally { db.close() }
    }
}
