package ir.saba.invoice.data.identifiers

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Independent, bounded, versioned public starter bank. Never touches taxpayer data. */
internal class SuraStarterCatalog(private val context:Context) {
    data class Entry(val group:String,val value:SuraIdentifier)
    data class Snapshot(val version:Long,val sourceHash:String,val asOf:String,val entries:List<Entry>)
    private val cache=AtomicFile(File(context.filesDir,"sura_starter_catalog.json"))
    private val lock=LOCK
    companion object {
        private val LOCK=Any()
        private const val LIMIT=1024*1024
        private const val URL_PATH="https://fiiitzfbdjzpmepzjsjw.supabase.co/rest/v1/sura_starter_catalog?select=version,source_hash,as_of,entries&order=version.desc&limit=1"
        private const val PUBLIC_KEY="sb_publishable_loYRUibarkSaekgzXQoogQ_Ic_MlDW1"
        val groups=linkedMapOf("accounting" to 25,"computer" to 30,"technical" to 30,"auto" to 40,"other" to 25)
        // Only the bundled asset may contain an unverified discovery catalogue.
        // Remote publishing still uses the existing strict verified-catalog parser.
        fun parseBundled(text:String):Snapshot {
            require(text.toByteArray(Charsets.UTF_8).size<=LIMIT)
            val o=JSONObject(text)
            if(o.optString("catalog_type")!="service_candidates") return parse(text)
            require(o.getString("official_verification")=="pending")
            val hash=o.getString("source_hash")
            require(hash.matches(Regex("[a-f0-9]{64}")))
            val asOf=o.getString("retrieved_on")
            require(asOf.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            val a=o.getJSONArray("entries")
            require(a.length()==150 && o.getInt("count")==150)
            val ids=HashSet<String>()
            val entries=(0 until a.length()).map { i ->
                val row=a.getJSONObject(i)
                val id=row.getString("id")
                require(id.matches(Regex("[0-9]{13}")) && ids.add(id))
                val group=row.getString("group"); require(group in groups)
                val description=row.getString("description")
                require(description.isNotBlank() && description.length<=1000)
                val kind=row.getString("kind")
                require(kind=="شناسه عمومی خدمت")
                require(row.getString("official_verification")=="pending")
                require(row.getString("expiration_date_reported").isEmpty())
                require(row.getString("source")=="Mohsensoft")
                require(row.getString("source_url").startsWith("https://mohsensoft.com/mtax/Stuffid/"))
                require(row.getString("vat_reported") in setOf("0","10"))
                // Never auto-populate a tax rate from an unverified source.
                Entry(group,SuraIdentifier(id,description,"","نیازمند استعلام رسمی",kind,"","Mohsensoft (pending)"))
            }
            val expected=mapOf("accounting" to 22,"computer" to 9,"auto" to 4,"technical" to 36,"other" to 79)
            require(expected.all{(g,n)->entries.count{it.group==g}==n})
            return Snapshot(0L,hash,asOf,entries)
        }
        fun parse(text:String):Snapshot {
            require(text.toByteArray(Charsets.UTF_8).size<=LIMIT)
            val o=JSONObject(text)
            val version=o.getLong("version");require(version in 1..1_000_000_000L)
            val hash=o.getString("source_hash");require(hash.matches(Regex("[a-f0-9]{64}")))
            val asOf=o.getString("as_of");require(asOf.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            val a=o.getJSONArray("entries");require(a.length() in 150..300)
            val ids=HashSet<String>()
            val entries=(0 until a.length()).map { i ->
                val row=a.getJSONObject(i)
                val id=row.getString("id");require(id.matches(Regex("[0-9]{13}"))&&ids.add(id))
                val group=row.getString("group");require(group in groups)
                val desc=row.getString("description");require(desc.isNotBlank()&&desc.length<=1000)
                val kind=row.getString("kind");require(kind.contains("عموم")&&kind.length<=100)
                require(kind.contains("خدمت")==(group in setOf("accounting","technical","other")))
                val vat=row.getString("vat");require(vat.length<=20 && (vat.isEmpty() || vat.toBigDecimalOrNull()?.let {it>=java.math.BigDecimal.ZERO&&it<=java.math.BigDecimal(100)}==true))
                val taxable=row.getString("taxable");require(taxable.length<=100)
                val expiry=row.getString("expiration_date");require(expiry.length<=50)
                val source=row.getString("source");require(source.isNotBlank()&&source.length<=100)
                Entry(group,SuraIdentifier(id,desc,vat,taxable,kind,expiry,source))
            }
            require(groups.all{(g,n)->entries.count{it.group==g}>=n})
            return Snapshot(version,hash,asOf,entries)
        }
    }
    fun snapshot():Snapshot?=synchronized(lock) {
        val bundled=runCatching { context.assets.open("starter/catalog.json").bufferedReader().use{parseBundled(it.readText())} }.getOrNull()
        val downloaded=runCatching {cache.openRead().use{parse(String(it.readBytes(),Charsets.UTF_8))}}.getOrNull()
        listOfNotNull(bundled,downloaded).maxByOrNull{it.version}
    }
    fun search(q:String,category:String?,specificity:String?,limit:Int):List<SuraIdentifier> {
        val normalized=SuraIdentifierStore.normalize(q)
        return snapshot()?.entries.orEmpty().map{it.value}.filter {
            (category!="کالا"||!it.kind.contains("خدمت")) &&
            (category!="خدمت"||it.kind.contains("خدمت")) &&
            specificity!="اختصاصی" &&
            (normalized.isBlank()||it.id.startsWith(normalized)||normalized.split(" ").all{term->SuraIdentifierStore.normalize(it.description).contains(term)})
        }.take(limit.coerceIn(1,100))
    }
    suspend fun update():Long?=withContext(Dispatchers.IO) {
        val c=URL(URL_PATH).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects=false;c.useCaches=false;c.connectTimeout=12000;c.readTimeout=15000
            c.setRequestProperty("apikey",PUBLIC_KEY);c.setRequestProperty("Accept","application/json")
            require(c.responseCode==200){"ارتباط با بانک عمومی تأیید نشد."}
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use{input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=LIMIT);out.write(b,0,n)}}
            val a=JSONArray(out.toString("UTF-8"))
            if(a.length()==0)return@withContext snapshot()?.version?.takeIf{it>0}
            require(a.length()==1)
            val json=a.getJSONObject(0).toString()
            val candidate=parse(json)
            synchronized(lock) {
                if(candidate.version>(snapshot()?.version?:0L)) {
                    val stream=cache.startWrite()
                    try {stream.write(json.toByteArray(Charsets.UTF_8));cache.finishWrite(stream)}
                    catch(e:Exception){cache.failWrite(stream);throw e}
                }
                snapshot()?.version
            }
        } finally {c.disconnect()}
    }
}
