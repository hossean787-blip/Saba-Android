package ir.saba.invoice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.font.FontWeight
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.*
import ir.saba.invoice.domain.InvoiceInput
import kotlinx.coroutines.launch

/** Full customer/supplier form, preserving Customer rows used by existing invoices. */
@Composable
fun CustomerManagementScreen(db: SabaDatabase, companyId: Long,
    customers: List<Customer>, onChanged: suspend () -> Unit,
    onNotice: (String) -> Unit, onEditing: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var profiles by remember(companyId) { mutableStateOf<List<CustomerProfile>>(emptyList()) }
    var busy by remember(companyId) { mutableStateOf(false) }
    var profilesReady by remember(companyId) { mutableStateOf(false) }
    var selectedId by remember(companyId) { mutableStateOf<Long?>(null) }
    var deleteTarget by remember(companyId) { mutableStateOf<Long?>(null) }
    var editorId by remember(companyId) { mutableStateOf<Long?>(null) }
    var editorOpen by remember(companyId) { mutableStateOf(false) }
    var search by remember(companyId) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(companyId, customers) {
        try { profiles = db.customerProfileDao().forCompany(companyId); profilesReady = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { profilesReady = false; onNotice(SuraErrors.describe(e,"بارگذاری اطلاعات تکمیلی مشتری انجام نشد. [CUSTOMER-LOAD]")) }
    }
    DisposableEffect(editorOpen) { onEditing(editorOpen); onDispose { onEditing(false) } }
    val current = customers.firstOrNull { it.id == editorId }
    val oldProfile = profiles.firstOrNull { it.customerId == editorId }
    var name by remember(editorOpen, editorId) { mutableStateOf(current?.name.orEmpty()) }
    var national by remember(editorOpen, editorId) { mutableStateOf(current?.nationalId.orEmpty()) }
    var economic by remember(editorOpen, editorId) { mutableStateOf(current?.economicId.orEmpty()) }
    var postal by remember(editorOpen, editorId) { mutableStateOf(current?.postalCode.orEmpty()) }
    var address by remember(editorOpen, editorId) { mutableStateOf(current?.address.orEmpty()) }
    var phone by remember(editorOpen, editorId) { mutableStateOf(current?.phone.orEmpty()) }
    var type by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.accountType ?: "NATURAL") }
    var active by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.active ?: true) }
    var isCustomer by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.isCustomer ?: true) }
    var isSupplier by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.isSupplier ?: false) }
    var mobile by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.mobile.orEmpty()) }
    var fax by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.fax.orEmpty()) }
    var email by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.email.orEmpty()) }
    var province by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.province.orEmpty()) }
    var county by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.county.orEmpty()) }
    var registration by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.registrationNumber.orEmpty()) }
    var birthCertificate by remember(editorOpen, editorId) { mutableStateOf(oldProfile?.birthCertificateNumber.orEmpty()) }
    if (!editorOpen) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF2F9FF)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy || !profilesReady) LinearProgressIndicator(Modifier.fillMaxWidth())
            val selected = customers.firstOrNull { it.id == selectedId && it.companyId == companyId }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { error=""; editorId=null; editorOpen=true }, enabled = !busy && profilesReady, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, Modifier.size(17.dp)); Text("ثبت")
                }
                OutlinedButton(onClick = {
                    if(selected==null)onNotice("ابتدا یک مشتری را از فهرست انتخاب کنید.")
                    else { error=""; editorId=selected.id; editorOpen=true }
                }, enabled = !busy && profilesReady, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(17.dp)); Text("ویرایش")
                }
                OutlinedButton(onClick = {
                    if(selected==null)onNotice("ابتدا یک مشتری را از فهرست انتخاب کنید.")
                    else if(profiles.firstOrNull{it.customerId==selected.id}?.active==false)onNotice("این مشتری قبلاً غیرفعال شده است.")
                    else deleteTarget=selected.id
                }, enabled = !busy && profilesReady, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(17.dp)); Text("حذف")
                }
            }
            OutlinedTextField(search, { search = it }, label = { Text("جستجوی مشتریان") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(17.dp), enabled = !busy)
            Text("فهرست مشتریان", fontWeight = FontWeight.Bold, color = Color(0xFF153A51), style = MaterialTheme.typography.titleMedium)
            fun normalized(value:String)=InvoiceInput.digits(value).replace('ي','ی').replace('ك','ک')
            val needle=normalized(search.trim())
            val rows=customers.filter { item ->
                item.companyId==companyId && (needle.isEmpty() || listOf(item.name,item.nationalId,item.economicId,item.phone,profiles.firstOrNull{it.customerId==item.id}?.mobile.orEmpty()).any{normalized(it).contains(needle,true)})
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if(rows.isEmpty())item{Text("مشتری یافت نشد.",color=Color(0xFF667F91))}
                itemsIndexed(rows,key={_,item->item.id}){index,item->
                    val chosen=selectedId==item.id
                    val profile=profiles.firstOrNull{it.customerId==item.id}
                    val typeLabel=when(profile?.accountType){"LEGAL"->"حقوقی";"PARTNERSHIP"->"مشارکت مدنی";"FOREIGN"->"اتباع غیرایرانی";"OTHER"->"سایر";else->"حقیقی"}
                    Card(modifier=Modifier.fillMaxWidth().clickable(enabled=!busy){selectedId=item.id},
                        colors=CardDefaults.cardColors(containerColor=if(chosen)Color(0xFFE5F7F9)else Color.White),
                        border=if(chosen)BorderStroke(1.dp,Color(0xFF2DA2B1))else null,
                        shape=RoundedCornerShape(19.dp),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)){
                        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            Surface(shape=RoundedCornerShape(13.dp),color=Color(0xFFE5F3F7)){
                                Text("${index+1}",modifier=Modifier.padding(11.dp),color=Color(0xFF157F96),fontWeight=FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(item.name,fontWeight=FontWeight.Bold,color=Color(0xFF193F55))
                                val role=if(profile?.isSupplier==true&&profile.isCustomer)"مشتری / تأمین‌کننده" else if(profile?.isSupplier==true)"تأمین‌کننده" else "مشتری"
                                Text("$role • $typeLabel",style=MaterialTheme.typography.labelSmall,color=Color(0xFF667F91))
                                Text("شناسه: ${item.nationalId.ifBlank{"ثبت نشده"}}",style=MaterialTheme.typography.labelSmall,color=Color(0xFF667F91))
                            }
                            if(profile?.active==false)Text("غیرفعال",color=Color(0xFFB74C6D),style=MaterialTheme.typography.labelSmall)
                            else if(chosen)Text("انتخاب‌شده",color=Color(0xFF007F71),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        deleteTarget?.let{id->
            AlertDialog(onDismissRequest={if(!busy)deleteTarget=null},title={Text("حذف مشتری")},
                text={Text("مشتری «${customers.firstOrNull{it.id==id}?.name.orEmpty()}» غیرفعال می‌شود. صورتحساب‌ها و سوابق آن محفوظ می‌مانند؛ از طریق ویرایش می‌توانید دوباره فعالش کنید.")},
                confirmButton={TextButton(enabled=!busy,onClick={
                    busy=true;error=""
                    scope.launch{
                        try{
                            db.withTransaction{
                                val customer=db.customerDao().byId(id)
                                require(customer?.companyId==companyId)
                                val prior=db.customerProfileDao().byCustomer(id)
                                require(prior==null||prior.companyId==companyId)
                                db.customerProfileDao().upsert((prior?:CustomerProfile(customerId=id,companyId=companyId)).copy(active=false))
                            }
                            profiles=db.customerProfileDao().forCompany(companyId)
                            deleteTarget=null;onChanged();onNotice("مشتری غیرفعال شد؛ سوابق محفوظ است.")
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){error=SuraErrors.describe(e,"غیرفعال‌سازی مشتری انجام نشد. [CUSTOMER-DELETE]");deleteTarget=null}
                        finally{busy=false}
                    }
                }){Text("تأیید غیرفعال‌سازی")}},
                dismissButton={TextButton(enabled=!busy,onClick={deleteTarget=null}){Text("انصراف")}})
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (editorId == null) "ثبت مشتری / تأمین‌کننده" else "ویرایش مشخصات", style = MaterialTheme.typography.titleLarge, color=Color(0xFF173B51), fontWeight=FontWeight.ExtraBold)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            @Composable fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
                Row { Checkbox(checked = value, onCheckedChange = change, enabled = !busy); Text(label, modifier = Modifier.padding(top = 12.dp)) }
            }
            Toggle("فعال", active) { active = it }
            Toggle("مشتری است", isCustomer) { isCustomer = it }
            Toggle("تأمین‌کننده است", isSupplier) { isSupplier = it }
            Text("نوع طرف حساب", style = MaterialTheme.typography.titleSmall)
            listOf("NATURAL" to "شخص حقیقی", "LEGAL" to "شخص حقوقی", "PARTNERSHIP" to "مشارکت مدنی", "FOREIGN" to "اتباع غیرایرانی", "OTHER" to "سایر").chunked(2).forEach { pair ->
                Row { pair.forEach { (value, title) ->
                    FilterChip(selected = type == value, onClick = { type = value }, label = { Text(title) }, modifier = Modifier.padding(end = 6.dp))
                } }
            }
            @Composable fun Field(label: String, value: String, help: String = "", modifier: Modifier = Modifier.fillMaxWidth(), change: (String) -> Unit) {
                var showHelp by remember { mutableStateOf(false) }
                OutlinedTextField(value, change, enabled = !busy,
                    placeholder = { Text(label, maxLines = 1) },
                    trailingIcon = if (help.isEmpty()) null else { {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(Icons.Default.Info, contentDescription = "راهنمای $label", tint = Color(0xFF12819B))
                        }
                    } },
                    shape = RoundedCornerShape(14.dp), modifier = modifier, singleLine = true)
                if (showHelp) AlertDialog(onDismissRequest = { showHelp = false },
                    title = { Text(label) }, text = { Text(help) },
                    confirmButton = { TextButton(onClick = { showHelp = false }) { Text("متوجه شدم") } })
            }
            Field("نام / عنوان *", name) { name = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(if (type == "LEGAL") "شناسه ملی *" else "کد ملی / شناسه", national,
                    "شناسه هویتی مرتبط با نوع طرف حساب را وارد کنید.", Modifier.weight(1f)) { national = InvoiceInput.digits(it) }
                Field("شماره اقتصادی", economic, "شماره اقتصادی ثبت‌شده برای طرف حساب.", Modifier.weight(1f)) { economic = InvoiceInput.digits(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("تلفن", phone, modifier = Modifier.weight(1f)) { phone = InvoiceInput.digits(it) }
                Field("فکس", fax, modifier = Modifier.weight(1f)) { fax = InvoiceInput.digits(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("موبایل", mobile, modifier = Modifier.weight(1f)) { mobile = InvoiceInput.digits(it) }
                Field("کد پستی", postal, "کد پستی ده رقمی محل طرف حساب.", Modifier.weight(1f)) { postal = InvoiceInput.digits(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("شماره ثبت", registration, modifier = Modifier.weight(1f)) { registration = it }
                Field("شماره شناسنامه", birthCertificate, modifier = Modifier.weight(1f)) { birthCertificate = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("استان", province, modifier = Modifier.weight(1f)) { province = it }
                Field("شهرستان", county, modifier = Modifier.weight(1f)) { county = it }
            }
            Field("ایمیل", email) { email = it.trim() }
            OutlinedTextField(address, { address = it }, enabled = !busy,
                placeholder = { Text("آدرس") }, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = !busy, onClick = {
                busy = true; error = ""
                scope.launch {
                    try {
                        require(name.isNotBlank()) { "نام الزامی است." }
                        require(isCustomer || isSupplier) { "حداقل یکی از گزینه‌های مشتری یا تأمین‌کننده را انتخاب کنید." }
                        require(national.isBlank() || national.length in 10..14) { "طول شناسه هویتی نامعتبر است." }
                        require(type != "LEGAL" || national.length == 11) { "شناسه ملی شخص حقوقی باید ۱۱ رقم باشد." }
                        require(postal.isBlank() || postal.length == 10) { "کد پستی باید ۱۰ رقم باشد." }
                        require(email.isBlank() || (email.contains("@") && email.substringAfter("@").contains("."))) { "ایمیل معتبر نیست." }
                        val id = db.withTransaction {
                        val savedId = if (editorId == null) db.customerDao().insert(Customer(
                            companyId = companyId, name = name.trim(), nationalId = national, economicId = economic,
                            postalCode = postal, address = address.trim(), phone = phone)) else {
                            val old = db.customerDao().byId(editorId!!) ?: error("مشتری یافت نشد.")
                            require(old.companyId == companyId)
                            db.customerDao().update(old.copy(name = name.trim(), nationalId = national, economicId = economic,
                                postalCode = postal, address = address.trim(), phone = phone))
                            old.id
                        }
                        db.customerProfileDao().upsert(CustomerProfile(customerId = savedId, companyId = companyId,
                            accountType = type, active = active, isCustomer = isCustomer, isSupplier = isSupplier,
                            mobile = mobile, fax = fax, email = email, province = province, county = county,
                            registrationNumber = registration, birthCertificateNumber = birthCertificate, nationality=oldProfile?.nationality?:"IR"))
                        savedId
                        }
                        selectedId=id
                        profiles = db.customerProfileDao().forCompany(companyId)
                        onChanged()
                        editorOpen = false
                        onNotice("اطلاعات طرف حساب ذخیره شد.")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = if(e is IllegalArgumentException && e.message in setOf("نام الزامی است.","حداقل یکی از گزینه‌های مشتری یا تأمین‌کننده را انتخاب کنید.","طول شناسه هویتی نامعتبر است.","شناسه ملی شخص حقوقی باید ۱۱ رقم باشد.","کد پستی باید ۱۰ رقم باشد.","ایمیل معتبر نیست."))e.message.orEmpty() else SuraErrors.describe(e,"ذخیره مشتری تأیید نشد؛ اطلاعات فرم را بررسی کنید. [CUSTOMER-SAVE]") }
                    finally { busy = false }
                }
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("ذخیره اطلاعات") }
            OutlinedButton(enabled = !busy, onClick = { editorOpen = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("انصراف") }
            }
        }
    }
}
