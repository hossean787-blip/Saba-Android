package ir.saba.invoice.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.saba.invoice.R

internal object SuraTelegram {
    const val CHANNEL_URL = "https://t.me/sura_app"
    fun telegramIntent() = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=sura_app"))
    fun webIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL))
    fun open(context: Context): Boolean {
        for (intent in listOf(telegramIntent(), webIntent())) {
            try {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Telegram is optional; the HTTPS channel is the fallback.
            } catch (_: SecurityException) {
                // A restricted handler must not crash the app.
            }
        }
        return false
    }
}

@Composable
internal fun SuraAboutScreen(onBack: () -> Unit, onSupport: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty().ifBlank { "نامشخص" }
    }
    var feedback by remember { mutableStateOf("") }
    val navy = SuraPalette.Midnight
    val teal = SuraPalette.Teal
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(16.dp).testTag("service-about"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(navy, teal)),
                RoundedCornerShape(28.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                Image(painterResource(R.drawable.sura_brand_icon), contentDescription = "لوگوی سورا",
                    modifier = Modifier.size(100.dp).padding(10.dp))
            }
            Text("صورتحساب سورا | SURA", color = Color.White,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Text("صبا گستر اراک", color = SuraPalette.Soft,
                style = MaterialTheme.typography.titleMedium)
            Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(24.dp)) {
                Text("نسخه " + version, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        AboutPanel("معرفی سورا") {
            Text("سورا با هدف ساده‌سازی مدیریت صورتحساب‌های الکترونیکی، مؤدیان و مشتریان طراحی شده است؛ ابزاری برای حسابداران و کسب‌وکارهایی که به نظم، دقت و مدیریت بهتر امور مالیاتی نیاز دارند.",
                style = MaterialTheme.typography.bodyMedium)
        }
        AboutPanel("خدمات سورا") {
            listOf("مدیریت مستقل مؤدیان و مشتریان", "صدور و پیگیری صورتحساب الکترونیکی",
                "پیش‌نمایش و خروجی PDF", "مدیریت اعتبار و سوابق", "پشتیبان‌گیری رمزگذاری‌شده و پشتیبانی کاربران")
                .forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).background(teal, RoundedCornerShape(50)))
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }
        }
        AboutPanel("کانال تلگرام سورا") {
            Text("اطلاع‌رسانی، آموزش و اخبار به‌روزرسانی برنامه", style = MaterialTheme.typography.bodyMedium)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("aboutTelegramLink")
                    .clickable {
                        feedback = if (SuraTelegram.open(context)) "" else "تلگرام یا مرورگر در دسترس نیست."
                    }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text("ورود به کانال تلگرام", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(SuraTelegram.CHANNEL_URL,
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        AboutPanel("ارتباط با ما") {
            OutlinedButton(onClick = {
                feedback = if (SuraContact.openDialer(context)) "" else "شماره‌گیر در دسترس نیست."
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(SuraContact.PHONE, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
            }
            OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("مرکز پشتیبانی")
            }
        }
        if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.error)
        Text("حقوق نرم‌افزار و هویت بصری سورا مطابق قوانین مالکیت فکری و مجوز اجزای ثالث محفوظ است.",
            modifier = Modifier.fillMaxWidth().padding(4.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت به خانه") }
    }
}

@Composable
private fun AboutPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
