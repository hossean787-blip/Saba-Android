package ir.saba.invoice.data

import ir.saba.invoice.data.identifiers.SuraStarterCatalog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SuraStarterCatalogTest {
    private fun fixture():JSONObject {
        val a=JSONArray();var n=0
        SuraStarterCatalog.groups.forEach{(g,count)->repeat(count){
            a.put(JSONObject().put("id",(1000000000000L+n++).toString()).put("group",g)
                .put("description","test only").put("vat","10").put("taxable","test").put("kind","شناسه عمومی خدمت")
                .put("expiration_date","").put("source","test"))
        }}
        return JSONObject().put("version",1).put("source_hash","a".repeat(64)).put("as_of","2026-11-06").put("entries",a)
    }
    @Test fun validatesSizeAndUniqueness() {
        val o=fixture()
        assertEquals(150,SuraStarterCatalog.parse(o.toString()).entries.size)
        o.getJSONArray("entries").getJSONObject(1).put("id",o.getJSONArray("entries").getJSONObject(0).getString("id"))
        try{SuraStarterCatalog.parse(o.toString());fail("duplicate accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun rejectsIncompleteCatalog() {
        val o=fixture();o.getJSONArray("entries").remove(0)
        try{SuraStarterCatalog.parse(o.toString());fail("incomplete accepted")}catch(_:IllegalArgumentException){}
    }
}
