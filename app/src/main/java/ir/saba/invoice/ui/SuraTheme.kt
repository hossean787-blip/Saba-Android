package ir.saba.invoice.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.saba.invoice.R

/** Shared visual tokens; no accounting or status logic. */
internal object SuraPalette {
    val Midnight=Color(0xFF071F32)
    val Navy=Color(0xFF12354B)
    val Ocean=Color(0xFF17616D)
    val Teal=Color(0xFF087D8A)
    val Gold=Color(0xFFEBCB90)
    val Canvas=Color(0xFFF2F9FF)
    val Muted=Color(0xFF536D7D)
    val Edge=Color(0xFFD8E6EC)
    val Soft=Color(0xFFE5F3F7)
    val OnDark=Color(0xFFD2EDF3)
}

private val suraFamily=FontFamily(Font(R.font.vazirmatn_regular),Font(R.font.vazirmatn_bold,FontWeight.Bold))
private val base=Typography()
private val suraTypography=base.copy(
    displayLarge=base.displayLarge.copy(fontFamily=suraFamily),
    displayMedium=base.displayMedium.copy(fontFamily=suraFamily),
    displaySmall=base.displaySmall.copy(fontFamily=suraFamily),
    headlineLarge=base.headlineLarge.copy(fontFamily=suraFamily),
    headlineMedium=base.headlineMedium.copy(fontFamily=suraFamily),
    headlineSmall=base.headlineSmall.copy(fontFamily=suraFamily),
    titleLarge=base.titleLarge.copy(fontFamily=suraFamily),
    titleMedium=base.titleMedium.copy(fontFamily=suraFamily),
    titleSmall=base.titleSmall.copy(fontFamily=suraFamily),
    bodyLarge=base.bodyLarge.copy(fontFamily=suraFamily),
    bodyMedium=base.bodyMedium.copy(fontFamily=suraFamily),
    bodySmall=base.bodySmall.copy(fontFamily=suraFamily),
    labelLarge=base.labelLarge.copy(fontFamily=suraFamily),
    labelMedium=base.labelMedium.copy(fontFamily=suraFamily),
    labelSmall=base.labelSmall.copy(fontFamily=suraFamily)
)
private val suraColors=lightColorScheme(
    primary=SuraPalette.Teal,onPrimary=Color.White,
    primaryContainer=SuraPalette.Soft,onPrimaryContainer=SuraPalette.Navy,
    secondary=SuraPalette.Ocean,onSecondary=Color.White,
    secondaryContainer=SuraPalette.Soft,onSecondaryContainer=SuraPalette.Navy,
    tertiary=Color(0xFF805A1B),onTertiary=Color.White,
    tertiaryContainer=Color(0xFFFFF3DE),onTertiaryContainer=Color(0xFF5C3B00),
    background=SuraPalette.Canvas,onBackground=SuraPalette.Navy,
    surface=Color.White,onSurface=SuraPalette.Navy,
    surfaceVariant=SuraPalette.Soft,onSurfaceVariant=SuraPalette.Muted,
    outline=Color(0xFF748E9A),outlineVariant=SuraPalette.Edge,
    error=Color(0xFFAA343D),onError=Color.White,
    errorContainer=Color(0xFFFCE8E9),onErrorContainer=Color(0xFF711C28)
)
private val suraShapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),
    medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(28.dp))

@Composable
fun SuraTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=suraColors,typography=suraTypography,shapes=suraShapes,content=content)
}
