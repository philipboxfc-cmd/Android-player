@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.fca.eq.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fca.eq.audio.ActiveSession
import app.fca.eq.audio.EqStatus
import app.fca.eq.audio.SessionRegistry
import app.fca.eq.data.AppState
import app.fca.eq.data.EqConfig
import app.fca.eq.data.NamedEq
import app.fca.eq.data.Presets
import app.fca.eq.data.Store

@Composable
fun EqScreen(state: AppState) {
    val eq = state.eq
    var saveDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<NamedEq?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Эквалайзер", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (state.enabled) "Включён · ${state.eqPresetName}" else "Выключен",
                    style = MaterialTheme.typography.bodyMedium, color = Muted,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = { on -> Store.update { it.copy(enabled = on) } })
        }

        ModeCard(state)

        Spacer(Modifier.height(8.dp))
        val custom = state.customPresets
        ChipRow(
            items = Presets.eq.map { it.name } + custom.map { it.name },
            selected = state.eqPresetName,
            onClick = { name ->
                val p = (Presets.eq + custom).firstOrNull { it.name == name } ?: return@ChipRow
                Store.update { it.copy(eq = p.eq.copy(limiter = it.eq.limiter, balance = it.eq.balance), eqPresetName = name) }
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { saveDialog = true }, label = { Text("Сохранить") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) })
                    if (custom.isNotEmpty()) AssistChip(
                        onClick = { deleteTarget = custom.firstOrNull { it.name == state.eqPresetName } ?: custom.last() },
                        label = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    )
                }
            },
        )

        SectionCard(null) {
            EqCurve(eq.bands, state.enabled)
            Spacer(Modifier.height(4.dp))
            BandSliders(eq.bands) { i, v ->
                Store.updateEq { c -> c.copy(bands = c.bands.toMutableList().also { it[i] = v }) }
            }
            TextButton(onClick = { Store.updateEq { it.copy(bands = List(it.bands.size) { 0f }) } }) {
                Text("Сбросить полосы")
            }
        }

        SectionCard("Громкость") {
            LabeledSlider(
                "Усиление громкости", eq.boostDb, 0f..30f,
                "+%.0f дБ".format(eq.boostDb), { v -> Store.updateEq { it.copy(boostDb = kotlin.math.round(v)) } },
                warn = eq.boostDb > 15f,
            )
            if (eq.boostDb > 15f) Text(
                "Выше +15 дБ возможны искажения и вред динамику/слуху. Не держи долго на максимуме.",
                style = MaterialTheme.typography.bodySmall, color = Warn,
            )
            LabeledSlider(
                "Предусилитель", eq.preamp, -12f..12f,
                "%+.1f дБ".format(eq.preamp), { v -> Store.updateEq { it.copy(preamp = kotlin.math.round(v * 2) / 2f) } },
            )
            SwitchRow(
                "Лимитер", eq.limiter, { on -> Store.updateEq { it.copy(limiter = on) } },
                hint = "Срезает пики, чтобы громкий звук не хрипел",
            )
        }

        SectionCard("Бас и пространство") {
            LabeledSlider("Бас", eq.bass.toFloat(), 0f..1000f, "${eq.bass / 10}%",
                { v -> Store.updateEq { it.copy(bass = v.toInt()) } })
            LabeledSlider("Объёмный звук", eq.virtualizer.toFloat(), 0f..1000f, "${eq.virtualizer / 10}%",
                { v -> Store.updateEq { it.copy(virtualizer = v.toInt()) } })
            LabeledSlider(
                "Баланс", eq.balance, -1f..1f,
                when {
                    eq.balance < -0.02f -> "Л ${(-eq.balance * 100).toInt()}%"
                    eq.balance > 0.02f -> "П ${(eq.balance * 100).toInt()}%"
                    else -> "Центр"
                },
                { v -> Store.updateEq { it.copy(balance = if (kotlin.math.abs(v) < 0.04f) 0f else v) } },
            )
            Text("Реверберация", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
        }
        ChipRow(
            items = Presets.reverbNames,
            selected = Presets.reverbNames.getOrNull(eq.reverb),
            onClick = { name -> Store.updateEq { it.copy(reverb = Presets.reverbNames.indexOf(name)) } },
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { Store.update { it.copy(eq = EqConfig(), eqPresetName = "Ровно") } },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("Сбросить всё") }
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Сохранить пресет") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("Название") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val n = name.trim()
                    Store.update { s ->
                        s.copy(customPresets = s.customPresets.filter { it.name != n } + NamedEq(n, s.eq), eqPresetName = n)
                    }
                    saveDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Отмена") } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить «${target.name}»?") },
            confirmButton = {
                TextButton(onClick = {
                    Store.update { s -> s.copy(customPresets = s.customPresets - target) }
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ModeCard(state: AppState) {
    val context = LocalContext.current
    val active by EqStatus.active.collectAsState()
    val running by EqStatus.running.collectAsState()

    SectionCard("Куда применять") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !state.globalMode, onClick = { Store.update { it.copy(globalMode = false) } },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Плееры") }
            SegmentedButton(
                selected = state.globalMode, onClick = { Store.update { it.copy(globalMode = true) } },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Весь звук") }
        }
        Spacer(Modifier.height(8.dp))
        val hint = if (state.globalMode)
            "Эффекты на общем выходе телефона: YouTube, браузер, игры, любой плеер. Работает не на всех прошивках — если эффекта не слышно, выбери «Плееры»."
        else
            "Эффекты цепляются к плеерам, которые сообщают о своей сессии: Spotify, YouTube Music, VK, Яндекс Музыка, AIMP, Poweramp, встроенный плеер. Если плеер не появился ниже — включи в нём музыку заново или выбери «Весь звук»."
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(8.dp))
        when {
            !state.enabled -> Text("Эквалайзер выключен", color = Muted)
            !running -> Text("Сервис запускается…", color = Muted)
            active.isEmpty() -> Text("Ждём, пока какой-нибудь плеер начнёт играть…", color = Muted)
            else -> active.forEach { SessionLine(it) }
        }
        val pm = context.getSystemService(PowerManager::class.java)
        if (state.enabled && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    )
                }
            }) { Text("Не давать системе выключать эквалайзер") }
        }
    }
}

@Composable
private fun SessionLine(s: ActiveSession) {
    val context = LocalContext.current
    val name = remember(s.pkg) {
        when (s.pkg) {
            "*" -> "Весь звук"
            SessionRegistry.OWN_PLAYER -> "Встроенный плеер"
            else -> runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(s.pkg, 0)).toString()
            }.getOrDefault(s.pkg)
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (s.working) "●" else "○", color = if (s.working) Accent else Warn)
        Spacer(Modifier.padding(4.dp))
        Text(name, Modifier.weight(1f))
        Text(
            when {
                !s.working -> "не удалось"
                s.dynamics -> "полный EQ"
                else -> "базовый EQ"
            },
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
    }
}
