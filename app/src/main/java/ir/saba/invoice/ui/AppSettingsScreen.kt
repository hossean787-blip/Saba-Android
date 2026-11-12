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
import androidx.compose.runtime.Composable
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

private val settingsInk = Color(0xFF102F49)
private val settingsMuted = Color(0xFF647E94)
private val settingsBlue = Color(0xFF155F99)
private val settingsTeal = Color(0xFF137C89)
private val settingsCanvas = Color(0xFFF3F8FD)
private val settingsBorder = Color(0xFFE3EDF5)

/** Settings only: keeps the original callbacks and persisted font/biometric flows. */
@Composable
fun AppSettingsScreen(
    fontScale: Float, busy: Boolean,
    onFontScale: (Float) -> Unit, onCommitFontScale: () -> Unit,
    onResetFont: () -> Unit,
    biometricEnabled: Boolean, onDisableBiometrics: () -> Unit,
    onBackToAccount: () -> Unit
) {
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
                        listOf(Color(0xFF09263F), Color(0xFF155A83), Color(0xFF16869A))
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
                        tint = Color(0xFFF2DAA5), modifier = Modifier.size(32.dp)
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
                        color = Color(0xFFCFEBF6)
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
                    color = Color(0xFFE8F2FF)
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
                    inactiveTrackColor = Color(0xFFD8E5F0)
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
            }
        }

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
