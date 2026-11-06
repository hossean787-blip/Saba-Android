package ir.saba.invoice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.identifiers.SuraIdentifierInputService
import ir.saba.invoice.data.identifiers.SuraSavedIdentifierStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual entry and batch Excel import stay available when no catalog is installed. */
@Composable
internal fun SuraIdentifierEntryPanel(
    bookmarks: SuraSavedIdentifierStore,
    taxpayerId: Long?,
    onSelectTaxpayer: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var identifier by rememberSaveable(taxpayerId) { mutableStateOf("") }
    var description by rememberSaveable(taxpayerId) { mutableStateOf("") }
    var vat by rememberSaveable(taxpayerId) { mutableStateOf("") }
    var type by rememberSaveable(taxpayerId) { mutableStateOf("") }
    var specificity by rememberSaveable(taxpayerId) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    val templateSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bytes = SuraIdentifierInputService.template()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("فایل ذخیره نشد.")
                }
                notice = "الگوی اکسل ۱۰ ردیفی ذخیره شد."
            } catch (e: Exception) {
                notice = "ذخیره الگوی اکسل ناموفق بود: " + e.message
            } finally { busy = false }
        }
    }

    val excelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val selectedTaxpayer = taxpayerId
        if (uri != null && selectedTaxpayer != null) scope.launch {
            busy = true
            try {
                val rows = SuraIdentifierInputService.readXlsx(context, uri)
                val inserted = bookmarks.importBatch(selectedTaxpayer, rows)
                onSaved()
                notice = inserted.toString() + " شناسه جدید ذخیره شد؛ " +
                    (rows.size - inserted) + " شناسه تکراری قبلی بدون تغییر ماند."
            } catch (e: Exception) {
                notice = "ورود اکسل انجام نشد: " + (e.message ?: "فایل معتبر نیست.")
            } finally { busy = false }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE4F4F3)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("شناسه‌های اختصاصی مؤدی", color = Color(0xFF113A46),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "بدون نصب بانک چندگیگابایتی، شناسه‌ها را تکی وارد کنید یا فایل اکسل تکمیل‌شده را بارگذاری کنید.",
                        color = Color(0xFF456872), style = MaterialTheme.typography.bodySmall
                    )
                    if (taxpayerId == null) TextButton(onClick = onSelectTaxpayer) {
                        Text("ابتدا مؤدی را انتخاب کنید")
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("ثبت دستی یک شناسه", color = Color(0xFF12354B),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = identifier, onValueChange = { identifier = it },
                        label = { Text("شناسه کالا یا خدمت *") },
                        supportingText = { Text("شناسه ۱۳ یا ۱۴ رقمی؛ ارقام فارسی پذیرفته می‌شوند.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && taxpayerId != null
                    )
                    OutlinedTextField(
                        value = description, onValueChange = { description = it },
                        label = { Text("شرح کالا یا خدمت *") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 3,
                        enabled = !busy && taxpayerId != null
                    )
                    OutlinedTextField(
                        value = vat, onValueChange = { vat = it },
                        label = { Text("نرخ مالیات (%) — اختیاری") },
                        supportingText = { Text("در صورت نامشخص بودن، خالی بگذارید؛ نرخ پیش‌فرض اعمال نمی‌شود.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && taxpayerId != null
                    )
                    Text("نوع شناسه", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "کالا", onClick = {
                            type = if (type == "کالا") "" else "کالا"
                        }, label = { Text("کالا") }, enabled = !busy)
                        FilterChip(selected = type == "خدمت", onClick = {
                            type = if (type == "خدمت") "" else "خدمت"
                        }, label = { Text("خدمت") }, enabled = !busy)
                    }
                    Text("طبقه شناسه", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = specificity == "عمومی", onClick = {
                            specificity = if (specificity == "عمومی") "" else "عمومی"
                        }, label = { Text("عمومی") }, enabled = !busy)
                        FilterChip(selected = specificity == "اختصاصی", onClick = {
                            specificity = if (specificity == "اختصاصی") "" else "اختصاصی"
                        }, label = { Text("اختصاصی") }, enabled = !busy)
                    }
                    Button(
                        enabled = !busy && taxpayerId != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087D8A)),
                        onClick = {
                            val id = taxpayerId ?: return@Button
                            scope.launch {
                                busy = true
                                try {
                                    val record = SuraIdentifierInputService.item(
                                        identifier, description, vat, type, specificity
                                    )
                                    bookmarks.save(id, record)
                                    onSaved()
                                    identifier = ""
                                    description = ""
                                    vat = ""
                                    type = ""
                                    specificity = ""
                                    notice = "شناسه برای مؤدی انتخاب‌شده ذخیره شد."
                                } catch (e: Exception) {
                                    notice = e.message ?: "ثبت شناسه انجام نشد."
                                } finally { busy = false }
                            }
                        }
                    ) { Text("ذخیره شناسه برای مؤدی") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ورود گروهی با اکسل", color = Color(0xFF12354B),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("قالب ۱۰ ردیفی را دریافت و تکمیل کنید. سپس همان فایل XLSX را بارگذاری کنید. " +
                        "هر بار حداکثر ۱۰۰۰ شناسه پذیرفته می‌شود.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF456872))
                    OutlinedButton(enabled = !busy, onClick = {
                        templateSaver.launch("SURA_Identifier_Template_10.xlsx")
                    }) { Text("دریافت قالب اکسل") }
                    Button(
                        enabled = !busy && taxpayerId != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087D8A)),
                        onClick = { excelPicker.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/octet-stream"
                        )) }
                    ) { Text("بارگذاری فایل اکسل تکمیل‌شده") }
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (notice.isNotBlank()) item {
            Text(notice, color = Color(0xFF087D8A),
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("اطلاعات ثبت‌شده توسط کاربر جایگزین اعتبارسنجی رسمی شناسه یا نرخ مالیات نیست.",
                color = Color(0xFF657D89), style = MaterialTheme.typography.bodySmall)
        }
    }
}
