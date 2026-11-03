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
import ir.saba.invoice.domain.IranDivisions

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
    var officialInvoice by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var economicId by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var county by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    var paymentBusy by remember { mutableStateOf(false) }
    var paymentMessage by remember { mutableStateOf("") }
    var pendingOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf("single") }
    val selectedQuantity = quantity.toLongOrNull()?.takeIf { it in 1L..999_999L }
    var quotedUnitRial by remember { mutableStateOf<Long?>(null) }
    var quotedTotalRial by remember { mutableStateOf<Long?>(null) }
    var quotedVatRial by remember { mutableStateOf<Long?>(null) }
    var gatewayReady by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf(false) }
    LaunchedEffect(accessToken, selectedQuantity) {
        quotedUnitRial = null
        quotedTotalRial = null
        quotedVatRial = null
        gatewayReady = false
        quoteError = false
        val count = selectedQuantity ?: return@LaunchedEffect
        if (accessToken.isBlank()) { quoteError = true; return@LaunchedEffect }
        runCatching { CreditQuoteClient.quote(accessToken, count) }
            .onSuccess { response ->
                if (response.optLong("quantity") == count &&
                    response.optString("currency") == "IRR" &&
                    response.optLong("unit_price_rial") > 0 &&
                    response.optLong("vat_rate_percent") == 10L &&
                    response.optLong("subtotal_rial") == count * response.optLong("unit_price_rial") &&
                    response.optLong("vat_rial") == (response.optLong("subtotal_rial") * 10 + 50) / 100 &&
                    response.optLong("total_rial") == response.optLong("subtotal_rial") + response.optLong("vat_rial")) {
                    quotedUnitRial = response.optLong("unit_price_rial")
                    quotedTotalRial = response.optLong("total_rial")
                    quotedVatRial = response.optLong("vat_rial")
                    gatewayReady = response.optBoolean("payment_enabled", false)
                } else quoteError = true
            }
            .onFailure { quoteError = true }
    }
    val cardShape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F9FF)).verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("خرید اعتبار", fontWeight = FontWeight.ExtraBold, color = deepBlue,
            style = MaterialTheme.typography.headlineSmall)
        Text("با خرید اعتبار، صورتحساب‌های خود را مدیریت کنید.",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF5F7391))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("single", "اعتبار تکی", "هر صورتحساب یک اعتبار"),
                Triple("monthly", "اشتراک ماهانه", "تا ۲۰ فاکتور در ۳۰ روز")
            ).forEach { (id, title, subtitle) ->
                val selected = plan == id
                Card(onClick = { plan = id }, modifier = Modifier.weight(1f).height(168.dp),
                    shape = cardShape,
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) Color(0xFF356DF0) else Color(0xFFE4EAF3)),
                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE8F4FF) else Color.White),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (selected) "●" else "○",
                            color = if (selected) Color(0xFF176AF0) else Color.Gray)
                        Text(title, fontWeight = FontWeight.ExtraBold, color = deepBlue,
                            style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, color = if (id == "monthly") violet else Color(0xFF526D8F),
                            style = MaterialTheme.typography.bodySmall)
                        Text(if (id == "single") "بدون تعهد ماهانه" else "سقف ۲۰ فاکتور",
                            color = deepBlue, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (plan != "free") Card(shape = cardShape, colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(if (plan == "single") "تعداد اعتبار موردنیاز" else "اشتراک ماهانه",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = deepBlue)
                if (plan == "single") {
                    Text("قیمت هر اعتبار: " + (quotedUnitRial?.let { java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(it) } ?: "در انتظار استعلام") + " ریال؛ هر صورتحساب یک اعتبار.",
                        color = Color(0xFF60768B), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            quantity = ((quantity.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString()
                        }) { Text("−") }
                        OutlinedTextField(quantity, onValueChange = {
                            if (it.all(Char::isDigit) && it.length <= 6) quantity = it
                        }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("تعداد اعتبار") }, shape = RoundedCornerShape(14.dp))
                        Button(onClick = {
                            quantity = ((quantity.toIntOrNull() ?: 0) + 1).coerceAtMost(999999).toString()
                        }) { Text("+") }
                    }
                } else {
                    Text("۳۰ روز اعتبار و حداکثر ۲۰ صورتحساب در هر دوره.",
                        color = deepBlue, style = MaterialTheme.typography.bodyMedium)
                }
                if (plan == "single") {
                    Text("مالیات ارزش افزوده (۱۰٪): " + (quotedVatRial?.let { java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(it) } ?: "—") + " ریال", color = deepBlue)
                    Text("مبلغ نهایی قابل پرداخت: " + (quotedTotalRial?.let { java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(it) } ?: "—") + " ریال",
                        color = deepBlue, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = officialInvoice, onCheckedChange = { officialInvoice = it })
                    Spacer(Modifier.width(10.dp))
                    Text("فاکتور رسمی می‌خواهم", color = deepBlue, fontWeight = FontWeight.Bold)
                }
                if (officialInvoice) {
                    Card(shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F2FF))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("اطلاعات فاکتور رسمی", fontWeight = FontWeight.Bold, color = violet)
                            @Composable fun Field(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(value, change, modifier = modifier,
                                    placeholder = { Text(label, maxLines = 1) }, singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
                            }
                            Field("نام و نام خانوادگی / نام شرکت", fullName, { fullName = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Field("کد اقتصادی", economicId, { economicId = it }, Modifier.weight(1f))
                                Field("شناسه ملی", nationalId, { nationalId = it }, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Field("شماره ثبت", registration, { registration = it }, Modifier.weight(1f))
                                Field("شماره تماس", phone, { phone = it }, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                ProvincePicker(province, IranDivisions.counties.keys.toList(),
                                    { province = it; county = "" }, Modifier.weight(1f), "استان")
                                ProvincePicker(county, IranDivisions.counties[province].orEmpty(),
                                    { county = it }, Modifier.weight(1f), "شهرستان")
                            }
                            Field("آدرس کامل", address, { address = it })
                        }
                    }
                }
                if (quoteError && plan == "single") Text("استعلام قیمت از سرور انجام نشد؛ اتصال اینترنت را بررسی کنید.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (paymentMessage.isNotBlank()) Text(paymentMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (plan == "single" && !gatewayReady && quotedTotalRial != null) Text("پذیرنده پرداخت در سرور هنوز فعال نشده است.", color = Color(0xFF977425), style = MaterialTheme.typography.bodySmall)
                if (pendingOrderId != null && plan == "single") {
                    OutlinedButton(onClick = {
                        val orderId = pendingOrderId ?: return@OutlinedButton
                        paymentBusy = true
                        scope.launch {
                            runCatching { ZarinPalPurchaseClient.status(accessToken, orderId) }
                                .onSuccess { result ->
                                    paymentMessage = when (result.optString("state")) {
                                        "verified" -> { pendingOrderId = null; "پرداخت تأیید شده و اعتبار در سرور ثبت شده است." }
                                        "failed" -> { pendingOrderId = null; "پرداخت ناموفق بوده است." }
                                        else -> "پرداخت هنوز توسط سرور تأیید نشده است؛ مجدداً پرداخت نکنید."
                                    }
                                }
                                .onFailure { paymentMessage = it.message ?: "استعلام پرداخت ناموفق بود." }
                            paymentBusy = false
                        }
                    }, enabled = !paymentBusy, modifier = Modifier.fillMaxWidth()) {
                        Text("استعلام نتیجه آخرین پرداخت")
                    }
                }
                Button(onClick = {
                    val count = selectedQuantity ?: return@Button
                    paymentBusy = true
                    paymentMessage = ""
                    scope.launch {
                        runCatching { ZarinPalPurchaseClient.create(accessToken, count) }
                            .onSuccess { response ->
                                pendingOrderId = response.optString("orderId").takeIf { it.isNotBlank() }
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(response.getString("paymentUrl")))) }
                                catch (_: Exception) { paymentMessage = "بازکردن درگاه پرداخت امکان‌پذیر نیست." }
                            }
                            .onFailure { paymentMessage = it.message ?: "درگاه پرداخت در دسترس نیست." }
                        paymentBusy = false
                    }
                }, enabled = plan == "single" && selectedQuantity != null && quotedTotalRial != null && gatewayReady && !paymentBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(if (plan == "single") (if (paymentBusy) "در حال ایجاد سفارش…" else "پرداخت امن زرین‌پال")
                         else "خرید اشتراک — در انتظار تعیین تعرفه اشتراک")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Card(onClick = { plan = "free" }, modifier = Modifier.weight(1f),
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
            Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEBEFF6))) {
                Column(Modifier.padding(14.dp)) {
                    Text("پلن چهارم", color = deepBlue, fontWeight = FontWeight.Bold)
                    Text("به‌زودی", color = Color(0xFF74859A))
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
                                catch(e:Exception) { promoMessage=e.message ?: "دریافت هدیه ناموفق بود." }
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
        Text("مبالغ به ریال نمایش داده می‌شوند. پرداخت پس از اتصال و تأیید درگاه زرین‌پال فعال می‌شود.",
            color = Color(0xFF5F7391), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProvincePicker(value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier, title: String) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(value.ifEmpty { title }, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.heightIn(max = 290.dp).verticalScroll(rememberScrollState())) {
                options.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); expanded = false })
                }
            }
        }
    }
}
