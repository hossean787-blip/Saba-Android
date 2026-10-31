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

/** Independent invoice event history; statuses describe local state, not tax submission. */
@Composable
fun EventsScreen(invoices: List<Invoice>, onInvoices: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF2F9FF)),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("رویدادها", style = MaterialTheme.typography.titleMedium,
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
            items(invoices, key = { it.id }) { invoice ->
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
