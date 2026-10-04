package app.fca.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.EmptyState
import app.fca.player.ui.components.SectionTitle
import app.fca.player.ui.components.TrackRow
import app.fca.player.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val tracks by container.library.tracks.collectAsStateWithLifecycle()
    val albums by container.library.albums.collectAsStateWithLifecycle()
    val artists by container.library.artists.collectAsStateWithLifecycle()
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim()
    val foundTracks = remember(q, tracks) {
        if (q.length < 1) emptyList() else tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }.take(60)
    }
    val foundAlbums = remember(q, albums) { if (q.isEmpty()) emptyList() else albums.filter { it.title.contains(q, true) || it.artist.contains(q, true) }.take(20) }
    val foundArtists = remember(q, artists) { if (q.isEmpty()) emptyList() else artists.filter { it.name.contains(q, true) }.take(20) }
    val foundPlaylists = remember(q, playlists) { if (q.isEmpty()) emptyList() else playlists.filter { it.name.contains(q, true) || it.vibe.title.contains(q, true) || it.description.contains(q, true) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Трек, исполнитель, альбом…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Очистить") }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { inner ->
        val nothing = q.isNotEmpty() && foundTracks.isEmpty() && foundAlbums.isEmpty() && foundArtists.isEmpty() && foundPlaylists.isEmpty()
        if (q.isEmpty()) {
            EmptyState(Icons.Rounded.Search, "Что послушаем?", "Ищи по названию, исполнителю, альбому или плейлисту.", Modifier.padding(inner))
            return@Scaffold
        }
        if (nothing) {
            EmptyState(Icons.Rounded.Search, "Ничего не найдено", "Попробуй другое слово.", Modifier.padding(inner))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (foundPlaylists.isNotEmpty()) {
                item { SectionTitle("Плейлисты") }
                items(foundPlaylists, key = { "p" + it.id }) { p ->
                    PlaylistCard(p, isPlaying = playerState.playlistId == p.id, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        nav.navigate(Routes.playlist(p.id))
                    }
                }
            }
            if (foundArtists.isNotEmpty()) {
                item { SectionTitle("Исполнители") }
                items(foundArtists, key = { "ar" + it.id }) { a -> ArtistRow(a) { nav.navigate(Routes.artist(a.id)) } }
            }
            if (foundAlbums.isNotEmpty()) {
                item { SectionTitle("Альбомы") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(foundAlbums, key = { "al" + it.id }) { album ->
                            AlbumCard(album, Modifier.width(140.dp)) { nav.navigate(Routes.album(album.id)) }
                        }
                    }
                }
            }
            if (foundTracks.isNotEmpty()) {
                item { SectionTitle("Треки") }
                items(foundTracks, key = { "t" + it.id }) { t ->
                    TrackRow(
                        track = t,
                        isCurrent = playerState.current?.mediaId == t.id,
                        isPlaying = playerState.isPlaying,
                        onClick = { container.player.playTracks(foundTracks, foundTracks.indexOf(t)) },
                        nav = nav,
                    )
                }
            }
        }
    }
}
