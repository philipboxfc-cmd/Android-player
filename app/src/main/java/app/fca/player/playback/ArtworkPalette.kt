package app.fca.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Достаёт «вайбовый» цвет из обложки (Palette) и кэширует результат по URI. */
object ArtworkPalette {

    private val cache = LruCache<String, Int>(128)
    private val misses = HashSet<String>()

    suspend fun colorFor(context: Context, uri: Uri): Int? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        cache.get(key)?.let { return@withContext it }
        synchronized(misses) { if (key in misses) return@withContext null }

        val bitmap = decodeSmall(context, uri)
        if (bitmap == null) {
            synchronized(misses) { misses.add(key) }
            return@withContext null
        }
        val palette = Palette.from(bitmap).maximumColorCount(24).generate()
        bitmap.recycle()
        val color = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
        if (color != null) cache.put(key, color) else synchronized(misses) { misses.add(key) }
        color
    }

    private fun decodeSmall(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 160 || bounds.outHeight / sample > 160) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}
