package ir.saba.invoice.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.data.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OfficialSubmissionServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val account = AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val now = Instant.parse("2026-11-03T16:00:00Z").toEpochMilli()
    private val name = "official-${UUID.randomUUID()}.db"
    private lateinit var db: SabaDatabase
    private lateinit var credentials: TaxpayerCredentialRepository
    private lateinit var api: FakeApi
    private lateinit var service: OfficialSubmissionService
    private var company = 0L
    private var invoice = 0L
    private val wrapping = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    companion object {
        private val customerKey by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
        private val organizationKey by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    }
    private fun open() {
        db=Room.databaseBuilder(context,SabaDatabase::class.java,name).build()
        val prefs=context.getSharedPreferences(name,0)
        credentials=TaxpayerCredentialRepository(db,account,prefs,TaxpayerKeyVault{wrapping})
        service=OfficialSubmissionService(db,account,credentials,api,sessionCheck={}){now}
    }
    @Before fun setup()=runBlocking {
        api=FakeApi();open()
        company=db.companyDao().insert(Company(purchaserId=account.ownerId,name="فروشنده",nationalId="1",economicId="12345678901"))
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=company,fiscalMemoryId="A11216"))
        val buyer=db.customerDao().insert(Customer(companyId=company,name="خریدار",nationalId="12345678901",economicId="12345678901"))
        invoice=db.invoiceDao().insert(Invoice(companyId=company,customerId=buyer,invoiceNumber="12",issueDate="1405/08/12",totalAmountRial=2200,taxAmountRial=200,status="FINALIZED"))
        db.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=invoice,itemCode="2710000138624",description="کالا",quantity=2,unitPriceRial=1000,taxRatePercent=10)))
        credentials.save(company,TestCertificates.pem(customerKey),TestCertificates.certificate(customerKey),now)
        Unit
    }
    @After fun close(){db.close();context.deleteDatabase(name);context.getSharedPreferences(name,0).edit().clear().commit()}
    private suspend fun prepare()=service.prepare(company,invoice,"2026-11-03T12:00:00+03:30","164").also { api.row=it }
    private suspend fun reject(action:suspend()->Unit){try{action();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    private inner class FakeApi:OfficialTaxTransport {
        lateinit var row:OfficialSubmission
        var posts=0;var gets=0;var nonces=0
        var lost=false;var status="SUCCESS";var corrupt=false;var wrongUid=false;var wrongTax=false;var metadataFailure=false
        var body=""
        var postGate:CompletableDeferred<Unit>?=null
        override suspend fun request(method:String,path:String,token:String?,body:String?):String {
            if(method=="POST") {
                posts++;this.body=body!!
                assertEquals("SENDING",db.officialSubmissionDao().byId(row.requestId)!!.state)
                postGate?.await()
                if(lost) throw java.io.IOException("lost response")
                return JSONObject().put("result",JSONArray().put(JSONObject().put("uid",if(wrongUid) "another" else row.requestId).put("referenceNumber","official-reference"))).toString()
            }
            gets++
            if(path.startsWith("nonce")) return JSONObject().put("nonce","challenge-${++nonces}").put("expDate",Instant.ofEpochMilli(now+30000).toString()).toString()
            assertFalse(token.isNullOrBlank())
            if(path=="server-information") {
                if(metadataFailure) throw java.io.IOException("offline")
                return JSONObject().put("serverTime",now).put("publicKeys",JSONArray().put(JSONObject().put("algorithm","RSA").put("purpose",1).put("id","organization-key").put("key",Base64.getEncoder().encodeToString(organizationKey.public.encoded)))).toString()
            }
            assertTrue(path.startsWith("inquiry-by-uid?"));assertTrue(path.contains("start="));assertTrue(path.contains("uidList=${row.requestId}"))
            val signedPayload=JSONObject().put("referenceNumber","official-reference").put("clientId",row.fiscalId).put("taxId",if(wrongTax) "OTHER" else row.taxId).put("receivedDate",Instant.ofEpochMilli(now-1000).toString())
            val encoder=Base64.getUrlEncoder().withoutPadding()
            val header=JSONObject().put("alg","RS256").put("sigT",Instant.ofEpochMilli(now).toString())
            val input=encoder.encodeToString(header.toString().toByteArray())+"."+encoder.encodeToString(signedPayload.toString().toByteArray())
            val signature=Signature.getInstance("SHA256withRSA").run{initSign(if(corrupt) customerKey.private else organizationKey.private);update(input.toByteArray());sign()}
            val signed=input+"."+encoder.encodeToString(signature)
            return JSONArray().put(JSONObject().put("uid",if(wrongUid) "other" else row.requestId).put("fiscalId",row.fiscalId).put("referenceNumber","official-reference").put("status",status).put("sign",signed)
                .put("data",JSONObject().put("error",JSONArray().put(JSONObject().put("code","E123"))))).toString()
        }
    }
    @Test fun futureSelectedTimeBlocksSubmissionBeforeAnyTaxNetworkOrQuotaChange()=runBlocking {
        val original = requireNotNull(db.invoiceDao().byId(invoice))
        val future = InvoiceLocalDetails(
            sendLaterDate = "۱۴۰۵/۰۹/۰۱", sendLaterTime = "۱۸:۰۰"
        ).toJson()
        db.invoiceDao().update(original.copy(localDetailsJson = future))
        val prepared = prepare()
        reject { service.submit(prepared.requestId) }
        assertEquals("PREPARED", db.officialSubmissionDao().byId(prepared.requestId)?.state)
        assertEquals(0, api.gets)
        assertEquals(0, api.posts)
        assertNull(db.invoiceDao().byId(invoice)?.trackingCode)
    }

    private suspend fun acceptedOriginalForReference(): OfficialSubmission {
        val original=prepare()
        assertEquals(1,db.officialSubmissionDao().claim(original.requestId))
        assertEquals(1,db.officialSubmissionDao().resolve(
            original.requestId, "ACCEPTED", "verified-reference", null, "verified-receipt-fixture"))
        return original
    }

    private suspend fun createReferredDocument(
        subject: String, number: String, quantity: Long
    ): Long {
        val old=requireNotNull(db.invoiceDao().byId(invoice))
        val reference=requireNotNull(db.officialSubmissionDao().forInvoice(invoice))
        val id=db.invoiceDao().insert(old.copy(id=0L, invoiceNumber=number,
            issueDate="1405/08/13", subject=subject, referenceTaxId=reference.taxId,
            totalAmountRial=quantity*1100, taxAmountRial=quantity*100,
            trackingCode=null))
        db.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId=id,
            itemCode="2710000138624",description="کالا",quantity=quantity,
            unitPriceRial=1000,taxRatePercent=10)))
        return id
    }

    @Test fun acceptedOriginalUnlocksOfficialCorrectionHeaderAndPreservesParentReference()=runBlocking {
        val parent=acceptedOriginalForReference()
        val id=createReferredDocument("CORRECTION","13",2L)
        val next=OfficialSubmissionService(db,account,credentials,api,
            sessionCheck={}){now + 86_400_000}
        val prepared=next.prepare(company,id,"2026-11-04T12:00:00+03:30","164")
        val h=JSONObject(prepared.payload).getJSONObject("header")
        assertEquals(2,h.getInt("ins"))
        assertEquals(parent.taxId,h.getString("irtaxid"))
        assertEquals("PREPARED",prepared.state)
        assertEquals(0,api.posts)
        assertEquals(0,api.gets)
    }

    @Test fun acceptedOriginalUnlocksOnlyPartialReturnAndRejectsChangedTaxRate()=runBlocking {
        val parent=acceptedOriginalForReference()
        val id=createReferredDocument("RETURN","14",1L)
        val next=OfficialSubmissionService(db,account,credentials,api,
            sessionCheck={}){now + 86_400_000}
        val result=next.prepare(company,id,"2026-11-04T12:00:00+03:30","164")
        val header=JSONObject(result.payload).getJSONObject("header")
        assertEquals(4,header.getInt("ins"))
        assertEquals(parent.taxId,header.getString("irtaxid"))
        assertEquals(1100L,header.getLong("tbill"))
        assertEquals(0,api.posts)
    }

    @Test fun unacceptedParentCannotCreateOfficialCorrectionOrSend()=runBlocking {
        val parent=prepare()
        val id=createReferredDocument("CORRECTION","13",2L)
        val next=OfficialSubmissionService(db,account,credentials,api,
            sessionCheck={}){now + 86_400_000}
        reject{next.prepare(company,id,"2026-11-04T12:00:00+03:30","164")}
        assertNull(db.officialSubmissionDao().forInvoice(id))
        assertEquals("PREPARED",db.officialSubmissionDao().byId(parent.requestId)?.state)
        assertEquals(0,api.posts);assertEquals(0,api.gets)
    }

    @Test fun signingReadinessIsLocalOnlyAndFailsClosedWithoutCredentials()=runBlocking {
        val ready=service.checkLocalSigningReadiness(company)
        assertEquals("A11216", ready.identity.fiscalMemoryId)
        assertTrue(ready.expiresAtEpochMillis > now)
        assertEquals(0, api.posts)
        assertEquals(0, api.gets)
        val other=OfficialSubmissionService(db, AccountScope.of(
            "22222222-2222-4222-8222-222222222222"), credentials, api,
            sessionCheck={}){now}
        reject { other.checkLocalSigningReadiness(company) }
        credentials.remove(company)
        reject { service.checkLocalSigningReadiness(company) }
        assertEquals(0, api.gets)
    }

    @Test fun creditAndMixedPreparationKeepOfficialSharesWithoutNetwork()=runBlocking {
        val original=requireNotNull(db.invoiceDao().byId(invoice))
        db.invoiceDao().update(original.copy(settlementMode="CREDIT",cashPaidRial=0L))
        val credit=service.prepare(company,invoice,"2026-11-03T12:00:00+03:30","164")
        val c=JSONObject(credit.payload).getJSONObject("header")
        assertEquals(2,c.getInt("setm"))
        assertEquals(2000L,c.getLong("insp"))
        assertEquals(0L,c.getLong("cap"))

        val mixedId=db.invoiceDao().insert(original.copy(
            id=0L,invoiceNumber="13",settlementMode="MIXED",cashPaidRial=1100L
        ))
        db.invoiceLineDao().insertAll(listOf(
            InvoiceLine(invoiceId=mixedId,itemCode="2710000138624",
                description="کالا",quantity=2,unitPriceRial=1000,taxRatePercent=10)
        ))
        val mixed=service.prepare(company,mixedId,"2026-11-03T12:00:00+03:30","164")
        val h=JSONObject(mixed.payload).getJSONObject("header")
        assertEquals(3,h.getInt("setm"))
        assertEquals(1000L,h.getLong("cap"))
        assertEquals(1000L,h.getLong("insp"))
        assertEquals(100L,h.getLong("tvop"))
        assertEquals("PREPARED",mixed.state)
        assertEquals(0,api.posts)
        assertEquals(0,api.gets)
    }

    @Test fun acknowledgementIsNotAcceptanceAndSignedInquiryIsBoundToInvoice()=runBlocking {
        val row=prepare();assertEquals("PREPARED",row.state);assertEquals(0,api.posts)
        val sent=service.submit(row.requestId);assertEquals("SUBMITTED",sent.state);assertNull(sent.signedReceipt)
        assertEquals("official-reference",db.invoiceDao().byId(invoice)!!.trackingCode)
        val packet=JSONArray(api.body).getJSONObject(0)
        assertEquals(row.requestId,packet.getJSONObject("header").getString("requestTraceId"))
        assertEquals(row.fiscalId,packet.getJSONObject("header").getString("fiscalId"))
        val parts=packet.getString("payload").split('.')
        assertEquals(5,parts.size)
        val decoder=Base64.getUrlDecoder()
        val rsa=javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(javax.crypto.Cipher.DECRYPT_MODE,organizationKey.private,javax.crypto.spec.OAEPParameterSpec("SHA-256","MGF1",java.security.spec.MGF1ParameterSpec.SHA256,javax.crypto.spec.PSource.PSpecified.DEFAULT))
        val cek=rsa.doFinal(decoder.decode(parts[1]))
        val aes=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(javax.crypto.Cipher.DECRYPT_MODE,javax.crypto.spec.SecretKeySpec(cek,"AES"),javax.crypto.spec.GCMParameterSpec(128,decoder.decode(parts[2])))
        aes.updateAAD(parts[0].toByteArray())
        val jws=String(aes.doFinal(decoder.decode(parts[3])+decoder.decode(parts[4]))).split('.')
        cek.fill(0)
        assertEquals(row.payload,String(decoder.decode(jws[1])))
        assertTrue(Signature.getInstance("SHA256withRSA").run { initVerify(customerKey.public);update((jws[0]+"."+jws[1]).toByteArray());verify(decoder.decode(jws[2])) })
        val accepted=service.inquire(row.requestId)
        assertEquals("ACCEPTED",accepted.state);assertNotNull(accepted.signedReceipt)
        reject{service.submit(row.requestId)}
        assertEquals(1,api.posts);assertNull(db.quotaDao().useForInvoice(invoice))
    }
    @Test fun lostPostResponseSurvivesReopenAndCannotBeResent()=runBlocking {
        val row=prepare();api.lost=true
        assertEquals("UNKNOWN",service.submit(row.requestId).state)
        db.close();open()
        reject{service.submit(row.requestId)}
        assertEquals("ACCEPTED",service.inquire(row.requestId).state)
        assertEquals(1,api.posts)
    }
    @Test fun killedProcessSendingStateCanOnlyBeQueried()=runBlocking {
        val row=prepare();db.officialSubmissionDao().claim(row.requestId)
        db.close();open();reject{service.submit(row.requestId)}
        api.status="IN_PROGRESS";assertEquals("SUBMITTED",service.inquire(row.requestId).state)
        assertEquals(0,api.posts)
    }
    @Test fun forgedSuccessNeverMarksInvoiceAccepted()=runBlocking {
        val row=prepare();service.submit(row.requestId);api.corrupt=true
        assertEquals("UNKNOWN",service.inquire(row.requestId).state)
        assertNull(db.officialSubmissionDao().byId(row.requestId)!!.signedReceipt)
        api.corrupt=false;api.wrongTax=true
        assertEquals("UNKNOWN",service.inquire(row.requestId).state)
    }
    @Test fun notFoundAndTimeoutDoNotEnableBlindRetry()=runBlocking {
        val row=prepare();api.lost=true;service.submit(row.requestId)
        for(status in listOf("NOT_FOUND","TIMEOUT","unexpected")) {
            api.status=status;assertEquals("UNKNOWN",service.inquire(row.requestId).state);reject{service.submit(row.requestId)}
        }
        assertEquals(1,api.posts)
    }
    @Test fun mismatchedAcknowledgementAndInquiryAreUnknown()=runBlocking {
        val row=prepare();api.wrongUid=true
        assertEquals("UNKNOWN",service.submit(row.requestId).state)
        assertEquals("UNKNOWN",service.inquire(row.requestId).state)
    }
    @Test fun failedInquiryRecordsErrorCodeWithoutClaimingSuccess()=runBlocking {
        val row=prepare();service.submit(row.requestId);api.status="FAILED"
        val result=service.inquire(row.requestId)
        assertEquals("REJECTED",result.state);assertTrue(result.lastError!!.contains("E123"));assertNull(result.signedReceipt)
    }
    @Test fun prePostNetworkFailureLeavesPreparedAndAllowsSafeRetry()=runBlocking {
        val row=prepare();api.metadataFailure=true
        try{service.submit(row.requestId);fail("Expected offline")}catch(_:java.io.IOException){}
        assertEquals("PREPARED",db.officialSubmissionDao().byId(row.requestId)!!.state);assertEquals(0,api.posts)
        api.metadataFailure=false;assertEquals("SUBMITTED",service.submit(row.requestId).state)
    }
    @Test fun immutablePreparationAndForeignOwnerAreRejectedBeforeNetwork()=runBlocking {
        val row=prepare();assertEquals(row,prepare())
        reject{service.prepare(company,invoice,"2026-11-03T13:00:00+03:30","164")}
        val other=AccountScope.of("22222222-2222-4222-8222-222222222222")
        val foreign=OfficialSubmissionService(db,other,credentials,api,sessionCheck={}){now}
        reject{foreign.submit(row.requestId)};reject{foreign.history(company)}
        assertEquals(0,api.gets);assertEquals(0,api.posts)
    }
    @Test fun alteredPayloadOrMemoryNeverReachesPost()=runBlocking {
        val row=prepare()
        db.openHelper.writableDatabase.execSQL("UPDATE official_submissions SET payload='{}' WHERE requestId=?",arrayOf(row.requestId))
        reject{service.submit(row.requestId)};assertEquals(0,api.gets)
    }
    @Test fun concurrentSubmitCallsHaveOneNetworkPost()=runBlocking {
        val row=prepare();api.postGate=CompletableDeferred()
        val sending=async { service.submit(row.requestId) }
        while(api.posts==0) yield()
        reject{service.submit(row.requestId)}
        api.postGate!!.complete(Unit)
        assertEquals("SUBMITTED",sending.await().state);assertEquals(1,api.posts)
    }
    @Test fun cancellationAfterSendStartedLeavesRecoverableUnknownState()=runBlocking {
        val row=prepare();api.postGate=CompletableDeferred()
        val sending=launch { service.submit(row.requestId) }
        while(api.posts==0) yield()
        sending.cancelAndJoin()
        assertEquals("UNKNOWN",db.officialSubmissionDao().byId(row.requestId)!!.state)
        reject{service.submit(row.requestId)}
        assertEquals("ACCEPTED",service.inquire(row.requestId).state);assertEquals(1,api.posts)
    }
    @Test fun preparedCanBeDiscardedButSendingCannotBeDeleted()=runBlocking {
        val original=prepare();service.discardPrepared(original.requestId)
        assertNull(db.officialSubmissionDao().forInvoice(invoice))
        val replacement=prepare();assertNotEquals(original.requestId,replacement.requestId)
        assertEquals(original.taxId,replacement.taxId)
        service.submit(replacement.requestId)
        reject{service.discardPrepared(replacement.requestId)}
        assertNotNull(db.officialSubmissionDao().byId(replacement.requestId))
    }
    @Test fun changedFiscalMemoryBlocksExistingRequestBeforeAnyNetworkCall()=runBlocking {
        val row=prepare();db.moadianDetailsDao().upsert(MoadianDetails(companyId=company,fiscalMemoryId="ABC123"))
        reject{service.submit(row.requestId)};assertEquals(0,api.gets);assertEquals(0,api.posts)
    }

    @Test fun sameFiscalSerialCannotBeReusedByChangingDateOrLeadingZeros()=runBlocking {
        prepare()
        val original=db.invoiceDao().byId(invoice)!!
        val duplicate=db.invoiceDao().insert(original.copy(id=0,invoiceNumber="00012",issueDate="1405/08/11"))
        db.invoiceLineDao().insertAll(db.invoiceLineDao().forInvoice(invoice).map{it.copy(id=0,invoiceId=duplicate)})
        reject{service.prepare(company,duplicate,"2026-11-02T12:00:00+03:30","164")}
        assertNull(db.officialSubmissionDao().forInvoice(duplicate));assertEquals(0,api.posts)
    }

    @Test fun oldTrackingCodeCannotBeSubmittedAsANewOfficialRequest()=runBlocking {
        val old=db.invoiceDao().byId(invoice)!!
        db.invoiceDao().update(old.copy(trackingCode="legacy-official-reference"))
        reject{prepare()}
        assertNull(db.officialSubmissionDao().forInvoice(invoice));assertEquals(0,api.posts);assertEquals(0,api.gets)
    }
    @Test fun invoiceTrackingAndOutboxReceiptRollbackTogether()=runBlocking {
        val row=prepare()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER tracking_failure BEFORE UPDATE OF trackingCode ON invoices BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertEquals("UNKNOWN",service.submit(row.requestId).state)
        assertNull(db.invoiceDao().byId(invoice)!!.trackingCode)
        assertNull(db.officialSubmissionDao().byId(row.requestId)!!.referenceNumber)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER tracking_failure")
        assertEquals("ACCEPTED",service.inquire(row.requestId).state)
        assertEquals("official-reference",db.invoiceDao().byId(invoice)!!.trackingCode)
        assertEquals(row.requestId,service.prepare(company,invoice,"2026-11-03T12:00:00+03:30","164").requestId)
        assertEquals(1,api.posts)
    }

    @Test fun missingAppSessionNeverStartsOfficialHttp()=runBlocking {
        val row=prepare()
        val unbound=OfficialSubmissionService(db,account,credentials,api){now}
        reject{unbound.submit(row.requestId)}
        assertEquals(0,api.gets);assertEquals(0,api.posts)
    }
    @Test fun expiredAppSessionBetweenMetadataAndPostDoesNotClaimOrSend()=runBlocking {
        val row=prepare();var checks=0
        val gated=OfficialSubmissionService(db,account,credentials,api,sessionCheck={checks++;require(checks<4)}){now}
        reject{gated.submit(row.requestId)}
        assertEquals("PREPARED",db.officialSubmissionDao().byId(row.requestId)!!.state)
        assertEquals(0,api.posts);assertTrue(api.gets>0)
    }

    @Test fun unsubmittedPreparationCanBeDiscardedAfterFiscalMemoryChanges()=runBlocking {
        val row=prepare()
        db.moadianDetailsDao().upsert(MoadianDetails(companyId=company,fiscalMemoryId="ABC123"))
        reject{service.submit(row.requestId)}
        service.discardPrepared(row.requestId)
        assertNull(db.officialSubmissionDao().byId(row.requestId));assertEquals(0,api.posts)
    }

}
