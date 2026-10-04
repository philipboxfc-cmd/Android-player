package app.fca.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import app.fca.player.data.model.formatDuration
import app.fca.player.di.LocalAppContainer
import app.fca.player.playback.PlayerUiState
import app.fca.player.ui.components.AddToPlaylistDialog
import app.fca.player.ui.components.Artwork
import app.fca.player.ui.components.EmptyState
import app.fca.player.ui.components.FcaTopBar
import app.fca.player.ui.components.PlayingBars
import app.fca.player.ui.navigation.Routes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val state by container.player.state.collectAsStateWithLifecycle()
    val position by container.player.position.collectAsStateWithLifecycle()
    val sleepEndAt by container.player.sleepEndAt.collectAsStateWithLifecycle()
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle()

    val current = state.current
    if (current == null) {
        Scaffold(topBar = { FcaTopBar("Сейчас играет", onBack = { nav.popBackStack() }) }) { inner ->
            EmptyState(Icons.Rounded.MusicOff, "Ничего не играет", "Выбери трек, альбом или плейлист.", Modifier.padding(inner))
        }
        return
    }

    val accent = MaterialTheme.colorScheme.primary
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    val duration = state.durationMs.coerceAtLeast(1L)
    val sliderValue = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val sourceLabel = state.playlistId?.let { id -> playlists.firstOrNull { it.id == id } }?.let { "${it.vibe.emoji} ${it.name}" }
        ?: current.album.ifBlank { "Очередь" }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to accent.copy(alpha = 0.85f),
                0.45f to accent.copy(alpha = 0.3f),
                1f to MaterialTheme.colorScheme.background,
            )
        ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            // Верхняя панель
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("СЕЙЧАС ИГРАЕТ", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sourceLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.Rounded.QueueMusic, "Очередь") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Таймер сна") },
                            leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                            onClick = { menu = false; showSleep = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Эквалайзер") },
                            leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                            onClick = { menu = false; nav.navigate(Routes.EQUALIZER) },
                        )
                        if (state.currentTrack != null) {
                            DropdownMenuItem(
                                text = { Text("В плейлист…") },
                                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                                onClick = { menu = false; showAddToPlaylist = true },
                            )
                            DropdownMenuItem(
                                text = { Text("К альбому") },
                                leadingIcon = { Icon(Icons.Rounded.Album, null) },
                                onClick = { menu = false; nav.navigate(Routes.album(state.currentTrack!!.albumId)) },
                            )
                            DropdownMenuItem(
                                text = { Text("К исполнителю") },
                                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                onClick = { menu = false; nav.navigate(Routes.artist(state.currentTrack!!.artistId)) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Обложка
            Artwork(
                current.artworkUri,
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .shadow(32.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent),
                shape = RoundedCornerShape(28.dp),
                iconSize = 96.dp,
                seed = current.mediaId,
            )

            Spacer(Modifier.weight(1f))

            // Название
            Text(
                current.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
            )
            Text(
                current.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(16.dp))

            // Прогресс
            Slider(
                value = sliderValue,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { container.player.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration((sliderValue * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // Управление
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { container.player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle, "Перемешать",
                        tint = if (state.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { container.player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Предыдущий", Modifier.size(40.dp))
                }
                FilledIconButton(
                    onClick = { container.player.playPause() },
                    modifier = Modifier.size(80.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Пауза" else "Играть",
                        Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = { container.player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Следующий", Modifier.size(40.dp))
                }
                IconButton(onClick = { container.player.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Повтор",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Таймер сна
            val end = sleepEndAt
            if (end != null) {
                val remainingMin = ((end - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
                Row(
                    Modifier.fillMaxWidth().clickable { showSleep = true }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp), tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Выключится через $remainingMin мин", style = MaterialTheme.typography.labelLarge, color = accent)
                }
            } else {
                Spacer(Modifier.height(30.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showQueue) {
        QueueSheet(state = state, onDismiss = { showQueue = false })
    }
    if (showSleep) {
        SleepTimerDialog(active = sleepEndAt != null, onDismiss = { showSleep = false }) { minutes ->
            container.player.setSleepTimer(minutes)
            showSleep = false
        }
    }
    if (showAddToPlaylist && state.currentTrack != null) {
        AddToPlaylistDialog(tracks = listOf(state.currentTrack!!), onDismiss = { showAddToPlaylist = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerUiState, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.currentIndex - 2).coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Очередь · ${state.queue.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { container.player.clearQueue(); onDismiss() }) { Text("Очистить") }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(state.queue, key = { i, e -> "${e.mediaId}_$i" }) { index, entry ->
                val isCurrent = index == state.currentIndex
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { container.player.skipToQueueIndex(index) }
                        .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                        if (isCurrent) PlayingBars(Modifier.size(16.dp), animate = state.isPlaying)
                        else Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.width(8.dp))
                    Artwork(entry.artworkUri, Modifier.size(42.dp), seed = entry.mediaId)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(entry.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { container.player.removeFromQueue(index) }) {
                        Icon(Icons.Rounded.Close, "Убрать из очереди", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(active: Boolean, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val options = listOf(10, 15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        text = {
            Column {
                options.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text("$m минут")
                    }
                }
            }
        },
        confirmButton = {
            if (active) TextButton(onClick = { onPick(null) }) { Text("Выключить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
