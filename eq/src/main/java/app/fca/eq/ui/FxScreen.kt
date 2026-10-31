@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.fca.eq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fca.eq.data.AppState
import app.fca.eq.data.PlayerFx
import app.fca.eq.data.Presets
import app.fca.eq.data.Store
import kotlin.math.log2
import kotlin.math.round

@Composable
fun FxScreen(state: AppState, onOpenPlayer: () -> Unit) {
    val fx = state.fx
    var linked by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Эффекты", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Питч, скорость, 8D, эхо, реверб, караоке", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }

        SectionCard(null) {
            Text(
                "Эти эффекты работают для треков во встроенном плеере. Android не даёт менять тон и скорость " +
                    "звука чужих приложений без root — эквалайзер, бас и усиление при этом работают везде.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            TextButton(onClick = onOpenPlayer) { Text("Открыть плеер") }
        }

        ChipRow(
            items = Presets.fx.map { it.first },
            selected = state.fxPresetName,
            onClick = { name ->
                val p = Presets.fx.first { it.first == name }.second
                Store.update { it.copy(fx = p, fxPresetName = name) }
            },
        )
        Spacer(Modifier.height(6.dp))

        SectionCard("Тон и скорость") {
            LabeledSlider(
                "Питч (тон)", fx.pitch, -12f..12f, "%+.1f пт".format(fx.pitch),
                { v -> Store.updateFx { it.copy(pitch = round(v * 10) / 10f) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Store.updateFx { it.copy(pitch = (it.pitch - 1f).coerceAtLeast(-12f)) } }) { Text("−1") }
                OutlinedButton(onClick = { Store.updateFx { it.copy(pitch = 0f) } }) { Text("0") }
                OutlinedButton(onClick = { Store.updateFx { it.copy(pitch = (it.pitch + 1f).coerceAtMost(12f)) } }) { Text("+1") }
            }
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                "Скорость", fx.speed, 0.25f..3f, "×%.2f".format(fx.speed),
                { v ->
                    val s = round(v * 100) / 100f
                    Store.updateFx { if (linked) it.copy(speed = s, pitch = round(12f * log2(s) * 10) / 10f) else it.copy(speed = s) }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    TextButton(onClick = {
                        Store.updateFx { if (linked) it.copy(speed = s, pitch = round(12f * log2(s) * 10) / 10f) else it.copy(speed = s) }
                    }) { Text("×" + if (s % 1f == 0f) s.toInt().toString() else s.toString()) }
                }
            }
            SwitchRow(
                "Как пластинка", linked, { linked = it },
                hint = "Скорость и тон меняются вместе (nightcore / slowed)",
            )
        }

        SectionCard("Пространство") {
            SwitchRow("8D-звук", fx.rate8d > 0f, { on -> Store.updateFx { it.copy(rate8d = if (on) 0.12f else 0f) } },
                hint = "Звук кружит вокруг головы — слушай в наушниках")
            if (fx.rate8d > 0f) LabeledSlider(
                "Скорость вращения", fx.rate8d, 0.03f..0.6f, "%.2f Гц".format(fx.rate8d),
                { v -> Store.updateFx { it.copy(rate8d = v) } },
            )
            LabeledSlider(
                "Ширина стерео", fx.width, 0f..2f,
                if (fx.width < 0.03f) "Моно" else "${(fx.width * 100).toInt()}%",
                { v -> Store.updateFx { it.copy(width = if (kotlin.math.abs(v - 1f) < 0.04f) 1f else v) } },
            )
            SwitchRow("Караоке", fx.karaoke, { on -> Store.updateFx { it.copy(karaoke = on) } },
                hint = "Убирает голос из центра (лучше всего на студийных записях)")
        }

        SectionCard("Реверб") {
            LabeledSlider("Сила", fx.reverbMix, 0f..1f, "${(fx.reverbMix * 100).toInt()}%",
                { v -> Store.updateFx { it.copy(reverbMix = v) } })
            LabeledSlider("Размер зала", fx.roomSize, 0f..1f, "${(fx.roomSize * 100).toInt()}%",
                { v -> Store.updateFx { it.copy(roomSize = v) } })
        }

        SectionCard("Эхо") {
            LabeledSlider("Сила", fx.echoMix, 0f..1f, "${(fx.echoMix * 100).toInt()}%",
                { v -> Store.updateFx { it.copy(echoMix = v) } })
            LabeledSlider("Задержка", fx.echoMs.toFloat(), 50f..1000f, "${fx.echoMs} мс",
                { v -> Store.updateFx { it.copy(echoMs = v.toInt()) } })
            LabeledSlider("Повторы", fx.echoFeedback, 0f..0.9f, "${(fx.echoFeedback * 100).toInt()}%",
                { v -> Store.updateFx { it.copy(echoFeedback = v) } })
        }

        TextButton(
            onClick = { Store.update { it.copy(fx = PlayerFx(), fxPresetName = "Обычный") } },
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
        ) { Text("Сбросить эффекты") }
    }
}
