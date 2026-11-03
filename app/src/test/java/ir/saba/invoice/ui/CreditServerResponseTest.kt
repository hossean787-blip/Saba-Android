package ir.saba.invoice.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CreditServerResponseTest {
    @Test fun requiresExplicitConsumptionEvidenceForBothPaidAndGift() {
        assertEquals(2L,CreditServerResponse.consumed(JSONObject("""{"consumed":true,"remaining":2}""")))
        assertEquals(2L,CreditServerResponse.consumed(JSONObject("""{"consumed":false,"already_consumed":true,"remaining":2}""")))
        for(raw in listOf("""{"remaining":2}""","""{"consumed":"true","remaining":2}""","""{"consumed":true,"remaining":2,"error":"failed"}""","""{"consumed":true,"already_consumed":true,"remaining":2}"""))
            assertThrows(IllegalArgumentException::class.java){CreditServerResponse.consumed(JSONObject(raw))}
    }
    @Test fun rejectsCoercedFractionalNegativeOrOverflowedBalances() {
        for(value in listOf<Any>("2",2.5,true,-1L,Long.MAX_VALUE))
            assertThrows(IllegalArgumentException::class.java){CreditServerResponse.balance(JSONObject().put("remaining",value),"remaining")}
        assertThrows(IllegalArgumentException::class.java){CreditServerResponse.consumed(JSONObject().put("consumed",true).put("remaining",Int.MAX_VALUE.toLong()+1),Int.MAX_VALUE.toLong())}
    }
    @Test fun onlyAnExactRequestAndAmountReceiptConfirmsATransfer() {
        val r=JSONObject("""{"status":"transferred","receipt":{"id":"request","credits":3}}""")
        assertTrue(CreditServerResponse.transferReceipt(r,"request",3))
        assertFalse(CreditServerResponse.transferReceipt(r,"other",3))
        assertFalse(CreditServerResponse.transferReceipt(r,"request",4))
        assertFalse(CreditServerResponse.transferReceipt(JSONObject().put("status","transferred"),"request",3))
    }
}
