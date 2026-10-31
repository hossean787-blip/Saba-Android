package ir.saba.invoice

import android.os.Bundle
import android.os.Build
import android.os.CancellationSignal
import android.hardware.biometrics.BiometricPrompt
import javax.crypto.Cipher
import ir.saba.invoice.auth.BiometricLoginVault
import ir.saba.invoice.auth.StoredLoginCredentials
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.filled.Menu
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

private enum class Page { DASHBOARD, COMPANIES, TAX_KEYS, CUSTOMERS, INVOICES, QUOTA, SIMULATION, SETTINGS, APP_SETTINGS }
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
    private val biometricVault by lazy { BiometricLoginVault(applicationContext) }
    private var biometricEnabled by mutableStateOf(false)
    private var biometricBusy by mutableStateOf(false)
    private var biometricNotice by mutableStateOf<String?>(null)

    @android.annotation.TargetApi(Build.VERSION_CODES.P)
    private fun confirmBiometric(title: String, onSuccess: () -> Unit, onDismiss: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { onDismiss(); return }
        var finished = false
        fun finish(action: () -> Unit) { if (!finished) { finished = true; action() } }
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setSubtitle("اثر انگشت ثبت‌شده دستگاه را تأیید کنید.")
                .setNegativeButton("انصراف", mainExecutor) { _, _ -> finish(onDismiss) }
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        finish(onSuccess)
                    }
                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        finish(onDismiss)
                    }
                })
        } catch (_: Exception) { finish(onDismiss) }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.P)
    private fun showBiometricPrompt(
        cipher: Cipher, title: String,
        onSuccess: (Cipher) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onDismiss()
            return
        }
        var complete = false
        fun finish(action: () -> Unit) {
            if (!complete) { complete = true; action() }
        }
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setSubtitle("هویت خود را با اثر انگشت تأیید کنید.")
                .setNegativeButton("انصراف", mainExecutor) { _, _ -> finish(onDismiss) }
                .build()
            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val verified = result.cryptoObject?.cipher
                        if (verified == null) finish(onDismiss)
                        else finish { onSuccess(verified) }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        finish(onDismiss)
                    }
                }
            )
        } catch (_: Exception) { finish(onDismiss) }
    }

    /**
     * User-requested screenshots are allowed while the app is foregrounded,
     * including on login/registration for troubleshooting. Hide app content in
     * the Android recent-apps snapshot while the Activity is backgrounded.
     * Password fields remain masked by default and never enter saved state.
     */
    override fun onResume() {
        super.onResume()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onPause() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = androidx.lifecycle.ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ir.saba.invoice.auth.OwnerAuthViewModel(ir.saba.invoice.auth.AuthGatewayClient(
                    "https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/saba-auth-gateway"
                )) as T
        })[ir.saba.invoice.auth.OwnerAuthViewModel::class.java]
        biometricEnabled = biometricVault.isEnabled()

        fun unlockWithBiometric() {
            if (auth.busy || biometricBusy || !biometricEnabled) return
            biometricBusy = true
            confirmBiometric("ورود به سورا",
                onSuccess = {
                    biometricBusy = false
                    try {
                        val credentials = biometricVault.unlockAfterAuthentication()
                        auth.login(credentials.email, credentials.password, fromBiometric = true) {
                            biometricVault.clear()
                            biometricEnabled = false
                        }
                    } catch (_: Exception) {
                        biometricVault.clear()
                        biometricEnabled = false
                        biometricNotice = "کلید ورود امن معتبر نیست؛ یک‌بار با رمز ثابت وارد شوید و اثر انگشت را دوباره فعال کنید."
                    }
                },
                onDismiss = { biometricBusy = false }
            )
        }

        fun enableBiometricLogin(credentials: StoredLoginCredentials) {
            if (biometricBusy || !biometricVault.canOffer()) {
                auth.discardBiometricEnrollment()
                biometricNotice = "اثر انگشت قوی در تنظیمات گوشی در دسترس نیست."
                return
            }
            biometricBusy = true
            confirmBiometric("فعال‌سازی ورود با اثر انگشت",
                onSuccess = {
                    biometricBusy = false
                    try {
                        biometricVault.enrollAfterAuthentication(credentials)
                        biometricEnabled = biometricVault.isEnabled()
                        biometricNotice = if (biometricEnabled)
                            "ورود با اثر انگشت فعال شد."
                        else "فعال‌سازی اثر انگشت انجام نشد."
                    } catch (_: Exception) {
                        biometricVault.clear()
                        biometricEnabled = false
                        biometricNotice = "رمزنگاری گوشی با این روش سازگار نیست؛ ورود با رمز ثابت همچنان فعال است."
                    } finally { auth.discardBiometricEnrollment() }
                },
                onDismiss = { biometricBusy = false; auth.discardBiometricEnrollment() }
            )
        }

        setContent {
            // Always play once per cold Activity creation, not after a rotation.
            var showStartupMotion by remember { mutableStateOf(savedInstanceState == null) }
            LaunchedEffect(auth.biometricEnrollment, auth.session, biometricEnabled) {
                if (auth.session != null && auth.biometricEnrollment != null &&
                    (!biometricVault.canOffer() || biometricEnabled)
                ) auth.discardBiometricEnrollment()
            }
            ir.saba.invoice.auth.AuthTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    BackHandler(enabled = !showStartupMotion && auth.session == null && auth.page == "register") {
                        auth.navigate("login")
                    }
                    if (showStartupMotion) {
                        StartupLogoMotion(onFinished = { showStartupMotion = false })
                    } else if (auth.session != null) {
                        SabaApp(
                            onLogout = auth::logout,
                            ownerName = auth.ownerName,
                            ownerEmail = auth.ownerEmail,
                            biometricEnabled = biometricEnabled,
                            onDisableBiometrics = {
                                biometricVault.clear()
                                biometricEnabled = false
                            }
                        )
                    } else if (auth.page == "register") {
                        RegistrationScreen(
                            onSubmit = auth::register,
                            onSignIn = { auth.navigate("login") },
                            submitting = auth.busy,
                            serverError = auth.message
                        )
                    } else {
                        OwnerLoginScreen(
                            onPasswordLogin = { email, password -> auth.login(email, password) },
                            onSmsLogin = {},
                            onRegister = { auth.navigate("register") },
                            busy = auth.busy || biometricBusy,
                            error = auth.message,
                            biometricEnabled = biometricEnabled,
                            onBiometricLogin = { unlockWithBiometric() }
                        )
                    }
                    if (!showStartupMotion && auth.session != null &&
                        auth.biometricEnrollment != null && !biometricBusy &&
                        !biometricEnabled && biometricVault.canOffer()
                    ) {
                        val credentials = auth.biometricEnrollment
                        AlertDialog(
                            onDismissRequest = { auth.discardBiometricEnrollment() },
                            title = { Text("ورود با اثر انگشت") },
                            text = { Text("مایلی دفعات بعد با اثر انگشت وارد شوی؟ اطلاعات ورود با کلید امن گوشی رمزنگاری می‌شود و فقط با تأیید خودت باز خواهد شد. اعتبار ورود همچنان توسط سرور بررسی می‌شود.") },
                            shape = RoundedCornerShape(28.dp),
                            containerColor = Color(0xFFF6FCFF),
                            titleContentColor = Color(0xFF163A53),
                            textContentColor = Color(0xFF476579),
                            confirmButton = {
                                TextButton(onClick = {
                                    credentials?.let { enableBiometricLogin(it) }
                                }) { Text("فعال‌سازی") }
                            },
                            dismissButton = {
                                TextButton(onClick = { auth.discardBiometricEnrollment() }) {
                                    Text("فعلاً نه")
                                }
                            }
                        )
                    }
                    biometricNotice?.let { info ->
                        AlertDialog(
                            onDismissRequest = { biometricNotice = null },
                            title = { Text("ورود امن سورا", fontWeight = FontWeight.Bold) },
                            text = { Text(info) },
                            shape = RoundedCornerShape(28.dp),
                            containerColor = Color(0xFFF6FCFF),
                            titleContentColor = Color(0xFF163A53),
                            textContentColor = Color(0xFF476579),
                            confirmButton = {
                                TextButton(onClick = { biometricNotice = null }) { Text("متوجه شدم") }
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SabaApp(
        onLogout: () -> Unit,
        ownerName: String?,
        ownerEmail: String?,
        biometricEnabled: Boolean,
        onDisableBiometrics: () -> Unit
    ) {
        var confirmLogout by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        var confirmDisableBiometrics by remember { mutableStateOf(false) }
        if (confirmDisableBiometrics) AlertDialog(
            onDismissRequest = { confirmDisableBiometrics = false },
            title = { Text("غیرفعال‌کردن اثر انگشت") },
            text = { Text("اطلاعات رمزنگاری‌شده ورود از این گوشی حذف می‌شود. دفعات بعد با رمز ثابت وارد شوید.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisableBiometrics = false
                    onDisableBiometrics()
                }) { Text("غیرفعال‌کردن") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisableBiometrics = false }) { Text("انصراف") }
            }
        )
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
        // One taxpayer context shared by Home, Customers, Invoices and quota screens.
        // Store its ID per authenticated owner to survive a complete app restart.
        val ownerSelectionKey = remember(ownerEmail) {
            "active_taxpayer_" + java.security.MessageDigest.getInstance("SHA-256")
                .digest(ownerEmail.orEmpty().trim().lowercase(java.util.Locale.ROOT).toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        var data by remember { mutableStateOf<CompanyData?>(null) }
        var selectedCompanyId by rememberSaveable(ownerSelectionKey) {
            mutableStateOf(preferences.getLong(ownerSelectionKey, -1L).takeIf { it > 0L })
        }
        fun selectTaxpayer(id: Long) {
            if (selectedCompanyId != id) {
                selectedCompanyId = id
                data = null // Never show data belonging to the previous taxpayer.
            }
            preferences.edit().putLong(ownerSelectionKey, id).apply()
        }
        var loading by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var editing by rememberSaveable { mutableStateOf(false) }
        var loadError by remember { mutableStateOf("") }
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
            if (next != null) selectTaxpayer(next) else { selectedCompanyId = null; data = null; preferences.edit().remove(ownerSelectionKey).apply() }
            if (next != null) refreshCompany(next)
        }
        val excelService = remember { ExcelTransferService(db) }
        var excelScope by remember { mutableStateOf("taxpayers") }
        var excelMode by remember { mutableStateOf("template") }
        val exportExcel = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ) { uri ->
            if (uri != null) scope.launch {
                try {
                    val payload = withContext(Dispatchers.IO) {
                        if (excelMode == "template") excelService.template(excelScope)
                        else excelService.export(excelScope, selectedCompanyId)
                    }
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { it.write(payload) }
                            ?: error("فایل خروجی قابل نوشتن نیست.")
                    }
                    notify("فایل اکسل با موفقیت ذخیره شد.")
                } catch (e: Exception) { notify(e.message ?: "ذخیره اکسل انجام نشد.") }
            }
        }
        val importExcel = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val data = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val size = stream.read(chunk)
                                if (size < 0) break
                                if (data.size() + size > 5_000_000) error("فایل اکسل بیش از حد بزرگ است.")
                                data.write(chunk, 0, size)
                            }
                            data.toByteArray()
                        } ?: error("فایل ورودی خوانده نشد.")
                    }
                    val count = withContext(Dispatchers.IO) {
                        excelService.import(excelScope, selectedCompanyId, bytes)
                    }
                    refresh()
                    notify("ورود اکسل انجام شد؛ $count رکورد جدید اضافه شد.")
                } catch (e: Exception) { notify(e.message ?: "خواندن اکسل انجام نشد.") }
            }
        }
        fun handleExcelAction(action: String, target: String) {
            if (target == "customers" && selectedCompanyId == null && action != "template") {
                notify("ابتدا در خانه یک مؤدی انتخاب کنید.")
                return
            }
            excelScope = target
            excelMode = action
            if (action == "import") importExcel.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/octet-stream"
            )) else exportExcel.launch("SURA_" + target + (if (action == "template") "_template" else "_export") + ".xlsx")
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
        BackHandler(enabled = page != Page.DASHBOARD && !editing) {
            if (!busy) page = if (page == Page.APP_SETTINGS) Page.SETTINGS else Page.DASHBOARD
        }

        MaterialTheme(shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), colorScheme = lightColorScheme(
            primary = Color(0xFF075F91), onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F1FF), onPrimaryContainer = Color(0xFF123C59),
            background = Color(0xFFF2F9FF), surface = Color.White,
            onBackground = Color(0xFF172C3D), onSurface = Color(0xFF172C3D)
        )) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !editing && !busy,
                        drawerContent = {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                ModalDrawerSheet(
                                    modifier = Modifier.width(290.dp),
                                    drawerContainerColor = Color(0xFF09253C),
                                    drawerContentColor = Color.White
                                ) {
                                    Column(
                                        Modifier.fillMaxHeight().padding(horizontal = 18.dp, vertical = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text("سورا | SURA", color = Color(0xFFE8CE91),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold)
                                        Text("صبا گستر اراک", color = Color(0xFFB8D0DD))
                                        HorizontalDivider(color = Color(0xFF345069))
                                        NavigationDrawerItem(
                                            label = { Text("تنظیمات برنامه") },
                                            selected = page == Page.APP_SETTINGS,
                                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                            onClick = {
                                                scope.launch { drawerState.close() }
                                                page = Page.APP_SETTINGS
                                            },
                                            colors = NavigationDrawerItemDefaults.colors(
                                                unselectedContainerColor = Color.Transparent,
                                                unselectedTextColor = Color.White,
                                                unselectedIconColor = Color(0xFFE8CE91),
                                                selectedContainerColor = Color(0xFF15526B),
                                                selectedTextColor = Color.White,
                                                selectedIconColor = Color(0xFFE8CE91)
                                            )
                                        )
                                        Spacer(Modifier.weight(1f))
                                        HorizontalDivider(color = Color(0xFF345069))
                                        Text("سورا | صبا گستر اراک", color = Color(0xFFB8D0DD),
                                            style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    modifier = Modifier.imePadding(),
                    topBar = {
                        Surface(shadowElevation = 5.dp, color = Color(0xFF06192C)) {
                            Row(Modifier.fillMaxWidth().statusBarsPadding()
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(Modifier.weight(1f)) {
                                    SuraBrandBanner(
                                        subtitle = when (page) {
                                            Page.DASHBOARD -> "داشبورد صورتحساب‌های الکترونیکی"
                                            Page.COMPANIES -> "مدیریت مودیان مالیاتی"
                                            Page.INVOICES -> "صدور و مدیریت صورتحساب"
                                            Page.CUSTOMERS -> "مدیریت مشتریان"
                                            Page.SETTINGS -> "حساب کاربری"
                                            Page.APP_SETTINGS -> "تنظیمات برنامه"
                                            else -> "سامانه صورتحساب سورا"
                                        }, compact = true
                                    )
                                }
                                IconButton(onClick = { scope.launch { drawerState.open() } },
                                    enabled = !editing && !busy, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Menu, contentDescription = "باز کردن منوی کشویی",
                                        tint = Color(0xFFE6D2A6), modifier = Modifier.size(30.dp))
                                }
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
                                onSelectTaxpayer = { page = Page.COMPANIES },
                                taxpayerOptions = companies.filter { it.id !in inactiveCompanyIds }.map { it.id to it.name },
                                onTaxpayerSelected = { selectTaxpayer(it) },
                                onNewInvoice = { if (!loading && !busy) { if (company == null) page = Page.COMPANIES else if ((current?.quota?.available ?: 0L) <= 0L) page = Page.QUOTA else { newInvoiceRequest++; page = Page.INVOICES } } },
                                onTaxKeys = { page = Page.TAX_KEYS },
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
                                MoadianScreen(db, companies, onChanged = { refresh() }, onSelect = { selectTaxpayer(it) }, onNotice = ::notify, onEditing = { editing = it })
                            }
                            Page.TAX_KEYS -> TaxpayerKeysScreen(onNotice = ::notify)
                            Page.CUSTOMERS -> if (current != null) key(current.companyId) {
                                CustomerManagementScreen(db, current.companyId, current.customers, onChanged = { refresh() }, onNotice = ::notify, onEditing = { editing = it })
                            } else Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("مشتریان و تأمین‌کنندگان", style = MaterialTheme.typography.headlineSmall)
                                Text("فرم مشتریان آماده است. ابتدا مؤدی صاحب این مشتریان را ثبت یا انتخاب کنید تا اطلاعات طرف‌حساب‌ها در پرونده درست ذخیره شوند.")
                                Button(onClick = { page = Page.COMPANIES }, modifier = Modifier.fillMaxWidth()) {
                                    Text("ثبت یا انتخاب مؤدی")
                                }
                            }
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
                                busy = busy,
                                ownerName = ownerName,
                                ownerEmail = ownerEmail,
                                onTransferCharge = {
                                    if (selectedCompanyId == null) {
                                        page = Page.COMPANIES
                                        notify("ابتدا یک مودی ثبت یا انتخاب کنید.")
                                    } else page = Page.QUOTA
                                },
                                onLogout = { confirmLogout = true },
                                onExcelAction = { action, target -> handleExcelAction(action, target) }
                            )
                            Page.APP_SETTINGS -> AppSettingsScreen(
                                fontScale = fontScale,
                                busy = busy,
                                onFontScale = { fontScale = it },
                                onCommitFontScale = { act { settings.setFontScale(fontScale) } },
                                onResetFont = { fontScale = 1f; act { settings.setFontScale(1f) } },
                                biometricEnabled = biometricEnabled,
                                onDisableBiometrics = { confirmDisableBiometrics = true },
                                onBackToAccount = { page = Page.SETTINGS }
                            )
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
                if (loading) "لطفاً کمی صبر کنید." else "از بخش مودیان، مودی خود را ثبت یا انتخاب کنید تا اطلاعات این قسمت نمایش داده شود.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
