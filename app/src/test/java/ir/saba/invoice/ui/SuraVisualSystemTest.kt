package ir.saba.invoice.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class SuraVisualSystemTest {
    private fun contrast(a:Color,b:Color):Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+0.05f)/(minOf(x,y)+0.05f)
    }
    @Test fun coreTextAndActionColorsMeetNormalTextContrast() {
        val pairs=listOf(
            SuraPalette.Navy to SuraPalette.Canvas,
            SuraPalette.Navy to Color.White,
            SuraPalette.Muted to Color.White,
            SuraPalette.Muted to SuraPalette.Soft,
            Color.White to SuraPalette.Teal,
            SuraPalette.Gold to SuraPalette.Midnight,
            SuraPalette.OnDark to SuraPalette.Ocean
        )
        pairs.forEach { (foreground,background)->
            assertTrue("Insufficient contrast: ${contrast(foreground,background)}",contrast(foreground,background)>=4.5f)
        }
    }
}
