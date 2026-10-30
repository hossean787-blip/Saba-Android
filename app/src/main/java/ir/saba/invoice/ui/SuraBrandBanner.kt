package ir.saba.invoice.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.saba.invoice.R

/** Shared premium brand header. Taxpayer selector lives only in the dashboard. */
@Composable
fun SuraBrandBanner(subtitle: String, compact: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(if (compact) 20.dp else 28.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF041A2C), Color(0xFF12455E), Color(0xFF061D33))))
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.sura_brand_icon),
            contentDescription = "نشان رسمی سورا",
            modifier = Modifier.size(if (compact) 48.dp else 72.dp).clip(RoundedCornerShape(15.dp))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SURA  |  سورا", color = Color(0xFFEFCF91),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color(0xFFD2EDF3), style = MaterialTheme.typography.labelMedium)
        }
        if (!compact) Text("✦", color = Color(0xFF56D3E4), style = MaterialTheme.typography.headlineMedium)
    }
}
