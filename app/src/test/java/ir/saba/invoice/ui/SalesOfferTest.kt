package ir.saba.invoice.ui

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SalesOfferTest {
    @Test fun parsesPublishedOffers() {
        val rows=SalesOffer.parseAll(JSONArray("""[{"id":"offer-1","title":"بسته اقتصادی","description":"برای شروع","credits":10,"price_rial":850000,"featured":true}]"""))
        assertEquals(1,rows.size)
        assertEquals("بسته اقتصادی",rows.single().title)
        assertEquals(10L,rows.single().credits)
        assertTrue(rows.single().featured)
    }
    @Test fun rejectsInvalidOffer() {
        assertTrue(runCatching {
            SalesOffer.parseAll(JSONArray("""[{"id":"x","title":"x","credits":0,"price_rial":1000}]"""))
        }.isFailure)
    }
}
