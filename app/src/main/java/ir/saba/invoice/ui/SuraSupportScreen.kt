package ir.saba.invoice.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
internal data class SupportMessage(val id:String,val ticketId:String,val sender:String,val body:String,val createdAt:String)
internal class SupportHttpError(val status:Int):java.io.IOException("Support HTTP $status")

internal class SupportTicketClient(
    private val account:AccountScope,private val token:String,
    private val connection:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}
) {
    companion object {
        val categories=listOf("ارسال صورتحساب","ورود و حساب کاربری","اعتبار و پرداخت","مشکل فنی","حریم خصوصی و اطلاعات")
        private const val BASE="https://fiiitzfbdjzpmepzjsjw.supabase.co/rest/v1/"
        private const val PUBLIC_KEY="sb_publishable_loYRUibarkSaekgzXQoogQ_Ic_MlDW1"
        private const val TICKETS="sura_support_tickets"
        private const val MESSAGES="sura_support_messages"
        private const val FIELDS="id,category,message,status,response,created_at"
        private const val THREAD="id,ticket_id,sender,body,created_at"
        const val PAGE_SIZE=20
        fun validMessage(value:String,min:Int=1)=value.trim().length in min..2000 &&
            !Regex("-----BEGIN (?:RSA |EC |ENCRYPTED )?PRIVATE KEY-----",RegexOption.IGNORE_CASE).containsMatchIn(value)
        fun uuid(value:String)=UUID.fromString(value).toString().also { require(it==value) }
    }
    private suspend fun request(table:String,method:String,query:String,body:JSONObject?=null):JSONArray=withContext(Dispatchers.IO) {
        require(table in setOf(TICKETS,MESSAGES))
        require(token.length in 1..32768 && token.none { it.isWhitespace() || it.isISOControl() })
        AccountSession.requireOwner(token,account)
        val c=connection(URL(BASE+table+query))
        try {
            c.instanceFollowRedirects=false;c.useCaches=false
            c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=15000
            c.setRequestProperty("apikey",PUBLIC_KEY);c.setRequestProperty("Authorization","Bearer $token")
            c.setRequestProperty("Accept","application/json")
            if(body!=null) {
                val bytes=body.toString().toByteArray(Charsets.UTF_8)
                c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8")
                c.setRequestProperty("Prefer","return=representation");c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use {it.write(bytes)}
            }
            val status=c.responseCode
            if(status !in 200..299) throw SupportHttpError(status)
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use {stream ->
                val buf=ByteArray(4096)
                while(true){val n=stream.read(buf);if(n<0)break;require(out.size()+n<=1024*1024);out.write(buf,0,n)}
            }
            JSONArray(out.toString("UTF-8"))
        } finally {c.disconnect()}
    }
    private fun parse(row:JSONObject):SupportTicket {
        val status=row.getString("status")
        require(status in setOf("open","in_progress","answered","closed"))
        return SupportTicket(uuid(row.getString("id")),row.getString("category"),row.getString("message"),status,
            if(row.isNull("response")) "" else row.getString("response"),row.getString("created_at"))
    }
    private fun parseMessage(row:JSONObject):SupportMessage {
        val sender=row.getString("sender");require(sender in setOf("customer","support"))
        return SupportMessage(uuid(row.getString("id")),uuid(row.getString("ticket_id")),sender,row.getString("body"),row.getString("created_at"))
    }
    suspend fun list(page:Int=0):List<SupportTicket> {
        require(page in 0..10000)
        val rows=request(TICKETS,"GET","?select=$FIELDS&order=created_at.desc,id.desc&limit=$PAGE_SIZE&offset=${page*PAGE_SIZE}")
        return (0 until rows.length()).map {parse(rows.getJSONObject(it))}
    }
    suspend fun find(id:String):SupportTicket? {
        val rows=request(TICKETS,"GET","?select=$FIELDS&id=eq.${uuid(id)}&limit=1")
        return if(rows.length()==1)parse(rows.getJSONObject(0)).also {require(it.id==id)} else null
    }
    suspend fun create(id:String,category:String,message:String,version:String):SupportTicket {
        uuid(id);require(category in categories && validMessage(message,10)&&version.length<=40)
        fun verify(t:SupportTicket)=t.also {require(it.id==id&&it.category==category&&it.message==message.trim())}
        find(id)?.let{return verify(it)}
        val body=JSONObject().put("id",id).put("category",category).put("message",message.trim()).put("app_version",version)
        return try {
            val rows=request(TICKETS,"POST","?select=$FIELDS",body);require(rows.length()==1);verify(parse(rows.getJSONObject(0)))
        } catch(e:CancellationException){throw e}
        catch(e:Exception){find(id)?.let(::verify)?:throw e}
    }
    suspend fun messages(ticketId:String,page:Int=0):List<SupportMessage> {
        uuid(ticketId);require(page in 0..10000)
        val rows=request(MESSAGES,"GET","?select=$THREAD&ticket_id=eq.$ticketId&order=created_at.desc,id.desc&limit=$PAGE_SIZE&offset=${page*PAGE_SIZE}")
        return (0 until rows.length()).map {parseMessage(rows.getJSONObject(it)).also {m->require(m.ticketId==ticketId)}}
    }
    private suspend fun findMessage(id:String):SupportMessage? {
        val rows=request(MESSAGES,"GET","?select=$THREAD&id=eq.${uuid(id)}&limit=1")
        return if(rows.length()==1)parseMessage(rows.getJSONObject(0)) else null
    }
    suspend fun reply(id:String,ticketId:String,message:String):SupportMessage {
        uuid(id);uuid(ticketId);require(validMessage(message))
        fun verify(m:SupportMessage)=m.also {require(it.id==id&&it.ticketId==ticketId&&it.sender=="customer"&&it.body==message.trim())}
        findMessage(id)?.let{return verify(it)}
        val ticket=requireNotNull(find(ticketId)){"درخواست در دسترس نیست."}
        require(ticket.status!="closed"){"این درخواست بسته شده است."}
        val body=JSONObject().put("id",id).put("ticket_id",ticketId).put("body",message.trim())
        return try {
            val rows=request(MESSAGES,"POST","?select=$THREAD",body);require(rows.length()==1);verify(parseMessage(rows.getJSONObject(0)))
        }catch(e:CancellationException){throw e}
        catch(e:Exception){findMessage(id)?.let(::verify)?:throw e}
    }
}

