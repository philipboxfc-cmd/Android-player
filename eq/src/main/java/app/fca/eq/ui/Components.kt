@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.fca.eq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fca.eq.audio.EffectChain
import app.fca.eq.data.BAND_FREQS
import app.fca.eq.data.BAND_MAX_DB
import app.fca.eq.data.BAND_MIN_DB
import kotlin.math.ln
import kotlin.math.exp

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
    warn: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = if (warn) Warn else MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChipRow(
    items: List<String>,
    selected: String?,
    onClick: (String) -> Unit,
    onLongClick: ((String) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { name ->
            FilterChip(
                selected = name == selected,
                onClick = { onClick(name) },
                label = { Text(name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        if (trailing != null) item { trailing() }
    }
}

/** Вертикальный слайдер полосы: обычный Slider, повёрнутый на 90°. */
@Composable
fun VerticalSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = BAND_MIN_DB..BAND_MAX_DB,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            },
    )
}

/** Плавная АЧХ над слайдерами. */
@Composable
fun EqCurve(bands: List<Float>, enabled: Boolean, modifier: Modifier = Modifier) {
    val color = if (enabled) Accent else Muted
    Canvas(modifier.fillMaxWidth().height(90.dp)) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, mid), Offset(w, mid), strokeWidth = 2f)
        val lo = ln(BAND_FREQS.first().toFloat() / 1.4f)
        val hi = ln(BAND_FREQS.last().toFloat() * 1.25f)
        val path = Path()
        val fill = Path()
        val steps = 120
        for (i in 0..steps) {
            val hz = exp(lo + (hi - lo) * i / steps)
            val db = EffectChain.interpolate(bands, hz)
            val x = w * i / steps
            val y = mid - (db / BAND_MAX_DB) * (mid - 6f)
            if (i == 0) { path.moveTo(x, y); fill.moveTo(x, mid); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(w, mid)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, color, style = Stroke(width = 5f))
    }
}

@Composable
fun BandSliders(bands: List<Float>, onChange: (Int, Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        bands.forEachIndexed { i, v ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    (if (v > 0) "+" else "") + "%.0f".format(v),
                    fontSize = 11.sp, color = if (v == 0f) Muted else MaterialTheme.colorScheme.primary,
                )
                Box(Modifier.height(190.dp).width(40.dp), contentAlignment = Alignment.Center) {
                    VerticalSlider(value = v, onChange = { onChange(i, kotlin.math.round(it * 2f) / 2f) })
                }
                Text(app.fca.eq.data.bandLabel(BAND_FREQS[i]), fontSize = 11.sp, color = Muted)
            }
        }
    }
}
