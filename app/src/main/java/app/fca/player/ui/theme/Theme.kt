package app.fca.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DefaultAccent = Color(0xFF7C4DFF)
val Night = Color(0xFF0B0B10)
val NightSurface = Color(0xFF14141C)
val NightSurfaceHigh = Color(0xFF1C1C26)
val NightSurfaceHigher = Color(0xFF262633)
val OnNight = Color(0xFFEEEEF5)
val OnNightMuted = Color(0xFFA8A8B8)

/** Чёрный или белый — в зависимости от яркости фона. */
fun Color.readableOn(): Color = if (luminance() > 0.55f) Color(0xFF111111) else Color.White

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Делает цвет пригодным для акцента на тёмном фоне: подтягивает слишком тёмные/серые цвета. */
fun Color.asAccent(): Color {
    val lum = luminance()
    return when {
        lum < 0.08f -> copy(alpha = 1f).compositeOver(Color.White).let { Color(it.red * 0.7f + 0.15f, it.green * 0.7f + 0.15f, it.blue * 0.7f + 0.15f) }
        else -> this
    }
}

fun vibeColorScheme(accent: Color): ColorScheme {
    val a = accent.asAccent()
    return darkColorScheme(
        primary = a,
        onPrimary = a.readableOn(),
        primaryContainer = a.copy(alpha = 0.28f).compositeOver(NightSurface),
        onPrimaryContainer = OnNight,
        inversePrimary = a,
        secondary = a.copy(alpha = 0.85f).compositeOver(NightSurfaceHigh),
        onSecondary = OnNight,
        secondaryContainer = a.copy(alpha = 0.18f).compositeOver(NightSurfaceHigh),
        onSecondaryContainer = OnNight,
        tertiary = a,
        onTertiary = a.readableOn(),
        tertiaryContainer = a.copy(alpha = 0.22f).compositeOver(NightSurface),
        onTertiaryContainer = OnNight,
        background = Night,
        onBackground = OnNight,
        surface = Night,
        onSurface = OnNight,
        surfaceVariant = NightSurfaceHigh,
        onSurfaceVariant = OnNightMuted,
        surfaceContainerLowest = Night,
        surfaceContainerLow = NightSurface,
        surfaceContainer = NightSurface,
        surfaceContainerHigh = NightSurfaceHigh,
        surfaceContainerHighest = NightSurfaceHigher,
        surfaceTint = a,
        outline = Color(0xFF3A3A48),
        outlineVariant = Color(0xFF2A2A36),
        error = Color(0xFFFF6B6B),
        onError = Color.Black,
        errorContainer = Color(0xFF4A1C1C),
        onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color.Black,
    )
}

val FcaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = Typography()
val FcaTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun FcaTheme(accent: Color = DefaultAccent, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = vibeColorScheme(accent),
        shapes = FcaShapes,
        typography = FcaTypography,
        content = content,
    )
}
