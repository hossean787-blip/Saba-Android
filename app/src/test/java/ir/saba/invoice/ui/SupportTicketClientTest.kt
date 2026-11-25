package ir.saba.invoice.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.net.*
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SupportTicketClientTest {
    private val account=AccountScope.of("11111111-1111-4111-8111-111111111111")
    private val other=AccountScope.of("22222222-2222-4222-8222-222222222222")
    private val ticket="11111111-2222-4333-8444-555555555555"
    private val message="22222222-2222-4333-8444-555555555555"
    private fun token(owner:AccountScope):String {
        val payload=JSONObject().put("sub",owner.ownerId).put("exp",System.currentTimeMillis()/1000+3600)
        return "e30."+Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().toByteArray())+".test"
    }
    private class Fake(url:URL,private val code:Int,private val response:String):HttpURLConnection(url) {
        val sent=ByteArrayOutputStream()
        override fun connect(){}
        override fun usingProxy()=false
        override fun disconnect(){}
        override fun getResponseCode()=code
        override fun getOutputStream():OutputStream=sent
        override fun getInputStream():InputStream=ByteArrayInputStream(response.toByteArray())
        override fun setFixedLengthStreamingMode(n:Int){}
    }
    private fun ticketRow(status:String="open")=JSONObject().put("id",ticket).put("category","مشکل فنی")
        .put("message","initial support question").put("status",status).put("response",JSONObject.NULL).put("created_at","2026-11-25")
    private fun messageRow()=JSONObject().put("id",message).put("ticket_id",ticket).put("sender","customer")
        .put("body","follow up").put("created_at","2026-11-25")
    @Test fun lostReplyAcknowledgementReadsOriginalIdWithoutSecondPost()=runBlocking {
        val answers=ArrayDeque(listOf(200 to "[]",200 to JSONArray().put(ticketRow()).toString(),503 to "[]",
            200 to JSONArray().put(messageRow()).toString()))
        val calls=mutableListOf<Fake>()
        val client=SupportTicketClient(account,token(account)){url->
            val (code,body)=answers.removeFirst();Fake(url,code,body).also{calls.add(it)}
        }
        assertEquals(message,client.reply(message,ticket,"follow up").id)
        assertEquals(1,calls.count{it.requestMethod=="POST"})
        val post=calls.single{it.requestMethod=="POST"}
        val json=JSONObject(post.sent.toString("UTF-8"))
        assertEquals(message,json.getString("id"));assertEquals(ticket,json.getString("ticket_id"))
        assertFalse(json.has("sender"));assertFalse(json.has("owner_id"))
        assertTrue(calls.all{it.url.protocol=="https"&&!it.instanceFollowRedirects})
        assertTrue(answers.isEmpty())
    }
    @Test fun closedTicketCannotPostAndForeignSessionCannotConnect()=runBlocking {
        val answers=ArrayDeque(listOf("[]",JSONArray().put(ticketRow("closed")).toString()))
        val calls=mutableListOf<Fake>()
        val client=SupportTicketClient(account,token(account)){url->Fake(url,200,answers.removeFirst()).also{calls.add(it)}}
        try{client.reply(message,ticket,"follow up");fail("closed ticket")}catch(_:IllegalArgumentException){}
        assertTrue(calls.all{it.requestMethod=="GET"})
        val foreign=SupportTicketClient(account,token(other)){error("must never connect")}
        try{foreign.list();fail("foreign token")}catch(_:IllegalArgumentException){}
    }
    @Test fun privateKeyTextIsRejectedBeforeNetwork()=runBlocking {
        val client=SupportTicketClient(account,token(account)){error("must not connect")}
        try{client.create(ticket,"مشکل فنی","-----BEGIN RSA PRIVATE KEY-----", "1");fail("secret")}catch(_:IllegalArgumentException){}
    }
    @Test fun operationJournalIsAccountScopedAndSurvivesRecreation() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val a=SupportOperationJournal(context,account)
        val id=a.id("reply:$ticket","same text")
        assertEquals(id,SupportOperationJournal(context,account).id("reply:$ticket","same text"))
        assertNotEquals(id,SupportOperationJournal(context,other).id("reply:$ticket","same text"))
        a.complete(id)
        assertNotEquals(id,a.id("reply:$ticket","same text"))
    }
}
