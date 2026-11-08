package ir.saba.moadian

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureMoadianGatewayTest {
    @Test fun acceptsOnlyOfficialHttpsRoots() {
        listOf("https://tp.tax.gov.ir", "https://tp.tax.gov.ir/", "https://tp.tax.gov.ir:443").forEach {
            assertEquals(it, GatewayConfig(it).baseUrl)
        }
    }

    @Test fun refusesEndpointOverridesThatCouldExposeBearerTokens() {
        listOf(
            "http://tp.tax.gov.ir",
            "https://tp.tax.gov.ir:8443",
            "https://tp.tax.gov.ir/api",
            "https://tp.tax.gov.ir/?token=1",
            "https://tp.tax.gov.ir/#fragment",
            "https://user@tp.tax.gov.ir",
            "https://tp.tax.gov.ir.evil.example"
        ).forEach { address ->
            assertThrows("Accepted forbidden URL: $address", IllegalArgumentException::class.java) {
                GatewayConfig(address)
            }
        }
    }

    @Test fun responseReaderRejectsOversizedDataAndPreservesNormalPayloads() {
        val sample = """{"ok":true}""".toByteArray()
        assertArrayEquals(sample, readBoundedMoadianResponse(ByteArrayInputStream(sample)))
        assertThrows(IllegalStateException::class.java) {
            readBoundedMoadianResponse(ByteArrayInputStream(ByteArray(MAX_MOADIAN_RESPONSE_BYTES + 1)))
        }
    }
}
