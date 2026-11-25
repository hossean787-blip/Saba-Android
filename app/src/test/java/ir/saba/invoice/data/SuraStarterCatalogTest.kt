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
                .put("description","test only").put("vat","10").put("taxable","test")
                .put("kind",if(g in setOf("accounting","technical","other"))"شناسه عمومی خدمت" else "شناسه عمومی کالا")
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
    @Test fun bundledServiceCandidatesPreserveReportedRateAndUnverifiedSource() {
        val ctx=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val raw=ctx.assets.open("starter/catalog.json").bufferedReader().use{it.readText()}
        val snapshot=SuraStarterCatalog.parseBundled(raw)
        assertEquals(0L,snapshot.version)
        assertEquals(150,snapshot.entries.size)
        assertEquals(150,snapshot.entries.map{it.value.id}.toSet().size)
        assertTrue(snapshot.entries.all{it.value.vat in setOf("0","10") && it.value.kind.contains("خدمت") && it.value.source == "Mohsensoft (pending)"})
        assertEquals(150, kotlinx.coroutines.runBlocking { ir.saba.invoice.data.identifiers.SuraIdentifierStore(ctx).starterEntries().size })
        val bank=SuraStarterCatalog(ctx)
        assertTrue(bank.search("2330001115075","خدمت",null,50).any{it.id=="2330001115075"})
        assertTrue(bank.search("","کالا",null,50).isEmpty())
        try{SuraStarterCatalog.parse(raw);fail("remote parser accepted candidate asset")}catch(_:Exception){}
    }
    @Test fun rejectsIncompleteCatalog() {
        val o=fixture();o.getJSONArray("entries").remove(0)
        try{SuraStarterCatalog.parse(o.toString());fail("incomplete accepted")}catch(_:IllegalArgumentException){}
    }
}
