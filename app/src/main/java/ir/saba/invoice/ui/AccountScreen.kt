package ir.saba.invoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.Locale

private val accountNavy = Color(0xFF071F32)
private val accountTeal = Color(0xFF0C7484)
private val accountGold = Color(0xFFEAD19C)
private val accountText = Color(0xFF16364E)
private val accountNumber = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR"))

/**
 * Owner-only account hub. Personal information is shown only after server login.
 * Password/mobile edits and charge purchase are NOT claimed as complete when
 * the backend does not expose authenticated endpoints for these actions.
 */
@Composable
fun AccountScreen(
    taxpayerCharge: Long,
    ownerCharge: Long,
    busy: Boolean,
    ownerName: String?,
    ownerEmail: String?,
    onTransferCharge: () -> Unit,
    onLogout: () -> Unit
) {
    var selected by remember { mutableStateOf<AccountAction?>(null) }
    val context = LocalContext.current
    // Account-scoped, device-local profile. Never misrepresent this as a server update.
    val profileKey = remember(ownerEmail) {
        ownerEmail?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }?.let { email ->
            MessageDigest.getInstance("SHA-256")
                .digest(email.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
    val profilePrefs = remember { context.getSharedPreferences("sura_owner_profile_local_v1", android.content.Context.MODE_PRIVATE) }
    var localName by remember(profileKey) { mutableStateOf(profileKey?.let { profilePrefs.getString("name_$it", "") }.orEmpty()) }
    var localMobile by remember(profileKey) { mutableStateOf(profileKey?.let { profilePrefs.getString("mobile_$it", "") }.orEmpty()) }
    var editingProfile by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editMobile by remember { mutableStateOf("") }
    var profileError by remember { mutableStateOf("") }
    val displayName = localName.ifBlank { ownerName?.takeIf(String::isNotBlank) ?: "مالک حساب سورا" }
    fun openProfile() {
        editName = localName.ifBlank { ownerName.orEmpty() }
        editMobile = localMobile
        profileError = ""
        editingProfile = true
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF2F9FF))
            .verticalScroll(rememberScrollState()).padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("حساب کاربری", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold, color = accountText)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = accountNavy),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(
                    listOf(accountNavy, Color(0xFF12445B), accountNavy)
                )).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(52.dp).clip(CircleShape)
                        .background(Color(0xFF285569)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null,
                            tint = accountGold, modifier = Modifier.size(37.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(displayName,
                            color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text(ownerEmail ?: "اطلاعات ایمیل در دسترس نیست",
                            color = Color(0xFFB6D7E3), style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("SURA", color = accountGold, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider(color = Color(0xFF37586A))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountBalance("شارژ آزاد مالک", ownerCharge, Modifier.weight(1f))
                    AccountBalance("شارژ مودی فعال", taxpayerCharge, Modifier.weight(1f))
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مشخصات مالک حساب", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = accountText)
                Text("نام: " + displayName)
                Text("ایمیل: " + (ownerEmail ?: "دریافت نشده"))
                Text("موبایل: " + localMobile.ifBlank { "ثبت نشده" })
                Text("نام و شماره موبایل در این بخش فقط روی همین دستگاه ذخیره می‌شوند و با سرور همگام نیستند.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B8093))
                Button(onClick = ::openProfile, enabled = !busy && profileKey != null,
                    modifier = Modifier.fillMaxWidth()) { Text("ویرایش مشخصات شخصی") }
            }
        }

        Text("خدمات حساب", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = accountText)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountActionTile("اطلاعات شخصی", "مشخصات مالک حساب",
                Icons.Default.Person, Color(0xFFE5F2FA), Color(0xFF146C92),
                Modifier.weight(1f), !busy, "personalInfo") { openProfile() }
            AccountActionTile("تغییر رمز", "امنیت حساب کاربری",
                Icons.Default.Edit, Color(0xFFE8EDFB), Color(0xFF515FAD),
                Modifier.weight(1f), !busy, "changePassword") { selected = AccountAction.PASSWORD }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountActionTile("تغییر شماره موبایل", "مدیریت شماره تماس",
                Icons.Default.AccountCircle, Color(0xFFF1EAF9), Color(0xFF8057A5),
                Modifier.weight(1f), !busy, "changeMobile") { selected = AccountAction.MOBILE }
            AccountActionTile("انتقال شارژ به مودیان", "تخصیص شارژ موجود",
                Icons.AutoMirrored.Filled.List, Color(0xFFE3F5F0), Color(0xFF14866D),
                Modifier.weight(1f), !busy, "transferCharge", onTransferCharge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountActionTile("خرید شارژ", "مدیریت خرید سهمیه",
                Icons.Default.Add, Color(0xFFFFF1E0), Color(0xFFB47A2F),
                Modifier.weight(1f), !busy, "purchaseCharge") { selected = AccountAction.PURCHASE }
            AccountActionTile("خروج از حساب", "خروج امن از پنل",
                Icons.Default.Settings, Color(0xFFF1F2F5), Color(0xFF566B7D),
                Modifier.weight(1f), !busy, "accountLogout", onLogout)
        }
        Text("انتقال شارژ فعلاً محلی و آزمایشی است؛ خرید آنلاین تا اتصال درگاه امن فعال نیست.",
            color = Color(0xFF6E8394), style = MaterialTheme.typography.labelSmall)
    }

    if (editingProfile) AlertDialog(
        onDismissRequest = { editingProfile = false },
        title = { Text("ویرایش مشخصات مالک") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(editName, { editName = it.take(100) },
                    label = { Text("نام و نام خانوادگی") }, singleLine = true)
                OutlinedTextField(editMobile, {
                    editMobile = it.map { ch -> when (ch) {
                        in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                        in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                        else -> ch
                    } }.filter(Char::isDigit).take(11).joinToString("")
                }, label = { Text("شماره موبایل") }, singleLine = true)
                Text("ایمیل ورود: " + ownerEmail.orEmpty(), style = MaterialTheme.typography.bodySmall)
                Text("تغییرات فقط روی همین گوشی ذخیره می‌شوند؛ شماره تأییدشده سرور تغییر نمی‌کند.",
                    style = MaterialTheme.typography.bodySmall)
                if (profileError.isNotBlank()) Text(profileError, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val key = profileKey
            if (key == null) profileError = "ابتدا وارد حساب شوید."
            else if (editName.isBlank()) profileError = "نام را وارد کنید."
            else if (editMobile.isNotBlank() && !Regex("09[0-9]{9}").matches(editMobile))
                profileError = "شماره موبایل باید ۱۱ رقم و با ۰۹ آغاز شود."
            else if (!profilePrefs.edit().putString("name_$key", editName.trim())
                    .putString("mobile_$key", editMobile).commit())
                profileError = "ذخیره در دستگاه انجام نشد."
            else {
                localName = editName.trim()
                localMobile = editMobile
                editingProfile = false
            }
        }) { Text("ذخیره در دستگاه") } },
        dismissButton = { TextButton(onClick = { editingProfile = false }) { Text("انصراف") } }
    )

    selected?.let { action ->
        val title = when (action) {
            AccountAction.PROFILE -> "اطلاعات شخصی مالک"
            AccountAction.PASSWORD -> "تغییر رمز عبور"
            AccountAction.MOBILE -> "تغییر شماره موبایل"
            AccountAction.PURCHASE -> "خرید شارژ"
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color(0xFFF9FCFF),
            title = { Text(title, color = accountText, fontWeight = FontWeight.Bold) },
            text = {
                when (action) {
                    AccountAction.PROFILE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("نام: " + (ownerName?.takeIf(String::isNotBlank) ?: "از سرور دریافت نشده"))
                        Text("ایمیل حساب: " + (ownerEmail ?: "از سرور دریافت نشده"))
                        Text("شماره موبایل: از سرور دریافت نشده")
                        Text("اطلاعات این بخش صرفاً از ورود تأییدشده همین نشست نمایش داده می‌شود.",
                            color = Color(0xFF6B8093), style = MaterialTheme.typography.bodySmall)
                    }
                    AccountAction.PASSWORD ->
                        Text("برای تغییر ایمن رمز، سرویس احراز هویت باید رمز فعلی را بررسی و تغییر رمز جدید را در سرور ثبت کند. این خدمت هنوز در سرور متصل نشده است؛ رمز فعلی شما تغییر نکرده است.")
                    AccountAction.MOBILE ->
                        Text("تغییر شماره همراه باید با تأیید هویت و کد یک‌بارمصرف انجام شود. این خدمت هنوز در سرور فعال نیست و شماره‌ای در این دستگاه تغییر داده نمی‌شود.")
                    AccountAction.PURCHASE ->
                        Text("خرید شارژ آنلاین نیازمند درگاه پرداخت، ثبت تراکنش و تأیید سهمیه از سرور است. فعلاً خرید واقعی فعال نیست و هیچ وجهی دریافت نمی‌شود.")
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("متوجه شدم") }
            }
        )
    }
}

private enum class AccountAction { PROFILE, PASSWORD, MOBILE, PURCHASE }

@Composable
private fun AccountBalance(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color(0xFFACCED9), style = MaterialTheme.typography.labelSmall)
        Text(accountNumber.format(value), color = accountGold,
            fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AccountActionTile(
    title: String, subtitle: String, icon: ImageVector,
    surface: Color, accent: Color, modifier: Modifier,
    enabled: Boolean, tag: String, onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(143.dp).testTag(tag)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.74f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = accountText, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = accent, style = MaterialTheme.typography.labelSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
