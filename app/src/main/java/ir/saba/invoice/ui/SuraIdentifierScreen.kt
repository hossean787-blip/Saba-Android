package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.identifiers.SuraBundledCatalogInstaller
import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraIdentifierStore
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.launch

private val catalogNavy = Color(0xFF12354B)
private val catalogTeal = Color(0xFF087D8A)
private val catalogIvory = Color(0xFFF4F9FC)

/** A standalone page; financial invoice state and its approved PDF remain unchanged. */
@Composable
fun SuraIdentifierScreen(
    account: AccountScope,
    taxpayerId: Long?,
    taxpayerName: String?,
    onBack: () -> Unit,
    onSelectTaxpayer: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val catalog = remember(context) { SuraIdentifierStore(context.applicationContext) }
    val bookmarks = remember(context, account) {
        SuraSavedIdentifierStore(context.applicationContext, account)
    }
    DisposableEffect(bookmarks) { onDispose { bookmarks.close() } }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var specificity by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("search") }
    var found by remember { mutableStateOf(emptyList<SuraIdentifier>()) }
    var saved by remember { mutableStateOf(emptyList<SuraIdentifier>()) }
    var catalogCount by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val hasBundledFullCatalog = remember(context) {
        runCatching { context.assets.list("catalog")?.contains("manifest.json") == true }.getOrDefault(false)
    }

    // When the complete catalog is packaged into the signed APK, unpack it offline
    // on the first visit. No manual ZIP selection or network connection is required.
    LaunchedEffect(catalog) {
        busy = hasBundledFullCatalog
        try {
            if (hasBundledFullCatalog) {
                message = "در حال آماده‌سازی بانک کامل همراه برنامه..."
                SuraBundledCatalogInstaller(context.applicationContext).installIfNeeded { progress ->
                    message = "استخراج و بررسی بانک کامل: $progress٪"
                }
                message = "بانک کامل داخل برنامه فعال است."
            }
            catalogCount = catalog.count()
        } catch (e: Exception) {
            catalogCount = runCatching { catalog.count() }.getOrDefault(0L)
            message = e.message ?: "راه‌اندازی بانک داخلی ناموفق بود؛ بانک قبلی حفظ شد."
        } finally {
            busy = false
        }
    }
    LaunchedEffect(bookmarks, taxpayerId) {
        saved = if (taxpayerId != null && taxpayerId > 0L)
            runCatching { bookmarks.forTaxpayer(taxpayerId) }.getOrDefault(emptyList())
        else emptyList()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = "در حال کنترل و نصب بانک اطلاعاتی..."
            try {
                val total = catalog.importVerified(uri)
                catalogCount = total
                found = emptyList()
                message = "بانک معتبر با " + total + " شناسه نصب شد."
            } catch (e: Exception) {
                message = e.message ?: "بارگذاری بانک اطلاعاتی ناموفق بود؛ بانک قبلی حفظ شد."
            } finally { busy = false }
        }
    }

    fun search() {
        scope.launch {
            busy = true
            try {
                found = catalog.search(query, category, specificity)
                message = if (found.isEmpty()) "شناسه‌ای پیدا نشد." else "حداکثر ۵۰ نتیجه اول نمایش داده شد."
            } catch (e: Exception) {
                found = emptyList()
                message = "جستجو ناموفق بود؛ بانک اطلاعاتی را بررسی کنید."
            } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().background(catalogIvory).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("شناسه کالا و خدمات", color = catalogNavy,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("بازگشت به خانه") }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = catalogNavy),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("پرونده مؤدی", color = Color(0xFFBADDE4),
                    style = MaterialTheme.typography.labelSmall)
                Text(taxpayerName ?: "مؤدی انتخاب نشده", color = Color.White,
                    fontWeight = FontWeight.Bold)
                if (taxpayerId == null) TextButton(onClick = onSelectTaxpayer) {
                    Text("انتخاب مؤدی برای ذخیره شناسه", color = Color(0xFFE7CB92))
                }
                Text(
                    if (catalogCount == null) "در حال بررسی بانک اطلاعاتی..."
                    else if (catalogCount == 0L) "بانک اطلاعاتی وارد نشده است."
                    else "بانک نصب‌شده: " + catalogCount + " شناسه",
                    color = Color(0xFFDDEDF1),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "search", onClick = { tab = "search" },
                label = { Text("جستجوی شناسه") })
            FilterChip(selected = tab == "saved", onClick = { tab = "saved" },
                label = { Text("ذخیره‌شده‌ها (" + saved.size + ")") })
        }
        if (tab == "search") {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("نام کالا، خدمت یا شناسه") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(selected = category == "کالا", onClick = {
                    category = if (category == "کالا") null else "کالا"
                }, label = { Text("کالا") })
                FilterChip(selected = category == "خدمت", onClick = {
                    category = if (category == "خدمت") null else "خدمت"
                }, label = { Text("خدمات") })
                FilterChip(selected = specificity == "عمومی", onClick = {
                    specificity = if (specificity == "عمومی") null else "عمومی"
                }, label = { Text("عمومی") })
                FilterChip(selected = specificity == "اختصاصی", onClick = {
                    specificity = if (specificity == "اختصاصی") null else "اختصاصی"
                }, label = { Text("اختصاصی") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { search() },
                    enabled = !busy && catalogCount != null && catalogCount!! > 0 && query.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = catalogTeal)
                ) { Text("جستجو") }
                if (!hasBundledFullCatalog || catalogCount != SuraBundledCatalogInstaller.FULL_RECORD_COUNT) {
                    OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) {
                        Text("ورود / به‌روزرسانی بانک")
                    }
                }
            }
            if (catalogCount == 0L) Text(
                if (hasBundledFullCatalog) "بانک کامل همراه برنامه است؛ راه‌اندازی آن به فضای ذخیره‌سازی داخلی کافی نیاز دارد."
                else "برای فعال‌شدن جستجوی کامل، فایل ZIP بانک سورا (نسخه سبک یا کامل) را انتخاب کنید. " +
                    "برای نسخه کامل به فضای ذخیره‌سازی کافی نیاز دارید.",
                style = MaterialTheme.typography.bodySmall, color = catalogNavy
            )
        } else if (taxpayerId == null) {
            Text("برای مشاهده شناسه‌های ذخیره‌شده ابتدا مؤدی را انتخاب کنید.",
                color = catalogNavy, style = MaterialTheme.typography.bodyMedium)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, color = catalogTeal,
            style = MaterialTheme.typography.bodySmall)
        Text(
            if (tab == "search") "نتایج جستجو" else "شناسه‌های مؤدی انتخاب‌شده",
            color = catalogNavy, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        HorizontalDivider(color = Color(0xFFDBE7EC))
        val entries = if (tab == "search") found else saved
        if (entries.isEmpty() && tab == "saved" && taxpayerId != null) {
            Text("برای این مؤدی هنوز شناسه‌ای ذخیره نشده است.", color = catalogNavy)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(entry.id, color = catalogTeal, fontWeight = FontWeight.Bold)
                        Text(entry.description, color = catalogNavy,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(entry.kind + "  •  نرخ ثبت‌شده: " + entry.vat + "٪",
                            color = Color(0xFF617B8A), style = MaterialTheme.typography.bodySmall)
                        if (entry.expirationDate.isNotBlank()) Text(
                            "تاریخ انقضای درج‌شده: " + entry.expirationDate,
                            color = Color(0xFFB05D38), style = MaterialTheme.typography.labelSmall
                        )
                        if (tab == "search") {
                            Button(
                                enabled = !busy && taxpayerId != null,
                                onClick = {
                                    val selected = taxpayerId ?: return@Button
                                    scope.launch {
                                        busy = true
                                        try {
                                            bookmarks.save(selected, entry)
                                            saved = bookmarks.forTaxpayer(selected)
                                            message = "شناسه " + entry.id + " برای همین مؤدی ذخیره شد."
                                        } catch (e: Exception) {
                                            message = "ثبت شناسه ناموفق بود."
                                        } finally { busy = false }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = catalogTeal)
                            ) { Text("ذخیره برای مؤدی") }
                        } else {
                            TextButton(enabled = !busy, onClick = {
                                val selected = taxpayerId ?: return@TextButton
                                scope.launch {
                                    busy = true
                                    try {
                                        bookmarks.remove(selected, entry.id)
                                        saved = bookmarks.forTaxpayer(selected)
                                        message = "شناسه از فهرست مؤدی حذف شد."
                                    } catch (e: Exception) { message = "حذف شناسه ناموفق بود." }
                                    finally { busy = false }
                                }
                            }) { Text("حذف از شناسه‌های ذخیره‌شده") }
                        }
                    }
                }
            }
        }
        Text(
            "این بانک آفلاین است؛ پیش از ارسال صورتحساب، اعتبار شناسه و نرخ مالیات را با مرجع رسمی کنترل کنید.",
            color = Color(0xFF657D89), style = MaterialTheme.typography.labelSmall
        )
    }
}
