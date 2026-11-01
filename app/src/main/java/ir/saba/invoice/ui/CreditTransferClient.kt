package ir.saba.invoice.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object CreditTransferClient {
 suspend fun request(token:String, taxpayerId:Long, amount:Long?=null, requestId:String?=null):JSONObject =
  withContext(Dispatchers.IO) {
   val connection = URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-credit-transfer").openConnection() as HttpURLConnection
   try {
    connection.requestMethod="POST"
    connection.doOutput=true
    connection.connectTimeout=15000
    connection.readTimeout=15000
    connection.setRequestProperty("Authorization","Bearer "+token)
    connection.setRequestProperty("Content-Type","application/json")
    val data=JSONObject().put("taxpayerId",taxpayerId)
    if(amount!=null) data.put("operation","transfer").put("credits",amount).put("requestId",requestId)
    else data.put("operation","balance")
    connection.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
    val status=connection.responseCode
    val response=JSONObject((if(status in 200..299)connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() })
    if(status !in 200..299 || response.has("error")) error(response.optString("error","service_unavailable"))
    response
   } finally { connection.disconnect() }
  }
}
