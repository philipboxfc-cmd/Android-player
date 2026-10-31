package app.fca.eq.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF00E5B0)
val Accent2 = Color(0xFF40C4FF)
val Bg = Color(0xFF0A0C10)
val CardBg = Color(0xFF14171E)
val CardHigh = Color(0xFF1C2029)
val Muted = Color(0xFF9AA3B2)
val Warn = Color(0xFFFFB74D)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0xFF0F3D33),
    onPrimaryContainer = Color(0xFFCFFFF0),
    secondary = Accent2,
    onSecondary = Color(0xFF00202E),
    secondaryContainer = Color(0xFF12313F),
    onSecondaryContainer = Color(0xFFD6F2FF),
    background = Bg,
    onBackground = Color(0xFFEAEFF5),
    surface = Bg,
    onSurface = Color(0xFFEAEFF5),
    surfaceVariant = CardHigh,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = CardBg,
    surfaceContainer = CardBg,
    surfaceContainerHigh = CardHigh,
    surfaceContainerHighest = Color(0xFF252A35),
    outline = Color(0xFF353B48),
    outlineVariant = Color(0xFF262B35),
)

@Composable
fun EqTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
