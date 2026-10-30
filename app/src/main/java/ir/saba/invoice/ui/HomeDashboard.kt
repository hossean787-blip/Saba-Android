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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.Invoice
import java.text.NumberFormat
import java.util.Locale

private val navy = Color(0xFF061D36)
private val blue = Color(0xFF164FC6)
private val mist = Color(0xFFF0F6FF)
private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR"))

/** Actual data only: no fabricated invoice statuses, transactions, or financial figures. */
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
    onCharge: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = navy)) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("سورا", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("مدیریت هوشمند صورتحساب مالیاتی", color = Color(0xFFB4D9FF), style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.weight(1.6f).clickable(onClick = onSelectTaxpayer), horizontalAlignment = Alignment.End) {
                        Text(taxpayerName ?: "انتخاب مودی", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("مودی فعال  ⌄", color = Color(0xFFC5D9F5), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.AccountCircle, contentDescription = "مودی فعال", tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeMetric("پیش‌نویس", draftCount, Color(0xFFFFE9ED), Color(0xFFC8224D), Modifier.weight(1f), onInvoices)
                HomeMetric("در صف ارسال", queueCount, Color(0xFFFFF2DD), Color(0xFFAC6300), Modifier.weight(1f), onInvoices)
                HomeMetric("شارژ باقی‌مانده", remainingCharge, Color(0xFFE0F8F0), Color(0xFF007C61), Modifier.weight(1f), onCharge)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.weight(1f).height(104.dp).clickable(onClick = onNewInvoice),
                    colors = CardDefaults.cardColors(containerColor = blue),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("ثبت فاکتور جدید", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("شروع صدور صورتحساب", color = Color(0xFFD8E8FF), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f).height(104.dp).clickable(onClick = onCustomers),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECEBFF)),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF6955C7))
                        Spacer(Modifier.height(4.dp))
                        Text("مشتریان", fontWeight = FontWeight.Bold, color = navy)
                        Text("${numberFormat.format(customerCount)} مشتری ثبت‌شده", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5A6385))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("آخرین فعالیت‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = navy)
                TextButton(onClick = onInvoices) { Text("مشاهده همه") }
            }
        }
        if (invoices.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Text("هنوز صورتحسابی برای این مودی ثبت نشده است.", modifier = Modifier.fillMaxWidth().padding(20.dp), color = Color(0xFF53637A))
                }
            }
        } else {
            items(invoices.take(5), key = { it.id }) { invoice ->
                val statusText = when (invoice.status) {
                    "DRAFT" -> "پیش‌نویس"
                    "FINALIZED" -> "نهایی محلی"
                    "CANCELLED" -> "لغوشده"
                    else -> "وضعیت نامشخص"
                }
                val statusBg = when (invoice.status) {
                    "DRAFT" -> Color(0xFFE8EEF7)
                    "FINALIZED" -> Color(0xFFE2F6EE)
                    else -> Color(0xFFFFE9EC)
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onInvoices),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = blue)
                        Column(Modifier.weight(1f)) {
                            Text("صورتحساب ${invoice.invoiceNumber}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(invoice.issueDate, color = Color(0xFF6A7890), style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${numberFormat.format(invoice.totalAmountRial)} ریال", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.background(statusBg, RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                Text(statusText, style = MaterialTheme.typography.labelSmall, color = navy)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("ارسال واقعی به سامانه مؤدیان هنوز فعال نیست؛ وضعیت‌ها بر اساس اطلاعات محلی نمایش داده می‌شوند.",
                color = Color(0xFF65758C), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HomeMetric(title: String, count: Long, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(112.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(numberFormat.format(count), style = MaterialTheme.typography.headlineSmall, color = fg, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 2)
        }
    }
}
