package ir.saba.invoice.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Presentation only: normalize Latin and Arabic-Indic digits to Persian. */
internal fun invoicePdfDisplayDigits(value:String):String = value.map { c ->
    when(c) {
        in '0'..'9' -> '۰'+(c-'0')
        in '٠'..'٩' -> '۰'+(c-'٠')
        else -> c
    }
}.joinToString("")

internal data class PdfZoom(val scale:Float=1f,val x:Float=0f,val y:Float=0f) {
    fun change(factor:Float,pan:Offset,viewport:IntSize):PdfZoom {
        val next=(scale*factor).coerceIn(1f,5f)
        if(next<=1f) return PdfZoom()
        val ratio=next/scale
        val maxX=viewport.width*(next-1f)/2f
        val maxY=viewport.height*(next-1f)/2f
        return PdfZoom(next,(x*ratio+pan.x).coerceIn(-maxX,maxX),(y*ratio+pan.y).coerceIn(-maxY,maxY))
    }
}

@Composable
internal fun ZoomableInvoicePdf(bitmap:Bitmap) {
    var expanded by remember(bitmap){mutableStateOf(false)}
    PdfZoomPanel(bitmap,onExpand={expanded=true})
    if(expanded) Dialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.Center) {
                TextButton(onClick={expanded=false},modifier=Modifier.align(Alignment.End).testTag("pdfCloseExpanded")){Text("بستن نمایش بزرگ")}
                PdfZoomPanel(bitmap,onExpand=null)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfZoomPanel(bitmap:Bitmap,onExpand:(()->Unit)?) {
    var zoom by remember(bitmap){mutableStateOf(PdfZoom())}
    var viewport by remember(bitmap){mutableStateOf(IntSize.Zero)}
    val image=remember(bitmap){bitmap.asImageBitmap()}
    val state=rememberTransformableState{factor,pan,_ ->
        zoom=zoom.change(factor,pan,viewport)
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height)
            .clipToBounds().background(Color.White)
            .onSizeChanged { size->viewport=size;zoom=zoom.change(1f,Offset.Zero,size) }
            .testTag("pdfZoomViewport")
            .transformable(state,canPan={zoom.scale>1f})
            .pointerInput(bitmap) {
                detectTapGestures(onDoubleTap={
                    zoom=if(zoom.scale>1f) PdfZoom() else zoom.change(2.5f,Offset.Zero,viewport)
                })
            },contentAlignment=Alignment.Center) {
            Image(image,"پیش‌نمایش واقعی PDF",contentScale=ContentScale.FillBounds,
                modifier=Modifier.fillMaxSize().graphicsLayer {
                    scaleX=zoom.scale;scaleY=zoom.scale;translationX=zoom.x;translationY=zoom.y
                })
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            TextButton(enabled=zoom.scale>1f,onClick={zoom=zoom.change(1f/1.5f,Offset.Zero,viewport)},
                modifier=Modifier.testTag("pdfZoomOut")){Text("−")}
            Text(invoicePdfDisplayDigits((zoom.scale*100).roundToInt().toString())+"٪",modifier=Modifier.testTag("pdfZoomPercent"))
            TextButton(enabled=zoom.scale<5f,onClick={zoom=zoom.change(1.5f,Offset.Zero,viewport)},
                modifier=Modifier.testTag("pdfZoomIn")){Text("+")}
            TextButton(enabled=zoom.scale>1f,onClick={zoom=PdfZoom()},modifier=Modifier.testTag("pdfZoomReset")){Text("اندازه اصلی")}
        }
        if(onExpand!=null) OutlinedButton(onClick=onExpand,modifier=Modifier.fillMaxWidth().testTag("pdfExpand")){Text("نمایش بزرگ PDF")}
        Text("برای بزرگ‌نمایی دو انگشت را از هم باز کنید؛ با دو ضربه اندازه را تغییر دهید.",
            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
