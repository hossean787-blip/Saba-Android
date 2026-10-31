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
    onTaxKeys: () -> Unit,
    onInvoices: () -> Unit,
    onCharge: () -> Unit,
    taxpayerOptions: List<Pair<Long, String>> = emptyList(),
    onTaxpayerSelected: (Long) -> Unit = {}
) {
    var selectorOpen by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF2F9FF)),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (taxpayerOptions.isNotEmpty()) selectorOpen = true else onSelectTaxpayer()
                },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = midnight),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(
                    listOf(midnight, ocean, Color(0xFF0A334D))
                ))) {
                    Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(34.dp).clip(CircleShape)
                                .background(Color(0xFF1C5970)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null,
                                    tint = gold, modifier = Modifier.size(23.dp))
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
                                Text("تغییر مودی  ‹", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall, color = cool)
                            }
                        }
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
                QuickAction("ثبت فاکتور جدید", Icons.Default.Add, Color(0xFF0B607A),
                    Color.White, gold, Modifier.weight(1f), onNewInvoice)
                QuickAction("کلیدهای مالیاتی", Icons.Default.Person, Color(0xFFE9E9FA),
                    textNavy, Color(0xFF595DAD), Modifier.weight(1f), onTaxKeys)
            }
        }

    }
}

@Composable
private fun PremiumMetric(
    title: String, count: Long, icon: ImageVector,
    background: Color, accent: Color, modifier: Modifier, onClick: () -> Unit
) {
    Card(modifier = modifier.height(76.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            Text(numberFormat.format(count), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, color = accent)
            Text(title, style = MaterialTheme.typography.labelSmall, color = accent,
                maxLines = 2)
        }
    }
}

@Composable
private fun QuickAction(
    title: String, icon: ImageVector, background: Color, titleColor: Color,
    iconColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(65.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(7.dp))
            Text(title, color = titleColor, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
