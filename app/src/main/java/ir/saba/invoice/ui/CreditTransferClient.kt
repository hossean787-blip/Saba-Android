package ir.saba.invoice.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object CreditTransferClient {
 suspend fun consume(token:String, taxpayerId:Long, invoiceKey:String):JSONObject =
  withContext(Dispatchers.IO) {
   require(token.isNotBlank() && taxpayerId>0L && invoiceKey.matches(Regex("[0-9a-fA-F-]{36}")))
   val connection = URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-credit-transfer").openConnection() as HttpURLConnection
   try {
    connection.requestMethod="POST"
    connection.doOutput=true
    connection.connectTimeout=15000
    connection.readTimeout=15000
    connection.setRequestProperty("Authorization","Bearer "+token)
    connection.setRequestProperty("Content-Type","application/json")
    val data=JSONObject().put("operation","consume").put("taxpayerId",taxpayerId).put("invoiceKey",invoiceKey)
    connection.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
    val status=connection.responseCode
    val stream=if(status in 200..299) connection.inputStream else connection.errorStream
    val response=JSONObject(stream.bufferedReader().use { it.readText() })
    if(status !in 200..299 || response.has("error")) error(response.optString("error","service_unavailable"))
    require(response.optBoolean("consumed") || response.optBoolean("already_consumed")) { "مصرف اعتبار تأیید نشد." }
    response
   } finally { connection.disconnect() }
  }
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
