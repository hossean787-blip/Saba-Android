package ir.saba.invoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.saba.invoice.security.AccountScope
import ir.saba.invoice.security.AccountSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal data class SupportTicket(val id:String,val category:String,val message:String,val status:String,val response:String,val createdAt:String)

internal class SupportTicketClient(
    private val account:AccountScope,private val token:String,
    private val connection:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}
) {
    companion object {
        val categories=listOf("ارسال صورتحساب","ورود و حساب کاربری","اعتبار و پرداخت","مشکل فنی","حریم خصوصی و اطلاعات")
        private const val BASE="https://fiiitzfbdjzpmepzjsjw.supabase.co/rest/v1/sura_support_tickets"
        // Publishable (not service-role) key. Row-level access is bound to the JWT by Postgres.
        private const val PUBLIC_KEY="sb_publishable_loYRUibarkSaekgzXQoogQ_Ic_MlDW1"
        private const val FIELDS="id,category,message,status,response,created_at"
    }
    private suspend fun request(method:String,query:String,body:JSONObject?=null):JSONArray=withContext(Dispatchers.IO) {
        AccountSession.requireOwner(token,account)
        val c=connection(URL(BASE+query))
        try {
            c.instanceFollowRedirects=false;c.useCaches=false
            c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=15000
            c.setRequestProperty("apikey",PUBLIC_KEY)
            c.setRequestProperty("Authorization","Bearer $token")
            c.setRequestProperty("Accept","application/json")
            if(body!=null) {
                val bytes=body.toString().toByteArray(Charsets.UTF_8)
                c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8")
                c.setRequestProperty("Prefer","return=representation")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            if(c.responseCode !in 200..299) throw java.io.IOException("Support request not confirmed")
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use { stream ->
                val buf=ByteArray(4096)
                while(true) {
                    val n=stream.read(buf);if(n<0)break
                    require(out.size()+n<=1024*1024)
                    out.write(buf,0,n)
                }
            }
            JSONArray(out.toString("UTF-8"))
        } finally {c.disconnect()}
    }
    private fun parse(row:JSONObject):SupportTicket {
        val id=row.getString("id")
        require(UUID.fromString(id).toString()==id)
        val status=row.getString("status")
        require(status in setOf("open","in_progress","answered","closed"))
        return SupportTicket(id,row.getString("category"),row.getString("message"),status,
            row.optString("response","").let { if(it=="null") "" else it },row.getString("created_at"))
    }
    suspend fun list():List<SupportTicket> {
        val rows=request("GET","?select=$FIELDS&order=created_at.desc&limit=50")
        return (0 until rows.length()).map { parse(rows.getJSONObject(it)) }
    }
    private suspend fun find(id:String):SupportTicket? {
        require(UUID.fromString(id).toString()==id)
        val rows=request("GET","?select=$FIELDS&id=eq.$id&limit=1")
        return if(rows.length()==1) parse(rows.getJSONObject(0)) else null
    }
    suspend fun create(id:String,category:String,message:String,version:String):SupportTicket {
        require(UUID.fromString(id).toString()==id && category in categories &&
            message.trim().length in 10..2000 && version.length<=40)
        val existing=find(id)
        if(existing!=null) {
            require(existing.category==category && existing.message==message.trim())
            return existing
        }
        val body=JSONObject().put("id",id).put("category",category)
            .put("message",message.trim()).put("app_version",version)
        return try {
            val rows=request("POST","?select=$FIELDS",body)
            require(rows.length()==1)
            parse(rows.getJSONObject(0)).also { require(it.id==id) }
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {
            // A lost acknowledgement is read back using the same ID; never POST twice.
            find(id)?.also { require(it.category==category && it.message==message.trim()) } ?: throw e
        }
    }
}

@Composable
internal fun SuraSupportScreen(account:AccountScope,token:String,onBusy:(Boolean)->Unit,onBack:()->Unit) {
    val ctx=LocalContext.current
    val version=remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName,0).versionName }.getOrNull().orEmpty().take(40) }
    val client=remember(account,token){SupportTicketClient(account,token)}
    val scope=rememberCoroutineScope()
    var tickets by remember {mutableStateOf<List<SupportTicket>>(emptyList())}
    var category by rememberSaveable {mutableStateOf(SupportTicketClient.categories.first())}
    var message by remember {mutableStateOf("")}
    var operation by remember {mutableStateOf(UUID.randomUUID().toString())}
    var busy by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf("")}
    var submittedAttempt by remember {mutableStateOf(false)}
    fun perform(block:suspend()->Unit) {
        if(busy)return
        busy=true;onBusy(true);notice=""
        scope.launch {
            try {block()} catch(e:CancellationException){throw e}
            catch(_:Exception){notice="پاسخ معتبر دریافت نشد. اتصال را بررسی و فهرست درخواست‌ها را تازه کنید. حداکثر ده درخواست در ۲۴ ساعت مجاز است."}
            finally {busy=false;onBusy(false)}
        }
    }
    LaunchedEffect(client) {perform { tickets=client.list() }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("service-support"),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("مرکز پشتیبانی سورا",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("ثبت درخواست و پیگیری پاسخ در همین حساب. پیام فقط پس از تأیید شما به سرور سورا فرستاده می‌شود.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("درخواست جدید",fontWeight=FontWeight.Bold)
                Text("رمز ورود، کلید خصوصی مالیاتی، اطلاعات کارت یا داده محرمانه مشتری را وارد نکنید.")
                SupportTicketClient.categories.forEach { item ->
                    FilterChip(selected=category==item,onClick={category=item},enabled=!busy&&!submittedAttempt,label={Text(item)})
                }
                OutlinedTextField(value=message,onValueChange={message=it.take(2000)},enabled=!busy&&!submittedAttempt,
                    label={Text("شرح درخواست (۱۰ تا ۲۰۰۰ نویسه)")},minLines=4,modifier=Modifier.fillMaxWidth())
                Button(enabled=!busy&&message.trim().length>=10,onClick={
                    submittedAttempt=true
                    perform {
                        val ticket=client.create(operation,category,message,version)
                        tickets=listOf(ticket)+tickets.filter {it.id!=ticket.id}
                        notice="درخواست ثبت شد. شماره پیگیری: "+ticket.id
                        message="";operation=UUID.randomUUID().toString();submittedAttempt=false
                    }
                },modifier=Modifier.fillMaxWidth().testTag("supportSubmit")) {
                    Text(if(submittedAttempt)"بررسی و تکمیل همان درخواست" else "تأیید و ثبت درخواست")
                }
                if(submittedAttempt&&!busy) TextButton(onClick={
                    // Do not change UUID silently; abandoning an uncertain attempt is explicit.
                    message="";operation=UUID.randomUUID().toString();submittedAttempt=false
                    notice="درخواست قبلی ممکن است ثبت شده باشد؛ قبل از درخواست جدید سوابق را بررسی کنید."
                }) {Text("انصراف از تلاش قبلی")}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("درخواست‌های من",fontWeight=FontWeight.Bold)
            TextButton(enabled=!busy,onClick={perform{tickets=client.list()}}){Text("تازه‌سازی")}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(notice.isNotBlank()) Text(notice,style=MaterialTheme.typography.bodyMedium)
        if(!busy&&tickets.isEmpty()) Text("درخواست تأییدشده‌ای در این فهرست دریافت نشده است.")
        tickets.forEach { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(t.category,fontWeight=FontWeight.Bold)
                    Text("وضعیت: "+when(t.status){"open"->"ثبت‌شده";"in_progress"->"در حال بررسی";"answered"->"پاسخ داده‌شده";else->"بسته"})
                    Text("شماره پیگیری: "+t.id,style=MaterialTheme.typography.bodySmall)
                    Text(t.message)
                    if(t.response.isNotBlank()) {HorizontalDivider();Text("پاسخ پشتیبانی",fontWeight=FontWeight.Bold);Text(t.response)}
                }
            }
        }
        OutlinedButton(onClick=onBack,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("بازگشت به خانه")}
    }
}
