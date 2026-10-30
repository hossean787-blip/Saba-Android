package ir.saba.invoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.Invoice
import java.text.NumberFormat
import java.util.Locale

private val midnight = Color(0xFF051B2D)
private val ocean = Color(0xFF0D475A)
private val teal = Color(0xFF0A8191)
private val gold = Color(0xFFEBCB90)
private val cool = Color(0xFFD8F5F8)
private val textNavy = Color(0xFF16334B)
private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR"))

/** Premium home screen; all numbers and activities come from persisted app data. */
@Composable
fun HomeDashboard(
    taxpayerName: String?,
    remainingCharge: Long,
    draftCount: Int,
    queueCount: Int,
    customerCount: Int,
    invoices: List<Invoice>,
    onSelectTaxpayer: () -> Unit,
    onNewInvoice: () -> Unit,
    onCustomers: () -> Unit,
    onInvoices: () -> Unit,
    onCharge: () -> Unit,
    taxpayerOptions: List<Pair<Long, String>> = emptyList(),
    onTaxpayerSelected: (Long) -> Unit = {}
) {
    var selectorOpen by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF2F9FF)),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("نمای کلی فعالیت‌ها", style = MaterialTheme.typography.titleLarge,
                    color = textNavy, fontWeight = FontWeight.ExtraBold)
                Text("صورتحساب‌ها و وضعیت مالیاتی در یک نگاه",
                    color = Color(0xFF668299), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Box(Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (taxpayerOptions.isNotEmpty()) selectorOpen = true else onSelectTaxpayer()
                },
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = midnight),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(
                    listOf(midnight, ocean, Color(0xFF0A334D))
                ))) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape)
                                .background(Color(0xFF1C5970)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null,
                                    tint = gold, modifier = Modifier.size(26.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("مودی فعال", color = Color(0xFFA7CBD4),
                                    style = MaterialTheme.typography.labelSmall)
                                Text(taxpayerName ?: "انتخاب مودی", color = Color.White,
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Surface(shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF174E62)) {
                                Text("تغییر مودی  ‹", modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.labelSmall, color = cool)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF315568), thickness = 1.dp)
                        Text(if (taxpayerName == null) "برای شروع، مودی موردنظر را ثبت یا انتخاب کنید."
                            else "گزارش اعداد این صفحه مربوط به مودی انتخاب‌شده است.",
                            color = Color(0xFFBBD7E2), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            DropdownMenu(expanded = selectorOpen, onDismissRequest = { selectorOpen = false }) {
                taxpayerOptions.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        selectorOpen = false
                        onTaxpayerSelected(id)
                    })
                }
            }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumMetric("پیش‌نویس", draftCount.toLong(), Icons.AutoMirrored.Filled.List,
                    Color(0xFFFFF0F2), Color(0xFFC45471), Modifier.weight(1f), onInvoices)
                PremiumMetric("در صف ارسال", queueCount.toLong(), Icons.AutoMirrored.Filled.List,
                    Color(0xFFFFF3E2), Color(0xFFB88033), Modifier.weight(1f), onInvoices)
                PremiumMetric("شارژ باقی‌مانده", remainingCharge, Icons.Default.AccountCircle,
                    Color(0xFFE6F6F3), Color(0xFF117C6B), Modifier.weight(1f), onCharge)
            }
        }
        item {
            Text("دسترسی سریع", style = MaterialTheme.typography.titleMedium,
                color = textNavy, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Card(
                    modifier = Modifier.weight(1.12f).height(151.dp).clickable(onClick = onNewInvoice),
                    shape = RoundedCornerShape(25.dp),
                    colors = CardDefaults.cardColors(containerColor = teal),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF0B7886), Color(0xFF0A4A73)))
                    )) {
                        Column(Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = gold,
                                modifier = Modifier.size(28.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("ثبت فاکتور جدید", color = Color.White,
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("شروع صدور صورتحساب", color = cool,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier.weight(1f).height(151.dp).clickable(onClick = onCustomers),
                    shape = RoundedCornerShape(25.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE9E9FA))
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = Color(0xFF595DAD), modifier = Modifier.size(26.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("مشتریان", fontWeight = FontWeight.Bold, color = textNavy,
                                style = MaterialTheme.typography.titleMedium)
                            Text("${numberFormat.format(customerCount)} مشتری ثبت‌شده",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFF6A7188))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("آخرین فعالیت‌ها", style = MaterialTheme.typography.titleMedium,
                    color = textNavy, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onInvoices) { Text("مشاهده همه", color = teal) }
            }
        }
        if (invoices.isEmpty()) {
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE7F4F9)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = teal)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("هنوز صورتحسابی ثبت نشده", fontWeight = FontWeight.Bold,
                                color = textNavy, style = MaterialTheme.typography.bodyMedium)
                            Text("برای این مودی، اولین صورتحساب را ایجاد کنید.",
                                color = Color(0xFF74869B), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            items(invoices.take(5), key = { it.id }) { invoice ->
                val statusText = when (invoice.status) {
                    "DRAFT" -> "پیش‌نویس"
                    "FINALIZED" -> "نهایی محلی"
                    "CANCELLED" -> "لغوشده"
                    "QUEUED" -> "در صف"
                    else -> "وضعیت نامشخص"
                }
                val statusColor = when (invoice.status) {
                    "FINALIZED" -> Color(0xFF0B806F)
                    "QUEUED" -> Color(0xFFAD792A)
                    "CANCELLED" -> Color(0xFFBD4A5E)
                    else -> Color(0xFF59718F)
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onInvoices),
                    shape = RoundedCornerShape(19.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE4F4F8)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = teal)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("صورتحساب ${invoice.invoiceNumber}", color = textNavy,
                                fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(invoice.issueDate, color = Color(0xFF75899B),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${numberFormat.format(invoice.totalAmountRial)} ریال",
                                color = textNavy, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold)
                            Text(statusText, color = statusColor,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFE8F2F7)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("ⓘ", color = Color(0xFF42728C))
                Text("ارسال واقعی به سامانه مؤدیان هنوز فعال نیست؛ وضعیت‌ها صرفاً بر اساس داده‌های محلی هستند.",
                    color = Color(0xFF56768C), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PremiumMetric(
    title: String, count: Long, icon: ImageVector,
    background: Color, accent: Color, modifier: Modifier, onClick: () -> Unit
) {
    Card(modifier = modifier.height(119.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Text(numberFormat.format(count), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold, color = accent)
            Text(title, style = MaterialTheme.typography.labelSmall, color = accent,
                maxLines = 2)
        }
    }
}
