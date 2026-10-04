package app.fca.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.data.model.EqSettings
import app.fca.player.di.LocalAppContainer
import app.fca.player.playback.AudioEffects
import app.fca.player.ui.components.FcaTopBar
import app.fca.player.ui.components.SectionTitle
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val settings by container.settings.eq.collectAsStateWithLifecycle()
    val bands by AudioEffects.bands.collectAsStateWithLifecycle()
    val presets by AudioEffects.presets.collectAsStateWithLifecycle()
    val available by AudioEffects.available.collectAsStateWithLifecycle()

    fun commit(new: EqSettings) {
        container.settings.saveEq(new)
        AudioEffects.apply(new)
    }

    // Текущие уровни полос (пока тянем слайдер — локально, сохраняем по отпусканию)
    val storedLevels = remember(settings.bandLevels, bands) { bands.map { b -> settings.bandLevels.getOrNull(b.index) ?: 0 } }
    var liveLevels by remember { mutableStateOf(storedLevels) }
    LaunchedEffect(storedLevels) { liveLevels = storedLevels }

    val enabled = settings.enabled && available

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FcaTopBar("Эквалайзер", onBack = { nav.popBackStack() }, actions = {
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { commit(settings.copy(enabled = it)) },
                    modifier = Modifier.padding(end = 12.dp),
                )
            })
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            if (!available) {
                Card(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Text(
                        "Эквалайзер подключится к плееру через секунду после запуска. Если на этом устройстве системные аудиоэффекты недоступны — настройки сохранятся, но звук не изменится.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Пресеты
            SectionTitle("Пресеты")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = settings.presetIndex < 0,
                        onClick = { commit(settings.copy(presetIndex = -1, bandLevels = liveLevels)) },
                        label = { Text("Свой") },
                        enabled = enabled,
                    )
                }
                items(presets.indices.toList()) { i ->
                    FilterChip(
                        selected = settings.presetIndex == i,
                        onClick = {
                            val levels = AudioEffects.presetLevels(i) ?: liveLevels
                            commit(settings.copy(presetIndex = i, bandLevels = levels))
                        },
                        label = { Text(presets[i]) },
                        enabled = enabled,
                    )
                }
            }

            // Полосы
            SectionTitle("Частоты", Modifier.padding(top = 8.dp)) {
                OutlinedButton(
                    enabled = enabled,
                    onClick = { commit(settings.copy(presetIndex = -1, bandLevels = bands.map { 0 })) },
                ) { Text("Сбросить") }
            }
            if (bands.isEmpty()) {
                Text(
                    "Нет данных о полосах",
                    Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val maxDb = (bands.first().maxLevel / 100f).roundToInt()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(300.dp)) {
                    bands.forEach { band ->
                        val level = liveLevels.getOrNull(band.index) ?: 0
                        Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${if (level > 0) "+" else ""}${(level / 100f).let { if (abs(it) < 10) "%.1f".format(it) else it.roundToInt().toString() }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            VerticalSlider(
                                value = level.toFloat(),
                                valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                                enabled = enabled,
                                onValueChange = { v ->
                                    val newLevels = liveLevels.toMutableList().also { it[band.index] = v.roundToInt() }
                                    liveLevels = newLevels
                                    AudioEffects.apply(settings.copy(presetIndex = -1, bandLevels = newLevels))
                                },
                                onValueChangeFinished = {
                                    commit(settings.copy(presetIndex = -1, bandLevels = liveLevels))
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            Text(band.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(
                    "Диапазон ±$maxDb дБ",
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Эффекты
            SectionTitle("Эффекты", Modifier.padding(top = 16.dp))
            EffectSlider(
                title = "Усиление басов",
                value = settings.bassBoost,
                max = 1000,
                enabled = enabled,
                valueLabel = { "${it / 10}%" },
                onChange = { commit(settings.copy(bassBoost = it)) },
            )
            EffectSlider(
                title = "Объёмный звук",
                value = settings.virtualizer,
                max = 1000,
                enabled = enabled,
                valueLabel = { "${it / 10}%" },
                onChange = { commit(settings.copy(virtualizer = it)) },
            )
            EffectSlider(
                title = "Громкость (loudness)",
                value = settings.loudness,
                max = 1500,
                enabled = enabled,
                valueLabel = { "+${it / 100} дБ" },
                onChange = { commit(settings.copy(loudness = it)) },
            )
        }
    }
}

@Composable
private fun EffectSlider(
    title: String,
    value: Int,
    max: Int,
    enabled: Boolean,
    valueLabel: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    var live by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel(live.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onChange(live.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
        )
    }
}

/** Вертикальный слайдер на Canvas с нулевой отметкой посередине. */
@Composable
fun VerticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val thumb = MaterialTheme.colorScheme.onSurface
    val alpha = if (enabled) 1f else 0.4f
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f

    Canvas(
        modifier.pointerInput(enabled, valueRange) {
            if (!enabled) return@pointerInput
            val pad = 14.dp.toPx()
            fun yToValue(y: Float): Float {
                val h = (size.height - 2 * pad).coerceAtLeast(1f)
                val frac = 1f - ((y - pad) / h).coerceIn(0f, 1f)
                return valueRange.start + frac * span
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                onValueChange(yToValue(down.position.y))
                drag(down.id) { change ->
                    onValueChange(yToValue(change.position.y))
                    change.consume()
                }
                onValueChangeFinished()
            }
        },
    ) {
        val pad = 14.dp.toPx()
        val cx = size.width / 2f
        val top = pad
        val bottom = size.height - pad
        val trackW = 6.dp.toPx()
        drawRoundRect(
            color = inactive,
            topLeft = Offset(cx - trackW / 2f, top),
            size = Size(trackW, bottom - top),
            cornerRadius = CornerRadius(trackW / 2f),
            alpha = alpha,
        )
        val zeroFrac = ((0f - valueRange.start) / span).coerceIn(0f, 1f)
        val zeroY = bottom - zeroFrac * (bottom - top)
        val frac = ((value - valueRange.start) / span).coerceIn(0f, 1f)
        val y = bottom - frac * (bottom - top)
        drawRoundRect(
            color = active,
            topLeft = Offset(cx - trackW / 2f, min(y, zeroY)),
            size = Size(trackW, abs(zeroY - y)),
            cornerRadius = CornerRadius(trackW / 2f),
            alpha = alpha,
        )
        // Нулевая отметка
        drawLine(Color.White.copy(alpha = 0.35f * alpha), Offset(cx - 10.dp.toPx(), zeroY), Offset(cx + 10.dp.toPx(), zeroY), strokeWidth = 1.dp.toPx())
        drawCircle(thumb, radius = 11.dp.toPx(), center = Offset(cx, y), alpha = alpha)
        drawCircle(active, radius = 6.dp.toPx(), center = Offset(cx, y), alpha = alpha)
    }
}
