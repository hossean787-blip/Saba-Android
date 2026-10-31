package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.saba.invoice.security.TaxpayerCsr
import ir.saba.invoice.security.TaxpayerKeyGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Standalone certificate-request workshop. It does not select, create, or
 * mutate a taxpayer record or the device-bound fiscal signing vault.
 */
@Composable
fun TaxpayerKeysScreen(onNotice: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var officialName by remember { mutableStateOf("") }
    var latinName by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var privatePem by remember { mutableStateOf("") }
    var publicPem by remember { mutableStateOf("") }
    var csrPem by remember { mutableStateOf("") }
    var privateSaved by remember { mutableStateOf(false) }
    var privateExportConfirmed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val exportPublic = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pem-file")) { uri ->
        if (uri != null && publicPem.isNotBlank()) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(publicPem.toByteArray(Charsets.US_ASCII)) }
                ?: error("File unavailable")
            onNotice("کلید عمومی ذخیره شد.")
        } catch (_: Exception) { onNotice("ذخیره کلید عمومی ناموفق بود.") }
    }
    val exportPrivate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pem-file")) { uri ->
        if (uri != null && privatePem.isNotBlank()) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(privatePem.toByteArray(Charsets.US_ASCII)) }
                ?: error("File unavailable")
            privateSaved = true
            onNotice("کلید خصوصی ذخیره شد. فایل را در مکانی امن و خارج از دسترس دیگران نگهداری کنید.")
        } catch (_: Exception) { onNotice("ذخیره کلید خصوصی انجام نشد.") }
    }
    val exportCsr = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pem-file")) { uri ->
        if (uri != null && csrPem.isNotBlank()) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(csrPem.toByteArray(Charsets.US_ASCII)) }
                ?: error("File unavailable")
            onNotice("فایل درخواست گواهی CSR ذخیره شد.")
        } catch (_: Exception) { onNotice("ذخیره CSR ناموفق بود.") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ساخت کلید و گواهی مالیاتی", style = MaterialTheme.typography.headlineSmall)
        Text("این ابزار مستقل از فهرست مؤدیان سورا است. مشخصات متقاضی را وارد کنید و کلیدها و درخواست گواهی را بسازید.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(officialName, { officialName = it.take(120) },
            label = { Text("نام شخص یا شرکت *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(latinName, { latinName = it.take(120) },
            label = { Text("نام لاتین شخص یا شرکت *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(nationalId, { nationalId = it.filter { c -> c.isDigit() }.take(14) },
            label = { Text("شناسه ملی / کد ملی *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(province, { province = it.take(120) },
            label = { Text("استان *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(city, { city = it.take(120) },
            label = { Text("شهرستان *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(email, { email = it.take(254) },
            label = { Text("پست الکترونیکی *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy && privatePem.isBlank(), modifier = Modifier.fillMaxWidth(),
            onClick = {
                error = ""
                if (officialName.isBlank() || latinName.isBlank() || nationalId.length !in 10..14 ||
                    province.isBlank() || city.isBlank() || !email.contains("@") || !email.substringAfter("@").contains(".")) {
                    error = "مشخصات ضروری را به‌طور صحیح وارد کنید."
                } else {
                    busy = true
                    scope.launch {
                        try {
                            val keys = withContext(Dispatchers.Default) { TaxpayerKeyGenerator.generate() }
                            val csr = withContext(Dispatchers.Default) {
                                TaxpayerCsr.generate(keys.privatePem, keys.publicPem,
                                    TaxpayerCsr.Subject(latinName, latinName, province, city, email = email))
                            }
                            privatePem = keys.privatePem
                            publicPem = keys.publicPem
                            csrPem = csr
                            privateSaved = false
                            privateExportConfirmed = false
                            onNotice("جفت کلید و درخواست CSR ساخته شد.")
                        } catch (_: Exception) { error = "ساخت کلیدها یا CSR انجام نشد." }
                        finally { busy = false }
                    }
                }
            }) { Text("تولید کلید عمومی، خصوصی و CSR") }

        if (publicPem.isNotBlank()) {
            OutlinedButton(onClick = { exportPublic.launch("sura-public-key.pem") },
                modifier = Modifier.fillMaxWidth()) { Text("دریافت فایل کلید عمومی") }
            Text("کلید خصوصی محرمانه است و نباید در کارپوشه یا پیام‌رسان بارگذاری شود.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row {
                Checkbox(checked = privateExportConfirmed, onCheckedChange = { privateExportConfirmed = it })
                Text("مسئولیت نگهداری امن کلید خصوصی را می‌پذیرم.",
                    modifier = Modifier.padding(top = 10.dp))
            }
            OutlinedButton(enabled = privateExportConfirmed, onClick = {
                exportPrivate.launch("sura-private-key-SECRET.pem")
            }, modifier = Modifier.fillMaxWidth()) { Text("دریافت کلید خصوصی (محرمانه)") }
            OutlinedButton(onClick = { exportCsr.launch("sura-certificate-request.csr") },
                modifier = Modifier.fillMaxWidth()) { Text("دریافت فایل CSR") }
            Text(if (privateSaved) "کلید خصوصی در محل انتخاب‌شده ذخیره شد."
                else "پیش از خروج از صفحه، کلید خصوصی را در محل امن ذخیره کنید.",
                style = MaterialTheme.typography.bodySmall)
            Button(enabled = privateSaved && !busy, onClick = {
                privatePem = ""; publicPem = ""; csrPem = ""; privateSaved = false
                privateExportConfirmed = false
                onNotice("کلیدهای موقت صفحه پاک شدند.")
            }, modifier = Modifier.fillMaxWidth()) { Text("پایان و پاک‌سازی کلیدهای موقت") }
        }
        HorizontalDivider()
        Text("تولید CSR به‌معنای صدور گواهی معتبر نیست. ثبت کلید عمومی و اخذ شناسه یکتا یا گواهی نزد مراجع مربوط انجام می‌شود.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://my.tax.gov.ir"))) }
            catch (_: Exception) { onNotice("امکان بازکردن کارپوشه وجود ندارد.") }
        }) { Text("ورود به کارپوشه رسمی مالیاتی") }
    }
}
