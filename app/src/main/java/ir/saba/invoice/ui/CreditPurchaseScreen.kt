package ir.saba.invoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

private val deepBlue = Color(0xFF073B66)
private val sky = Color(0xFFDAEEFF)
private val violet = Color(0xFF8046CE)

@Composable
fun CreditPurchaseScreen(ownerId: String, accessToken: String) {
    val states = rememberSaveableStateHolder()
    states.SaveableStateProvider(ir.saba.invoice.security.AccountScope.of(ownerId).storageKey) {
        key(ownerId) { CreditPurchaseContent(ownerId,accessToken) }
    }
}

@Composable
private fun CreditPurchaseContent(ownerId: String, accessToken: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var promoCode by remember { mutableStateOf("") }
    var promoBusy by remember { mutableStateOf(false) }
    var promoMessage by remember { mutableStateOf("") }
    var promoGranted by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }

    var plan by rememberSaveable { mutableStateOf("single") }
    val selectedQuantity=if(plan=="monthly")20L else quantity.toLongOrNull()?.takeIf{it in 1L..999999L}
    var quote by remember { mutableStateOf<PurchaseQuote?>(null) }
    var quoteError by remember { mutableStateOf("") }
    var quoteBusy by remember { mutableStateOf(false) }
    var quoteAttempt by remember { mutableIntStateOf(0) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var orderId by rememberSaveable { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var historyReady by remember { mutableStateOf(false) }
    var checkoutBusy by remember { mutableStateOf(false) }
    var paymentMessage by remember { mutableStateOf("") }
    var pendingOnlineId by remember { mutableStateOf<String?>(null) }
    var onlineReady by remember { mutableStateOf(false) }
    val active=history.firstOrNull{it.optString("state") in setOf("draft","pending")}
    val displayedOrder=history.firstOrNull{it.optString("id")==orderId}
    fun money(value:Long?)=value?.let{java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(it)} ?: "—"
    suspend fun refreshHistory(){
        history=cardPurchaseHistory(accessToken)
        historyReady=true
    }
    suspend fun refreshOrders(){
        historyReady=false;onlineReady=false
        refreshHistory()
        pendingOnlineId=ZarinPalPurchaseClient.pending(accessToken)
        onlineReady=true
    }
    fun payAction(action:suspend ()->Unit){
        if(checkoutBusy)return
        checkoutBusy=true;paymentMessage=""
        scope.launch{
            try{action()}catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){paymentMessage=SuraErrors.describe(e,"وضعیت پرداخت تأیید نشد؛ همان سفارش را دوباره بررسی کنید. [CHECKOUT-STATUS]")}
            finally{checkoutBusy=false}
        }
    }
    LaunchedEffect(accessToken){
        try{refreshOrders()}catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){paymentMessage=SuraErrors.describe(e,"بازیابی سفارش‌های قبلی ناموفق بود. [CHECKOUT-RECOVERY]")}
    }
    LaunchedEffect(accessToken,plan,selectedQuantity,quoteAttempt){
        quote=null;quoteError="";quoteBusy=false
        if(plan=="free")return@LaunchedEffect
        val units=selectedQuantity?:return@LaunchedEffect
        quoteBusy=true
        try{quote=cardPurchaseQuote(accessToken,plan,units)}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){quoteError=SuraErrors.describe(e,"استعلام مبلغ تأیید نشد. [QUOTE-ERROR]")}
        finally{quoteBusy=false}
    }
    val q=quote?.takeIf{it.matches(plan,selectedQuantity)}
    val canPurchase=historyReady&&onlineReady&&active==null&&pendingOnlineId==null&&!checkoutBusy
    val cardShape=RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F9FF)).verticalScroll(rememberScrollState())
        .padding(horizontal=12.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("خرید اعتبار",fontWeight=FontWeight.ExtraBold,color=deepBlue,style=MaterialTheme.typography.headlineSmall)
        if(displayedOrder!=null){
            CardTransferPanel(accessToken,displayedOrder,onRefresh={refreshHistory()},onBack={orderId=null;checkout=false})
        }else if(checkout && q!=null && active==null){
            Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(3.dp)){
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("انتخاب روش پرداخت",color=deepBlue,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge)
                    Text(if(q.kind=="monthly")"اشتراک ماهانه • ۲۰ صورتحساب" else "${q.units} اعتبار تکی",color=deepBlue)
                    Text("مبلغ قابل پرداخت: ${money(q.total)} ریال",fontWeight=FontWeight.Bold,color=deepBlue)
                    Card(shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(2.dp,Color(0xFF356DF0)),colors=CardDefaults.cardColors(containerColor=Color(0xFFE8F4FF))){
                        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                            RadioButton(selected=true,onClick=null)
                            Column(Modifier.padding(start=8.dp)){
                                Text("کارت‌به‌کارت",fontWeight=FontWeight.Bold,color=deepBlue)
                                Text("ارسال رسید و تأیید توسط مدیرکل",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Button(enabled=canPurchase&&q.enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),onClick={payAction{
                        // Recheck both histories before reserving. An existing order is resumed, not overwritten.
                        refreshOrders()
                        val prior=history.firstOrNull{it.optString("state") in setOf("draft","pending")}
                        if(prior!=null){orderId=prior.getString("id");checkout=false;return@payAction}
                        if(pendingOnlineId!=null){checkout=false;paymentMessage="ابتدا سفارش پرداخت قبلی را تعیین تکلیف کنید.";return@payAction}
                        try{
                            val result=CardTransferApi.call(accessToken,"reserve",org.json.JSONObject().put("kind",q.kind).put("units",q.units).put("total",q.total))
                            val id=result.getString("id")
                            refreshHistory()
                            require(history.any{it.optString("id")==id})
                            orderId=id;checkout=false
                        }catch(e:kotlinx.coroutines.CancellationException){throw e}
                        catch(e:Exception){
                            // Lost acknowledgement: do not let a second checkout begin without recovery.
                            historyReady=false
                            checkout=false
                            quoteAttempt++
                            throw e
                        }
                    }}){Text(if(checkoutBusy)"در حال ثبت سفارش…" else "ادامه پرداخت")}
                    OutlinedButton(onClick={checkout=false},enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth()){Text("بازگشت به انتخاب پلن")}
                }
            }
        }else{
            Text("پلن موردنظر خود را انتخاب کنید.",style=MaterialTheme.typography.bodySmall,color=Color(0xFF5F7391))
            if(active!=null){
                Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color(0xFFE8F4FF))){
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("سفارش پرداخت قبلی",fontWeight=FontWeight.Bold,color=deepBlue)
                        Text(if(active.optString("state")=="pending")"رسید در انتظار بررسی است؛ دوباره واریز نکنید." else "یک سفارش ثبت شده هنوز رسید ندارد.")
                        Button(onClick={orderId=active.getString("id");checkout=false},enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth()){Text("ادامه و پیگیری سفارش")}
                    }
                }
            }
            if(pendingOnlineId!=null){
                Text("سفارش پرداخت قبلی هنوز تعیین تکلیف نشده است؛ پیش از خرید جدید استعلام بگیرید.",color=MaterialTheme.colorScheme.error)
                OutlinedButton(enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth(),onClick={payAction{
                    val id=pendingOnlineId?:return@payAction
                    val result=ZarinPalPurchaseClient.status(accessToken,id)
                    if(result.optString("state") in setOf("verified","failed")){refreshOrders();paymentMessage="وضعیت سفارش قبلی به‌روزرسانی شد."}
                    else paymentMessage="سفارش قبلی هنوز تعیین تکلیف نشده است؛ دوباره پرداخت نکنید."
                }}){Text("استعلام سفارش قبلی")}
            }
            if(!historyReady||!onlineReady){
                OutlinedButton(onClick={payAction{refreshOrders()}},enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth()){Text("بررسی وضعیت سفارش‌های قبلی")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(Triple("single","اعتبار تکی","هر صورتحساب یک اعتبار"),Triple("monthly","اشتراک ماهانه","۲۰ صورتحساب در ۳۰ روز")).forEach{(id,title,subtitle)->
                    val selected=plan==id
                    Card(onClick={plan=id;checkout=false},enabled=!checkoutBusy,modifier=Modifier.weight(1f).height(168.dp),shape=cardShape,
                        border=androidx.compose.foundation.BorderStroke(if(selected)2.dp else 1.dp,if(selected)Color(0xFF356DF0) else Color(0xFFE4EAF3)),
                        colors=CardDefaults.cardColors(containerColor=if(selected)Color(0xFFE8F4FF) else Color.White),elevation=CardDefaults.cardElevation(3.dp)){
                        Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                            Text(if(selected)"●" else "○",color=if(selected)Color(0xFF176AF0) else Color.Gray)
                            Text(title,fontWeight=FontWeight.ExtraBold,color=deepBlue,style=MaterialTheme.typography.titleMedium)
                            Text(subtitle,color=Color(0xFF526D8F),style=MaterialTheme.typography.bodySmall)
                            Text(if(id=="single")"بدون تعهد ماهانه" else "تمدید دستی",color=deepBlue,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if(plan!="free")Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(3.dp)){
                Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
                    Text(if(plan=="single")"تعداد اعتبار موردنیاز" else "اشتراک ماهانه",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=deepBlue)
                    if(plan=="single"){
                        Text("قیمت هر اعتبار: ${money(q?.let{it.subtotal/it.units})} ریال",color=Color(0xFF60768B),style=MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedButton(onClick={quantity=((quantity.toIntOrNull()?:1)-1).coerceAtLeast(1).toString()}){Text("−")}
                            OutlinedTextField(quantity,{quantity=ir.saba.invoice.data.identifiers.SuraIdentifierStore.normalize(it).filter{c->c in '0'..'9'}.take(6)},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),label={Text("تعداد اعتبار")},shape=RoundedCornerShape(14.dp))
                            Button(onClick={quantity=((quantity.toIntOrNull()?:0)+1).coerceAtMost(999999).toString()}){Text("+")}
                        }
                    }else Text("۲۰ صورتحساب در ۳۰ روز، تمدید دستی",color=deepBlue)
                    Text("مبلغ پایه: ${money(q?.subtotal)} ریال",color=deepBlue)
                    Text("مالیات ارزش افزوده (۱۰٪): ${money(q?.vat)} ریال",color=deepBlue)
                    Text("مبلغ نهایی قابل پرداخت: ${money(q?.total)} ریال",color=deepBlue,fontWeight=FontWeight.Bold)
                    if(quoteBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(quoteError.isNotBlank()){
                        Text(quoteError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={quoteAttempt++},modifier=Modifier.fillMaxWidth()){Text("استعلام دوباره")}
                    }
                    if(q!=null&&!q.enabled)Text("فروش این پلن فعلاً فعال نیست.",color=MaterialTheme.colorScheme.error)
                    Button(onClick={checkout=true},enabled=q?.enabled==true&&canPurchase&&!quoteBusy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("ادامه خرید")}
                }
            }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Card(onClick = { plan = "free";checkout=false }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (plan == "free") 2.dp else 1.dp, Color(0xFF359985)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F9F3))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("صورتحساب رایگان", color = deepBlue, fontWeight = FontWeight.Bold)
                    Text("۳ اعتبار هدیه", color = Color(0xFF168366))
                    Text("یک‌بار برای هر حساب و دستگاه", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (plan == "free") {
            Card(shape = cardShape, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("فعال‌سازی هدیه ۳ صورتحساب", color = deepBlue, fontWeight = FontWeight.Bold)
                    Text("برای دریافت یک‌باره اعتبار، کد هدیه را وارد کنید.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(promoCode, { promoCode = it.take(48); promoMessage = "" },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("کد هدیه") }, shape = RoundedCornerShape(14.dp))
                    Button(enabled = !promoBusy && !promoGranted && promoCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            promoBusy = true
                            promoMessage = ""
                            scope.launch {
                                try {
                                    ir.saba.invoice.security.AccountSession.requireOwner(accessToken,ir.saba.invoice.security.AccountScope.of(ownerId))
                                    FreeCreditClient.redeem(context,accessToken,promoCode).getOrThrow()
                                    promoGranted=true;promoMessage="۳ اعتبار هدیه در سرور ثبت شد."
                                } catch(e:kotlinx.coroutines.CancellationException) { throw e }
                                catch(e:Exception) { promoMessage=SuraErrors.describe(e,"دریافت هدیه تأیید نشد؛ موجودی را استعلام کنید. [GIFT-ERROR]") }
                                finally { promoBusy=false }
                            }
                        }) { Text(if (promoBusy) "در حال بررسی…" else "فعال‌سازی هدیه") }
                    if (promoMessage.isNotBlank()) Text(promoMessage, color = deepBlue,
                        style = MaterialTheme.typography.bodySmall)
                    Text("شناسه دستگاه برای جلوگیری از دریافت تکراری بررسی می‌شود. IP به‌تنهایی معیار مسدودسازی نیست.",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF687A89))
                    Text("مصرف اعتبار برای هر فاکتور جدید نیازمند تأیید سرور است.",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF977425))
                }
            }
        }

            val recent=history.filter{it.optString("state") in setOf("approved","rejected")}.take(5)
            if(recent.isNotEmpty()){
                Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                        Text("سوابق پرداخت",color=deepBlue,fontWeight=FontWeight.Bold)
                        recent.forEach{r->TextButton(onClick={orderId=r.getString("id")},modifier=Modifier.fillMaxWidth()){Text((if(r.optString("state")=="approved")"تأیید شده" else "رد شده")+" | "+money(r.optLong("amount_rial"))+" ریال")}}
                    }
                }
            }
        }
        if(checkoutBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(paymentMessage.isNotBlank())Text(paymentMessage,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Text("مبالغ به ریال هستند؛ اعتبار فقط پس از تأیید سرور شارژ می‌شود.",color=Color(0xFF5F7391),style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
    }
}
