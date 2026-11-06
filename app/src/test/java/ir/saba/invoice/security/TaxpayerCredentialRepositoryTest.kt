package ir.saba.invoice.security

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import javax.crypto.KeyGenerator
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaxpayerCredentialRepositoryTest {
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val otherAccount=AccountScope.of("22222222-2222-4222-8222-222222222222")
    private val now=Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()
    private lateinit var db:SabaDatabase
    private lateinit var prefs:android.content.SharedPreferences
    private lateinit var repository:TaxpayerCredentialRepository
    private var first=0L;private var second=0L;private var foreign=0L
    companion object {
        private val keyA by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
        private val keyB by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    }
    @Before fun setup()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        db=Room.inMemoryDatabaseBuilder(context,SabaDatabase::class.java).build()
        prefs=context.getSharedPreferences("credential_test",0);prefs.edit().clear().commit()
        val wrapping=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
        repository=TaxpayerCredentialRepository(db,account,prefs,TaxpayerKeyVault{wrapping})
        first=db.companyDao().insert(Company(purchaserId=account.ownerId,name="اول",nationalId="1"))
        second=db.companyDao().insert(Company(purchaserId=account.ownerId,name="دوم",nationalId="2"))
        foreign=db.companyDao().insert(Company(purchaserId=otherAccount.ownerId,name="بیگانه",nationalId="3"))
        for ((id,memory) in listOf(first to "A11216",second to "ABC123",foreign to "A11216"))
            db.moadianDetailsDao().upsert(MoadianDetails(companyId=id,fiscalMemoryId=memory))
    }
    @After fun close(){db.close()}
    private suspend fun saveA()=repository.save(first,TestCertificates.pem(keyA),TestCertificates.certificate(keyA),now)
    private suspend fun rejected(block:suspend()->Unit){
        try {block();fail("Expected credential rejection")}catch(_:IllegalArgumentException){}
    }
    @Test fun encryptsPrivateKeyAndRestoresOnlyItsScopedProfile()=runBlocking {
        val profile=saveA()
        assertEquals(TaxpayerIdentity(account,first,"A11216"),profile.identity)
        val raw=prefs.getString("company_$first",null)!!
        assertFalse(raw.contains("PRIVATE KEY"));assertFalse(raw.contains(Base64.getEncoder().encodeToString(keyA.private.encoded)))
        assertEquals(profile.certificateSha256,repository.profile(first,now)!!.certificateSha256)
    }
    @Test fun foreignAccountCannotSaveOrReadCredentials()=runBlocking {
        rejected{repository.save(foreign,TestCertificates.pem(keyA),TestCertificates.certificate(keyA),now)}
        rejected{repository.profile(foreign,now)}
        assertFalse(prefs.contains("company_$foreign"))
    }
    @Test fun mismatchedOrExpiredCertificateDoesNotOverwriteGoodProfile()=runBlocking {
        val profile=saveA()
        rejected{repository.save(first,TestCertificates.pem(keyA),TestCertificates.certificate(keyB),now)}
        try {repository.save(first,TestCertificates.pem(keyA),TestCertificates.certificate(keyA,true),now);fail("Expected expiry rejection")}catch(_:java.security.cert.CertificateException){}
        assertEquals(profile.certificateSha256,repository.profile(first,now)!!.certificateSha256)
    }
    @Test fun changedFiscalMemoryCannotReuseOldCredentialEnvelope()=runBlocking {
        saveA()
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=first,fiscalMemoryId="ZZZ999"))
        rejected{repository.profile(first,now)}
    }
    @Test fun copyingCredentialRecordToAnotherTaxpayerFails()=runBlocking {
        saveA()
        prefs.edit().putString("company_$second",prefs.getString("company_$first",null)).commit()
        rejected{repository.profile(second,now)}
    }
    @Test fun removingOneTaxpayerPreservesOthers()=runBlocking {
        saveA();repository.save(second,TestCertificates.pem(keyB),TestCertificates.certificate(keyB),now)
        repository.remove(first)
        assertNull(repository.profile(first,now));assertNotNull(repository.profile(second,now))
    }
    @Test fun v2AuthorizationUsesSelectedTaxpayerKeyCertificateAndMemory()=runBlocking {
        saveA();repository.save(second,TestCertificates.pem(keyB),TestCertificates.certificate(keyB),now)
        val challenge=TaxpayerNonce(TaxpayerIdentity(account,second,"ABC123"),"synthetic-nonce",now+30000)
        val jws=MoadianV2Signer(repository).authorization(second,challenge,now)
        val parts=jws.split('.')
        val decoder=Base64.getUrlDecoder()
        val header=JSONObject(String(decoder.decode(parts[0])))
        val payload=JSONObject(String(decoder.decode(parts[1])))
        assertEquals("ABC123",payload.getString("clientId"));assertEquals("synthetic-nonce",payload.getString("nonce"))
        assertEquals("RS256",header.getString("alg"));assertEquals("sigT",header.getJSONArray("crit").getString(0))
        assertEquals("2026-11-03T12:00:00Z",header.getString("sigT"))
        assertArrayEquals(TestCertificates.certificate(keyB),Base64.getDecoder().decode(header.getJSONArray("x5c").getString(0)))
        val signed=(parts[0]+"."+parts[1]).toByteArray(Charsets.US_ASCII)
        assertTrue(Signature.getInstance("SHA256withRSA").run{initVerify(keyB.public);update(signed);verify(decoder.decode(parts[2]))})
        assertFalse(Signature.getInstance("SHA256withRSA").run{initVerify(keyA.public);update(signed);verify(decoder.decode(parts[2]))})
    }
    @Test fun expiredForeignAndReplayedNoncesCannotAuthorize()=runBlocking {
        saveA();val signer=MoadianV2Signer(repository)
        rejected{signer.authorization(first,TaxpayerNonce(TaxpayerIdentity(otherAccount,first,"A11216"),"nonce",now+30000),now)}
        rejected{signer.authorization(first,TaxpayerNonce(TaxpayerIdentity(account,first,"A11216"),"nonce",now),now)}
        val nonce=TaxpayerNonce(TaxpayerIdentity(account,first,"A11216"),"nonce",now+30000)
        signer.authorization(first,nonce,now)
        try{signer.authorization(first,nonce,now);fail("Expected replay rejection")}catch(_:IllegalStateException){}
    }
    @Test fun recreatedNonceObjectCannotBypassSignerReplayCheck()=runBlocking {
        saveA();val signer=MoadianV2Signer(repository)
        val identity=TaxpayerIdentity(account,first,"A11216")
        signer.authorization(first,TaxpayerNonce(identity,"one-use",now+30000),now)
        try{signer.authorization(first,TaxpayerNonce(identity,"one-use",now+30000),now);fail("Expected repeated nonce rejection")}catch(_:IllegalStateException){}
    }
    @Test fun connectionDiagnosticUsesOnlySelectedIdentityAndDoesNotSubmitInvoices()=runBlocking {
        saveA();val paths=mutableListOf<String>()
        val transport=object:MoadianPublicTransport {
            override suspend fun nonce():String {paths+="nonce";return JSONObject().put("nonce","probe-nonce").put("expDate",Instant.ofEpochMilli(now+30000).toString()).toString()}
            override suspend fun serverInformation(authorization:String):String {
                paths+="server-information"
                val payload=JSONObject(String(Base64.getUrlDecoder().decode(authorization.split('.')[1])))
                assertEquals("A11216",payload.getString("clientId"))
                return """{"serverTime":1,"publicKeys":[{"id":"synthetic"}]}"""
            }
        }
        MoadianConnectionProbe(repository,transport){now}.check(first)
        assertEquals(listOf("nonce","server-information"),paths)
        assertTrue(db.invoiceDao().forCompany(first).isEmpty())
    }
    @Test fun inactiveTaxpayerCannotAuthorize()=runBlocking {
        saveA();db.moadianDetailsDao().upsert(MoadianDetails(companyId=first,fiscalMemoryId="A11216",inactive=true))
        rejected{MoadianV2Signer(repository).authorization(first,TaxpayerNonce(TaxpayerIdentity(account,first,"A11216"),"nonce",now+30000),now)}
    }
    @Test fun privateKeyCanBeStagedBeforeFiscalMemoryAndCannotSendUntilCertificate() = runBlocking {
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=first,fiscalMemoryId=""))
        val pem=TestCertificates.pem(keyA)
        repository.stagePrivateKey(first,pem)
        assertTrue(repository.hasPendingPrivateKey(first))
        val envelope=prefs.getString("pending_company_$first",null)!!
        assertFalse(envelope.contains("PRIVATE KEY"))
        assertFalse(envelope.contains(Base64.getEncoder().encodeToString(keyA.private.encoded)))
        try {repository.profile(first,now);fail("Missing memory ID must block official material")}
        catch(_:IllegalArgumentException){}
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=first,fiscalMemoryId="A11216"))
        assertNull(repository.profile(first,now))
        val profile=repository.saveWithPendingPrivateKey(first,TestCertificates.certificate(keyA),now)
        assertEquals("A11216",profile.identity.fiscalMemoryId)
        assertFalse(repository.hasPendingPrivateKey(first))
        assertNotNull(repository.profile(first,now))
    }

    @Test fun pendingKeyCannotBeMovedBetweenTaxpayersOrAccounts() = runBlocking {
        repository.stagePrivateKey(first,TestCertificates.pem(keyA))
        val stolen=prefs.getString("pending_company_$first",null)!!
        prefs.edit().putString("pending_company_$second",stolen).commit()
        try {
            repository.saveWithPendingPrivateKey(second,TestCertificates.certificate(keyA),now)
            fail("AEAD must bind pending key to the original taxpayer")
        } catch(_:Exception) {}
        assertNull(repository.profile(second,now))
        try {repository.stagePrivateKey(foreign,TestCertificates.pem(keyA));fail("Cross-account staging must fail")}
        catch(_:IllegalArgumentException){}
        repository.remove(first)
        assertFalse(repository.hasPendingPrivateKey(first))
    }
    @Test fun certificateFreeProbeAuthenticatesAndReadsOnlyScopedMemory() = runBlocking {
        repository.stagePrivateKey(first,TestCertificates.pem(keyA))
        val expectedId="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
        val issuedToken="synthetic-token-for-tests-only"
        val called=mutableListOf<String>()
        val transport=object:LegacyMoadianTransport {
            override suspend fun post(operation:String,envelope:JSONObject,headers:Map<String,String>):JSONObject {
                called+=operation
                assertTrue(operation in setOf("GET_TOKEN","GET_FISCAL_INFORMATION"))
                val packet=envelope.getJSONObject("packet")
                assertEquals(operation,packet.getString("packetType"))
                assertEquals(expectedId,headers.getValue("requestTraceId"))
                assertEquals(now.toString(),headers.getValue("timestamp"))
                assertEquals(1,envelope.getInt("time"))
                assertFalse(envelope.toString().contains("PRIVATE KEY"))
                val fields=sortedMapOf<String,String>()
                if(operation=="GET_TOKEN") {
                    assertFalse(headers.containsKey("Authorization"))
                    assertEquals("A11216",packet.getJSONObject("data").getString("username"))
                    fields["data.username"]="A11216"
                    fields["fiscalId"]="#"
                } else {
                    assertEquals("Bearer $issuedToken",headers.getValue("Authorization"))
                    assertEquals("A11216",packet.getString("fiscalId"))
                    fields["Authorization"]=issuedToken
                    fields["data"]="#"
                    fields["fiscalId"]="A11216"
                }
                fields["dataSignature"]="#"
                fields["encryptionKeyId"]="#"
                fields["iv"]="#"
                fields["packetType"]=operation
                fields["requestTraceId"]=expectedId
                fields["retry"]="false"
                fields["symmetricKey"]="#"
                fields["timestamp"]=now.toString()
                fields["uid"]="#"
                val canonical=fields.values.joinToString("#")
                val signature=Signature.getInstance("SHA256withRSA").apply {
                    initVerify(keyA.public);update(canonical.toByteArray(Charsets.UTF_8))
                }
                assertTrue("Legacy request signature must match canonical contract",
                    signature.verify(Base64.getDecoder().decode(envelope.getString("signature"))))
                return if(operation=="GET_TOKEN")
                    JSONObject().put("result",JSONObject().put("data",JSONObject().put("token",issuedToken)))
                else
                    JSONObject().put("result",JSONObject().put("data",
                        JSONObject().put("fiscalStatus","ACTIVE").put("economicCode","12345678901234")))
            }
        }
        MoadianLegacyConnectionProbe(repository,transport,{now},{expectedId}).check(first,"A11216")
        assertEquals(listOf("GET_TOKEN","GET_FISCAL_INFORMATION"),called)
        assertFalse(prefs.getString("pending_company_$first","")!!.contains(issuedToken))
    }

    @Test fun certificateFreeProbeRejectsWrongTaxpayerAndMissingKeyBeforeNetwork() = runBlocking {
        repository.stagePrivateKey(first,TestCertificates.pem(keyA))
        var requests=0
        val transport=object:LegacyMoadianTransport {
            override suspend fun post(operation:String,envelope:JSONObject,headers:Map<String,String>):JSONObject {
                requests++
                return JSONObject()
            }
        }
        val probe=MoadianLegacyConnectionProbe(repository,transport,{now})
        try {probe.check(second,"ABC123");fail("Missing staged key must be rejected")}
        catch(_:IllegalStateException) {}
        try {probe.check(first,"ABC123");fail("Mismatched fiscal ID must be rejected")}
        catch(_:IllegalArgumentException) {}
        assertEquals(0,requests)
    }

    @Test fun certificateFreeProbeDoesNotTreatTokenOrEmptyMemoryResponseAsSuccess() = runBlocking {
        repository.stagePrivateKey(first,TestCertificates.pem(keyA))
        var count=0
        val transport=object:LegacyMoadianTransport {
            override suspend fun post(operation:String,envelope:JSONObject,headers:Map<String,String>):JSONObject {
                count++
                return if(operation=="GET_TOKEN")
                    JSONObject().put("result",JSONObject().put("data",JSONObject().put("token","mock-token")))
                else JSONObject().put("result",JSONObject().put("data",JSONObject()))
            }
        }
        try {
            MoadianLegacyConnectionProbe(repository,transport,{now}).check(first,"A11216")
            fail("Empty fiscal information must not count as a verified connection")
        }catch(_:IllegalArgumentException){}
        assertEquals(2,count)
    }
}
