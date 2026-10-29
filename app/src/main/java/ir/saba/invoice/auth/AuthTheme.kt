package ir.saba.invoice.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import ir.saba.invoice.R
import androidx.compose.ui.unit.dp

@Composable
fun AuthTheme(content: @Composable () -> Unit) {
    val family = FontFamily(Font(R.font.vazirmatn_regular), Font(R.font.vazirmatn_bold, FontWeight.Bold))
    val base = Typography()
    MaterialTheme(
        typography = base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family)
        ),
        colorScheme = lightColorScheme(
            primary = Color(0xFF075F91), onPrimary = Color.White,
            primaryContainer = Color(0xFFD9F1FF), onPrimaryContainer = Color(0xFF123C59),
            outline = Color(0xFF6B8EA5), outlineVariant = Color(0xFFD4E8F4),
            secondary = Color(0xFF2378A3), background = Color(0xFFF2F9FF),
            surface = Color.White, onSurface = Color(0xFF172C3D),
            surfaceVariant = Color(0xFFE7F4FD), onSurfaceVariant = Color(0xFF456176)
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}

@Composable
internal fun AuthLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().background(Brush.linearGradient(
                listOf(Color(0xFFDDF3FF), Color(0xFF8DD5FA), Color(0xFFC3EAFF))), RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("سورا", style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF103C59), fontWeight = FontWeight.Bold)
            Text("مدیریت یکپارچه صورتحساب", color = Color(0xFF234F6B),
                style = MaterialTheme.typography.bodyMedium)
            Text("دقیق • ساده • همراه شما", color = Color(0xFF285B78),
                style = MaterialTheme.typography.labelMedium)
        }
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4E8F4))
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
        Text("سورا | صبا گستر اراک", Modifier.padding(bottom = 20.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
