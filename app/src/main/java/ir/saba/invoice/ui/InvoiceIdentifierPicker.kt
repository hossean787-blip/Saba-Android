package ir.saba.invoice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.identifiers.SuraIdentifier
import ir.saba.invoice.data.identifiers.SuraIdentifierStore
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import ir.saba.invoice.security.AccountScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The invoice editor can select an identifier saved by the CURRENT taxpayer, or query
 * an optional user-installed offline catalog. It does not install a catalog, download
 * records from the internet, or mutate another taxpayer's saved identifiers.
 */
@Composable
internal fun InvoiceIdentifierPicker(
    ownerId: String,
    companyId: Long,
    enabled: Boolean,
    onSelected: (SuraIdentifier) -> Unit
) {
    val context = LocalContext.current
    val account = remember(ownerId) { AccountScope.of(ownerId) }
    val bookmarks = remember(context, account) {
        SuraSavedIdentifierStore(context.applicationContext, account)
    }
    val catalog = remember(context) { SuraIdentifierStore(context.applicationContext) }
    DisposableEffect(bookmarks) { onDispose { bookmarks.close() } }

    var saved by remember(account.storageKey, companyId) {
        mutableStateOf(emptyList<SuraIdentifier>())
    }
    var mode by rememberSaveable { mutableStateOf("closed") }
    var filter by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<SuraIdentifier>()) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bookmarks, companyId) {
        try {
            saved = bookmarks.forTaxpayer(companyId)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            notice = "خواندن شناسه‌های ذخیره‌شده مؤدی ممکن نیست."
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "انتخاب سریع شناسه کالا و خدمات",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF12354B)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("invoiceSavedIdentifierButton"),
                    onClick = { mode = if (mode == "saved") "closed" else "saved"; notice = "" }
                ) { Text("شناسه‌های مؤدی", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("invoiceCatalogIdentifierButton"),
                    onClick = { mode = if (mode == "catalog") "closed" else "catalog"; notice = "" }
                ) { Text("جستجوی بانک", style = MaterialTheme.typography.labelSmall) }
            }
            if (mode != "closed") {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (mode == "saved") "فیلتر شناسه‌های مؤدی" else "نام یا شناسه کالا/خدمت") }
                )
                if (mode == "catalog") {
                    OutlinedButton(
                        enabled = enabled && !loading && filter.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                loading = true
                                notice = ""
                                matches = emptyList()
                                try {
                                    if (!catalog.installed()) {
                                        notice = "بانک آفلاین نصب نیست؛ از بخش شناسه‌ها بانک را بارگذاری کنید یا شناسه‌های مؤدی را انتخاب کنید."
                                    } else {
                                        matches = catalog.search(filter, null, null, limit = 50)
                                        if (matches.isEmpty()) notice = "شناسه‌ای پیدا نشد."
                                    }
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    notice = "جستجو در بانک انجام نشد؛ بانک نصب‌شده را بررسی کنید."
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    ) { Text(if (loading) "در حال جستجو…" else "جستجو در بانک نصب‌شده") }
                }
                val options = if (mode == "saved") {
                    val q = SuraIdentifierStore.normalize(filter)
                    saved.filter {
                        q.isBlank() || SuraIdentifierStore.normalize(it.id).contains(q) ||
                            SuraIdentifierStore.normalize(it.description).contains(q)
                    }.take(35)
                } else matches
                if (mode == "saved" && options.isEmpty()) {
                    Text(
                        "شناسه‌ای برای این مؤدی پیدا نشد. ابتدا در بخش «شناسه کالا و خدمات» ثبت دستی یا ورود اکسل انجام دهید.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (options.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 188.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        options.forEach { item ->
                            TextButton(
                                enabled = enabled && !loading,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("invoiceSelectIdentifier_" + item.id),
                                onClick = {
                                    onSelected(item)
                                    mode = "closed"
                                    notice = ""
                                }
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        item.description,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        item.id + " • مالیات: " + item.vat.ifBlank { "نیازمند بررسی" },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A5929))
            Text(
                "انتخاب شناسه، به‌معنای تأیید اعتبار رسمی یا نرخ مالیات نیست.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF58717C)
            )
        }
    }
}
