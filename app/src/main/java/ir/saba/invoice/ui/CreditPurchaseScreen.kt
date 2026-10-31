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
fun CreditPurchaseScreen() {
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

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF3F9FE))
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = sky), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("کیف اعتبار سورا", color = deepBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("موجودی قابل استفاده پس از اتصال به سرور نمایش داده می‌شود.", color = deepBlue, style = MaterialTheme.typography.bodySmall)
                Text("اعتبار تأییدنشده قابل مصرف نیست", style = MaterialTheme.typography.labelSmall, color = Color(0xFF52758D))
            }
        }
        Text("خرید اعتبار و اشتراک", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = deepBlue)
        Text("پلن موردنیاز خود را انتخاب کنید.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF54718B))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFB7DFFF),Color(0xFFEAF6FF))),RoundedCornerShape(14.dp)).padding(15.dp)) {
                    Text("خرید اعتبار تکی", fontWeight = FontWeight.Bold, color = deepBlue, style = MaterialTheme.typography.titleMedium)
                }
                Text("برای هر صورتحساب یک اعتبار مصرف می‌شود؛ اعتبار تکی محدودیت زمانی ندارد.", style = MaterialTheme.typography.bodySmall)
                Text("تعداد اعتبار موردنیاز", color = deepBlue, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { quantity = ((quantity.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() }) { Text("−") }
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { if(it.all(Char::isDigit) && it.length <= 6) quantity = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("تعداد") }
                    )
                    OutlinedButton(onClick = { quantity = ((quantity.toIntOrNull() ?: 0) + 1).coerceAtMost(999999).toString() }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = officialInvoice, onCheckedChange = { officialInvoice = it })
                    Text("فاکتور رسمی می‌خواهم", fontWeight = FontWeight.SemiBold, color = deepBlue)
                }
                if (officialInvoice) {
                    Text("مشخصات دریافت‌کننده فاکتور رسمی", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = deepBlue)
                    @Composable fun Field(label: String, value: String, update: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = value, onValueChange = update,
                            placeholder = { Text(label) }, singleLine = true,
                            shape = RoundedCornerShape(12.dp), modifier = modifier)
                    }
                    Field("نام و نام خانوادگی / نام شرکت", fullName, { fullName = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("کد اقتصادی", economicId, { economicId = it }, Modifier.weight(1f))
                        Field("شناسه ملی", nationalId, { nationalId = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("شماره ثبت", registration, { registration = it }, Modifier.weight(1f))
                        Field("شماره تماس", phone, { phone = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProvincePicker(province, IranDivisions.counties.keys.toList(), { province = it; county = "" }, Modifier.weight(1f), "استان")
                        ProvincePicker(county, IranDivisions.counties[province].orEmpty(), { county = it }, Modifier.weight(1f), "شهرستان")
                    }
                    Field("آدرس کامل", address, { address = it })
                    Text("مشخصات صرفاً در فرم وارد شده‌اند و تا اتصال سرور ارسال نمی‌شوند.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF56758D))
                }
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF83B9DF))) {
                    Text("خرید اعتبار تکی — در انتظار تعیین قیمت")
                }
            }
        }
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E7FA))) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("اشتراک ماهانه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = violet)
                Text("۳۰ روز اعتبار • سقف ۲۰ صورتحساب در هر دوره", color = deepBlue)
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("خرید اشتراک — در انتظار تعیین قیمت") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("پلن سوم", "پلن چهارم").forEach { plan ->
                Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE7ECF4))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(plan, fontWeight = FontWeight.Bold, color = deepBlue)
                        Text("به‌زودی", color = Color(0xFF74859A))
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = sky), shape = RoundedCornerShape(17.dp)) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = deepBlue)
                Spacer(Modifier.width(9.dp))
                Text("پرداخت امن زرین‌پال پس از اتصال و تأیید سرور فعال خواهد شد.", style = MaterialTheme.typography.bodySmall, color = deepBlue)
            }
        }
        Spacer(Modifier.height(18.dp))
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
