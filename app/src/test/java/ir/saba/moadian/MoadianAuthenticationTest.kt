package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MoadianAuthenticationTest {
    @Test fun validTokenUsesInjectedTransport() = runBlocking {
        var requests = 0
        val client = object : MoadianHttpClient {
            override suspend fun post(path: String, bearerToken: String, body: ByteArray): ByteArray {
                requests++
                assertEquals("session", bearerToken)
                return "ok".toByteArray()
            }
        }
        val auth = object : AccessTokenProvider {
            override suspend fun tokenFor(fiscalMemoryId: String) = AccessToken("session", 200_000L)
        }
        val answer = AuthenticatedMoadianGateway(client, auth).submitFor("ABC123", "/api/v2/invoice", byteArrayOf(1), 10L)
        assertEquals("ok", String(answer))
        assertEquals(1, requests)
    }
    @Test(expected = IllegalStateException::class) fun expiredTokenBlocksNetwork() = runBlocking {
        val client = object : MoadianHttpClient {
            override suspend fun post(path: String, bearerToken: String, body: ByteArray): ByteArray =
                error("Must not invoke HTTP")
        }
        val auth = object : AccessTokenProvider {
            override suspend fun tokenFor(fiscalMemoryId: String) = AccessToken("expired", 5L)
        }
        AuthenticatedMoadianGateway(client, auth).submitFor("ABC123", "/api/v2/invoice", byteArrayOf(1), 10L)
    }
}
