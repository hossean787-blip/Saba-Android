package ir.saba.invoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import ir.saba.invoice.data.*
import kotlinx.coroutines.launch

/** Basic functional shell. Original v21 advanced UI is not yet completely imported. */
class MainActivity : ComponentActivity() {
    private val db by lazy { SabaDatabase.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SabaScreen() }
    }

    @Composable
    private fun SabaScreen() {
        var companies by remember { mutableStateOf(emptyList<Company>()) }
        var selectedCompany by remember { mutableStateOf<Long?>(null) }
        var invoices by remember { mutableStateOf(emptyList<Invoice>()) }
        var name by remember { mutableStateOf("") }
        var nationalId by remember { mutableStateOf("") }
        var notice by remember { mutableStateOf("") }

        fun refresh() {
            lifecycleScope.launch {
                companies = db.companyDao().all()
                selectedCompany = selectedCompany?.takeIf { id -> companies.any { it.id == id } }
                    ?: companies.firstOrNull()?.id
                invoices = selectedCompany?.let { db.invoiceDao().forCompany(it) } ?: emptyList()
            }
        }
        LaunchedEffect(Unit) { refresh() }
        LaunchedEffect(selectedCompany) {
            invoices = selectedCompany?.let { db.invoiceDao().forCompany(it) } ?: emptyList()
        }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("فاکتور صبا", style = MaterialTheme.typography.headlineMedium)
                    Text("نسخه توسعه‌ای — اتصال سامانه مؤدیان هنوز فعال نیست")
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام شرکت") })
                    OutlinedTextField(value = nationalId, onValueChange = { nationalId = it }, label = { Text("شناسه ملی") })
                    Button(onClick = {
                        lifecycleScope.launch {
                            try {
                                require(name.isNotBlank() && nationalId.isNotBlank())
                                db.companyDao().insert(Company(
                                    purchaserId = "local-owner", name = name.trim(), nationalId = nationalId.trim()
                                ))
                                name = ""
                                nationalId = ""
                                notice = "شرکت ثبت شد"
                                refresh()
                            } catch (e: Exception) {
                                notice = e.message ?: "خطا در ثبت"
                            }
                        }
                    }) { Text("ثبت شرکت") }
                    if (notice.isNotBlank()) Text(notice)
                    Text("شرکت‌ها", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(companies, key = { it.id }) { company ->
                            TextButton(onClick = { selectedCompany = company.id }) {
                                Text((if (company.id == selectedCompany) "✓ " else "") + company.name)
                            }
                        }
                    }
                    Text("صورتحساب‌های شرکت: ${invoices.size}")
                    Text("صدور و ارسال واقعی در نسخه بعدی تکمیل می‌شود.")
                }
            }
        }
    }
}