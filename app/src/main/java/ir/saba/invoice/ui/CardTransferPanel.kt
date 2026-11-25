package ir.saba.invoice.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

private object CardTransferApi {
    suspend fun call(token:String,route:String,body:JSONObject=JSONObject()):JSONObject=withContext(Dispatchers.IO){
        require(route in setOf("quote","reserve","mine","submit") && token.isNotBlank())
        val c=URL("https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-card-transfer/$route").openConnection() as HttpURLConnection
        try {
            c.requestMethod="POST";c.instanceFollowRedirects=false;c.useCaches=false;c.doOutput=true
            c.connectTimeout=15000;c.readTimeout=25000
            c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json")
            val bytes=body.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size<=680000)
            c.setFixedLengthStreamingMode(bytes.size);c.outputStream.use{it.write(bytes)}
            val code=c.responseCode
            val stream=if(code in 200..299)c.inputStream else c.errorStream
            val raw=stream?.use{input->val out=ByteArrayOutputStream();val buf=ByteArray(4096);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=131072);out.write(buf,0,n)};out.toString("UTF-8")}.orEmpty()
            val obj=try{JSONObject(raw)}catch(e:org.json.JSONException){throw SuraServiceException(code,"invalid_response")}
            if(code !in 200..299 || obj.has("error"))throw SuraServiceException(code,obj.optString("error"))
            obj
        } finally {c.disconnect()}
    }
}
private suspend fun cardReceipt(context:android.content.Context,uri:Uri):String=withContext(Dispatchers.IO){
    val resolver=context.contentResolver
    val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,opts)}
    require(opts.outWidth in 1..24000 && opts.outHeight in 1..24000)
    var sample=1
    while(maxOf(opts.outWidth,opts.outHeight)/sample>1600)sample*=2
    val bitmap=resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("تصویر قابل خواندن نیست.")
    try{
        val out=ByteArrayOutputStream()
        for(q in listOf(85,70,55,40)){
            out.reset();require(bitmap.compress(Bitmap.CompressFormat.JPEG,q,out))
            if(out.size()<=500000)return@withContext Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
        }
        error("حجم تصویر زیاد است.")
    }finally{bitmap.recycle()}
}
@Composable
internal fun CardTransferPanel(token:String){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var kind by remember{mutableStateOf("single")}
    var quantity by remember{mutableStateOf("1")}
    var quote by remember{mutableStateOf<JSONObject?>(null)}
    var active by remember{mutableStateOf<JSONObject?>(null)}
    var history by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var busy by remember{mutableStateOf(false)}
    var loaded by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var tracking by remember{mutableStateOf("")}
    var date by remember{mutableStateOf("")}
    var receipt by remember{mutableStateOf<String?>(null)}
    suspend fun refresh(){
        val a=CardTransferApi.call(token,"mine").getJSONArray("requests")
        history=(0 until a.length()).map{a.getJSONObject(it)}
        active=history.firstOrNull{it.optString("state") in setOf("draft","pending")}
        loaded=true
    }
    fun run(action:suspend ()->Unit){if(busy)return;busy=true;message="";scope.launch{try{action()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message=SuraErrors.describe(e,"عملیات کارت‌به‌کارت تأیید نشد؛ وضعیت درخواست را بررسی کنید. [CARD-UNKNOWN]")}finally{busy=false}}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)run{receipt=cardReceipt(context,uri);message="تصویر رسید آماده ارسال است."}}
    LaunchedEffect(token){run{refresh()}}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("پرداخت کارت‌به‌کارت",style=MaterialTheme.typography.titleLarge)
        Text("اعتبار فقط پس از تطبیق واریز با گردش حساب بانکی توسط مدیرکل شارژ می‌شود.")
        OutlinedButton(onClick={run{refresh()}},enabled=!busy){Text("به‌روزرسانی وضعیت")}
        val order=active
        if(loaded&&order==null){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=kind=="single",onClick={kind="single";quote=null},label={Text("اعتبار تکی")},enabled=!busy)
                FilterChip(selected=kind=="monthly",onClick={kind="monthly";quote=null},label={Text("اشتراک ۳۰ روزه")},enabled=!busy)
            }
            if(kind=="single")OutlinedTextField(quantity,{quantity=it.filter{c->c in '0'..'9'}.take(6);quote=null},label={Text("تعداد اعتبار")},singleLine=true,enabled=!busy)
            else Text("۲۰ صورتحساب در ۳۰ روز؛ تمدید دستی")
            val units=if(kind=="monthly")20L else quantity.toLongOrNull()?.takeIf{it in 1L..999999L}
            OutlinedButton(enabled=!busy&&units!=null,onClick={val n=units?:return@OutlinedButton;run{quote=CardTransferApi.call(token,"quote",JSONObject().put("kind",kind).put("units",n))}}){Text("استعلام مبلغ")}
            quote?.let{q->
                Text("مبلغ نهایی: "+q.optLong("total_rial")+" ریال")
                if(!q.optBoolean("enabled"))Text("فروش کارت‌به‌کارت هنوز فعال نیست.")
                Button(enabled=!busy&&q.optBoolean("enabled")&&units!=null,onClick={val n=units?:return@Button;run{CardTransferApi.call(token,"reserve",JSONObject().put("kind",kind).put("units",n).put("total",q.getLong("total_rial")));refresh()}}){Text("دریافت شماره کارت و ثبت سفارش")}
            }
        }
        if(order!=null){
            Text("سفارش: "+order.getString("id"))
            Text("مبلغ: "+order.getLong("amount_rial")+" ریال")
            if(order.optString("state")=="draft"){
                Text("شماره کارت مقصد:")
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Text(order.getString("destination_card").chunked(4).joinToString("-"),
                        style=MaterialTheme.typography.titleMedium.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr))
                }
                Text("صاحب کارت: "+order.getString("holder"))
                Text("فقط مبلغ دقیق همین سفارش را واریز کنید. اطلاعات محرمانه کارت را در رسید ارسال نکنید.")
                OutlinedTextField(tracking,{tracking=it.filter{c->c in '0'..'9'}.take(32)},label={Text("شماره پیگیری")},singleLine=true,enabled=!busy)
                OutlinedTextField(date,{date=it.filter{c->c in '0'..'9'||c=='/'}.take(10)},label={Text("تاریخ واریز ۱۴۰۵/۰۹/۰۴ (اعداد انگلیسی)")},singleLine=true,enabled=!busy)
                OutlinedButton(onClick={picker.launch("image/*")},enabled=!busy){Text(if(receipt==null)"انتخاب تصویر رسید" else "تغییر تصویر رسید")}
                Button(enabled=!busy&&receipt!=null&&tracking.length>=4&&date.matches(Regex("[0-9]{4}/[0-9]{2}/[0-9]{2}")),onClick={val image=receipt?:return@Button;run{CardTransferApi.call(token,"submit",JSONObject().put("id",order.getString("id")).put("tracking",tracking).put("date",date).put("receipt",image));receipt=null;refresh();message="رسید برای بررسی ثبت شد."}}){Text("ارسال رسید")}
            }else Text("در انتظار بررسی مدیرکل؛ دوباره واریز نکنید.")
        }
        history.filter{it.optString("state") in setOf("approved","rejected")}.take(5).forEach{r->Text((if(r.optString("state")=="approved")"تأیید شده" else "رد شده")+" | "+r.optLong("amount_rial")+" ریال | "+r.optString("review_reason"))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message)
    }}
}
