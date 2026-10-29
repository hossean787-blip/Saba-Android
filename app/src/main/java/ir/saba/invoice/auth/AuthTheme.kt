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
import androidx.compose.ui.unit.dp

@Composable
fun AuthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF176B68), onPrimary = Color.White,
            secondary = Color(0xFFB59758), background = Color(0xFFF4F7F8),
            surface = Color.White, onSurface = Color(0xFF172C3D),
            surfaceVariant = Color(0xFFE7EEF0), onSurfaceVariant = Color(0xFF4C626C)
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
            Modifier.fillMaxWidth().background(Brush.linearGradient(
                listOf(Color(0xFF102D41), Color(0xFF176B68))))
                .padding(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("صبا", style = MaterialTheme.typography.headlineLarge,
                color = Color.White, fontWeight = FontWeight.Bold)
            Text("مدیریت یکپارچه صورتحساب", color = Color(0xFFD1E9E6),
                style = MaterialTheme.typography.bodyMedium)
            Text("دقیق • ساده • همراه شما", color = Color(0xFFE5C789),
                style = MaterialTheme.typography.labelMedium)
        }
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp), shadowElevation = 3.dp
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
        Text("صبا | حساب کاربری مالک", Modifier.padding(bottom = 20.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
