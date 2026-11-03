package ir.saba.invoice.ui

import org.json.JSONObject
import java.util.UUID

internal object CreditTransferClient {
    private val http = CreditHttpTransport(CreditEndpoint.PAID)
    private fun validateRequestId(value: String) {
        val id = UUID.fromString(value)
        require(id.toString() == value.lowercase(java.util.Locale.ROOT) && id.version() in 1..8 && id.variant() == 2)
    }
    suspend fun consume(token: String, taxpayerId: Long, invoiceKey: String): JSONObject {
        require(taxpayerId in 1..CreditServerResponse.MAX_SAFE_CREDITS)
        validateRequestId(invoiceKey)
        return http.post(token,JSONObject().put("operation","consume").put("taxpayerId",taxpayerId).put("invoiceKey",invoiceKey))
            .also { CreditServerResponse.consumed(it) }
    }
    suspend fun receipt(token: String, taxpayerId: Long, requestId: String): JSONObject {
        require(taxpayerId in 1..CreditServerResponse.MAX_SAFE_CREDITS)
        validateRequestId(requestId)
        return http.post(token,JSONObject().put("operation","receipt").put("taxpayerId",taxpayerId).put("requestId",requestId))
    }
    suspend fun request(token: String, taxpayerId: Long, amount: Long? = null, requestId: String? = null): JSONObject {
        require(taxpayerId in 1..CreditServerResponse.MAX_SAFE_CREDITS)
        val body = JSONObject().put("taxpayerId",taxpayerId)
        if (amount != null) {
            require(amount in 1..100_000_000L)
            validateRequestId(requireNotNull(requestId))
            body.put("operation","transfer").put("credits",amount).put("requestId",requestId)
        } else body.put("operation","balance")
        return http.post(token,body)
    }
}
