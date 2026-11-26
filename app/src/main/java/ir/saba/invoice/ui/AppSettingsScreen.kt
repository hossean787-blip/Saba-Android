package ir.saba.invoice.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val settingsInk = SuraPalette.Navy
private val settingsMuted = SuraPalette.Muted
private val settingsBlue = SuraPalette.Teal
private val settingsTeal = SuraPalette.Teal
private val settingsCanvas = SuraPalette.Canvas
private val settingsBorder = SuraPalette.Edge

enum class SettingsDestination { TAXPAYERS, TAX_CONNECTION, BACKUP, RESTORE, SUPPORT, PRIVACY, TERMS, ABOUT, ACCOUNT }

data class SettingsControls(
    val protectScreen:Boolean,val playIntro:Boolean,val biometricAvailable:Boolean,
    val onProtectScreen:(Boolean)->Unit,val onPlayIntro:(Boolean)->Unit,val onBiometricSetup:()->Unit,
    val onNavigate:(SettingsDestination)->Unit,val onSystemSettings:()->Unit,
    val diagnosticResult:String,val onDiagnostic:()->Unit,val onResetAppearance:()->Unit,val onLogout:()->Unit,
    val integrityResult:String="هنوز بررسی نشده",val onIntegrityCheck:(()->Unit)?=null
)

