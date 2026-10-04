package app.fca.player.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.fca.player.ai.AiImport
import app.fca.player.ai.AiTrack
import app.fca.player.data.model.tracksWord
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.ColorDot
import app.fca.player.ui.components.FcaTopBar
import kotlinx.coroutines.launch

@Composable
fun AiImportScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val tracks by container.library.tracks.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val aiTracks = remember(tracks) { tracks.map { AiTrack(it.id, it.title, it.artist, it.album, it.durationMs) } }
    val prompt = remember(aiTracks) { AiImport.buildPrompt(aiTracks) }

    var jsonText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var matched by remember { mutableStateOf<List<AiImport.MatchedPlaylist>>(emptyList()) }
    val excluded = remember { mutableStateListOf<Int>() }
    var showPrompt by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf<Int?>(null) }

    fun parse() {
        error = null
        importedCount = null
        AiImport.parse(jsonText)
            .onSuccess { parsed ->
                matched = AiImport.match(parsed, aiTracks)
                excluded.clear()
            }
            .onFailure {
                matched = emptyList()
                error = it.message ?: "Не удалось разобрать JSON"
            }
    }

    fun import() {
        val toImport = matched.filterIndexed { i, _ -> i !in excluded }.map { it.playlist }
        scope.launch {
            container.playlists.upsertAll(toImport)
            importedCount = toImport.size
            matched = emptyList()
            jsonText = ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FcaTopBar("ИИ-плейлисты", onBack = { nav.popBackStack() }) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.large) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Скопируй промпт со всей библиотекой, отправь его ChatGPT, Claude, Gemini или любому другому ИИ. " +
                                "Он вернёт JSON — вставь его сюда, и плейлисты с вайбами и цветами появятся сами.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // Шаг 1
            item {
                StepCard(step = "1", title = "Скопируй промпт") {
                    Text(
                        "В промпте ${tracksWord(tracks.size)}, около ${prompt.length / 1024} КБ текста. " +
                            if (tracks.size > 1500) "Коллекция большая: если ИИ не примет весь текст, разбей список на части." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(prompt))
                                copied = true
                            },
                            enabled = tracks.isNotEmpty(),
                        ) {
                            Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "Скопировано" else "Копировать")
                        }
                        FilledTonalButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, prompt)
                                    putExtra(Intent.EXTRA_SUBJECT, "Промпт для плейлистов")
                                }
                                context.startActivity(Intent.createChooser(send, "Отправить промпт"))
                            },
                            enabled = tracks.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Поделиться")
                        }
                    }
                    TextButton(onClick = { showPrompt = !showPrompt }) { Text(if (showPrompt) "Скрыть промпт" else "Показать промпт") }
                    if (showPrompt) {
                        Text(
                            prompt.take(1500) + if (prompt.length > 1500) "\n…" else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Шаг 2
            item {
                StepCard(step = "2", title = "Вставь JSON от ИИ") {
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it; error = null },
                        placeholder = { Text("{ \"playlists\": [ … ] }") },
                        minLines = 5,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        isError = error != null,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { clipboard.getText()?.text?.let { jsonText = it; error = null } }) {
                            Icon(Icons.Rounded.ContentPaste, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Из буфера")
                        }
                        Button(onClick = { parse() }, enabled = jsonText.isNotBlank()) { Text("Разобрать") }
                    }
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Результат
            if (importedCount != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Готово! Создано плейлистов: $importedCount", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { nav.popBackStack() }) { Text("К плейлистам") }
                        }
                    }
                }
            }

            if (matched.isNotEmpty()) {
                item {
                    Text(
                        "3. Проверь и импортируй",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                itemsIndexed(matched) { index, m ->
                    val included = index !in excluded
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.clickable { if (included) excluded.add(index) else excluded.remove(index) },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = included, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            ColorDot(Color(m.playlist.color), size = 16.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${m.playlist.vibe.emoji} ${m.playlist.vibe.title} · найдено ${m.matchedCount} из ${m.parsed.tracks.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (m.playlist.description.isNotBlank()) {
                                    Text(
                                        m.playlist.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (m.unmatched.isNotEmpty()) {
                                    Text(
                                        "Не нашлось: " + m.unmatched.take(5).joinToString(", ") + if (m.unmatched.size > 5) " и ещё ${m.unmatched.size - 5}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    val count = matched.size - excluded.size
                    Button(
                        onClick = { import() },
                        enabled = count > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Создать $count ${if (count == 1) "плейлист" else if (count in 2..4) "плейлиста" else "плейлистов"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: String, title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
