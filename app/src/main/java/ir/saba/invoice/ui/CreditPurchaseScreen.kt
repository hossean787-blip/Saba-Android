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
fun CreditPurchaseScreen(accessToken: String) {
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

    var plan by remember { mutableStateOf("single") }
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
                    Text("برای هر صورتحساب یک اعتبار مصرف می‌شود.",
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
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(if (plan == "single") "خرید اعتبار تکی — در انتظار تعیین قیمت"
                         else "خرید اشتراک — در انتظار تعیین قیمت")
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
                                val result = FreeCreditClient.redeem(context, accessToken, promoCode)
                                promoMessage = result.fold(
                                    onSuccess = { promoGranted = true; "۳ اعتبار هدیه در سرور ثبت شد." },
                                    onFailure = { it.message ?: "دریافت هدیه ناموفق بود." }
                                )
                                promoBusy = false
                            }
                        }) { Text(if (promoBusy) "در حال بررسی…" else "فعال‌سازی هدیه") }
                    if (promoMessage.isNotBlank()) Text(promoMessage, color = deepBlue,
                        style = MaterialTheme.typography.bodySmall)
                    Text("شناسه دستگاه برای جلوگیری از دریافت تکراری بررسی می‌شود. IP به‌تنهایی معیار مسدودسازی نیست.",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF687A89))
                    Text("مصرف این اعتبار برای ثبت فاکتور، پس از اتصال کیف پول سرور فعال می‌شود.",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF977425))
                }
            }
        }
        Text("پرداخت امن زرین‌پال پس از تعیین تعرفه و تأیید سرور فعال می‌شود.",
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
