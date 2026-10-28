package ir.saba.invoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.lifecycle.lifecycleScope
import ir.saba.invoice.data.Company
import ir.saba.invoice.data.Customer
import ir.saba.invoice.data.Invoice
import ir.saba.invoice.data.InvoiceLine
import ir.saba.invoice.data.InvoiceRepository
import ir.saba.invoice.data.QuotaRepository
import ir.saba.invoice.data.QuotaWallet
import ir.saba.invoice.data.CompanyQuota
import ir.saba.invoice.data.SabaDatabase
import ir.saba.invoice.data.MoadianSimulation
import ir.saba.moadian.SubmissionPreparation
import kotlinx.coroutines.launch

private enum class Page { DASHBOARD, COMPANIES, CUSTOMERS, INVOICES, QUOTA, SIMULATION, SETTINGS }

class MainActivity : ComponentActivity() {
    private val db by lazy { SabaDatabase.getInstance(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SabaApp() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SabaApp() {
        var page by remember { mutableStateOf(Page.DASHBOARD) }
        var fontScale by remember { mutableFloatStateOf(1.0f) }
        var companies by remember { mutableStateOf(listOf<Company>()) }
        var customers by remember { mutableStateOf(listOf<Customer>()) }
        var selectedCompanyId by remember { mutableStateOf<Long?>(null) }
        var notice by remember { mutableStateOf("") }
        var companyName by remember { mutableStateOf("") }
        var companyNationalId by remember { mutableStateOf("") }
        var purchaserId by remember { mutableStateOf("owner-1") }
        var customerName by remember { mutableStateOf("") }
        var customerNationalId by remember { mutableStateOf("") }
        var invoices by remember { mutableStateOf(listOf<Invoice>()) }
        var simulations by remember { mutableStateOf(listOf<MoadianSimulation>()) }
        var invoiceNumber by remember { mutableStateOf("") }
        var invoiceDate by remember { mutableStateOf("") }
        var invoiceCustomerId by remember { mutableStateOf<Long?>(null) }
        var itemDescription by remember { mutableStateOf("") }
        var itemQuantity by remember { mutableStateOf("1") }
        var itemPrice by remember { mutableStateOf("") }
        var itemTax by remember { mutableStateOf("10") }
        var customerMenuExpanded by remember { mutableStateOf(false) }
        var purchasedQuotaInput by remember { mutableStateOf("50") }
        var transferQuotaInput by remember { mutableStateOf("10") }
        var wallet by remember { mutableStateOf<QuotaWallet?>(null) }
        var companyQuota by remember { mutableStateOf<CompanyQuota?>(null) }

        fun reload() {
            lifecycleScope.launch {
                companies = db.companyDao().all()
                selectedCompanyId = selectedCompanyId?.takeIf { id -> companies.any { it.id == id } } ?: companies.firstOrNull()?.id
                customers = selectedCompanyId?.let { db.customerDao().forCompany(it) } ?: emptyList()
                invoices = selectedCompanyId?.let { db.invoiceDao().forCompany(it) } ?: emptyList()
                simulations = selectedCompanyId?.let { db.moadianSimulationDao().forCompany(it) } ?: emptyList()
                val company = companies.firstOrNull { it.id == selectedCompanyId }
                wallet = company?.let { db.quotaDao().wallet(it.purchaserId) }
                companyQuota = selectedCompanyId?.let { db.quotaDao().companyQuota(it) }
            }
        }
        LaunchedEffect(Unit) { reload() }
        LaunchedEffect(selectedCompanyId) {
            val id = selectedCompanyId
            customers = if (id == null) emptyList() else db.customerDao().forCompany(id)
            invoices = if (id == null) emptyList() else db.invoiceDao().forCompany(id)
            simulations = if (id == null) emptyList() else db.moadianSimulationDao().forCompany(id)
            invoiceCustomerId = customers.firstOrNull()?.id
            val company = companies.firstOrNull { it.id == id }
            wallet = company?.let { db.quotaDao().wallet(it.purchaserId) }
            companyQuota = id?.let { db.quotaDao().companyQuota(it) }
        }

        val premiumPurple = Color(0xFF6246AE)
        val premiumScheme = lightColorScheme(
            primary = premiumPurple, onPrimary = Color.White,
            primaryContainer = Color(0xFFECE5FB), onPrimaryContainer = Color(0xFF25184D),
            background = Color(0xFFF8F7FC), surface = Color.White,
            onBackground = Color(0xFF1D1834), onSurface = Color(0xFF1D1834)
        )
        MaterialTheme(colorScheme = premiumScheme, shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp))) {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Surface(color = Color.White, shadowElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("صبا", style = MaterialTheme.typography.headlineSmall,
                                    color = premiumPurple, fontWeight = FontWeight.ExtraBold)
                                Text("مدیریت فاکتور و مؤدیان", style = MaterialTheme.typography.labelMedium)
                            }
                            Text(companies.firstOrNull { it.id == selectedCompanyId }?.name ?: "انتخاب شرکت",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = Color.White, tonalElevation = 5.dp) {
                        listOf(
                            Triple(Page.DASHBOARD, "خانه", Icons.Default.Home),
                            Triple(Page.COMPANIES, "شرکت‌ها", Icons.Default.AccountCircle),
                            Triple(Page.INVOICES, "فاکتورها", Icons.Default.List),
                            Triple(Page.CUSTOMERS, "مشتریان", Icons.Default.Person),
                            Triple(Page.SETTINGS, "تنظیمات", Icons.Default.Settings)
                        ).forEach { (destination, title, icon) ->
                            NavigationBarItem(selected = page == destination,
                                onClick = { page = destination },
                                icon = { Icon(icon, contentDescription = title) },
                                label = { Text(title, maxLines = 1) })
                        }
                    }
                },
                snackbarHost = {
                    if (notice.isNotBlank()) {
                        SnackbarHost(hostState = remember { SnackbarHostState() }) {
                            Snackbar(
                                action = { TextButton(onClick = { notice = "" }) { Text("بستن") } },
                                modifier = Modifier.padding(12.dp)
                            ) { Text(notice) }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    when (page) {

                        Page.DASHBOARD -> {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = premiumPurple),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("صورتحساب الکترونیکی", color = Color.White,
                                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("صدور، پیگیری و مدیریت صورتحساب‌ها", color = Color.White)
                                    Button(onClick = { page = Page.INVOICES },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = premiumPurple)) {
                                        Icon(Icons.Default.Add, null)
                                        Text("صدور فاکتور جدید")
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(
                                    Triple("شرکت‌ها", companies.size.toString(), Page.COMPANIES),
                                    Triple("مشتریان", customers.size.toString(), Page.CUSTOMERS),
                                    Triple("فاکتورها", invoices.size.toString(), Page.INVOICES)
                                ).forEach { (label, amount, destination) ->
                                    ElevatedCard(onClick = { page = destination }, modifier = Modifier.weight(1f)) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(amount, style = MaterialTheme.typography.headlineSmall, color = premiumPurple)
                                            Text(label, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            ElevatedCard(onClick = { page = Page.SIMULATION },
                                modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("آزمایش سامانه مؤدیان", fontWeight = FontWeight.Bold)
                                    Text("ارسال آزمایشی آفلاین؛ ارسال واقعی غیرفعال است.")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { page = Page.QUOTA }) { Text("مدیریت سهمیه‌ها") }
                        }
                        Page.COMPANIES -> {
                            Text("ثبت شرکت / مؤدی", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text("نام شرکت") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = companyNationalId, onValueChange = { companyNationalId = it }, label = { Text("شناسه ملی") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = purchaserId, onValueChange = { purchaserId = it }, label = { Text("شناسه خریدار اصلی") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                lifecycleScope.launch {
                                    try {
                                        require(companyName.isNotBlank()) { "نام شرکت الزامی است." }
                                        require(companyNationalId.isNotBlank()) { "شناسه ملی الزامی است." }
                                        require(companyNationalId.trim().all { it.isDigit() }) { "شناسه ملی باید فقط شامل رقم باشد." }
                                        require(purchaserId.isNotBlank()) { "شناسه خریدار الزامی است." }
                                        db.companyDao().insert(Company(purchaserId = purchaserId.trim(), name = companyName.trim(), nationalId = companyNationalId.trim()))
                                        companyName = ""; companyNationalId = ""; notice = "شرکت ثبت شد."; reload()
                                    } catch (e: Exception) { notice = e.message ?: "ثبت شرکت انجام نشد." }
                                }
                            }) { Text("ذخیره شرکت") }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(companies, key = { it.id }) { company ->
                                    ElevatedCard(onClick = { selectedCompanyId = company.id; page = Page.CUSTOMERS }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(company.name, fontWeight = FontWeight.SemiBold)
                                            Text("شناسه ملی: ${company.nationalId}")
                                            Text("برای مشاهده مشتریان انتخاب کنید")
                                        }
                                    }
                                }
                            }
                        }
                        Page.CUSTOMERS -> {
                            Text("مشتریان", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (companies.isEmpty()) {
                                Text("ابتدا از بخش شرکت‌ها، یک شرکت ثبت کنید.")
                            } else {
                                Text("شرکت انتخاب‌شده", style = MaterialTheme.typography.labelLarge)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                companies.forEach { company ->
                                    FilterChip(selected = selectedCompanyId == company.id, onClick = { selectedCompanyId = company.id }, label = { Text(company.name) })
                                }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("افزودن مشتری جدید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("نام مشتری") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = customerNationalId, onValueChange = { customerNationalId = it }, label = { Text("شناسه ملی مشتری (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    lifecycleScope.launch {
                                        try {
                                            val companyId = selectedCompanyId ?: error("ابتدا شرکت را انتخاب کنید.")
                                            require(customerName.isNotBlank()) { "نام مشتری الزامی است." }
                                            db.customerDao().insert(Customer(companyId = companyId, name = customerName.trim(), nationalId = customerNationalId.trim()))
                                            customerName = ""; customerNationalId = ""; notice = "مشتری ثبت شد."
                                            customers = db.customerDao().forCompany(companyId)
                                        } catch (e: Exception) { notice = e.message ?: "ثبت مشتری انجام نشد." }
                                    }
                                }) { Text("ذخیره مشتری") }
                                Spacer(Modifier.height(16.dp))
                                Text("فهرست مشتریان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(customers, key = { it.id }) { customer ->
                                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(14.dp)) {
                                                Text(customer.name, fontWeight = FontWeight.SemiBold)
                                                Text("شناسه ملی: ${customer.nationalId.ifBlank { "ثبت نشده" }}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Page.INVOICES -> {
                            Text("صدور فاکتور | پیش‌نویس", style = MaterialTheme.typography.titleLarge)
                            if (selectedCompanyId == null) {
                                Text("ابتدا یک شرکت ثبت کنید.")
                            } else {
                                OutlinedTextField(value = invoiceNumber, onValueChange = { invoiceNumber = it }, label = { Text("شماره فاکتور") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = invoiceDate, onValueChange = { invoiceDate = it }, label = { Text("تاریخ فاکتور (مثلاً ۱۴۰۵/۰۷/۰۲)") }, modifier = Modifier.fillMaxWidth())
                                Box {
                                    OutlinedButton(onClick = { customerMenuExpanded = true }, enabled = customers.isNotEmpty()) {
                                        Text(customers.firstOrNull { it.id == invoiceCustomerId }?.name ?: "انتخاب مشتری")
                                    }
                                    DropdownMenu(expanded = customerMenuExpanded, onDismissRequest = { customerMenuExpanded = false }) {
                                        customers.forEach { customer ->
                                            DropdownMenuItem(text = { Text(customer.name) }, onClick = {
                                                invoiceCustomerId = customer.id
                                                customerMenuExpanded = false
                                            })
                                        }
                                    }
                                }
                                OutlinedTextField(value = itemDescription, onValueChange = { itemDescription = it }, label = { Text("شرح کالا یا خدمت") }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = itemQuantity, onValueChange = { itemQuantity = it.filter(Char::isDigit) }, label = { Text("تعداد") }, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = itemPrice, onValueChange = { itemPrice = it.filter(Char::isDigit) }, label = { Text("قیمت واحد (ریال)") }, modifier = Modifier.weight(2f))
                                }
                                OutlinedTextField(value = itemTax, onValueChange = { itemTax = it.filter(Char::isDigit).take(3) }, label = { Text("درصد مالیات (موقت)") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    lifecycleScope.launch {
                                        try {
                                            val companyId = selectedCompanyId ?: error("شرکت را انتخاب کنید.")
                                            val customerId = invoiceCustomerId ?: error("مشتری را انتخاب کنید.")
                                            val qty = itemQuantity.toLongOrNull() ?: error("تعداد نامعتبر است.")
                                            val price = itemPrice.toLongOrNull() ?: error("قیمت نامعتبر است.")
                                            val tax = itemTax.toIntOrNull() ?: error("درصد مالیات نامعتبر است.")
                                            require(itemDescription.isNotBlank()) { "شرح کالا یا خدمت الزامی است." }
                                            require(qty > 0) { "تعداد باید بیشتر از صفر باشد." }
                                            require(price >= 0) { "قیمت نمی‌تواند منفی باشد." }
                                            require(tax in 0..100) { "درصد مالیات باید بین صفر و صد باشد." }
                                            val repo = InvoiceRepository(db)
                                            repo.saveDraft(
                                                companyId = companyId,
                                                customerId = customerId,
                                                invoiceNumber = invoiceNumber,
                                                issueDate = invoiceDate,
                                                lines = listOf(InvoiceLine(invoiceId = 0, description = itemDescription.trim(), quantity = qty, unitPriceRial = price, taxRatePercent = tax))
                                            )
                                            invoiceNumber = ""; invoiceDate = ""; itemDescription = ""; itemQuantity = "1"; itemPrice = ""
                                            invoices = db.invoiceDao().forCompany(companyId)
                                            notice = "پیش‌نویس فاکتور ذخیره شد. هنوز به سامانه مؤدیان ارسال نشده است."
                                        } catch (e: Exception) { notice = e.message ?: "ذخیره فاکتور انجام نشد." }
                                    }
                                }, enabled = customers.isNotEmpty()) { Text("ذخیره پیش‌نویس") }
                                Spacer(Modifier.height(8.dp))
                                Text("فاکتورهای شرکت انتخاب‌شده", style = MaterialTheme.typography.titleMedium)
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(invoices, key = { it.id }) { invoice ->
                                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text("فاکتور ${invoice.invoiceNumber}", fontWeight = FontWeight.SemiBold)
                                                Text("تاریخ: ${invoice.issueDate} | وضعیت: ${if (invoice.status == "DRAFT") "پیش‌نویس" else invoice.status}")
                                                Text("مبلغ کل: ${invoice.totalAmountRial} ریال")
                                                if (invoice.status == "DRAFT") {
                                                    Button(onClick = {
                                                        lifecycleScope.launch {
                                                            try {
                                                                InvoiceRepository(db).finalizeDraft(invoice.id)
                                                                invoices = selectedCompanyId?.let { db.invoiceDao().forCompany(it) } ?: emptyList()
                                                                companyQuota = selectedCompanyId?.let { db.quotaDao().companyQuota(it) }
                                                                notice = "فاکتور نهایی شد و یک سهمیه مصرف شد. ارسال به سامانه مؤدیان انجام نشده است."
                                                            } catch (e: Exception) {
                                                                notice = e.message ?: "نهایی‌سازی فاکتور انجام نشد."
                                                            }
                                                        }
                                                    }) { Text("نهایی‌سازی و مصرف سهمیه") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Page.SIMULATION -> {
                            Text("آزمایش آفلاین مؤدیان", style = MaterialTheme.typography.titleLarge)
                            Text("هیچ اطلاعاتی به سامانه واقعی ارسال نمی‌شود.")
                            val eligible = invoices.filter { it.status == "FINALIZED" }
                            if (eligible.isEmpty()) Text("فاکتور نهایی‌شده‌ای برای آزمایش وجود ندارد.")
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(eligible, key = { it.id }) { invoice ->
                                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("فاکتور شماره: ${invoice.invoiceNumber}")
                                            Text("مبلغ: ${invoice.totalAmountRial} ریال")
                                            val recorded = simulations.firstOrNull { it.invoiceId == invoice.id }
                                            Text(if (recorded == null) "بدون آزمون" else "آزمایش محلی ثبت‌شده")
                                            Button(enabled = recorded == null, onClick = {
                                                lifecycleScope.launch {
                                                    try {
                                                        val companyId = selectedCompanyId ?: error("شرکت انتخاب نشده است.")
                                                        require(invoice.companyId == companyId) { "مغایرت شرکت فاکتور" }
                                                        val existing = db.moadianSimulationDao().latestForInvoice(invoice.id)
                                                        require(existing == null) { "این صورتحساب قبلاً آزمایش شده است." }
                                                        val payload = "${invoice.id}|${invoice.companyId}|${invoice.invoiceNumber}|${invoice.totalAmountRial}|${invoice.taxAmountRial}".toByteArray(Charsets.UTF_8)
                                                        val request = SubmissionPreparation.prepare(payload)
                                                        db.moadianSimulationDao().insert(MoadianSimulation(
                                                            companyId = companyId, invoiceId = invoice.id,
                                                            requestId = request.requestId, payloadHash = request.payloadSha256,
                                                            createdAtMillis = System.currentTimeMillis()
                                                        ))
                                                        simulations = db.moadianSimulationDao().forCompany(companyId)
                                                        notice = "آزمایش آفلاین ثبت شد؛ هیچ ارسال واقعی انجام نشد."
                                                    } catch (e: Exception) {
                                                        notice = e.message ?: "خطای آزمایش"
                                                    }
                                                }
                                            }) { Text("شبیه‌سازی ارسال") }
                                        }
                                    }
                                }
                            }
                        }
                        Page.QUOTA -> {
                            Text("مدیریت سهمیه", style = MaterialTheme.typography.titleLarge)
                            val selectedCompany = companies.firstOrNull { it.id == selectedCompanyId }
                            if (selectedCompany == null) {
                                Text("ابتدا یک شرکت ثبت و انتخاب کنید.")
                            } else {
                                Text("شرکت انتخاب‌شده: ${selectedCompany.name}")
                                Text("شناسه خریدار اصلی: ${selectedCompany.purchaserId}")
                                Text("سهمیه آزاد حساب اصلی: ${wallet?.available?.toString() ?: "ایجاد نشده"}")
                                Text("سهمیه تخصیص‌یافته به شرکت: ${companyQuota?.allocatedTotal ?: 0}")
                                Text("سهمیه باقی‌مانده شرکت: ${companyQuota?.available ?: 0}")
                                Spacer(Modifier.height(8.dp))
                                Text("ایجاد کیف سهمیه خریدار اصلی")
                                OutlinedTextField(value = purchasedQuotaInput, onValueChange = { purchasedQuotaInput = it.filter(Char::isDigit) }, label = { Text("تعداد سهمیه خریداری‌شده") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    lifecycleScope.launch {
                                        try {
                                            val amount = purchasedQuotaInput.toLongOrNull() ?: error("تعداد سهمیه معتبر نیست.")
                                            QuotaRepository(db).initializeWallet(selectedCompany.purchaserId, amount)
                                            wallet = db.quotaDao().wallet(selectedCompany.purchaserId)
                                            notice = "کیف سهمیه ایجاد شد. این کار فقط یک‌بار برای هر خریدار انجام می‌شود."
                                        } catch (e: Exception) { notice = e.message ?: "ایجاد کیف سهمیه انجام نشد." }
                                    }
                                }, enabled = wallet == null) { Text(if (wallet == null) "ایجاد کیف سهمیه" else "کیف سهمیه قبلاً ایجاد شده") }
                                Spacer(Modifier.height(8.dp))
                                Text("انتقال سهمیه از خریدار اصلی به شرکت انتخاب‌شده")
                                OutlinedTextField(value = transferQuotaInput, onValueChange = { transferQuotaInput = it.filter(Char::isDigit) }, label = { Text("تعداد برای انتقال") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    lifecycleScope.launch {
                                        try {
                                            val amount = transferQuotaInput.toLongOrNull() ?: error("تعداد انتقال معتبر نیست.")
                                            QuotaRepository(db).transferFromMaster(
                                                purchaserId = selectedCompany.purchaserId,
                                                companyId = selectedCompany.id,
                                                amount = amount,
                                                operationId = java.util.UUID.randomUUID().toString()
                                            )
                                            wallet = db.quotaDao().wallet(selectedCompany.purchaserId)
                                            companyQuota = db.quotaDao().companyQuota(selectedCompany.id)
                                            notice = "انتقال سهمیه ثبت شد."
                                        } catch (e: Exception) { notice = e.message ?: "انتقال سهمیه انجام نشد." }
                                    }
                                }, enabled = wallet != null) { Text("انتقال سهمیه") }
                                Text("هشدار: این نسخه برای آزمایش محلی است؛ خرید واقعی، مجوز تجاری و کنترل دسترسی سمت سرور پیاده‌سازی نشده است.")
                            }
                        }
                        Page.SETTINGS -> {
                            Text("تنظیمات نمایش", style = MaterialTheme.typography.titleLarge)
                            Text("اندازه نوشته‌ها: ${(fontScale * 100).toInt()}٪")
                            Slider(
                                value = fontScale,
                                onValueChange = { fontScale = it },
                                valueRange = 0.85f..1.35f,
                                steps = 9
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { fontScale = 1.0f }) { Text("اندازه پیش‌فرض") }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("وضعیت اتصال سامانه مؤدیان", style = MaterialTheme.typography.titleMedium)
                            Text("پیکربندی نشده؛ هیچ فاکتوری از این نسخه به سامانه واقعی ارسال نمی‌شود.")
                            Spacer(Modifier.height(8.dp))
                            Text("وضعیت نسخه: نمونه اولیه محلی؛ ساخت APK و آزمون روی دستگاه هنوز تأیید نشده است.")
                        }
                    }

                }
            }
            }
            }
        }
    }
}
