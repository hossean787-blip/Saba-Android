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

internal object CardTransferApi {
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
internal suspend fun cardPurchaseQuote(token:String,kind:String,units:Long):PurchaseQuote {
    val q=CardTransferApi.call(token,"quote",JSONObject().put("kind",kind).put("units",units))
    require(q.optString("kind")==kind && q.optLong("quantity",-1)==units &&
        q.optString("currency")=="IRR" && q.optLong("vat_rate_percent")==10L && q.opt("enabled") is Boolean)
    val quote=PurchaseQuote.validate(kind,units,q.getLong("subtotal_rial"),q.getLong("vat_rial"),q.getLong("total_rial"),q.getBoolean("enabled"))
    require(kind!="single" || q.optLong("unit_price_rial")==quote.subtotal/units)
    return quote
}

internal suspend fun cardPurchaseHistory(token:String):List<JSONObject>{
    val rows=CardTransferApi.call(token,"mine").getJSONArray("requests")
    return (0 until rows.length()).map{rows.getJSONObject(it)}
}

/** Receipt step only: plan and quantity are selected once, on the purchase screen. */
@Composable
internal fun CardTransferPanel(token:String,order:JSONObject,onRefresh:suspend ()->Unit,onBack:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val orderId=order.getString("id")
    var busy by remember(orderId){mutableStateOf(false)}
    var message by remember(orderId){mutableStateOf("")}
    var tracking by remember(orderId){mutableStateOf("")}
    var date by remember(orderId){mutableStateOf("")}
    var receipt by remember(orderId){mutableStateOf<String?>(null)}
    fun run(action:suspend ()->Unit){if(busy)return;busy=true;message="";scope.launch{try{action()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message=SuraErrors.describe(e,"عملیات تأیید نشد؛ وضعیت همین سفارش را استعلام کنید. [CARD-UNKNOWN]")}finally{busy=false}}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)run{receipt=cardReceipt(context,uri);message="تصویر رسید آماده ارسال است."}}
    Card(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color.White),elevation=CardDefaults.cardElevation(3.dp)){
      Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("پرداخت کارت‌به‌کارت",style=MaterialTheme.typography.titleLarge)
        Text(if(order.optString("kind")=="monthly")"اشتراک ماهانه" else "${order.optLong("units")} اعتبار تکی")
        Text("مبلغ سفارش: "+java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(order.getLong("amount_rial"))+" ریال",fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
        when(order.optString("state")){
          "draft"->{
            Text("شماره کارت مقصد")
            val card=order.getString("destination_card")
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr){
                Text(card.chunked(4).joinToString("-"),style=MaterialTheme.typography.titleLarge.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr))
            }
            val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
            OutlinedButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(card));message="شماره کارت کپی شد."},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("کپی شماره کارت")}
            Text("صاحب کارت: "+order.getString("holder"))
            Text("فقط مبلغ دقیق همین سفارش را واریز کنید. اعتبار پس از تأیید واریز واقعی توسط مدیرکل شارژ می‌شود.")
            OutlinedTextField(tracking,{tracking=ir.saba.invoice.data.identifiers.SuraIdentifierStore.normalize(it).filter{c->c in '0'..'9'}.take(32)},label={Text("شماره پیگیری")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(date,{date=ir.saba.invoice.data.identifiers.SuraIdentifierStore.normalize(it).filter{c->c in '0'..'9'||c=='/'}.take(10)},label={Text("تاریخ واریز (سال/ماه/روز)")},placeholder={Text("۱۴۰۵/۰۹/۰۴")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={picker.launch("image/*")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(receipt==null)"انتخاب تصویر رسید" else "تغییر تصویر رسید")}
            Button(enabled=!busy&&receipt!=null&&tracking.length>=4&&date.matches(Regex("[0-9]{4}/[0-9]{2}/[0-9]{2}")),modifier=Modifier.fillMaxWidth(),onClick={val image=receipt?:return@Button;run{
                CardTransferApi.call(token,"submit",JSONObject().put("id",orderId).put("tracking",tracking).put("date",date).put("receipt",image))
                receipt=null;onRefresh();message="رسید برای بررسی ثبت شد."
            }}){Text("ارسال رسید")}
          }
          "pending"->Text("رسید در انتظار بررسی مدیرکل است؛ دوباره واریز نکنید.")
          "approved"->Text("پرداخت تأیید شده است.")
          "rejected"->Text("درخواست رد شده است: "+order.optString("review_reason"))
          else->Text("وضعیت سفارش قابل تأیید نیست؛ دوباره استعلام کنید.")
        }
        OutlinedButton(onClick={run{onRefresh()}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("به‌روزرسانی وضعیت")}
        TextButton(onClick=onBack,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("بازگشت به خرید اعتبار")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message)
      }
    }
}
