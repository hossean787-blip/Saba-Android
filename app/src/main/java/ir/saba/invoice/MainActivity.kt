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
import androidx.room.withTransaction
import ir.saba.invoice.auth.RegistrationScreen
import ir.saba.invoice.auth.OwnerLoginScreen
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
        // Allow screenshots of ordinary app pages for support and bug reporting.
        // Keep authentication screens protected while passwords may be visible.
        val auth = androidx.lifecycle.ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ir.saba.invoice.auth.OwnerAuthViewModel(ir.saba.invoice.auth.AuthGatewayClient(
                    "https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/saba-auth-gateway"
                )) as T
        })[ir.saba.invoice.auth.OwnerAuthViewModel::class.java]
        setContent {
            androidx.compose.runtime.SideEffect {
                if (auth.session == null) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            ir.saba.invoice.auth.AuthTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BackHandler(enabled = auth.session == null && auth.page == "register") {
                        auth.navigate("login")
                    }
                    if (auth.session != null) {
                        SabaApp(onLogout = auth::logout)
                    } else if (auth.page == "register") {
                        RegistrationScreen(
                            onSubmit = auth::register,
                            onSignIn = { auth.navigate("login") },
                            submitting = auth.busy,
                            serverError = auth.message
                        )
                    } else {
                        OwnerLoginScreen(
                            onPasswordLogin = auth::login,
                            onSmsLogin = {},
                            onRegister = { auth.navigate("register") },
                            busy = auth.busy,
                            error = auth.message
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SabaApp(onLogout: () -> Unit) {
        var confirmLogout by remember { mutableStateOf(false) }
        if (confirmLogout) AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("خروج از حساب") },
            text = { Text("برای دسترسی دوباره باید با ایمیل و رمز عبور وارد شوید.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("خروج") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("انصراف") } }
        )
        val preferences = remember { getSharedPreferences("display", MODE_PRIVATE) }
        var page by rememberSaveable { mutableStateOf(Page.DASHBOARD) }
        var fontScale by rememberSaveable {
            val legacy = preferences.getFloat("font_scale", 1f)
            mutableFloatStateOf(if (legacy.isFinite()) legacy.coerceIn(0.85f, 1.35f) else 1f)
        }
        val savedFontScale by settings.fontScale.collectAsState(initial = fontScale)
        LaunchedEffect(savedFontScale) { fontScale = savedFontScale }
        var companies by remember { mutableStateOf(emptyList<Company>()) }
        var inactiveCompanyIds by remember { mutableStateOf(emptySet<Long>()) }
        var selectedCompanyId by rememberSaveable { mutableStateOf<Long?>(null) }
        var data by remember { mutableStateOf<CompanyData?>(null) }
        var loading by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var editing by rememberSaveable { mutableStateOf(false) }
        var loadError by remember { mutableStateOf("") }
        var companyMenu by remember { mutableStateOf(false) }
        var purchased by rememberSaveable { mutableStateOf("50") }
        var allocation by rememberSaveable { mutableStateOf("10") }
        var transferOperationId by rememberSaveable(selectedCompanyId, allocation) { mutableStateOf("") }
        var newInvoiceRequest by rememberSaveable { mutableIntStateOf(0) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        fun notify(message: String) { scope.launch { snackbar.showSnackbar(message, actionLabel = "بستن") } }

        suspend fun refreshCompany(id: Long) {
            val company = db.companyDao().byId(id) ?: error("شرکت پیدا نشد.")
            val snapshot = db.withTransaction { CompanyData(id, db.customerDao().forCompany(id), db.invoiceDao().forCompany(id),
                db.moadianSimulationDao().forCompany(id), db.quotaDao().wallet(company.purchaserId), db.quotaDao().companyQuota(id)) }
            if (selectedCompanyId == id) data = snapshot
        }
        suspend fun refresh() {
            val rows = db.companyDao().all()
            companies = rows
            inactiveCompanyIds = db.moadianDetailsDao().all().filter { it.inactive }.map { it.companyId }.toSet()
            val activeRows = rows.filter { it.id !in inactiveCompanyIds }
            val next = selectedCompanyId?.takeIf { id -> activeRows.any { it.id == id } } ?: activeRows.firstOrNull()?.id
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
        LaunchedEffect(page) {
            if (page != Page.INVOICES) newInvoiceRequest = 0
        }
        BackHandler(enabled = page != Page.DASHBOARD && !editing) { if (!busy) page = Page.DASHBOARD }

        MaterialTheme(shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), colorScheme = lightColorScheme(
            primary = Color(0xFF075F91), onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F1FF), onPrimaryContainer = Color(0xFF123C59),
            background = Color(0xFFF2F9FF), surface = Color.White,
            onBackground = Color(0xFF172C3D), onSurface = Color(0xFF172C3D)
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
                                        Text("سورا", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("مدیریت فاکتور و مؤدیان", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Box(Modifier.weight(1f)) {
                                        TextButton(enabled = !editing && !busy, onClick = { companyMenu = true }) {
                                            Text(companies.firstOrNull { it.id == selectedCompanyId }?.name ?: "انتخاب مودی", maxLines = 2)
                                        }
                                        DropdownMenu(companyMenu, { companyMenu = false }) {
                                            companies.filter { it.id !in inactiveCompanyIds }.forEach { company -> DropdownMenuItem(text = { Text(company.name) }, onClick = {
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
                        NavigationBar(containerColor = Color(0xFFE7F4FD), tonalElevation = 0.dp) {
                            listOf(Triple(Page.DASHBOARD, "خانه", Icons.Default.Home), Triple(Page.COMPANIES, "مودیان", Icons.Default.AccountCircle),
                                Triple(Page.INVOICES, "فاکتورها", Icons.AutoMirrored.Filled.List), Triple(Page.CUSTOMERS, "مشتریان", Icons.Default.Person),
                                Triple(Page.SETTINGS, "حساب کاربری", Icons.Default.AccountCircle)).forEach { (destination, title, icon) ->
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
                            Page.DASHBOARD -> HomeDashboard(
                                taxpayerName = company?.name,
                                remainingCharge = current?.quota?.available ?: 0L,
                                draftCount = current?.invoices?.count { it.status == "DRAFT" } ?: 0,
                                queueCount = current?.invoices?.count { it.status == "QUEUED" } ?: 0,
                                customerCount = current?.customers?.size ?: 0,
                                invoices = current?.invoices.orEmpty(),
                                onSelectTaxpayer = { companyMenu = true },
                                onNewInvoice = { if (!loading && !busy) { if (company == null) page = Page.COMPANIES else if ((current?.quota?.available ?: 0L) <= 0L) page = Page.QUOTA else { newInvoiceRequest++; page = Page.INVOICES } } },
                                onCustomers = { page = Page.CUSTOMERS },
                                onInvoices = { page = Page.INVOICES },
                                onCharge = { page = Page.QUOTA }
                            )
                            Page.COMPANIES -> {
                                if (companies.isEmpty() && !loading) {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("شروع کار با سورا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("برای ثبت مشتری و صدور صورتحساب، ابتدا اطلاعات شرکت یا مؤدی خود را ثبت کنید.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                MoadianScreen(db, companies, onChanged = { refresh() }, onSelect = { selectedCompanyId = it }, onNotice = ::notify, onEditing = { editing = it })
                            }
                            Page.CUSTOMERS -> if (current != null) key(current.companyId) {
                                CustomersScreen(db, current.companyId, current.customers, onChanged = { refresh() }, onNotice = ::notify, onEditing = { editing = it })
                            } else CompanyPlaceholder(loading)
                            Page.INVOICES -> if (current != null) key(current.companyId) {
                                InvoiceScreen(db, current.companyId, current.customers, current.invoices, onChanged = { refresh() }, onNotice = ::notify, onEditorActive = { editing = it }, availableCharge = current.quota?.available ?: 0, onNeedCharge = { page = Page.QUOTA }, newInvoiceRequest = newInvoiceRequest, onNewInvoiceRequestHandled = { newInvoiceRequest = 0 })
                            } else CompanyPlaceholder(loading)
                            Page.QUOTA -> if (current != null && company != null) {
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("انتقال شارژ مودی", style = MaterialTheme.typography.titleLarge)
                                    Text("شرکت: ${company.name}")
                                    Text("شارژ آزاد حساب مالک: ${current.wallet?.available ?: 0}")
                                    Text("شارژ باقی‌مانده مودی: ${current.quota?.available ?: 0}")
                                    OutlinedTextField(purchased, { purchased = it }, label = { Text("تعداد سهمیه آزمایشی") }, enabled = !busy && current.wallet == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Button(enabled = !busy && current.wallet == null, onClick = { act {
                                        QuotaRepository(db).initializeWallet(company.purchaserId, InvoiceInput.wholeNumber(purchased, "سهمیه")); refresh(); notify("کیف آزمایشی ایجاد شد.")
                                    } }) { Text("ایجاد کیف آزمایشی") }
                                    OutlinedTextField(allocation, { allocation = it }, label = { Text("تعداد شارژ برای انتقال") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Button(enabled = !busy && current.wallet != null, onClick = { act {
                                        if (transferOperationId.isEmpty()) transferOperationId = java.util.UUID.randomUUID().toString()
                                        QuotaRepository(db).transferFromMaster(company.purchaserId, company.id, InvoiceInput.wholeNumber(allocation, "تخصیص"), transferOperationId)
                                        refresh(); transferOperationId = ""; notify("شارژ با موفقیت به مودی منتقل شد.")
                                    } }) { Text("تأیید انتقال شارژ") }
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
                            Page.SETTINGS -> AccountScreen(
                                taxpayerCharge = current?.quota?.available ?: 0,
                                ownerCharge = current?.wallet?.available ?: 0,
                                fontScale = fontScale, busy = busy,
                                onCharge = { page = Page.QUOTA },
                                onFontScale = { fontScale = it },
                                onCommitFontScale = { act { settings.setFontScale(fontScale) } },
                                onResetFont = { fontScale = 1f; act { settings.setFontScale(1f) } },
                                onLogout = { confirmLogout = true }
                            )
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
                if (loading) "لطفاً کمی صبر کنید." else "از بخش مودیان، مودی خود را ثبت یا انتخاب کنید تا اطلاعات این قسمت نمایش داده شود.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