/** Device appearance and security settings. No financial or account data is cleared here. */
@Composable
fun AppSettingsScreen(
    fontScale: Float, busy: Boolean,
    onFontScale: (Float) -> Unit, onCommitFontScale: () -> Unit,
    onResetFont: () -> Unit,
    biometricEnabled: Boolean, onDisableBiometrics: () -> Unit,
    onBackToAccount: () -> Unit,
    controls: SettingsControls? = null
) {
    val context=LocalContext.current
    val version=remember { runCatching { context.packageManager.getPackageInfo(context.packageName,0).versionName }.getOrNull().orEmpty() }
    var resetConfirm by remember { mutableStateOf(false) }
    var biometricConfirm by remember { mutableStateOf(false) }
    if(resetConfirm) AlertDialog(onDismissRequest={resetConfirm=false},title={Text("بازنشانی ظاهر")},
        text={Text("اندازه متن و لوگوموشن به حالت پیش‌فرض برمی‌گردند. تنظیمات امنیتی، حساب، مؤدیان، فاکتورها و اعتبار دست‌نخورده باقی می‌مانند.")},
        confirmButton={TextButton(enabled=!busy,onClick={resetConfirm=false;controls?.onResetAppearance?.invoke()}){Text("بازنشانی")}},
        dismissButton={TextButton(onClick={resetConfirm=false}){Text("انصراف")}})
    if(biometricConfirm) AlertDialog(onDismissRequest={biometricConfirm=false},title={Text("فعال‌سازی ورود امن")},
        text={Text("برای فعال‌سازی، از حساب خارج شوید و یک‌بار با رمز ثابت وارد شوید. سپس پیشنهاد فعال‌سازی اثر انگشت را تأیید کنید.")},
        confirmButton={TextButton(enabled=!busy,onClick={biometricConfirm=false;controls?.onBiometricSetup?.invoke()}){Text("ادامه")}},
        dismissButton={TextButton(onClick={biometricConfirm=false}){Text("انصراف")}})
    val scale = if (fontScale.isFinite()) fontScale.coerceIn(0.85f, 1.35f) else 1f
    val percentage = (scale * 100).roundToInt()

    Column(
        Modifier.fillMaxSize()
            .background(settingsCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // The header is a branded surface, not a separate app-level banner.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(SuraPalette.Midnight, SuraPalette.Ocean, Color(0xFF16869A))
                    )
                ).testTag("settingsHero")
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-48).dp)
                    .size(152.dp).background(Color.White.copy(alpha = 0.055f), CircleShape)
            )
            Box(
                Modifier.align(Alignment.BottomStart).offset(x = (-44).dp, y = 58.dp)
                    .size(118.dp).background(Color.White.copy(alpha = 0.045f), CircleShape)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 23.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(58.dp)
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(19.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings, contentDescription = null,
                        tint = SuraPalette.Gold, modifier = Modifier.size(32.dp)
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "تنظیمات برنامه",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold, color = Color.White
                    )
                    Text(
                        "ظاهر و امنیت سورا، مطابق نیاز شما",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuraPalette.OnDark
                    )
                }
            }
        }

        Text(
            "شخصی‌سازی",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = settingsInk,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
        SettingsPanel {
            SettingsPanelHeading(
                title = "تنظیمات نمایش",
                description = "اندازه نوشته‌ها را برای مطالعه راحت‌تر تنظیم کنید.",
                iconColor = settingsBlue
            )
            HorizontalDivider(color = settingsBorder)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "اندازه متن برنامه",
                    color = settingsInk, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = SuraPalette.Soft
                ) {
                    Text(
                        "$percentage٪",
                        Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("settingsFontPercent"),
                        color = settingsBlue, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Slider(
                value = scale,
                onValueChange = onFontScale,
                onValueChangeFinished = onCommitFontScale,
                enabled = !busy, valueRange = 0.85f..1.35f,
                modifier = Modifier.fillMaxWidth().testTag("settingsFontSlider"),
                colors = SliderDefaults.colors(
                    thumbColor = settingsBlue,
                    activeTrackColor = settingsBlue,
                    inactiveTrackColor = SuraPalette.Edge
                )
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("کوچک‌تر", style = MaterialTheme.typography.labelSmall, color = settingsMuted)
                Text("پیش‌فرض", style = MaterialTheme.typography.labelSmall, color = settingsMuted)
                Text("بزرگ‌تر", style = MaterialTheme.typography.labelSmall, color = settingsMuted)
            }
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = Color(0xFFF2F7FC),
                border = BorderStroke(1.dp, settingsBorder)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "پیش‌نمایش نوشته‌ها",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = settingsBlue
                    )
                    Text(
                        "صورتحساب سورا، ساده و دقیق",
                        style = MaterialTheme.typography.bodyLarge,
                        color = settingsInk
                    )
                    Text(
                        "تغییر اندازه متن در بخش‌های برنامه اعمال می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = settingsMuted
                    )
                }
            }
            OutlinedButton(
                onClick = onResetFont, enabled = !busy && percentage != 100,
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth().testTag("settingsFontReset"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = settingsBlue),
                border = BorderStroke(1.dp, settingsBorder)
            ) {
                Text("بازگردانی اندازه پیش‌فرض", fontWeight = FontWeight.SemiBold)
            }
            if(controls!=null) {
                HorizontalDivider(color=settingsBorder)
                SettingsSwitch("نمایش لوگوموشن آغاز برنامه","در اجرای سرد بعدی برنامه اعمال می‌شود.",controls.playIntro,!busy,
                    controls.onPlayIntro,"settingsIntro")
                TextButton(onClick={resetConfirm=true},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("settingsResetAppearance")) {
                    Text("بازنشانی تنظیمات ظاهری")
                }
            }
        }

        Text(
            "حریم خصوصی و امنیت",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = settingsInk,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
        SettingsPanel {
            SettingsPanelHeading(
                title = "ورود با اثر انگشت",
                description = "دسترسی امن و سریع به حساب در همین دستگاه",
                iconColor = settingsTeal
            )
            Surface(
                color = if (biometricEnabled) Color(0xFFE7F5F2) else Color(0xFFF2F6FA),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.AccountCircle, contentDescription = null,
                        tint = if (biometricEnabled) settingsTeal else settingsMuted,
                        modifier = Modifier.size(23.dp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (biometricEnabled) "ورود بیومتریک فعال است" else "ورود بیومتریک غیرفعال است",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = settingsInk,
                            modifier = Modifier.testTag("settingsBiometricStatus")
                        )
                        Text(
                            if (biometricEnabled)
                                "اطلاعات ورود به‌صورت رمزنگاری‌شده در همین گوشی نگهداری می‌شود."
                            else "پس از ورود با رمز، در صورت پشتیبانی دستگاه می‌توانید آن را فعال کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = settingsMuted
                        )
                    }
                }
            }
            if (biometricEnabled) {
                OutlinedButton(
                    onClick = onDisableBiometrics,
                    enabled = !busy,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settingsBiometricDisable"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF985F41)),
                    border = BorderStroke(1.dp, Color(0xFFEBDCD2))
                ) {
                    Text("غیرفعال‌کردن ورود با اثر انگشت")
                }
            } else if(controls!=null) {
                OutlinedButton(onClick={biometricConfirm=true},enabled=!busy&&controls.biometricAvailable,
                    modifier=Modifier.fillMaxWidth().testTag("settingsBiometricEnable")) {
                    Text("فعال‌سازی ورود با اثر انگشت")
                }
                if(!controls.biometricAvailable) Text("اثر انگشت قوی در این دستگاه آماده نیست؛ ابتدا تنظیمات امنیتی گوشی را بررسی کنید.",
                    style=MaterialTheme.typography.bodySmall,color=settingsMuted)
            }
            if(controls!=null) {
                HorizontalDivider(color=settingsBorder)
                SettingsSwitch("محافظت از صفحه","جلوگیری از ثبت اسکرین‌شات و ضبط صفحه سورا؛ پیش‌نمایش برنامه در پس‌زمینه همیشه محافظت می‌شود.",
                    controls.protectScreen,!busy,controls.onProtectScreen,"settingsProtectScreen")
                Text("این تنظیمات مخصوص همین دستگاه است.",style=MaterialTheme.typography.bodySmall,color=settingsMuted)
                controls.onIntegrityCheck?.let { checkIntegrity ->
                    HorizontalDivider(color=settingsBorder)
                    Text("احراز اصالت برنامه و دستگاه",fontWeight=FontWeight.SemiBold,color=settingsInk)
                    Text(controls.integrityResult,style=MaterialTheme.typography.bodySmall,color=settingsMuted,
                        modifier=Modifier.testTag("settingsIntegrityResult"))
                    OutlinedButton(onClick=checkIntegrity,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("settingsIntegrityCheck")) {
                        Text("بررسی اصالت نسخه نصب‌شده")
                    }
                    Text("نتیجه معتبر اخیر تا ۳۰ ثانیه نمایش داده می‌شود. اختلال یا محدودیت زمانی به معنی جعلی بودن برنامه نیست. این بررسی فعلاً دسترسی حساب را مسدود نمی‌کند.",
                        style=MaterialTheme.typography.labelSmall,color=settingsMuted)
                }
                SettingsLink("اطلاعات حساب کاربری","مشخصات و گزینه‌های حساب",Icons.Default.Person,!busy) { controls.onNavigate(SettingsDestination.ACCOUNT) }
                SettingsLink("تنظیمات برنامه در گوشی","مدیریت مجوزها و فضای ذخیره‌سازی در اندروید",Icons.Default.Settings,!busy,controls.onSystemSettings)
            }
        }

        Text("اتصال و اطلاعات مالیاتی",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=settingsInk)
        SettingsPanel {
            SettingsPanelHeading(
                title = "اتصال سامانه مؤدیان",
                description = "وضعیت اتصال هر مؤدی در بخش مخصوص همان مؤدی مدیریت می‌شود.",
                iconColor = settingsBlue
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    Icons.Default.Info, contentDescription = null,
                    tint = settingsTeal, modifier = Modifier.size(19.dp)
                )
                Text(
                    "برای مدیریت کلید خصوصی و شناسه یکتا به بخش «مودیان» بروید. ارسال از صفحه «ارسال رسمی صورتحساب» انجام می‌شود. ذخیره فاکتور با پذیرش مالیاتی یکسان نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = settingsMuted,
                    textAlign = TextAlign.Start
                )
            }
            if(controls!=null) {
                HorizontalDivider(color=settingsBorder)
                SettingsLink("مدیریت مؤدیان","اطلاعات و کلیدهای هر مؤدی",Icons.Default.Person,!busy){controls.onNavigate(SettingsDestination.TAXPAYERS)}
                SettingsLink("وضعیت اتصال مالیاتی","بازکردن پرونده اتصال مؤدی انتخاب‌شده",Icons.Default.CheckCircle,!busy){controls.onNavigate(SettingsDestination.TAX_CONNECTION)}
                HorizontalDivider(color=settingsBorder)
                Text("بررسی ارتباط با سرور سورا",fontWeight=FontWeight.SemiBold,color=settingsInk)
                Text(controls.diagnosticResult,style=MaterialTheme.typography.bodySmall,color=settingsMuted,modifier=Modifier.testTag("settingsDiagnosticResult"))
                OutlinedButton(onClick=controls.onDiagnostic,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("settingsDiagnostic")) {
                    Text("استعلام اتصال سرور")
                }
                Text("این استعلام فقط سرویس اعتبار و نشست حساب را بررسی می‌کند، نه پذیرش مالیاتی.",style=MaterialTheme.typography.labelSmall,color=settingsMuted)
            }
        }

        if(controls!=null) {
            Text("مدیریت داده‌ها",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=settingsInk)
            SettingsPanel {
                SettingsLink("پشتیبان‌گیری","تهیه فایل رمزگذاری‌شده حساب",Icons.Default.Share,!busy){controls.onNavigate(SettingsDestination.BACKUP)}
                HorizontalDivider(color=settingsBorder)
                SettingsLink("بازیابی اطلاعات","بازگردانی اطلاعات همان حساب",Icons.Default.Refresh,!busy){controls.onNavigate(SettingsDestination.RESTORE)}
            }
            Text("راهنما و اطلاعات برنامه",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=settingsInk)
            SettingsPanel {
                SettingsLink("مرکز پشتیبانی","ثبت درخواست و پیگیری پاسخ",Icons.Default.Email,!busy){controls.onNavigate(SettingsDestination.SUPPORT)}
                HorizontalDivider(color=settingsBorder)
                SettingsLink("حریم خصوصی","نحوه نگهداری و پردازش اطلاعات",Icons.Default.Lock,!busy){controls.onNavigate(SettingsDestination.PRIVACY)}
                HorizontalDivider(color=settingsBorder)
                SettingsLink("قوانین و شرایط استفاده","نسخه "+ir.saba.invoice.auth.SuraLegal.VERSION,Icons.Default.CheckCircle,!busy){controls.onNavigate(SettingsDestination.TERMS)}
                HorizontalDivider(color=settingsBorder)
                SettingsLink("درباره سورا","نسخه "+version.ifBlank{"نامشخص"},Icons.Default.Info,!busy){controls.onNavigate(SettingsDestination.ABOUT)}
            }
            OutlinedButton(onClick=controls.onLogout,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("settingsLogout"),
                colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text("خروج از حساب کاربری") }
        }

        OutlinedButton(
            onClick = onBackToAccount,
            enabled = !busy,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .testTag("settingsBackToAccount"),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White, contentColor = settingsBlue
            ),
            border = BorderStroke(1.dp, settingsBorder)
        ) {
            Text("بازگشت به حساب کاربری", fontWeight = FontWeight.Bold)
        }
        Text(
            "سورا | صبا گستر اراک",
            style = MaterialTheme.typography.labelSmall,
            color = settingsMuted,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsPanelHeading(title: String, description: String, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            Modifier.size(43.dp).background(iconColor.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (title == "تنظیمات نمایش") Icons.Default.Settings else Icons.Default.Info,
                contentDescription = null, tint = iconColor,
                modifier = Modifier.size(23.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = settingsInk, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = settingsMuted)
        }
    }
}

@Composable
private fun SettingsSwitch(title:String,description:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit,tag:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text(title,color=settingsInk,fontWeight=FontWeight.SemiBold)
            Text(description,style=MaterialTheme.typography.bodySmall,color=settingsMuted)
        }
        Switch(checked=checked,onCheckedChange=onChange,enabled=enabled,modifier=Modifier.testTag(tag))
    }
}

@Composable
private fun SettingsLink(title:String,description:String,icon:ImageVector,enabled:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled=enabled,onClick=onClick).padding(6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(42.dp).background(settingsBlue.copy(alpha=.09f),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){
            Icon(icon,null,tint=settingsBlue)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(title,color=settingsInk,fontWeight=FontWeight.SemiBold)
            Text(description,color=settingsMuted,style=MaterialTheme.typography.bodySmall)
        }
        Text("‹",color=settingsMuted,style=MaterialTheme.typography.headlineSmall)
    }
}
