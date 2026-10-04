package app.fca.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.components.EmptyState
import app.fca.player.ui.components.MiniPlayer
import app.fca.player.ui.navigation.Routes
import app.fca.player.ui.screens.AiImportScreen
import app.fca.player.ui.screens.AlbumScreen
import app.fca.player.ui.screens.ArtistScreen
import app.fca.player.ui.screens.EqualizerScreen
import app.fca.player.ui.screens.LibraryScreen
import app.fca.player.ui.screens.NowPlayingScreen
import app.fca.player.ui.screens.PlaylistEditorScreen
import app.fca.player.ui.screens.PlaylistScreen
import app.fca.player.ui.screens.SearchScreen
import app.fca.player.ui.theme.DefaultAccent
import app.fca.player.ui.theme.FcaTheme

@Composable
fun FcaRoot(openNowPlayingRequest: Int) {
    val container = LocalAppContainer.current
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle()

    // Акцент: цвет плейлиста, если играем плейлист; иначе цвет обложки; иначе фирменный.
    val targetAccent = remember(playerState.playlistId, playerState.artworkColor, playlists) {
        val fromPlaylist = playerState.playlistId?.let { id -> playlists.firstOrNull { it.id == id } }?.let { Color(it.color) }
        fromPlaylist ?: playerState.artworkColor?.let { Color(it) } ?: DefaultAccent
    }
    val accent by animateColorAsState(targetAccent, animationSpec = tween(700), label = "accent")

    FcaTheme(accent = accent) {
        PermissionGate {
            MainNavigation(openNowPlayingRequest = openNowPlayingRequest)
        }
    }
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        asked = true
        granted = result[audioPermission] == true ||
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
    }

    fun request() {
        val perms = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(perms.toTypedArray())
    }

    LaunchedEffect(granted) {
        if (granted) container.library.onPermissionGranted() else if (!asked) request()
    }

    if (granted) {
        content()
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "Нужен доступ к музыке",
                subtitle = "Плеер читает треки и обложки из памяти телефона. Без разрешения библиотека будет пустой.",
                action = {
                    Button(onClick = { request() }) { Text("Разрешить") }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    }) { Text("Открыть настройки") }
                },
            )
        }
    }
}

@Composable
private fun MainNavigation(openNowPlayingRequest: Int) {
    val container = LocalAppContainer.current
    val nav = rememberNavController()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val position by container.player.position.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showMini = playerState.hasMedia && currentRoute != Routes.NOW_PLAYING

    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest > 0 && currentRoute != Routes.NOW_PLAYING) {
            nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showMini,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                MiniPlayer(
                    state = playerState,
                    positionMs = position,
                    onClick = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } },
                    onPlayPause = { container.player.playPause() },
                    onNext = { container.player.next() },
                )
            }
        },
    ) { inner ->
        val bottom = inner.calculateBottomPadding()
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = bottom)
                .consumeWindowInsets(PaddingValues(bottom = bottom)),
        ) {
            NavHost(navController = nav, startDestination = Routes.LIBRARY, modifier = Modifier.fillMaxSize()) {
                composable(Routes.LIBRARY) { LibraryScreen(nav) }
                composable(Routes.SEARCH) { SearchScreen(nav) }
                composable(Routes.NOW_PLAYING) { NowPlayingScreen(nav) }
                composable(Routes.EQUALIZER) { EqualizerScreen(nav) }
                composable(Routes.AI_IMPORT) { AiImportScreen(nav) }
                composable(Routes.ALBUM, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    AlbumScreen(nav, it.arguments?.getLong("id") ?: -1L)
                }
                composable(Routes.ARTIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ArtistScreen(nav, it.arguments?.getLong("id") ?: -1L)
                }
                composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    PlaylistScreen(nav, it.arguments?.getString("id") ?: "")
                }
                composable(
                    Routes.PLAYLIST_EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("preselect") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {
                    PlaylistEditorScreen(
                        nav = nav,
                        playlistId = it.arguments?.getString("id"),
                        preselectTrackId = it.arguments?.getString("preselect")?.toLongOrNull(),
                    )
                }
            }
        }
    }
}
