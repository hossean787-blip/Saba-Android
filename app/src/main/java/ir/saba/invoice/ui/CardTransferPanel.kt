package ir.saba.invoice.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.saveable.rememberSaveable
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
        require(route in setOf("quote","reserve","mine","submit","cancel") && token.isNotBlank())
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
    require(opts.outWidth in 1..24000 && opts.outHeight in 1..24000) { "invalid_image" }
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
    val q=CardTransferApi.call(token,"quote",JSONObject().put("kind",kind).put("units",units).put("checkoutVersion",2))
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

private val bankNavy=Color(0xFF092D48)
private val bankBlue=Color(0xFF096488)
private val bankGold=Color(0xFFF3D28A)
private val bankMuted=Color(0xFF587286)
private fun cardMoney(value:Long)=java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(value)
private fun receiptDigits(value:String)=value.map{c->when(c){in '۰'..'۹'->'0'+(c-'۰');in '٠'..'٩'->'0'+(c-'٠');else->c}}.filter{it in '0'..'9'}.joinToString("")
internal fun formatReceiptDate(value:String):String{
    val digits=receiptDigits(value).take(8)
    return listOf(digits.take(4),digits.drop(4).take(2),digits.drop(6).take(2)).filter{it.isNotEmpty()}.joinToString("/")
}
internal fun receiptIssue(tracking:String,date:String,hasImage:Boolean):String?{
    if(!hasImage)return "ابتدا تصویر رسید را انتخاب کنید."
    if(!tracking.matches(Regex("[0-9]{4,32}")))return "شماره پیگیری بانکی را کامل وارد کنید (۴ تا ۳۲ رقم)."
    if(!date.matches(Regex("[0-9]{4}/[0-9]{2}/[0-9]{2}")))return "تاریخ واریز را به شکل ۱۴۰۵/۰۹/۰۴ وارد کنید."
    val parts=date.split('/').map{it.toInt()}
    if(parts[0] !in 1300..1599 || parts[1] !in 1..12 || parts[2] !in 1..(if(parts[1]<=6)31 else 30))return "تاریخ شمسی واردشده معتبر نیست."
    return null
}

