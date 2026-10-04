package app.fca.player.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.data.model.Album
import app.fca.player.data.model.Artist
import app.fca.player.data.model.Playlist
import app.fca.player.data.model.tracksWord
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.Artwork
import app.fca.player.ui.components.EmptyState
import app.fca.player.ui.components.PlayShuffleButtons
import app.fca.player.ui.components.TrackRow
import app.fca.player.ui.navigation.Routes
import app.fca.player.ui.theme.readableOn
import kotlinx.coroutines.launch

private val tabs = listOf("Треки", "Альбомы", "Артисты", "Плейлисты")

enum class TrackSort(val title: String) {
    TITLE("По названию"),
    ARTIST("По исполнителю"),
    DATE_ADDED("Сначала новые"),
    DURATION("По длительности"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val isLoading by container.library.isLoading.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "FCA",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "music · vibes · you",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Rounded.Search, "Поиск") }
                        IconButton(onClick = { nav.navigate(Routes.AI_IMPORT) }) { Icon(Icons.Rounded.AutoAwesome, "ИИ-плейлисты") }
                        IconButton(onClick = { nav.navigate(Routes.EQUALIZER) }) { Icon(Icons.Rounded.GraphicEq, "Эквалайзер") }
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { container.library.refresh() }) { Icon(Icons.Rounded.Refresh, "Обновить") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[pagerState.currentPage]).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    divider = {},
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = pagerState.currentPage == i,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text = { Text(title, fontWeight = if (pagerState.currentPage == i) FontWeight.Bold else FontWeight.Medium) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { inner ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(inner),
        ) { page ->
            when (page) {
                0 -> TracksTab(nav)
                1 -> AlbumsTab(nav)
                2 -> ArtistsTab(nav)
                else -> PlaylistsTab(nav)
            }
        }
    }
}

// ---------------- Треки ----------------

@Composable
private fun TracksTab(nav: NavHostController) {
    val container = LocalAppContainer.current
    val tracks by container.library.tracks.collectAsStateWithLifecycle()
    val loadedOnce by container.library.loadedOnce.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(runCatching { TrackSort.valueOf(container.settings.trackSort) }.getOrDefault(TrackSort.TITLE)) }
    var sortMenu by remember { mutableStateOf(false) }

    val sorted = remember(tracks, sort) {
        when (sort) {
            TrackSort.TITLE -> tracks.sortedBy { it.title.lowercase() }
            TrackSort.ARTIST -> tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }))
            TrackSort.DATE_ADDED -> tracks.sortedByDescending { it.dateAdded }
            TrackSort.DURATION -> tracks.sortedByDescending { it.durationMs }
        }
    }

    if (tracks.isEmpty()) {
        if (loadedOnce) {
            EmptyState(
                Icons.Rounded.MusicNote,
                "Треков пока нет",
                "Скинь музыку в папку Music или Download — плеер сам подхватит её вместе с обложками и тегами.",
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PlayShuffleButtons(
                    onPlay = { container.player.playTracks(sorted, 0) },
                    onShuffle = { container.player.playTracks(sorted, shuffle = true) },
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tracksWord(tracks.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        Row(
                            Modifier.clip(MaterialTheme.shapes.small).clickable { sortMenu = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Sort, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(sort.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            TrackSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.title) },
                                    leadingIcon = { RadioButton(selected = s == sort, onClick = null) },
                                    onClick = { sort = s; container.settings.trackSort = s.name; sortMenu = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        itemsIndexed(sorted, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = playerState.current?.mediaId == track.id,
                isPlaying = playerState.isPlaying,
                onClick = { container.player.playTracks(sorted, index) },
                nav = nav,
            )
        }
    }
}

// ---------------- Альбомы ----------------

@Composable
private fun AlbumsTab(nav: NavHostController) {
    val container = LocalAppContainer.current
    val albums by container.library.albums.collectAsStateWithLifecycle()
    if (albums.isEmpty()) {
        EmptyState(Icons.Rounded.Album, "Альбомов нет", "Они появятся, как только в библиотеке будут треки с тегами альбома.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album) { nav.navigate(Routes.album(album.id)) }
        }
    }
}

@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Artwork(album.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), shape = MaterialTheme.shapes.medium, iconSize = 40.dp, seed = album.id)
        Spacer(Modifier.height(8.dp))
        Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------- Артисты ----------------

@Composable
private fun ArtistsTab(nav: NavHostController) {
    val container = LocalAppContainer.current
    val artists by container.library.artists.collectAsStateWithLifecycle()
    if (artists.isEmpty()) {
        EmptyState(Icons.Rounded.Person, "Исполнителей нет", "Добавь музыку — список соберётся автоматически.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        items(artists, key = { it.id }) { artist ->
            ArtistRow(artist) { nav.navigate(Routes.artist(artist.id)) }
        }
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artist.artworkUri, Modifier.size(56.dp), shape = CircleShape, fallbackIcon = Icons.Rounded.Person, seed = artist.id)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${albumsWord(artist.albums.size)} · ${tracksWord(artist.tracks.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun albumsWord(n: Int): String {
    val r10 = n % 10
    val r100 = n % 100
    return when {
        r100 in 11..19 -> "$n альбомов"
        r10 == 1 -> "$n альбом"
        r10 in 2..4 -> "$n альбома"
        else -> "$n альбомов"
    }
}

// ---------------- Плейлисты ----------------

@Composable
private fun PlaylistsTab(nav: NavHostController) {
    val container = LocalAppContainer.current
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    icon = Icons.Rounded.Add,
                    title = "Новый плейлист",
                    subtitle = "Выбери треки, вайб и цвет",
                    modifier = Modifier.weight(1f),
                ) { nav.navigate(Routes.playlistEdit()) }
                ActionCard(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "ИИ-плейлисты",
                    subtitle = "Промпт → JSON → плейлисты",
                    modifier = Modifier.weight(1f),
                ) { nav.navigate(Routes.AI_IMPORT) }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QueueMusic, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    Spacer(Modifier.height(12.dp))
                    Text("Плейлистов пока нет", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Собери первый вручную или попроси ИИ разложить всю коллекцию по вайбам.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                isPlaying = playerState.playlistId == playlist.id && playerState.hasMedia,
                onClick = { nav.navigate(Routes.playlist(playlist.id)) },
            )
        }
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, isPlaying: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = Color(playlist.color)
    val onColor = color.readableOn()
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(color, color.copy(alpha = 0.75f).let { Color(it.red * 0.6f, it.green * 0.6f, it.blue * 0.6f) }),
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).background(onColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(playlist.vibe.emoji, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge, color = onColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${playlist.vibe.title} · ${tracksWord(playlist.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onColor.copy(alpha = 0.8f),
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isPlaying) {
                Spacer(Modifier.width(8.dp))
                app.fca.player.ui.components.PlayingBars(color = onColor)
            }
        }
    }
}
