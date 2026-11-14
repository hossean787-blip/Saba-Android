package ir.saba.invoice.ui

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ZarinPalPurchaseClientTest {
    @Test fun unauthorizedMessageExplainsLoginIssue() {
        assertTrue(ZarinPalPurchaseClient.createFailureMessage(
            401, JSONObject("""{"error":"unauthorized"}""")).contains("نشست ورود"))
    }

    @Test fun networkFailureExplainsServerConnection() {
        assertTrue(ZarinPalPurchaseClient.createFailureMessage(
            503, JSONObject("""{"error":"gateway_network_unavailable"}""")).contains("ارتباط سرور"))
    }

    @Test fun gatewayRejectionUsesValidatedHttpStatus() {
        assertTrue(ZarinPalPurchaseClient.createFailureMessage(
            502, JSONObject("""{"error":"gateway_http_error","httpStatus":403}""")).contains("HTTP 403"))
        assertTrue(!ZarinPalPurchaseClient.createFailureMessage(
            502, JSONObject("""{"error":"gateway_http_error","httpStatus":999999}""")).contains("999999"))
    }

    @Test fun merchantAndPriceErrorsAreDistinct() {
        assertTrue(ZarinPalPurchaseClient.createFailureMessage(
            503, JSONObject("""{"error":"merchant_not_configured"}""")).contains("شناسه پذیرنده"))
        assertTrue(ZarinPalPurchaseClient.createFailureMessage(
            503, JSONObject("""{"error":"price_unavailable"}""")).contains("قیمت اعتبار"))
    }
    @Test fun pendingRecoveryRejectsMalformedOrSpoofedOrderIds() {
        val id = "11111111-1111-4111-8111-111111111111"
        assertEquals(id,ZarinPalPurchaseClient.parsePendingOrder(JSONObject().put("orderId",id)))
        assertNull(ZarinPalPurchaseClient.parsePendingOrder(JSONObject().put("orderId",JSONObject.NULL)))
        assertNull(ZarinPalPurchaseClient.parsePendingOrder(JSONObject("{}")))
        for(bad in listOf("", "../invalid", "not-a-uuid", 123)) {
            assertTrue(runCatching {
                ZarinPalPurchaseClient.parsePendingOrder(JSONObject().put("orderId",bad))
            }.isFailure)
        }
    }

}
