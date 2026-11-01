package ir.saba.invoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val transferNavy = Color(0xFF07283F)
private val transferBlue = Color(0xFF116AA4)
private val transferMuted = Color(0xFF668195)
private val transferBackground = Color(0xFFF1F7FB)
private val transferOutline = Color(0xFFDCE9F1)

/**
 * Owner wallet → selected taxpayer only.
 * Balances and transfers MUST be sourced from the authoritative financial backend.
 * paymentServiceReady is retained for compatibility with existing navigation callers;
 * it never unlocks a transfer on its own.
 */
@Composable
fun CreditManagementScreen(
    taxpayerName: String,
    onBack: () -> Unit,
    paymentServiceReady: Boolean = false
) {
    var amount by remember(taxpayerName) { mutableStateOf("") }
    val parsedAmount = amount.toLongOrNull()
    val amountValid = parsedAmount != null && parsedAmount > 0L
    // No confirmed server-side balance/atomic transaction API is currently wired here.
    val balanceVerified = false
    val transferAvailable = paymentServiceReady && balanceVerified && amountValid
    var showUnavailable by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(transferBackground)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("انتقال اعتبار", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold, color = transferNavy)
                Text("از کیف پول مالک به مودی", color = transferMuted,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(17.dp), color = Color(0xFFDFEEFA)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null,
                    tint = transferBlue, modifier = Modifier.padding(13.dp).size(28.dp))
            }
        }

        Card(shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = transferNavy),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(transferNavy, Color(0xFF135578)))
                ).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null,
                        tint = Color(0xFFB9E4FC))
                    Spacer(Modifier.width(9.dp))
                    Text("کیف پول مالک حساب", color = Color(0xFFE4F4FF),
                        fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("—", color = Color.White, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("اعتبار", color = Color(0xFFB9E4FC), style = MaterialTheme.typography.bodyMedium)
                }
                Text("موجودی پس از تأیید سرور مالی نمایش داده می‌شود",
                    color = Color(0xFFB9D6E7), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اعتبار قابل انتقال", color = Color.White,
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("نامشخص", color = Color(0xFFB9E4FC), fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(shape = RoundedCornerShape(21.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, transferOutline)) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مودی دریافت‌کننده", color = transferMuted,
                    style = MaterialTheme.typography.labelLarge)
                Text(taxpayerName.ifBlank { "مودی انتخاب نشده" },
                    color = transferNavy, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                HorizontalDivider(color = transferOutline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("موجودی فعلی مودی", color = transferMuted,
                        modifier = Modifier.weight(1f))
                    Text("در انتظار استعلام", color = transferBlue,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Card(shape = RoundedCornerShape(21.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, transferOutline)) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("تعیین تعداد اعتبار", color = transferNavy,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { proposed ->
                        if (proposed.length <= 9 && proposed.all { it in '0'..'9' }) amount = proposed
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("تعداد اعتبار") },
                    placeholder = { Text("مثلاً ۱۰") },
                    suffix = { Text("اعتبار") },
                    isError = amount.isNotBlank() && !amountValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5L, 10L, 25L, 50L).forEach { preset ->
                        FilterChip(selected = parsedAmount == preset,
                            onClick = { amount = preset.toString() },
                            label = { Text("$preset") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp))
                    }
                }
                Text("هر ۱ اعتبار، امکان ایجاد ۱ صورتحساب جدید است.",
                    color = transferMuted, style = MaterialTheme.typography.bodySmall)
                if (amount.isNotBlank() && !amountValid) {
                    Text("تعداد اعتبار باید بیشتر از صفر باشد.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFF2F7FB)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("اعتبار پس از انتقال", color = transferMuted, modifier = Modifier.weight(1f))
                        Text(if (amountValid) "$parsedAmount اعتبار" else "—",
                            color = transferNavy, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { showUnavailable = true },
                    enabled = amountValid && taxpayerName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = transferBlue)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("بررسی امکان انتقال", fontWeight = FontWeight.Bold)
                }
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE6F1F9)) {
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = transferBlue,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("انتقال امن فقط پس از استعلام آنلاین موجودی و تأیید تراکنش توسط سرور مالی فعال می‌شود. در حال حاضر امکان انتقال وجود ندارد.",
                    color = transferNavy, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null,
                tint = transferBlue, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text("سوابق انتقال اعتبار", color = transferNavy,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, transferOutline)) {
            Text("سوابق پس از اتصال به سرور مالی نمایش داده می‌شود.",
                modifier = Modifier.fillMaxWidth().padding(19.dp),
                textAlign = TextAlign.Center, color = transferMuted,
                style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("بازگشت")
        }
        Spacer(Modifier.height(12.dp))
    }
    if (showUnavailable) {
        AlertDialog(
            onDismissRequest = { showUnavailable = false },
            title = { Text("انتقال اعتبار هنوز فعال نیست") },
            text = { Text("درخواست شما بررسی شد؛ سرویس مالی هنوز موجودی قابل انتقال را تأیید نکرده است. هیچ اعتباری کسر یا منتقل نشده است.") },
            confirmButton = { TextButton(onClick = { showUnavailable = false }) { Text("متوجه شدم") } }
        )
    }
}