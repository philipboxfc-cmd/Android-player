package app.fca.player.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fca.player.playback.ArtworkPalette
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

/** Обложка с градиентной заглушкой, если картинки нет. */
@Composable
fun Artwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
    iconSize: Dp = 28.dp,
    seed: Long = uri?.toString()?.hashCode()?.toLong() ?: 0L,
) {
    val context = LocalContext.current
    val fallback = remember(seed) { fallbackBrush(seed) }
    Box(modifier.clip(shape).background(fallback)) {
        if (uri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    else -> FallbackIcon(fallbackIcon, iconSize)
                }
            }
        } else {
            FallbackIcon(fallbackIcon, iconSize)
        }
    }
}

@Composable
private fun FallbackIcon(icon: ImageVector, size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size))
    }
}

private val fallbackPalette = listOf(
    Color(0xFF7C4DFF) to Color(0xFFE040FB),
    Color(0xFF00BCD4) to Color(0xFF3F51B5),
    Color(0xFFFF7043) to Color(0xFFE91E63),
    Color(0xFF26A69A) to Color(0xFF66BB6A),
    Color(0xFFFFA726) to Color(0xFFEF5350),
    Color(0xFF5C6BC0) to Color(0xFF8E24AA),
    Color(0xFF29B6F6) to Color(0xFF7C4DFF),
    Color(0xFFEC407A) to Color(0xFFFF7043),
)

private fun fallbackBrush(seed: Long): Brush {
    val (a, b) = fallbackPalette[((seed % fallbackPalette.size + fallbackPalette.size) % fallbackPalette.size).toInt()]
    return Brush.linearGradient(listOf(a, b))
}

/** Цвет обложки через Palette — для фоновых градиентов экранов. */
@Composable
fun rememberArtworkColor(uri: Uri?): Color? {
    val context = LocalContext.current
    var color by remember(uri) { mutableStateOf<Color?>(null) }
    LaunchedEffect(uri) {
        color = uri?.let { ArtworkPalette.colorFor(context, it) }?.let { Color(it) }
    }
    return color
}
