package app.fca.eq.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.fca.eq.data.AppState
import app.fca.eq.player.PlayerController
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(state: AppState, player: PlayerController) {
    val ui by player.state.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val first = player.addFiles(uris)
            if (!ui.isPlaying) player.playAt(first)
        }
    }

    LaunchedEffect(ui.isPlaying) {
        while (ui.isPlaying) {
            player.refresh()
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Плеер", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Эффекты: ${state.fxPresetName} · EQ: ${state.eqPresetName}",
                    style = MaterialTheme.typography.bodyMedium, color = Muted,
                )
            }
            if (state.queue.isNotEmpty()) IconButton(onClick = { player.clear() }) {
                Icon(Icons.Filled.DeleteSweep, "Очистить")
            }
            FilledIconButton(onClick = { pick.launch(arrayOf("audio/*")) }) { Icon(Icons.Filled.Add, "Добавить") }
        }

        SectionCard(null) {
            Text(
                ui.title.ifBlank { if (state.queue.isEmpty()) "Ничего не выбрано" else "Нажми на трек" },
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            var dragging by remember { mutableStateOf<Float?>(null) }
            val dur = ui.durationMs.coerceAtLeast(1)
            Slider(
                value = dragging ?: (ui.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo((it * dur).toLong()) }
                    dragging = null
                },
            )
            Row {
                Text(fmt(ui.positionMs), style = MaterialTheme.typography.labelSmall, color = Muted)
                Spacer(Modifier.weight(1f))
                Text(fmt(ui.durationMs), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Перемешать", tint = if (ui.shuffle) Accent else Muted)
                }
                IconButton(onClick = { player.previous() }) { Icon(Icons.Filled.SkipPrevious, "Назад", Modifier.size(32.dp)) }
                FilledIconButton(onClick = { player.togglePlay() }, modifier = Modifier.size(64.dp)) {
                    Icon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Играть", Modifier.size(36.dp))
                }
                IconButton(onClick = { player.next() }) { Icon(Icons.Filled.SkipNext, "Вперёд", Modifier.size(32.dp)) }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Повтор", tint = if (ui.repeat == Player.REPEAT_MODE_OFF) Muted else Accent,
                    )
                }
            }
        }

        if (state.queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(56.dp), tint = Muted)
                    Spacer(Modifier.padding(6.dp))
                    Text(
                        "Добавь треки кнопкой «+» — любые аудиофайлы из памяти, загрузок или облака. " +
                            "Ещё можно открыть файл в проводнике через «Открыть с помощью → FCA EQ».",
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.queue, key = { _, item -> item.uri }) { index, item ->
                    val current = item.uri == ui.currentUri
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { player.playAt(index) }
                            .background(if (current) Accent.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(8.dp).background(if (current) Accent else Color.Transparent, CircleShape)
                        )
                        Spacer(Modifier.padding(6.dp))
                        Text(
                            item.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (current) Accent else MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { player.remove(item) }) { Icon(Icons.Filled.Close, "Убрать", tint = Muted) }
                    }
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