/** Receipt step only: the server's frozen order amount is authoritative. */
@Composable
internal fun CardTransferPanel(token:String,order:JSONObject,onRefresh:suspend ()->Unit,onBack:()->Unit,onCancel:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val clipboard=LocalClipboardManager.current
    val orderId=order.getString("id")
    val state=order.optString("state")
    var busy by remember(orderId){mutableStateOf(false)}
    var message by remember(orderId){mutableStateOf("")}
    var isError by remember(orderId){mutableStateOf(false)}
    var tracking by rememberSaveable(orderId){mutableStateOf("")}
    var date by rememberSaveable(orderId){mutableStateOf("")}
    var receipt by remember(orderId){mutableStateOf<String?>(null)}
    val preview=remember(receipt){receipt?.let{encoded->runCatching{
        val bytes=Base64.decode(encoded,Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()
    }.getOrNull()}}
    fun run(action:suspend ()->Unit){
        if(busy)return
        busy=true;message="";isError=false
        scope.launch{
            try{action()}catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){isError=true;message=SuraErrors.describe(e,"عملیات تأیید نشد؛ وضعیت همین سفارش را استعلام کنید. [CARD-UNKNOWN]")}
            finally{busy=false}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null)run{
            try{receipt=cardReceipt(context,uri);message="تصویر رسید آماده ارسال است."}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){isError=true;message=SuraErrors.describe(e,"تصویر قابل خواندن یا فشرده‌سازی نیست؛ عکس دیگری انتخاب کنید. [CARD-IMAGE]")}
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(2.dp)){
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("خلاصه سفارش",fontWeight=FontWeight.Bold,color=bankNavy,style=MaterialTheme.typography.titleMedium)
                Text(if(order.optString("kind")=="monthly")"اشتراک ۳۰ روزه • ۲۰ صورتحساب" else "${order.optLong("units")} اعتبار تکی",color=bankMuted)
                Text("مبلغ دقیق واریز",style=MaterialTheme.typography.labelMedium,color=bankMuted)
                Text(cardMoney(order.getLong("amount_rial"))+" ریال",color=bankNavy,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineSmall)
                TextButton(onClick={clipboard.setText(AnnotatedString(order.getLong("amount_rial").toString()));message="مبلغ کپی شد.";isError=false},enabled=!busy){Text("کپی مبلغ",color=bankBlue)}
            }
        }
        if(state=="draft"){
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(bankNavy,bankBlue)),RoundedCornerShape(24.dp)).padding(19.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Text("کارت مقصد",color=bankGold,fontWeight=FontWeight.Bold)
                    Text("SURA  |  سورا",color=Color.White,style=MaterialTheme.typography.labelMedium)
                }
                val card=order.getString("destination_card")
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr){
                    Text(card.chunked(4).joinToString("  "),color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge.copy(textDirection=TextDirection.Ltr))
                }
                Text("به نام "+order.getString("holder"),color=Color.White,style=MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick={clipboard.setText(AnnotatedString(card));message="شماره کارت کپی شد.";isError=false},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.filledTonalButtonColors(containerColor=bankGold,contentColor=bankNavy)){Text("کپی شماره کارت")}
            }
            Text("فقط مبلغ همین سفارش را واریز کنید. شارژ اعتبار پس از تطبیق واریز با حساب بانکی انجام می‌شود.",color=bankMuted,style=MaterialTheme.typography.bodySmall)
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(2.dp)){
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("ثبت رسید واریز",fontWeight=FontWeight.ExtraBold,color=bankNavy,style=MaterialTheme.typography.titleLarge)
                    Text("تصویر رسید، شماره پیگیری و تاریخ واریز را تکمیل کنید.",color=bankMuted,style=MaterialTheme.typography.bodySmall)
                    if(preview!=null){
                        Image(bitmap=preview,contentDescription="پیش‌نمایش رسید انتخاب‌شده",modifier=Modifier.fillMaxWidth().heightIn(max=220.dp),contentScale=ContentScale.Fit)
                        Text("✓ تصویر آماده ارسال است",color=Color(0xFF137D62),fontWeight=FontWeight.Medium)
                    }else{
                        Column(Modifier.fillMaxWidth().background(Color(0xFFEFF7FC),RoundedCornerShape(16.dp)).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            Text("تصویر رسید بانکی",color=bankNavy,fontWeight=FontWeight.Bold)
                            Text("تصویر واضح و خوانا",color=bankMuted,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick={picker.launch("image/*")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(receipt==null)"انتخاب تصویر رسید" else "تغییر تصویر رسید")}
                    if(receipt!=null)TextButton(onClick={receipt=null;message="";isError=false},enabled=!busy){Text("حذف تصویر انتخابی")}
                    OutlinedTextField(tracking,{tracking=receiptDigits(it).take(32);message="";isError=false},label={Text("شماره پیگیری بانکی *")},placeholder={Text("شماره مرجع رسید")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    OutlinedTextField(date,{date=formatReceiptDate(it);message="";isError=false},label={Text("تاریخ شمسی واریز *")},placeholder={Text("۱۴۰۵/۰۹/۰۴")},supportingText={Text("۸ رقم تاریخ؛ جداکننده خودکار است.")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    val issue=receiptIssue(tracking,date,receipt!=null)
                    if(issue!=null)Text(issue,style=MaterialTheme.typography.bodySmall,color=bankMuted)
                    Button(enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=bankBlue),onClick={
                        val problem=receiptIssue(tracking,date,receipt!=null)
                        if(problem!=null){isError=true;message=problem;return@Button}
                        val image=receipt?:return@Button
                        run{
                            val result=CardTransferApi.call(token,"submit",JSONObject().put("id",orderId).put("tracking",tracking).put("date",date).put("receipt",image))
                            require(result.optString("id")==orderId&&result.optString("state")=="pending")
                            onRefresh()
                            receipt=null;message="رسید برای بررسی ثبت شد."
                        }
                    }){Text(if(busy)"در حال انجام…" else "ارسال رسید برای بررسی")}
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(message.isNotBlank())Text(message,color=if(isError)MaterialTheme.colorScheme.error else Color(0xFF137D62),style=MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(onClick=onCancel,enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("انصراف از سفارش (هنوز واریز نکرده‌ام)")}
        }else{
            Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(20.dp)){
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(when(state){"pending"->"در انتظار تأیید مدیرکل";"approved"->"پرداخت تأیید شده";"rejected"->"رسید رد شده";"cancelled"->"سفارش لغو شده";else->"وضعیت نامشخص"},fontWeight=FontWeight.Bold,color=bankNavy)
                    if(state=="pending")Text("رسید ثبت شده است؛ دوباره واریز نکنید.")
                    if(state=="rejected")Text(order.optString("review_reason"))
                    if(message.isNotBlank())Text(message,color=if(isError)MaterialTheme.colorScheme.error else bankBlue)
                }
            }
        }
        OutlinedButton(onClick={run{onRefresh()}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("به‌روزرسانی وضعیت")}
        TextButton(onClick=onBack,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("بازگشت به خرید اعتبار")}
    }
}
