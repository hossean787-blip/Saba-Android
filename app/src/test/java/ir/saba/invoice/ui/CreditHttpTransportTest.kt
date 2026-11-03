package ir.saba.invoice.ui

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.*
import java.io.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CreditHttpTransportTest {
    private class Fake(url:URL,val code:Int=200,val response:String="{}"):HttpURLConnection(url) {
        val sent=ByteArrayOutputStream();var closed=false;var length=-1
        override fun connect(){}
        override fun usingProxy()=false
        override fun disconnect(){closed=true}
        override fun getResponseCode()=code
        override fun getOutputStream():OutputStream=sent
        override fun getInputStream():InputStream=ByteArrayInputStream(response.toByteArray())
        override fun setFixedLengthStreamingMode(n:Int){length=n}
    }
    @Test fun postsOnlyToFixedOriginWithoutRedirectsOrReplayableBufferedBody()=runBlocking {
        lateinit var fake:Fake
        val transport=CreditHttpTransport(CreditEndpoint.PAID){url -> Fake(url).also{fake=it}}
        transport.post("token",JSONObject().put("operation","consume").put("taxpayerId",12))
        assertEquals("fiiitzfbdjzpmepzjsjw.supabase.co",fake.url.host)
        assertEquals("https",fake.url.protocol);assertEquals("POST",fake.requestMethod)
        assertFalse(fake.instanceFollowRedirects);assertFalse(fake.useCaches);assertTrue(fake.closed)
        assertEquals(fake.sent.size(),fake.length);assertEquals("Bearer token",fake.getRequestProperty("Authorization"))
    }
    @Test fun redirectsErrorsAndOversizedBodiesFailClosed()=runBlocking {
        for((status,body) in listOf(302 to "{}",200 to "{\"error\":\"failed\"}",200 to "x".repeat(65537))) {
            lateinit var fake:Fake
            val t=CreditHttpTransport(CreditEndpoint.GIFT){url -> Fake(url,status,body).also{fake=it}}
            try{t.post("token",JSONObject(),"/consume");fail("Expected rejection")}catch(_:IllegalArgumentException){}
            assertTrue(fake.closed)
        }
    }
    @Test fun newlineTokenAndNonContractPathNeverOpenAConnection()=runBlocking {
        val t=CreditHttpTransport(CreditEndpoint.PAID){error("Must not open connection")}
        try{t.post("token\r\nsecret",JSONObject());fail("Expected rejection")}catch(_:IllegalArgumentException){}
        try{t.post("token",JSONObject(),"/redirect");fail("Expected rejection")}catch(_:IllegalArgumentException){}
    }
}
