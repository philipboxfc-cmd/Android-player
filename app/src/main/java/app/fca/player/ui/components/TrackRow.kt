package app.fca.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.data.model.Playlist
import app.fca.player.data.model.Track
import app.fca.player.data.model.Vibe
import app.fca.player.data.model.formatDuration
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.navigation.Routes
import kotlinx.coroutines.launch

/**
 * Универсальная строка трека со «вкусным» меню: играть следующим, в очередь, в плейлист, к альбому, к артисту.
 * [extraMenu] — дополнительные пункты (например, «удалить из плейлиста»).
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nav: NavHostController? = null,
    showArtwork: Boolean = true,
    showAlbumNavigation: Boolean = true,
    showArtistNavigation: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    var menuOpen by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        } else if (showArtwork) {
            Box(Modifier.size(48.dp)) {
                Artwork(track.artworkUri, Modifier.size(48.dp), seed = track.albumId)
                if (isCurrent) {
                    Box(
                        Modifier.size(48.dp).padding(14.dp)
                            .then(Modifier),
                    ) {
                        PlayingBars(Modifier.size(20.dp), color = Color.White, animate = isPlaying)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${track.artist} · ${formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Меню трека", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Играть следующим") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                    onClick = { menuOpen = false; container.player.playNext(track) },
                )
                DropdownMenuItem(
                    text = { Text("Добавить в очередь") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                    onClick = { menuOpen = false; container.player.addToQueue(listOf(track)) },
                )
                DropdownMenuItem(
                    text = { Text("В плейлист…") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                    onClick = { menuOpen = false; addToPlaylist = true },
                )
                if (nav != null && (showAlbumNavigation || showArtistNavigation)) {
                    HorizontalDivider()
                    if (showAlbumNavigation) {
                        DropdownMenuItem(
                            text = { Text("К альбому") },
                            leadingIcon = { Icon(Icons.Rounded.Album, null) },
                            onClick = { menuOpen = false; nav.navigate(Routes.album(track.albumId)) },
                        )
                    }
                    if (showArtistNavigation) {
                        DropdownMenuItem(
                            text = { Text("К исполнителю") },
                            leadingIcon = { Icon(Icons.Rounded.Person, null) },
                            onClick = { menuOpen = false; nav.navigate(Routes.artist(track.artistId)) },
                        )
                    }
                }
                if (extraMenu != null) {
                    HorizontalDivider()
                    extraMenu { menuOpen = false }
                }
            }
        }
    }

    if (addToPlaylist) {
        AddToPlaylistDialog(tracks = listOf(track), onDismiss = { addToPlaylist = false })
    }
}

/** Диалог выбора плейлиста (с быстрым созданием нового). */
@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val ids = tracks.map { it.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "В плейлист" else "Добавить ${tracks.size} треков") },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Название плейлиста") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Новый плейлист", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    if (playlists.isNotEmpty()) HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(playlists, key = { it.id }) { p ->
                            val already = ids.all { it in p.trackIds }
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !already) {
                                        scope.launch { container.playlists.addTracks(p.id, ids) }
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ColorDot(Color(p.color), size = 14.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${p.vibe.emoji} ${p.vibe.title} · ${p.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (already) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            container.playlists.upsert(Playlist(name = newName.trim(), vibe = Vibe.CHILL, trackIds = ids))
                        }
                        onDismiss()
                    },
                ) { Text("Создать") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (creating) creating = false else onDismiss() }) {
                Text(if (creating) "Назад" else "Отмена")
            }
        },
    )
}
