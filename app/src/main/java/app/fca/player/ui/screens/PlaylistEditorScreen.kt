package app.fca.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import app.fca.player.data.model.Playlist
import app.fca.player.data.model.Vibe
import app.fca.player.data.model.formatDuration
import app.fca.player.data.model.tracksWord
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.Artwork
import app.fca.player.ui.components.FcaTopBar
import app.fca.player.ui.components.SectionTitle
import app.fca.player.ui.components.VibeChip
import app.fca.player.ui.navigation.Routes
import app.fca.player.ui.theme.readableOn
import app.fca.player.ui.theme.toArgbLong
import kotlinx.coroutines.launch
import java.util.UUID

private val swatches: List<Long> = listOf(
    0xFF7C4DFF, 0xFF536DFE, 0xFF29B6F6, 0xFF4FC3F7, 0xFF26A69A, 0xFF66BB6A, 0xFF00E676,
    0xFFFFEE58, 0xFFFFCA28, 0xFFFFA726, 0xFFFF7043, 0xFFE53935, 0xFFFF4081, 0xFFEC407A,
    0xFFE040FB, 0xFF8E24AA, 0xFFB39DDB, 0xFF7986CB, 0xFF546E7A, 0xFFBDBDBD,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistEditorScreen(nav: NavHostController, playlistId: String?, preselectTrackId: Long?) {
    val container = LocalAppContainer.current
    val allTracks by container.library.tracks.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val existing = remember(playlistId) { playlistId?.let { container.playlists.byId(it) } }
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var vibe by rememberSaveable { mutableStateOf(existing?.vibe ?: Vibe.CHILL) }
    var color by rememberSaveable { mutableLongStateOf(existing?.color ?: Vibe.CHILL.defaultColor) }
    var customColor by rememberSaveable { mutableStateOf(existing != null && existing.color != existing.vibe.defaultColor) }
    var hue by rememberSaveable { mutableStateOf(-1f) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }

    val selected = remember {
        mutableStateListOf<Long>().apply {
            existing?.trackIds?.let { addAll(it) }
            preselectTrackId?.let { if (it !in this) add(it) }
        }
    }
    val selectedSet = selected.toSet()

    val filtered = remember(allTracks, query, selectedOnly, selectedSet) {
        val q = query.trim()
        allTracks.asSequence()
            .filter { !selectedOnly || it.id in selectedSet }
            .filter { q.isEmpty() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
            .toList()
    }

    val accent = Color(color)

    fun save() {
        val playlist = Playlist(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            vibe = vibe,
            color = color,
            description = description.trim(),
            trackIds = selected.toList(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        scope.launch {
            container.playlists.upsert(playlist)
            nav.popBackStack()
            if (existing == null) nav.navigate(Routes.playlist(playlist.id))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FcaTopBar(
                title = if (existing == null) "Новый плейлист" else "Редактирование",
                onBack = { nav.popBackStack() },
                actions = {
                    TextButton(onClick = { save() }, enabled = name.isNotBlank()) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = 32.dp)) {
            // Превью
            item {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
                        .background(Brush.linearGradient(listOf(accent, Color(accent.red * 0.55f, accent.green * 0.55f, accent.blue * 0.55f))))
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(vibe.emoji, style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                name.ifBlank { "Без названия" },
                                style = MaterialTheme.typography.titleLarge,
                                color = accent.readableOn(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${vibe.title} · ${tracksWord(selected.size)}", color = accent.readableOn().copy(alpha = 0.8f))
                        }
                    }
                }
            }
            // Имя и описание
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание (необязательно)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // Вайб
            item {
                SectionTitle("Вайб")
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Vibe.entries.forEach { v ->
                        VibeChip(v, selected = v == vibe, onClick = {
                            vibe = v
                            if (!customColor) color = v.defaultColor
                        })
                    }
                }
            }
            // Цвет
            item {
                SectionTitle("Цвет") {
                    if (customColor) {
                        TextButton(onClick = { customColor = false; color = vibe.defaultColor; hue = -1f }) { Text("Как у вайба") }
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(swatches) { sw ->
                        val c = Color(sw)
                        val isSel = sw == color
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .border(if (isSel) 3.dp else 0.dp, if (isSel) Color.White else Color.Transparent, CircleShape)
                                .clickable { color = sw; customColor = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSel) Icon(Icons.Rounded.Check, null, tint = c.readableOn())
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Оттенок", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = if (hue < 0f) 0f else hue,
                        onValueChange = {
                            hue = it
                            color = Color.hsv(it, 0.72f, 0.95f).toArgbLong()
                            customColor = true
                        },
                        valueRange = 0f..360f,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
            }
            // Треки
            item {
                SectionTitle("Треки · выбрано ${selected.size}") {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { selected.clear() }) { Text("Снять все") }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск по названию, исполнителю, альбому") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Очистить") }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedOnly, onClick = { selectedOnly = !selectedOnly }, label = { Text("Только выбранные") })
                    if (query.isNotBlank() && filtered.isNotEmpty()) {
                        val allIn = filtered.all { it.id in selectedSet }
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (allIn) selected.removeAll(filtered.map { it.id }.toSet())
                                else filtered.forEach { if (it.id !in selectedSet) selected.add(it.id) }
                            },
                            label = { Text(if (allIn) "Убрать найденные" else "Добавить найденные (${filtered.size})") },
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { track ->
                val checked = track.id in selectedSet
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (checked) selected.remove(track.id) else selected.add(track.id) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Artwork(track.artworkUri, Modifier.size(42.dp), seed = track.albumId)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (playerState.current?.mediaId == track.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${track.artist} · ${track.album} · ${formatDuration(track.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (selectedOnly) "Пока ничего не выбрано" else "Ничего не найдено",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
