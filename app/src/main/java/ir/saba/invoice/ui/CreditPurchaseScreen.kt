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

private val deepBlue = SuraPalette.Navy
private val sky = SuraPalette.Soft
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
    var salesOffers by remember { mutableStateOf<List<SalesOffer>>(emptyList()) }
    var offersLoading by remember { mutableStateOf(true) }
    var selectedOfferId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedOffer=salesOffers.firstOrNull{it.id==selectedOfferId}

    var freeSelected by rememberSaveable { mutableStateOf(false) }
    val plan=if(freeSelected)"free" else if(selectedOffer!=null)"offer" else "single"
    val selectedQuantity=if(selectedOffer!=null)selectedOffer.credits else quantity.toLongOrNull()?.takeIf{it in 1L..999999L}
    var quote by remember { mutableStateOf<PurchaseQuote?>(null) }
    var quoteError by remember { mutableStateOf("") }
    var quoteBusy by remember { mutableStateOf(false) }
    var quoteAttempt by remember { mutableIntStateOf(0) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var orderId by rememberSaveable { mutableStateOf<String?>(null) }
    var cancelId by remember { mutableStateOf<String?>(null) }
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
        try{
            refreshOrders()
            val response=CardTransferApi.call(accessToken,"offers")
            salesOffers=SalesOffer.parseAll(response.getJSONArray("offers"))
            if(selectedOfferId!=null&&salesOffers.none{it.id==selectedOfferId})selectedOfferId=null
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){paymentMessage=SuraErrors.describe(e,"دریافت طرح‌های فروش انجام نشد؛ برای تازه‌سازی دوباره تلاش کنید. [OFFERS-LOAD]")}
        finally{offersLoading=false}
    }
    LaunchedEffect(accessToken,plan,selectedQuantity,selectedOfferId,selectedOffer?.priceRial,quoteAttempt){
        quote=null;quoteError="";quoteBusy=false
        if(plan=="free")return@LaunchedEffect
        val units=selectedQuantity?:return@LaunchedEffect
        quoteBusy=true
        try{quote=cardPurchaseQuote(accessToken,plan,units,selectedOfferId)}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){quoteError=SuraErrors.describe(e,"استعلام مبلغ تأیید نشد. [QUOTE-ERROR]")}
        finally{quoteBusy=false}
    }
    val q=quote?.takeIf{it.matches(plan,selectedQuantity)}
    val canPurchase=historyReady&&onlineReady&&active==null&&pendingOnlineId==null&&!checkoutBusy
    val cardShape=RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxSize().background(SuraPalette.Canvas).verticalScroll(rememberScrollState())
        .padding(horizontal=12.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("خرید اعتبار",fontWeight=FontWeight.ExtraBold,color=deepBlue,style=MaterialTheme.typography.headlineSmall)
        if(displayedOrder!=null){
            CardTransferPanel(accessToken,displayedOrder,onRefresh={refreshHistory()},onBack={orderId=null;checkout=false},onCancel={if(!checkoutBusy)cancelId=displayedOrder.getString("id")})
        }else if(checkout && q!=null && active==null){
            Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(3.dp)){
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("انتخاب روش پرداخت",color=deepBlue,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge)
                    Text(if(q.kind=="offer")((selectedOffer?.title ?: "طرح فروش")+" • ${q.units} صورتحساب") else "${q.units} اعتبار تکی",color=deepBlue)
                    Text("مبلغ قابل پرداخت: ${money(q.total)} ریال",fontWeight=FontWeight.Bold,color=deepBlue)
                    Card(shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(2.dp,SuraPalette.Teal),colors=CardDefaults.cardColors(containerColor=SuraPalette.Soft)){
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
                            val reserveBody=org.json.JSONObject().put("kind",q.kind).put("units",q.units).put("total",q.total).put("checkoutVersion",2)
                            if(q.kind=="offer")reserveBody.put("offerId",requireNotNull(selectedOfferId))
                            val result=CardTransferApi.call(accessToken,"reserve",reserveBody)
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
            Text("تعداد اعتبار یا گزینه هدیه را انتخاب کنید.",style=MaterialTheme.typography.bodySmall,color=SuraPalette.Muted)
            if(active!=null){
                Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=SuraPalette.Soft)){
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("سفارش پرداخت قبلی",fontWeight=FontWeight.Bold,color=deepBlue)
                        Text(if(active.optString("state")=="pending")"رسید در انتظار بررسی است؛ دوباره واریز نکنید." else "یک سفارش ثبت شده هنوز رسید ندارد.")
                        Button(onClick={orderId=active.getString("id");checkout=false},enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth()){Text("ادامه و پیگیری سفارش")}
                        if(active.optString("state")=="draft")TextButton(onClick={cancelId=active.getString("id")},enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("انصراف از سفارش")}
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
            if(offersLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(salesOffers.isNotEmpty()||!offersLoading)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){
                Text("طرح‌های فروش سورا",fontWeight=FontWeight.Bold,color=deepBlue,style=MaterialTheme.typography.titleMedium)
                TextButton(enabled=!offersLoading&&!checkoutBusy,onClick={offersLoading=true;scope.launch{try{val response=CardTransferApi.call(accessToken,"offers");salesOffers=SalesOffer.parseAll(response.getJSONArray("offers"));if(selectedOfferId!=null&&salesOffers.none{it.id==selectedOfferId})selectedOfferId=null;paymentMessage="طرح‌های فروش به‌روز شد."}catch(e:Exception){paymentMessage=SuraErrors.describe(e,"به‌روزرسانی طرح‌ها ناموفق بود.")}finally{offersLoading=false}}}){Text("به‌روزرسانی")}
            }
            salesOffers.forEach{offer->
                val selected=selectedOfferId==offer.id
                Card(onClick={freeSelected=false;selectedOfferId=offer.id;checkout=false;quote=null;quoteError=""},enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth(),
                    shape=cardShape,border=androidx.compose.foundation.BorderStroke(if(selected)2.dp else 1.dp,if(selected)SuraPalette.Teal else SuraPalette.Edge),
                    colors=CardDefaults.cardColors(containerColor=if(selected)SuraPalette.Soft else Color.White),elevation=CardDefaults.cardElevation(2.dp)){
                    Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            RadioButton(selected=selected,onClick=null)
                            Text(offer.title,color=deepBlue,fontWeight=FontWeight.ExtraBold,modifier=Modifier.weight(1f))
                            if(offer.featured)Text("پیشنهاد ویژه",color=violet,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                        }
                        if(offer.description.isNotBlank())Text(offer.description,color=SuraPalette.Muted,style=MaterialTheme.typography.bodySmall)
                        Text("${offer.credits} صورتحساب",color=deepBlue,fontWeight=FontWeight.SemiBold)
                        Text("قیمت پایه: ${money(offer.priceRial)} ریال",color=SuraPalette.Muted,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(onClick={freeSelected=false;selectedOfferId=null;checkout=false},enabled=!checkoutBusy,modifier=Modifier.fillMaxWidth(),shape=cardShape,
                border=androidx.compose.foundation.BorderStroke(if(!freeSelected&&selectedOfferId==null)2.dp else 1.dp,if(!freeSelected&&selectedOfferId==null)SuraPalette.Teal else SuraPalette.Edge),
                colors=CardDefaults.cardColors(containerColor=if(!freeSelected&&selectedOfferId==null)SuraPalette.Soft else Color.White),elevation=CardDefaults.cardElevation(3.dp)){
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    RadioButton(selected=plan=="single",onClick=null)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text("اعتبار تکی",color=deepBlue,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium)
                        Text("هر صورتحساب یک اعتبار • خرید به تعداد دلخواه",color=SuraPalette.Muted,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(plan=="single")Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(3.dp)){
                Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
                    Text("تعداد اعتبار موردنیاز",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=deepBlue)
                    Text("قیمت هر اعتبار: ${money(q?.let{it.subtotal/it.units})} ریال",color=SuraPalette.Muted,style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={quantity=((quantity.toIntOrNull()?:1)-1).coerceAtLeast(1).toString()}){Text("−")}
                        OutlinedTextField(quantity,{quantity=ir.saba.invoice.data.identifiers.SuraIdentifierStore.normalize(it).filter{ch->ch in '0'..'9'}.take(6)},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),label={Text("تعداد اعتبار")},shape=RoundedCornerShape(14.dp))
                        Button(onClick={quantity=((quantity.toIntOrNull()?:0)+1).coerceAtMost(999999).toString()}){Text("+")}
                    }
                    Text("مبلغ پایه: ${money(q?.subtotal)} ریال",color=deepBlue)
                    Text("مالیات ارزش افزوده (۱۰٪): ${money(q?.vat)} ریال",color=deepBlue)
                    Text("مبلغ نهایی قابل پرداخت: ${money(q?.total)} ریال",color=deepBlue,fontWeight=FontWeight.Bold)
                    if(quoteBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(quoteError.isNotBlank()){Text(quoteError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={quoteAttempt++},modifier=Modifier.fillMaxWidth()){Text("استعلام دوباره")}}
                    if(q!=null&&!q.enabled)Text("فروش این پلن فعلاً فعال نیست.",color=MaterialTheme.colorScheme.error)
                    Button(onClick={checkout=true},enabled=q?.enabled==true&&canPurchase&&!quoteBusy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("ادامه خرید")}
                }
            }
            if(plan=="offer"&&selectedOffer!=null)Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(3.dp)){
                Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("خلاصه طرح انتخابی",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=deepBlue)
                    Text(selectedOffer.title,color=deepBlue,fontWeight=FontWeight.ExtraBold)
                    Text("${selectedOffer.credits} صورتحساب • ${money(q?.subtotal)} ریال مبلغ پایه",color=deepBlue)
                    Text("مالیات ارزش افزوده (۱۰٪): ${money(q?.vat)} ریال",color=deepBlue)
                    Text("مبلغ نهایی قابل پرداخت: ${money(q?.total)} ریال",color=deepBlue,fontWeight=FontWeight.Bold)
                    if(quoteBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(quoteError.isNotBlank()){Text(quoteError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={quoteAttempt++},modifier=Modifier.fillMaxWidth()){Text("استعلام دوباره")}}
                    if(q!=null&&!q.enabled)Text("فروش این طرح فعلاً فعال نیست.",color=MaterialTheme.colorScheme.error)
                    Button(onClick={checkout=true},enabled=q?.enabled==true&&canPurchase&&!quoteBusy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("خرید این طرح")}
                }
            }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Card(onClick = { freeSelected=true;selectedOfferId=null;checkout=false }, modifier = Modifier.fillMaxWidth(),
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
                        style = MaterialTheme.typography.labelSmall, color = SuraPalette.Muted)
                    Text("مصرف اعتبار برای هر فاکتور جدید نیازمند تأیید سرور است.",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF977425))
                }
            }
        }

            val recent=history.filter{it.optString("state") in setOf("approved","rejected","cancelled")}.take(5)
            if(recent.isNotEmpty()){
                Card(shape=cardShape,colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                        Text("سوابق پرداخت",color=deepBlue,fontWeight=FontWeight.Bold)
                        recent.forEach{r->TextButton(onClick={orderId=r.getString("id")},modifier=Modifier.fillMaxWidth()){Text((when(r.optString("state")){"approved"->"تأیید شده";"cancelled"->"لغو شده";else->"رد شده"})+" | "+money(r.optLong("amount_rial"))+" ریال")}}
                    }
                }
            }
        }
        if(checkoutBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(paymentMessage.isNotBlank())Text(paymentMessage,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Text("مبالغ به ریال هستند؛ اعتبار فقط پس از تأیید سرور شارژ می‌شود.",color=SuraPalette.Muted,style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
    }
    cancelId?.let{id->
        AlertDialog(onDismissRequest={cancelId=null},title={Text("انصراف از سفارش")},
            text={Text("فقط اگر هیچ مبلغی برای این سفارش واریز نکرده‌اید، آن را لغو کنید. اگر واریز کرده‌اید، رسید همان سفارش را ثبت کنید.")},
            confirmButton={TextButton(enabled=!checkoutBusy,onClick={
                cancelId=null
                payAction{
                    historyReady=false
                    val result=CardTransferApi.call(accessToken,"cancel",org.json.JSONObject().put("id",id).put("notPaid",true))
                    require(result.optString("id")==id&&result.optString("state")=="cancelled")
                    refreshHistory();orderId=null;checkout=false;quoteAttempt++
                    paymentMessage="سفارش لغو شد. اکنون می‌توانید خرید جدید انجام دهید."
                }
            }){Text("تأیید انصراف؛ واریز نکرده‌ام",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton(onClick={cancelId=null}){Text("بازگشت")}})
    }

}
