package app.fca.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.data.model.formatDurationLong
import app.fca.player.data.model.tracksWord
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.AddToPlaylistDialog
import app.fca.player.ui.components.Artwork
import app.fca.player.ui.components.EmptyState
import app.fca.player.ui.components.FcaTopBar
import app.fca.player.ui.components.GradientBackdrop
import app.fca.player.ui.components.PlayShuffleButtons
import app.fca.player.ui.components.TrackRow
import app.fca.player.ui.components.rememberArtworkColor
import app.fca.player.ui.navigation.Routes

@Composable
fun AlbumScreen(nav: NavHostController, albumId: Long) {
    val container = LocalAppContainer.current
    val albums by container.library.albums.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.id == albumId }
    var addAll by remember { mutableStateOf(false) }

    if (album == null) {
        Scaffold(topBar = { FcaTopBar("Альбом", onBack = { nav.popBackStack() }) }) { inner ->
            EmptyState(Icons.Rounded.Album, "Альбом не найден", modifier = Modifier.padding(inner))
        }
        return
    }

    val accent = rememberArtworkColor(album.artworkUri) ?: MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        GradientBackdrop(accent, Modifier.fillMaxWidth().height(420.dp))
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                FcaTopBar("", onBack = { nav.popBackStack() }, actions = {
                    IconButton(onClick = { addAll = true }) { Icon(Icons.Rounded.PlaylistAdd, "Альбом в плейлист") }
                })
            },
        ) { inner ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = 24.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(
                            album.artworkUri,
                            Modifier.size(220.dp).shadow(24.dp, MaterialTheme.shapes.large, ambientColor = accent, spotColor = accent),
                            shape = MaterialTheme.shapes.large,
                            iconSize = 64.dp,
                            seed = album.id,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(album.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            album.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp).clickable { nav.navigate(Routes.artist(album.artistId)) },
                        )
                        Text(
                            listOfNotNull(
                                album.year.takeIf { it > 0 }?.toString(),
                                tracksWord(album.tracks.size),
                                formatDurationLong(album.durationMs),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(20.dp))
                        PlayShuffleButtons(
                            onPlay = { container.player.playTracks(album.tracks, 0) },
                            onShuffle = { container.player.playTracks(album.tracks, shuffle = true) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                itemsIndexed(album.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.mediaId == track.id,
                        isPlaying = playerState.isPlaying,
                        onClick = { container.player.playTracks(album.tracks, index) },
                        nav = nav,
                        showAlbumNavigation = false,
                        leading = {
                            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (track.trackNumber > 0) track.trackNumber.toString() else (index + 1).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (playerState.current?.mediaId == track.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (addAll) AddToPlaylistDialog(tracks = album.tracks, onDismiss = { addAll = false })
}
