package ir.saba.invoice.ui

import org.json.JSONArray

internal data class SalesOffer(
    val id:String,val title:String,val description:String,val credits:Long,
    val priceRial:Long,val featured:Boolean
) {
    companion object {
        fun parseAll(array:JSONArray):List<SalesOffer> = (0 until array.length()).map { i ->
            val o=array.getJSONObject(i)
            SalesOffer(
                id=o.getString("id"),
                title=o.getString("title"),
                description=o.optString("description"),
                credits=o.getLong("credits"),
                priceRial=o.getLong("price_rial"),
                featured=o.optBoolean("featured",false)
            ).also { require(it.id.isNotBlank() && it.title.length in 2..80 && it.credits in 1L..999999L && it.priceRial>0) }
        }
    }
}
