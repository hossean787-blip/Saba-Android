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
    var privatePem by remember { mutableStateOf("") }
    var publicPem by remember { mutableStateOf("") }
    var privateSaved by remember { mutableStateOf(false) }
    var privateExportConfirmed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val exportPublic = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pem-file")) { uri ->
        if (uri != null && publicPem.isNotBlank()) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(publicPem.toByteArray(Charsets.US_ASCII)) }
                ?: error("File unavailable")
            onNotice("کلید عمومی ذخیره شد.")
        } catch (e: Exception) { onNotice(SuraErrors.describe(e,"ذخیره کلید عمومی ناموفق بود. [KEY-PUBLIC]")) }
    }
    val exportPrivate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pem-file")) { uri ->
        if (uri != null && privatePem.isNotBlank()) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(privatePem.toByteArray(Charsets.US_ASCII)) }
                ?: error("File unavailable")
            privateSaved = true
            onNotice("کلید خصوصی ذخیره شد. فایل را در مکانی امن و خارج از دسترس دیگران نگهداری کنید.")
        } catch (e: Exception) { onNotice(SuraErrors.describe(e,"ذخیره کلید خصوصی انجام نشد. [KEY-PRIVATE]")) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ساخت کلید مالیاتی", style = MaterialTheme.typography.headlineSmall)
        Text("کلید عمومی و خصوصی RSA را بسازید؛ کلید عمومی را در کارپوشه ثبت کنید.",
            style = MaterialTheme.typography.bodyMedium)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy && privatePem.isBlank(), modifier = Modifier.fillMaxWidth(),
            onClick = {
                error = ""
                    busy = true
                    scope.launch {
                        try {
                            val keys = withContext(Dispatchers.Default) { TaxpayerKeyGenerator.generate() }
                            privatePem = keys.privatePem
                            publicPem = keys.publicPem
                            privateSaved = false
                            privateExportConfirmed = false
                            onNotice("جفت کلید عمومی و خصوصی ساخته شد.")
                        } catch (_: Exception) { error = "ساخت کلیدها انجام نشد." }
                        finally { busy = false }
                    }
            }) { Text("تولید کلید عمومی و خصوصی") }

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
            Text(if (privateSaved) "کلید خصوصی در محل انتخاب‌شده ذخیره شد."
                else "پیش از خروج از صفحه، کلید خصوصی را در محل امن ذخیره کنید.",
                style = MaterialTheme.typography.bodySmall)
            Button(enabled = privateSaved && !busy, onClick = {
                privatePem = ""; publicPem = ""; privateSaved = false
                privateExportConfirmed = false
                onNotice("کلیدهای موقت صفحه پاک شدند.")
            }, modifier = Modifier.fillMaxWidth()) { Text("پایان و پاک‌سازی کلیدهای موقت") }
        }
        HorizontalDivider()
        Text("ثبت کلید عمومی و دریافت شناسه یکتا در کارپوشه رسمی انجام می‌شود. کلید خصوصی فقط در سورا استفاده می‌شود.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://my.tax.gov.ir"))) }
            catch (_: Exception) { onNotice("امکان بازکردن کارپوشه وجود ندارد.") }
        }) { Text("ورود به کارپوشه رسمی مالیاتی") }
    }
}
