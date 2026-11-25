package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
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
    var tab by remember { mutableStateOf("starter") }
    var found by remember { mutableStateOf(emptyList<SuraIdentifier>()) }
    var saved by remember(bookmarks, taxpayerId) { mutableStateOf(emptyList<SuraIdentifier>()) }
    var starterEntries by remember { mutableStateOf(emptyList<SuraIdentifier>()) }
    var starterQuery by remember { mutableStateOf("") }
    var catalogCount by remember { mutableStateOf<Long?>(null) }
    var starterCount by remember { mutableStateOf(0) }
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
            starterCount = catalog.starterCount()
            starterEntries = catalog.starterEntries()
        } catch (e: Exception) {
            catalogCount = runCatching { catalog.count() }.getOrDefault(0L)
            message = SuraErrors.transport(e) ?: e.message ?: "راه‌اندازی بانک داخلی ناموفق بود؛ بانک قبلی حفظ شد. [CATALOG-INIT]"
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
                message = SuraErrors.transport(e) ?: e.message ?: "بارگذاری بانک اطلاعاتی ناموفق بود؛ بانک قبلی حفظ شد. [CATALOG-IMPORT]"
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

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("شناسه کالا و خدمات", color = catalogNavy,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("بازگشت", color=catalogTeal) }
            }
            Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().testTag("identifierTaxpayerCard")) {
                Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(catalogNavy,Color(0xFF17616D))))
                    .padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Surface(shape=RoundedCornerShape(12.dp),color=Color.White.copy(alpha=.13f)) {
                        Icon(Icons.Default.AccountCircle,contentDescription=null,tint=Color(0xFFE7CB92),
                            modifier=Modifier.padding(9.dp).size(24.dp))
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text("پرونده مؤدی",color=Color(0xFFBADDE4),style=MaterialTheme.typography.labelSmall)
                        Text(taxpayerName ?: "مؤدی انتخاب نشده",color=Color.White,fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(taxpayerId==null) TextButton(onClick=onSelectTaxpayer) {
                            Text("انتخاب مؤدی",color=Color(0xFFE7CB92))
                        }
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(catalogCount?.toString() ?: "…",color=Color.White,fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.titleMedium)
                        Text("شناسه بانک",color=Color(0xFFDDEDF1),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().testTag("identifierTabs"),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                CatalogTab("بانک آماده",starterCount.toString(),Icons.AutoMirrored.Filled.List,tab=="starter",
                    {tab="starter"},Modifier.weight(1f).testTag("identifierTabStarter"))
                CatalogTab("شناسه مؤدی",saved.size.toString(),Icons.Default.AccountCircle,tab=="saved",
                    {tab="saved"},Modifier.weight(1f).testTag("identifierTabSaved"))
                CatalogTab("جستجوی بانک",null,Icons.Default.Search,tab=="search",
                    {tab="search"},Modifier.weight(1f).testTag("identifierTabSearch"))
                CatalogTab("ثبت / اکسل",null,Icons.Default.Add,tab=="entry",
                    {tab="entry"},Modifier.weight(1f).testTag("identifierTabEntry"))
            }
        }
    }
    val pageState=rememberLazyListState()
    LaunchedEffect(tab) { pageState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().background(catalogIvory).padding(horizontal=12.dp)) {
        if (tab == "entry") {
            SuraIdentifierEntryPanel(
                bookmarks = bookmarks,
                taxpayerId = taxpayerId,
                onSelectTaxpayer = onSelectTaxpayer,
                onSaved = {
                    scope.launch {
                        saved = taxpayerId?.let { bookmarks.forTaxpayer(it) }.orEmpty()
                    }
                },
                modifier = Modifier.weight(1f), header = header
            )
        } else {
        val savedIds = saved.map { it.id }.toSet()
        val starterById = starterEntries.associateBy { it.id }
        val entries = when (tab) {
            "search" -> found
            "starter" -> {
                val q = SuraIdentifierStore.normalize(starterQuery)
                starterEntries.filter { q.isBlank() || it.id.contains(q) ||
                    SuraIdentifierStore.normalize(it.description).contains(q) }
            }
            else -> saved.map { row ->
                if (row.vat.isBlank() && row.source == "Mohsensoft (pending)")
                    starterById[row.id]?.takeIf { it.source == row.source }?.let { row.copy(vat = it.vat) } ?: row
                else row
            }
        }
        LazyColumn(state=pageState, modifier=Modifier.fillMaxWidth().weight(1f).testTag("identifierPageScroll"),
            contentPadding=PaddingValues(top=8.dp,bottom=22.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item(key="header") { header() }
            item(key="controls") {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if (tab == "search") {
            OutlinedButton(enabled=!busy,onClick={
                scope.launch {
                    busy=true
                    try{
                        val v=catalog.updateStarter()
                        catalogCount=catalog.count();starterCount=catalog.starterCount()
                        starterEntries=catalog.starterEntries()
                        found=catalog.search(query,category,specificity)
                        message=if(v==null)"هنوز بانک منتخب تأییدشده منتشر نشده است." else "بانک منتخب نسخه $v آماده است."
                    }catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(e:Exception){message=SuraErrors.describe(e,"به‌روزرسانی تأیید نشد. [CATALOG-UPDATE]")+" بانک قبلی حفظ شد."}
                    finally{busy=false}
                }
            },modifier=Modifier.fillMaxWidth()){Text("به‌روزرسانی شناسه‌های منتخب")}
            CatalogSearchField(query,{query=it},"نام کالا، خدمت یا شناسه")
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
                    enabled = !busy && catalogCount != null && catalogCount!! > 0 && (query.isNotBlank() || starterCount>0),
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
        } else if (tab == "starter") {
            CatalogSearchField(starterQuery,{starterQuery=it},"جستجوی شناسه‌های آماده")
            Text(if (taxpayerId == null) "برای افزودن شناسه ابتدا مؤدی را انتخاب کنید." else
                "با تیک هر شناسه، به فهرست مؤدی فعلی اضافه می‌شود.", style = MaterialTheme.typography.bodySmall)
        } else if (taxpayerId == null) {
            Text("برای مشاهده شناسه‌های ذخیره‌شده ابتدا مؤدی را انتخاب کنید.",
                color = catalogNavy, style = MaterialTheme.typography.bodyMedium)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, color = catalogTeal,
            style = MaterialTheme.typography.bodySmall)
        Text(
            when(tab) { "search" -> "نتایج جستجو"; "starter" -> "تمام شناسه‌های آماده ($starterCount)"; else -> "شناسه‌های مؤدی انتخاب‌شده" },
            color = catalogNavy, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        HorizontalDivider(color = Color(0xFFDBE7EC))
        if (entries.isEmpty()) Text(
            if(tab=="saved" && taxpayerId!=null) "برای این مؤدی هنوز شناسه‌ای ذخیره نشده است."
            else if(tab=="starter" && starterQuery.isNotBlank()) "شناسه مطابق جستجو پیدا نشد."
            else if(tab=="search") "برای مشاهده نتایج، جستجو را انجام دهید." else "",
            color=catalogNavy,style=MaterialTheme.typography.bodySmall)
                }
            }
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.id, color = catalogTeal, fontWeight = FontWeight.Bold)
                            if (tab == "starter" || tab == "search") Checkbox(
                                checked = entry.id in savedIds,
                                enabled = !busy && taxpayerId != null,
                                modifier = Modifier.testTag("saveIdentifier_" + entry.id),
                                onCheckedChange = { checked ->
                                    val selected = taxpayerId ?: return@Checkbox
                                    scope.launch {
                                        busy = true
                                        try {
                                            if (checked) bookmarks.importBatch(selected, listOf(entry))
                                            else bookmarks.remove(selected, entry.id)
                                            saved = bookmarks.forTaxpayer(selected)
                                            message = if (checked) "شناسه به فهرست مؤدی اضافه شد." else "شناسه از فهرست مؤدی حذف شد."
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                        catch (_: Exception) { message = "تغییر فهرست مؤدی انجام نشد." }
                                        finally { busy = false }
                                    }
                                }
                            )
                        }
                        Text(entry.description, color = catalogNavy,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(entry.kind + "  •  نرخ ثبت‌شده: " +
                                if (entry.vat.isBlank()) "نامشخص" else entry.vat + "٪",
                            color = Color(0xFF617B8A), style = MaterialTheme.typography.bodySmall)
                        if (entry.source == "Mohsensoft (pending)") Text(
                            "نرخ گزارش‌شده؛ اعتبار رسمی هنوز تأیید نشده است.",
                            color = Color(0xFFB05D38), style = MaterialTheme.typography.labelSmall
                        )
                        if (entry.expirationDate.isNotBlank()) Text(
                            "تاریخ انقضای درج‌شده: " + entry.expirationDate,
                            color = Color(0xFFB05D38), style = MaterialTheme.typography.labelSmall
                        )
                        if (tab == "saved") {
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
            item(key="footer") {
                Text("بانک آفلاین و داده‌های دستی باید پیش از ارسال صورتحساب با مرجع رسمی تطبیق داده شوند.",
                    color = Color(0xFF657D89), style = MaterialTheme.typography.labelSmall)
            }
        }
        }
    }
}

@Composable
private fun CatalogTab(label:String,count:String?,image:ImageVector,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier) {
    Surface(modifier=modifier.heightIn(min=76.dp).selectable(selected=selected,onClick=onClick,role=Role.Tab),
        shape=RoundedCornerShape(13.dp),
        color=if(selected) catalogNavy else Color.White,
        border=androidx.compose.foundation.BorderStroke(1.dp,if(selected) catalogNavy else Color(0xFFDCE8ED))) {
        Column(Modifier.padding(horizontal=3.dp,vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(3.dp)) {
            val ink=if(selected) Color.White else catalogNavy
            Icon(image,contentDescription=null,tint=if(selected) Color(0xFFE7CB92) else catalogTeal,
                modifier=Modifier.size(22.dp))
            Text(label,color=ink,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold,
                textAlign=TextAlign.Center,maxLines=2)
            if(count!=null) Text(count,color=if(selected) Color(0xFFC4E8E9) else catalogTeal,
                style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CatalogSearchField(value:String,onChange:(String)->Unit,label:String) {
    OutlinedTextField(value=value,onValueChange=onChange,modifier=Modifier.fillMaxWidth().testTag("identifierSearch"),
        singleLine=true,shape=RoundedCornerShape(18.dp),
        placeholder={ Text(label,style=MaterialTheme.typography.bodyMedium) },
        leadingIcon={ Icon(Icons.Default.Search,contentDescription=null,tint=catalogTeal) },
        trailingIcon={ if(value.isNotEmpty()) IconButton(onClick={onChange("")}) {
            Icon(Icons.Default.Close,contentDescription="پاک کردن جستجو")
        } },
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=catalogTeal,unfocusedBorderColor=Color(0xFFCEDFE5),
            focusedContainerColor=Color.White,unfocusedContainerColor=Color.White,cursorColor=catalogTeal))
}

