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

private val midnight = SuraPalette.Midnight
private val ocean = SuraPalette.Ocean
private val teal = SuraPalette.Teal
private val gold = SuraPalette.Gold
private val cool = SuraPalette.OnDark
private val textNavy = SuraPalette.Navy
private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR"))

/** Premium home screen; all numbers and activities come from persisted app data. */
@Composable
fun HomeDashboard(
    taxpayerName: String?,
    remainingCharge: Long?,
    draftCount: Int,
    queueCount: Int,
    customerCount: Int,
    invoices: List<Invoice>,
    onSelectTaxpayer: () -> Unit,
    onNewInvoice: () -> Unit,
    onTaxKeys: () -> Unit,
    onInvoices: () -> Unit,
    onCharge: () -> Unit,
    onIdentifiers: () -> Unit = {},
    taxpayerOptions: List<Pair<Long, String>> = emptyList(),
    onTaxpayerSelected: (Long) -> Unit = {},
    onDrafts: () -> Unit = onInvoices
) {
    var selectorOpen by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SuraPalette.Canvas),
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
                    listOf(midnight, ocean, SuraPalette.Ocean)
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
                                color = SuraPalette.Ocean) {
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
                    SuraPalette.Soft, SuraPalette.Teal, Modifier.weight(1f), onDrafts)
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
                QuickAction("ثبت فاکتور جدید", Icons.Default.Add, SuraPalette.Teal,
                    Color.White, gold, Modifier.weight(1f), onNewInvoice)
                QuickAction("ساخت کلید مالیاتی", Icons.Default.Person, Color(0xFFE9E9FA),
                    textNavy, Color(0xFF595DAD), Modifier.weight(1f), onTaxKeys)
            }
        }
        item {
            QuickAction("شناسه کالا و خدمات", Icons.AutoMirrored.Filled.List,
                Color(0xFFE1F5F3), textNavy, teal, Modifier.fillMaxWidth(), onIdentifiers)
        }
        item {
            SuraOwnPromotion(onNewInvoice = onNewInvoice)
        }

    }
}

/**
 * Compact SURA-owned promotion, placed directly after the product/service identifier action.
 * The banner is self-contained, offline, and sized for the home-screen ad slot.
 */
@Composable
private fun SuraOwnPromotion(onNewInvoice: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A59)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF111B58), Color(0xFF29207D), Color(0xFF542BD0))
                    )
                )
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).size(150.dp)
                    .offset(x = 42.dp, y = (-54).dp)
                    .clip(CircleShape)
                    .background(Color(0x332F8BFF))
            )
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 158.dp)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.55f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "SURA  |  سورا",
                        color = Color(0xFFEBCB90),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "صورتحساب الکترونیک",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "سریع، مطمئن، حرفه‌ای",
                        color = Color(0xFFE3DFFF),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("✓", color = Color(0xFF5DE0C1), fontWeight = FontWeight.Black)
                        Text("اتصال به سامانه مؤدیان", color = Color.White,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Button(
                        onClick = onNewInvoice,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEBCB90),
                            contentColor = Color(0xFF17264A)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Text("همین حالا استفاده کنید", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.88f).height(116.dp)
                            .offset(x = (-4).dp, y = (-5).dp),
                        shape = RoundedCornerShape(13.dp),
                        color = Color(0xFFF8F8FF),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("سورا", color = Color(0xFF3926A5),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold)
                            repeat(4) { index ->
                                Box(
                                    Modifier.fillMaxWidth(if (index == 3) 0.58f else 0.92f)
                                        .height(5.dp).clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFFD8D7F2))
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).size(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF4B35C8),
                        shadowElevation = 9.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumMetric(
    title: String, count: Long?, icon: ImageVector,
    background: Color, accent: Color, modifier: Modifier, onClick: () -> Unit
) {
    Card(modifier = modifier.height(76.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            Text(count?.let { numberFormat.format(it) } ?: "—", style = MaterialTheme.typography.titleMedium,
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
