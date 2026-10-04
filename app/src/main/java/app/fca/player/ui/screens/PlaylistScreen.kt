package app.fca.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.data.model.formatDurationLong
import app.fca.player.data.model.tracksWord
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.EmptyState
import app.fca.player.ui.components.FcaTopBar
import app.fca.player.ui.components.GradientBackdrop
import app.fca.player.ui.components.PlayShuffleButtons
import app.fca.player.ui.components.TrackRow
import app.fca.player.ui.components.VibeChip
import app.fca.player.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(nav: NavHostController, playlistId: String) {
    val container = LocalAppContainer.current
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle()
    val allTracks by container.library.tracks.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val playlist = playlists.firstOrNull { it.id == playlistId }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (playlist == null) {
        Scaffold(topBar = { FcaTopBar("Плейлист", onBack = { nav.popBackStack() }) }) { inner ->
            EmptyState(Icons.Rounded.QueueMusic, "Плейлист не найден", modifier = Modifier.padding(inner))
        }
        return
    }

    val tracks = remember(playlist.trackIds, allTracks) { container.library.tracksByIds(playlist.trackIds) }
    val color = Color(playlist.color)
    val missing = playlist.trackIds.size - tracks.size

    Box(Modifier.fillMaxSize()) {
        GradientBackdrop(color, Modifier.fillMaxWidth().height(400.dp), strength = 0.75f)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                FcaTopBar("", onBack = { nav.popBackStack() }, actions = {
                    IconButton(onClick = { nav.navigate(Routes.playlistEdit(playlist.id)) }) { Icon(Icons.Rounded.Edit, "Редактировать") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Добавить в очередь") },
                                leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                                onClick = { menu = false; container.player.addToQueue(tracks) },
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить плейлист") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                })
            },
        ) { inner ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = 24.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(132.dp).background(color.copy(alpha = 0.25f), RoundedCornerShape(32.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(playlist.vibe.emoji, style = MaterialTheme.typography.displayLarge)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(playlist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        VibeChip(playlist.vibe, selected = true, onClick = {}, color = color)
                        if (playlist.description.isNotBlank()) {
                            Text(
                                playlist.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        Text(
                            buildString {
                                append(tracksWord(tracks.size))
                                if (tracks.isNotEmpty()) append(" · ").append(formatDurationLong(tracks.sumOf { it.durationMs }))
                                if (missing > 0) append(" · $missing не найдено на устройстве")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(20.dp))
                        PlayShuffleButtons(
                            enabled = tracks.isNotEmpty(),
                            onPlay = { container.player.playTracks(tracks, 0, playlistId = playlist.id) },
                            onShuffle = { container.player.playTracks(tracks, playlistId = playlist.id, shuffle = true) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (tracks.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("В плейлисте пока пусто", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { nav.navigate(Routes.playlistEdit(playlist.id)) }) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.size(6.dp))
                                Text("Добавить треки")
                            }
                        }
                    }
                }
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.mediaId == track.id,
                        isPlaying = playerState.isPlaying,
                        onClick = { container.player.playTracks(tracks, index, playlistId = playlist.id) },
                        nav = nav,
                        extraMenu = { dismiss ->
                            if (index > 0) {
                                DropdownMenuItem(
                                    text = { Text("Поднять выше") },
                                    leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) },
                                    onClick = { dismiss(); scope.launch { container.playlists.moveTrack(playlist.id, index, index - 1) } },
                                )
                            }
                            if (index < tracks.lastIndex) {
                                DropdownMenuItem(
                                    text = { Text("Опустить ниже") },
                                    leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) },
                                    onClick = { dismiss(); scope.launch { container.playlists.moveTrack(playlist.id, index, index + 1) } },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Убрать из плейлиста") },
                                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { dismiss(); scope.launch { container.playlists.removeTrack(playlist.id, track.id) } },
                            )
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${playlist.name}»?") },
            text = { Text("Сами треки останутся на устройстве, удалится только плейлист.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        container.playlists.delete(playlist.id)
                        nav.popBackStack()
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
