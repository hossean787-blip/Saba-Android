package ir.saba.invoice.ui

import org.json.JSONObject
import org.junit.Assert.assertTrue
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
}