/** Persist only operation IDs and hashes; retry an ambiguous send with the same key even after restart. */
internal class SupportOperationJournal(context:Context,account:AccountScope) {
    private val prefs=context.getSharedPreferences("sura_support_operations_${account.storageKey}",Context.MODE_PRIVATE)
    fun id(scope:String,body:String):String = synchronized(SupportOperationJournal::class.java) {
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest((scope+"\u0000"+body.trim()).toByteArray())
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        val name="pending_$hash"
        prefs.getString(name,null)?:UUID.randomUUID().toString().also {require(prefs.edit().putString(name,it).commit())}
    }
    fun complete(id:String) = synchronized(SupportOperationJournal::class.java) {
        val edit=prefs.edit()
        prefs.all.filterValues {it==id}.keys.forEach {edit.remove(it)}
        require(edit.commit())
    }
}
private val navy=Color(0xFF12354C)
private val teal=Color(0xFF087A83)
private fun supportStatus(status:String)=when(status){"open"->"ثبت‌شده";"in_progress"->"در حال بررسی";"answered"->"پاسخ داده‌شده";else->"بسته"}
private fun supportError(e:Exception)=if(e is SupportHttpError) SuraErrors.message(e.status)+" شماره درخواست قبلی حفظ می‌شود." else SuraErrors.describe(e,"ارتباط تأیید نشد. همان متن را دوباره ارسال کنید؛ شماره درخواست قبلی حفظ می‌شود. [SUPPORT-UNKNOWN]")
@Composable private fun SupportHeader(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(navy,teal)),RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(title,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text(subtitle,color=Color(0xFFD9F3F2),style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun TicketStatus(status:String) {
    val color=when(status){"open"->Color(0xFF245DA6);"in_progress"->Color(0xFF9A6312);"answered"->Color(0xFF147A59);else->Color(0xFF64748B)}
    Surface(color=color.copy(alpha=.1f),shape=RoundedCornerShape(12.dp)){
        Text(supportStatus(status),color=color,modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun SuraSupportScreen(account:AccountScope,token:String,onBusy:(Boolean)->Unit,onBack:()->Unit) {
    val ctx=LocalContext.current
    val version=remember{runCatching{ctx.packageManager.getPackageInfo(ctx.packageName,0).versionName}.getOrNull().orEmpty().take(40)}
    val client=remember(account,token){SupportTicketClient(account,token)}
    val journal=remember(account){SupportOperationJournal(ctx.applicationContext,account)}
    val scope=rememberCoroutineScope()
    var tickets by remember(account){mutableStateOf<List<SupportTicket>>(emptyList())}
    var selected by remember(account){mutableStateOf<SupportTicket?>(null)}
    var thread by remember(account){mutableStateOf<List<SupportMessage>>(emptyList())}
    var category by remember(account){mutableStateOf(SupportTicketClient.categories.first())}
    var message by remember(account){mutableStateOf("")}
    var reply by remember(account){mutableStateOf("")}
    var tab by remember(account){mutableStateOf(0)}
    var page by remember(account){mutableIntStateOf(0)}
    var threadPage by remember(account){mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf("")}
    var loaded by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    fun perform(block:suspend()->Unit) {
        if(busy)return
        busy=true;onBusy(true);notice=""
        scope.launch {
            try{block()}catch(e:CancellationException){throw e}
            catch(e:Exception){notice=supportError(e)}
            finally{busy=false;onBusy(false)}
        }
    }
    suspend fun refreshTicket(id:String) {
        val ticket=requireNotNull(client.find(id))
        val messages=client.messages(id,0)
        selected=ticket;thread=messages;threadPage=0
    }
    LaunchedEffect(client){perform{tickets=client.list();loaded=true}}
    BackHandler(enabled=selected!=null) {
        if(!busy){selected=null;thread=emptyList();reply="";tab=1}
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F8FC)).verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag("service-support"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SupportHeader("مرکز پشتیبانی سورا","ثبت درخواست، پیگیری و گفتگو با پشتیبانی در حساب شما")
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(notice.isNotBlank())Text(notice,color=navy,style=MaterialTheme.typography.bodyMedium)
        val ticket=selected
        if(ticket!=null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                TextButton(enabled=!busy,onClick={selected=null;thread=emptyList();reply="";tab=1}){Text("بازگشت به درخواست‌ها")}
                TicketStatus(ticket.status)
            }
            Text(ticket.category,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
            Text(ticket.createdAt,style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={
                val clipboard=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("شماره پیگیری",ticket.id));notice="شماره پیگیری کپی شد."
            },modifier=Modifier.fillMaxWidth()){Text("کپی شماره پیگیری")}
            SupportBubble("درخواست شما",ticket.message,true)
            if(ticket.response.isNotBlank())SupportBubble("پاسخ اولیه پشتیبانی",ticket.response,false)
            thread.asReversed().forEach {m->SupportBubble(if(m.sender=="customer")"شما" else "پشتیبانی",m.body,m.sender=="customer",m.createdAt)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                TextButton(enabled=!busy&&threadPage>0,onClick={perform{val next=threadPage-1;thread=client.messages(ticket.id,next);threadPage=next}}){Text("جدیدتر")}
                TextButton(enabled=!busy&&thread.size==SupportTicketClient.PAGE_SIZE,onClick={perform{val next=threadPage+1;val rows=client.messages(ticket.id,next);if(rows.isNotEmpty()){thread=rows;threadPage=next}else notice="پیام قدیمی‌تری وجود ندارد."}}){Text("قدیمی‌تر")}
                TextButton(enabled=!busy,onClick={perform{refreshTicket(ticket.id)}}){Text("تازه‌سازی")}
            }
            if(ticket.status!="closed") {
                OutlinedTextField(value=reply,onValueChange={reply=it.take(2000)},label={Text("پیام جدید")},enabled=!busy,
                    minLines=3,modifier=Modifier.fillMaxWidth().testTag("supportReplyText"),supportingText={Text("${reply.length}/۲۰۰۰")})
                Button(enabled=!busy&&SupportTicketClient.validMessage(reply),onClick={
                    perform {
                        val id=journal.id("reply:"+ticket.id,reply)
                        val sent=client.reply(id,ticket.id,reply)
                        journal.complete(id);reply=""
                        thread=if(threadPage==0) (listOf(sent)+thread.filter{it.id!=sent.id}).take(SupportTicketClient.PAGE_SIZE) else listOf(sent)
                        threadPage=0
                        notice="پیام ثبت شد."
                        try { refreshTicket(ticket.id) }
                        catch(e:CancellationException){throw e}
                        catch(_:Exception){notice="پیام ثبت شد؛ تازه‌سازی گفتگو انجام نشد."}
                    }
                },modifier=Modifier.fillMaxWidth().testTag("supportReply")){Text("ارسال پیام")}
            }else Text("درخواست بسته است؛ برای موضوع جدید تیکت تازه ثبت کنید.")
        }else {
            TabRow(selectedTabIndex=tab){
                Tab(selected=tab==0,onClick={if(!busy)tab=0},text={Text("درخواست جدید")})
                Tab(selected=tab==1,onClick={if(!busy)tab=1},text={Text("درخواست‌های من")})
            }
            if(tab==0){
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("موضوع درخواست",fontWeight=FontWeight.Bold,color=navy)
                        SupportTicketClient.categories.forEach{item->
                            FilterChip(selected=category==item,onClick={category=item},enabled=!busy,label={Text(item)})
                        }
                        OutlinedTextField(value=message,onValueChange={message=it.take(2000)},enabled=!busy,
                            label={Text("شرح مشکل")},minLines=4,modifier=Modifier.fillMaxWidth().testTag("supportMessage"),
                            supportingText={Text("${message.length}/۲۰۰۰؛ حداقل ۱۰ نویسه")})
                        Text("رمز، کلید خصوصی، اطلاعات کارت و اطلاعات محرمانه مشتری را وارد نکنید.",color=Color(0xFF9A6312),style=MaterialTheme.typography.bodySmall)
                        Button(enabled=!busy&&SupportTicketClient.validMessage(message,10),onClick={confirm=true},
                            modifier=Modifier.fillMaxWidth().testTag("supportSubmit")){Text("ثبت درخواست")}
                    }
                }
                OutlinedButton(onClick={tab=1},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("پیگیری درخواست‌های قبلی")}
            }else{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text("درخواست‌های ثبت‌شده",fontWeight=FontWeight.Bold,color=navy)
                    TextButton(enabled=!busy,onClick={perform{tickets=client.list();page=0;loaded=true}}){Text("تازه‌سازی")}
                }
                if(loaded&&tickets.isEmpty())Text("درخواستی ثبت نشده است.")
                if(!loaded&&!busy)Text("فهرست هنوز از سرور دریافت نشده است.")
                tickets.forEach{t->
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
                        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(t.category,fontWeight=FontWeight.Bold);TicketStatus(t.status)}
                            Text(t.message,maxLines=3)
                            Text(t.createdAt,style=MaterialTheme.typography.bodySmall)
                            TextButton(enabled=!busy,onClick={perform{refreshTicket(t.id);reply=""}}){Text("مشاهده و ادامه گفتگو")}
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    TextButton(enabled=!busy&&page>0,onClick={perform{val n=page-1;tickets=client.list(n);page=n}}){Text("قبلی")}
                    TextButton(enabled=!busy&&tickets.size==SupportTicketClient.PAGE_SIZE,onClick={perform{val n=page+1;val rows=client.list(n);if(rows.isNotEmpty()){tickets=rows;page=n}else notice="درخواست دیگری وجود ندارد."}}){Text("بعدی")}
                }
            }
            OutlinedButton(onClick=onBack,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("بازگشت به خانه")}
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("تأیید ثبت درخواست")},
        text={Text("متن درخواست در سرور پشتیبانی ذخیره می‌شود. از نبود اطلاعات محرمانه اطمینان حاصل کنید.")},
        confirmButton={TextButton(onClick={confirm=false;perform {
            val id=journal.id("new:"+category,message)
            val t=client.create(id,category,message,version)
            journal.complete(id);message="";selected=t;thread=emptyList();threadPage=0;tab=1
            tickets=listOf(t)+tickets.filter{it.id!=t.id};notice="درخواست با موفقیت ثبت شد."
        }}){Text("تأیید و ارسال")}},dismissButton={TextButton(onClick={confirm=false}){Text("انصراف")}})
}
@Composable private fun SupportBubble(label:String,body:String,customer:Boolean,date:String="") {
    Column(Modifier.fillMaxWidth().background(if(customer)Color(0xFFE0F2F1) else Color.White,RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(label,fontWeight=FontWeight.Bold,color=if(customer)teal else navy)
        Text(body,color=navy)
        if(date.isNotBlank())Text(date,style=MaterialTheme.typography.labelSmall,color=Color(0xFF64748B))
    }
}
