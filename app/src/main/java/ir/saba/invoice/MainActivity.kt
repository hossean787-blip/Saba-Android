package ir.saba.invoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.saba.invoice.auth.RegistrationScreen
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceInput
import ir.saba.invoice.ui.*
import ir.saba.moadian.SubmissionPreparation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class Page { DASHBOARD, COMPANIES, CUSTOMERS, INVOICES, QUOTA, SIMULATION, SETTINGS }
private data class CompanyData(
    val companyId: Long,
    val customers: List<Customer>,
    val invoices: List<Invoice>,
    val simulations: List<MoadianSimulation>,
    val wallet: QuotaWallet?,
    val quota: CompanyQuota?
)

class MainActivity : ComponentActivity() {
    private val db by lazy { SabaDatabase.getInstance(applicationContext) }
    private val settings by lazy { SabaPreferences(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showRegistration by rememberSaveable { mutableStateOf(true) }
            if (showRegistration) {
                MaterialTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        RegistrationScreen(
                            onSubmit = {
                                // No SMS or account activation without a trusted backend.
                                // Never persist passwords or personal identifiers locally here.
                            },
                            onSignIn = { showRegistration = false },
                            serverError = "ثبت‌نام واقعی تا تأیید الگوی پیامک و تکمیل سرویس احراز هویت غیرفعال است. گزینه «ورود» فقط نسخه آزمایشی آفلاین را باز می‌کند؛ حساب مالک ایجاد نمی‌شود."
                        )
                    }
                }
            } else SabaApp()
        }
    }

    @Composable
    private fun SabaApp() {
        val preferences = remember { getSharedPreferences("display", MODE_PRIVATE) }
        var page by rememberSaveable { mutableStateOf(Page.DASHBOARD) }
        var fontScale by rememberSaveable {
            val legacy = preferences.getFloat("font_scale", 1f)
            mutableFloatStateOf(if (legacy.isFinite()) legacy.coerceIn(0.85f, 1.35f) else 1f)
        }
        val savedFontScale by settings.fontScale.collectAsState(initial = fontScale)
        LaunchedEffect(savedFontScale) { fontScale = savedFontScale }
        var companies by remember { mutableStateOf(emptyList<Company>()) }
        var selectedCompanyId by rememberSaveable { mutableStateOf<Long?>(null) }
        var data by remember { mutableStateOf<CompanyData?>(null) }
        var loading by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var editing by rememberSaveable { mutableStateOf(false) }
        var loadError by remember { mutableStateOf("") }
        var companyMenu by remember { mutableStateOf(false) }
        var purchased by rememberSaveable { mutableStateOf("50") }
        var allocation by rememberSaveable { mutableStateOf("10") }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        fun notify(message: String) { scope.launch { snackbar.showSnackbar(message, actionLabel = "بستن") } }

        suspend fun refreshCompany(id: Long) {
            val company = db.companyDao().byId(id) ?: error("شرکت پیدا نشد.")
            val snapshot = CompanyData(id, db.customerDao().forCompany(id), db.invoiceDao().forCompany(id),
                db.moadianSimulationDao().forCompany(id), db.quotaDao().wallet(company.purchaserId), db.quotaDao().companyQuota(id))
            if (selectedCompanyId == id) data = snapshot
        }
        suspend fun refresh() {
            val rows = db.companyDao().all()
            companies = rows
            val next = selectedCompanyId?.takeIf { id -> rows.any { it.id == id } } ?: rows.firstOrNull()?.id
            selectedCompanyId = next
            if (next != null) refreshCompany(next) else data = null
        }
        fun act(action: suspend () -> Unit) {
            if (busy) return
            busy = true
            scope.launch {
                try { action() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { notify(e.message ?: "عملیات انجام نشد؛ دوباره تلاش کنید.") }
                finally { busy = false }
            }
        }
        LaunchedEffect(Unit) {
            loading = true
            try { refresh(); loadError = "" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { loadError = "اطلاعات بارگذاری نشد: ${e.message.orEmpty()}" }
            finally { loading = false }
        }
        LaunchedEffect(selectedCompanyId, page) {
            val id = selectedCompanyId ?: return@LaunchedEffect
            loading = true
            try { refreshCompany(id); loadError = "" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { loadError = "اطلاعات شرکت بارگذاری نشد: ${e.message.orEmpty()}" }
            finally { loading = false }
        }
        BackHandler(enabled = page != Page.DASHBOARD && !editing) { if (!busy) page = Page.DASHBOARD }

        MaterialTheme(shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), colorScheme = lightColorScheme(
            primary = Color(0xFF6246AE), onPrimary = Color.White,
            primaryContainer = Color(0xFFECE5FB), onPrimaryContainer = Color(0xFF25184D),
            background = Color(0xFFF8F7FC), surface = Color.White,
            onBackground = Color(0xFF1D1834), onSurface = Color(0xFF1D1834)
        )) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
                Scaffold(
                    modifier = Modifier.imePadding(),
                    topBar = {
                        Surface(shadowElevation = 2.dp) {
                            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text("صبا", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("مدیریت فاکتور و مؤدیان", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Box(Modifier.weight(1f)) {
                                        TextButton(enabled = !editing && !busy, onClick = { companyMenu = true }) {
                                            Text(companies.firstOrNull { it.id == selectedCompanyId }?.name ?: "انتخاب شرکت", maxLines = 2)
                                        }
                                        DropdownMenu(companyMenu, { companyMenu = false }) {
                                            companies.forEach { company -> DropdownMenuItem(text = { Text(company.name) }, onClick = {
                                                selectedCompanyId = company.id; companyMenu = false
                                            }) }
                                        }
                                    }
                                }
                                if (editing) Text("برای تغییر بخش یا شرکت، ابتدا فرم را ذخیره یا ببندید.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFFF1ECF9), tonalElevation = 0.dp) {
                            listOf(Triple(Page.DASHBOARD, "خانه", Icons.Default.Home), Triple(Page.COMPANIES, "شرکت‌ها", Icons.Default.AccountCircle),
                                Triple(Page.INVOICES, "فاکتورها", Icons.AutoMirrored.Filled.List), Triple(Page.CUSTOMERS, "مشتریان", Icons.Default.Person),
                                Triple(Page.SETTINGS, "تنظیمات", Icons.Default.Settings)).forEach { (destination, title, icon) ->
                                NavigationBarItem(selected = page == destination, enabled = !editing && !busy,
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                    onClick = { page = destination }, icon = { Icon(icon, title) }, label = { Text(title, maxLines = 1) })
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (loadError.isNotEmpty()) {
                            Text(loadError, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(enabled = !busy, onClick = { act { refresh(); loadError = "" } }) { Text("تلاش مجدد") }
                        }
                        val current = data?.takeIf { it.companyId == selectedCompanyId }
                        val company = companies.firstOrNull { it.id == selectedCompanyId }
                        when (page) {
                            Page.DASHBOARD -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                item {
                                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("مدیریت هوشمند صورتحساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(company?.name ?: "ابتدا شرکت خود را ثبت کنید.")
                                            Button(onClick = { page = if (company == null) Page.COMPANIES else Page.INVOICES }) { Text(if (company == null) "ثبت شرکت" else "ورود به فاکتورها") }
                                        }
                                    }
                                }
                                item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.weight(1f)) { DashboardCard("شرکت‌ها", companies.size.toString()) { page = Page.COMPANIES } }
                                        Box(Modifier.weight(1f)) { DashboardCard("مشتریان", (current?.customers?.size ?: 0).toString()) { page = Page.CUSTOMERS } }
                                    }
                                }
                                item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.weight(1f)) { DashboardCard("پیش‌نویس‌ها", (current?.invoices?.count { it.status == "DRAFT" } ?: 0).toString()) { page = Page.INVOICES } }
                                        Box(Modifier.weight(1f)) { DashboardCard("نهایی محلی", (current?.invoices?.count { it.status == "FINALIZED" } ?: 0).toString()) { page = Page.INVOICES } }
                                    }
                                }
                                item { OutlinedButton(onClick = { page = Page.QUOTA }) { Text("مدیریت سهمیه") } }
                                item { OutlinedButton(onClick = { page = Page.SIMULATION }) { Text("آزمایش آفلاین مؤدیان") } }
                                item { Text("ارسال واقعی مؤدیان فعال نیست؛ نهایی‌شدن سند به معنی ارسال نیست.") }
                            }
                            Page.COMPANIES -> {
                                if (companies.isEmpty() && !loading) {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("شروع کار با صبا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("برای ثبت مشتری و صدور صورتحساب، ابتدا اطلاعات شرکت یا مؤدی خود را ثبت کنید.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                CompaniesScreen(db, companies, onChanged = { refresh() }, onSelect = { selectedCompanyId = it; page = Page.CUSTOMERS }, onNotice = ::notify, onEditing = { editing = it })
                            }
                            Page.CUSTOMERS -> if (current != null) key(current.companyId) {
                                CustomersScreen(db, current.companyId, current.customers, onChanged = { refresh() }, onNotice = ::notify, onEditing = { editing = it })
                            } else CompanyPlaceholder(loading)
                            Page.INVOICES -> if (current != null) key(current.companyId) {
                                InvoiceScreen(db, current.companyId, current.customers, current.invoices, onChanged = { refresh() }, onNotice = ::notify, onEditorActive = { editing = it })
                            } else CompanyPlaceholder(loading)
                            Page.QUOTA -> if (current != null && company != null) {
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("مدیریت سهمیه محلی", style = MaterialTheme.typography.titleLarge)
                                    Text("شرکت: ${company.name}")
                                    Text("آزاد حساب اصلی: ${current.wallet?.available ?: 0}")
                                    Text("باقی‌مانده شرکت: ${current.quota?.available ?: 0}")
                                    OutlinedTextField(purchased, { purchased = it }, label = { Text("تعداد سهمیه آزمایشی") }, enabled = !busy && current.wallet == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Button(enabled = !busy && current.wallet == null, onClick = { act {
                                        QuotaRepository(db).initializeWallet(company.purchaserId, InvoiceInput.wholeNumber(purchased, "سهمیه")); refresh(); notify("کیف آزمایشی ایجاد شد.")
                                    } }) { Text("ایجاد کیف آزمایشی") }
                                    OutlinedTextField(allocation, { allocation = it }, label = { Text("تعداد تخصیص به این شرکت") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Button(enabled = !busy && current.wallet != null, onClick = { act {
                                        QuotaRepository(db).transferFromMaster(company.purchaserId, company.id, InvoiceInput.wholeNumber(allocation, "تخصیص"), java.util.UUID.randomUUID().toString())
                                        refresh(); notify("سهمیه تخصیص یافت.")
                                    } }) { Text("تخصیص سهمیه") }
                                    Text("این سهمیه فقط محلی و آزمایشی است؛ خرید و مجوز تجاری فعال نیست.")
                                }
                            } else CompanyPlaceholder(loading)
                            Page.SIMULATION -> if (current != null) LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                item { Text("آزمایش آفلاین مؤدیان", style = MaterialTheme.typography.titleLarge); Text("هیچ اطلاعاتی به سامانه واقعی ارسال نمی‌شود.") }
                                val eligible = current.invoices.filter { it.status == "FINALIZED" }
                                if (eligible.isEmpty()) item { Text("ابتدا یک فاکتور را نهایی کنید.") }
                                items(eligible, key = { it.id }) { invoice ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("فاکتور ${invoice.invoiceNumber}")
                                            Text(rial(invoice.totalAmountRial))
                                            val recorded = current.simulations.any { it.invoiceId == invoice.id }
                                            Text(if (recorded) "آزمایش محلی ثبت شده؛ ارسال نشده" else "بدون آزمایش محلی")
                                            Button(enabled = !busy && !recorded, onClick = { act {
                                                val payload = "${invoice.id}|${invoice.companyId}|${invoice.invoiceNumber}|${invoice.totalAmountRial}|${invoice.taxAmountRial}".toByteArray(Charsets.UTF_8)
                                                val prepared = SubmissionPreparation.prepare(payload)
                                                db.moadianSimulationDao().insert(MoadianSimulation(companyId = current.companyId, invoiceId = invoice.id, requestId = prepared.requestId, payloadHash = prepared.payloadSha256, createdAtMillis = System.currentTimeMillis()))
                                                refresh(); notify("آزمایش آفلاین ثبت شد؛ ارسال واقعی انجام نشد.")
                                            } }) { Text("ثبت آزمایش محلی") }
                                        }
                                    }
                                }
                            } else CompanyPlaceholder(loading)
                            Page.SETTINGS -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("تنظیمات نمایش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("اندازه متن برنامه: ${(fontScale * 100).toInt()}٪؛ اندازه متن سیستم نیز رعایت می‌شود.")
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("اندازه نوشته‌ها", style = MaterialTheme.typography.titleSmall)
                                        Slider(value = fontScale, onValueChange = { fontScale = it }, valueRange = 0.85f..1.35f,
                                            onValueChangeFinished = { act { settings.setFontScale(fontScale) } }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                OutlinedButton(onClick = { fontScale = 1f; act { settings.setFontScale(1f) } }) { Text("اندازه پیش‌فرض") }
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("وضعیت سامانه مؤدیان", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("اتصال غیرفعال است؛ هیچ صورتحسابی ارسال نمی‌شود.")
                                        Text("ثبت محلی فاکتور از ارسال مالیاتی مستقل است.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(title: String, value: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CompanyPlaceholder(loading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (loading) "در حال آماده‌سازی اطلاعات" else "هنوز شرکتی انتخاب نشده است",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (loading) "لطفاً کمی صبر کنید." else "از بخش شرکت‌ها، مؤدی خود را ثبت یا انتخاب کنید تا اطلاعات این قسمت نمایش داده شود.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
