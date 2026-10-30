package ir.saba.invoice.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.saba.invoice.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The introductory animation uses the application's existing, owner-approved brand PNG.
 * It does not load media, send network requests, or retain user information.
 */
@Composable
fun StartupLogoMotion(onFinished: () -> Unit) {
    val context = LocalContext.current
    val original = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.sura_brand_icon)
    }
    // The launcher icon keeps its approved framed appearance; the cinematic
    // introduction extracts only the central mark (without frame or navy matte).
    val symbol = remember(original) { original?.let(::cutoutSuraSymbol) }
    val particles = remember(symbol) {
        if (symbol != null) sampleLogoParticles(symbol) else emptyList()
    }
    val progress = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            onFinished()
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 3100, easing = LinearEasing))
        delay(200)
        finish()
    }

    val fraction = progress.value
    val gather = logoGather(fraction)
    val reveal = logoReveal(fraction)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(
                0f to Color(0xFF163D60),
                0.52f to Color(0xFF0B223D),
                1f to Color(0xFF050D1E)
            )
        )
    ) {
        val emblemSide = minOf(maxWidth * 0.70f, maxHeight * 0.39f, 310.dp)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val emblemPx = with(density) { emblemSide.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x5537B8EB), Color(0x182C80C6), Color.Transparent),
                    center = center,
                    radius = radius * 0.78f
                ),
                radius = radius * 0.78f,
                center = center
            )
            val spread = radius * 0.83f
            val fade = (1f - reveal * 0.92f).coerceIn(0f, 1f)
            particles.forEachIndexed { index, part ->
                val drift = (1f - gather) * 25f * sin(fraction * 15f + index * 0.043f)
                val endX = center.x + part.targetX * emblemPx
                val endY = center.y + part.targetY * emblemPx
                val x = endX + (center.x + part.startX * spread - endX) * (1f - gather) + drift
                val y = endY + (center.y + part.startY * spread - endY) * (1f - gather)
                val color = when (part.tone) {
                    0 -> Color(0xFFE9D49A)
                    1 -> Color(0xFF78DCF9)
                    else -> Color(0xFFF6FAFF)
                }
                drawCircle(
                    color = color,
                    radius = part.radius * (0.75f + fraction * 0.5f),
                    center = Offset(x, y),
                    alpha = (fade * part.brightness * (fraction * 3f).coerceIn(0f, 1f))
                        .coerceIn(0f, 1f)
                )
            }
        }

        if (symbol != null) {
            Image(
                bitmap = symbol.asImageBitmap(),
                contentDescription = "نشان سورا",
                modifier = Modifier.align(Alignment.Center).size(emblemSide).graphicsLayer {
                    alpha = reveal
                    val scale = 0.94f + 0.06f * reveal
                    scaleX = scale
                    scaleY = scale
                }
            )
        }

        Text(
            text = "صبا گستر اراک",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE2EAF4).copy(alpha = reveal),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
        TextButton(
            onClick = { finish() },
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCFDEEA))
        ) {
            Text("رد کردن معرفی")
        }
    }
}

/** Pure, deterministic timeline functions, independently covered by JVM tests. */
internal fun logoGather(progress: Float): Float {
    val t = ((progress - 0.06f) / 0.77f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun logoReveal(progress: Float): Float =
    ((progress - 0.62f) / 0.30f).coerceIn(0f, 1f)

/**
 * Builds an in-memory, square transparent mark from the source launcher image.
 * Keep the original icon untouched: its gold launcher border is intentionally
 * absent from the animation. The crop excludes the frame before alpha keying.
 */
internal fun cutoutSuraSymbol(icon: Bitmap): Bitmap {
    val side = min(icon.width, icon.height)
    val crop = (side * 0.12f).toInt()
    val outputSide = side - crop * 2
    if (outputSide <= 0) return icon
    val offsetX = (icon.width - outputSide) / 2
    val offsetY = (icon.height - outputSide) / 2
    val source = IntArray(outputSide * outputSide)
    val result = IntArray(source.size)
    icon.getPixels(source, 0, outputSide, offsetX, offsetY, outputSide, outputSide)
    source.forEachIndexed { index, pixel ->
        val strength = max(AndroidColor.red(pixel), AndroidColor.green(pixel))
        // The original matte is dark navy; preserve fully saturated gold and cyan
        // while feathering only their anti-aliased edges.
        val opacity = ((strength - 45) * 4).coerceIn(0, 255)
        val alpha = opacity * AndroidColor.alpha(pixel) / 255
        result[index] = (pixel and 0x00FFFFFF) or (alpha shl 24)
    }
    return Bitmap.createBitmap(outputSide, outputSide, Bitmap.Config.ARGB_8888).apply {
        setPixels(result, 0, outputSide, 0, 0, outputSide, outputSide)
    }
}

private data class LogoParticle(
    val targetX: Float, val targetY: Float,
    val startX: Float, val startY: Float,
    val radius: Float, val brightness: Float, val tone: Int
)

/** Sample visible logo pixels; use corner-color exclusion for fully opaque PNG artwork. */
private fun sampleLogoParticles(bitmap: Bitmap): List<LogoParticle> {
    val random = Random(20261030)
    val candidates = ArrayList<Pair<Float, Float>>()
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return emptyList()
    val corner = bitmap.getPixel(0, 0)
    val corners = intArrayOf(
        corner, bitmap.getPixel(width - 1, 0),
        bitmap.getPixel(0, height - 1), bitmap.getPixel(width - 1, height - 1)
    )
    val transparentArtwork = corners.any { AndroidColor.alpha(it) < 90 }
    val stride = (min(width, height) / 120).coerceAtLeast(2)
    for (y in 0 until height step stride) {
        for (x in 0 until width step stride) {
            val pixel = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 110) continue
            val difference = abs(AndroidColor.red(pixel) - AndroidColor.red(corner)) +
                abs(AndroidColor.green(pixel) - AndroidColor.green(corner)) +
                abs(AndroidColor.blue(pixel) - AndroidColor.blue(corner))
            if (transparentArtwork || difference > 90) {
                candidates.add(Pair(x.toFloat() / width - 0.5f, y.toFloat() / height - 0.5f))
            }
        }
    }
    if (candidates.isEmpty()) candidates.add(Pair(0f, 0f))
    return List(960) { index ->
        val target = candidates[random.nextInt(candidates.size)]
        val theta = random.nextFloat() * 2f * PI.toFloat()
        val distance = 0.15f + random.nextFloat() * 0.85f
        LogoParticle(
            targetX = target.first, targetY = target.second,
            startX = cos(theta) * distance, startY = sin(theta) * distance,
            radius = 0.7f + random.nextFloat() * 1.6f,
            brightness = 0.48f + random.nextFloat() * 0.52f,
            tone = index % 3
        )
    }
}
