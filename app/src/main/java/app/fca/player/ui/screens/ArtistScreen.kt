package app.fca.player.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.data.model.tracksWord
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.Artwork
import app.fca.player.ui.components.EmptyState
import app.fca.player.ui.components.FcaTopBar
import app.fca.player.ui.components.GradientBackdrop
import app.fca.player.ui.components.PlayShuffleButtons
import app.fca.player.ui.components.SectionTitle
import app.fca.player.ui.components.TrackRow
import app.fca.player.ui.components.rememberArtworkColor
import app.fca.player.ui.navigation.Routes

@Composable
fun ArtistScreen(nav: NavHostController, artistId: Long) {
    val container = LocalAppContainer.current
    val artists by container.library.artists.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.id == artistId }

    if (artist == null) {
        Scaffold(topBar = { FcaTopBar("Исполнитель", onBack = { nav.popBackStack() }) }) { inner ->
            EmptyState(Icons.Rounded.Person, "Исполнитель не найден", modifier = Modifier.padding(inner))
        }
        return
    }

    val accent = rememberArtworkColor(artist.artworkUri) ?: MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        GradientBackdrop(accent, Modifier.fillMaxWidth().height(380.dp))
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { FcaTopBar("", onBack = { nav.popBackStack() }) },
        ) { inner ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = 24.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(
                            artist.artworkUri,
                            Modifier.size(160.dp).shadow(20.dp, CircleShape, ambientColor = accent, spotColor = accent),
                            shape = CircleShape,
                            fallbackIcon = Icons.Rounded.Person,
                            iconSize = 56.dp,
                            seed = artist.id,
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(artist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            "${albumsWord(artist.albums.size)} · ${tracksWord(artist.tracks.size)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(20.dp))
                        PlayShuffleButtons(
                            onPlay = { container.player.playTracks(artist.tracks, 0) },
                            onShuffle = { container.player.playTracks(artist.tracks, shuffle = true) },
                        )
                    }
                }
                if (artist.albums.isNotEmpty()) {
                    item { SectionTitle("Альбомы", Modifier.padding(top = 12.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(artist.albums, key = { it.id }) { album ->
                                AlbumCard(album, Modifier.width(140.dp)) { nav.navigate(Routes.album(album.id)) }
                            }
                        }
                    }
                }
                item { SectionTitle("Треки", Modifier.padding(top = 8.dp)) }
                itemsIndexed(artist.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.mediaId == track.id,
                        isPlaying = playerState.isPlaying,
                        onClick = { container.player.playTracks(artist.tracks, index) },
                        nav = nav,
                        showArtistNavigation = false,
                    )
                }
            }
        }
    }
}
