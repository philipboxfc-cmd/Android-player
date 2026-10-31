package app.fca.eq.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.fca.eq.MainActivity
import app.fca.eq.data.Store
import app.fca.eq.player.PlayerController

@Composable
fun EqRoot(player: PlayerController, tab: MutableIntState) {
    val state by Store.state.collectAsState()
    EqTheme {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = CardBg) {
                    NavigationBarItem(
                        selected = tab.intValue == MainActivity.TAB_EQ, onClick = { tab.intValue = MainActivity.TAB_EQ },
                        icon = { Icon(Icons.Filled.GraphicEq, null) }, label = { Text("Эквалайзер") },
                    )
                    NavigationBarItem(
                        selected = tab.intValue == MainActivity.TAB_FX, onClick = { tab.intValue = MainActivity.TAB_FX },
                        icon = { Icon(Icons.Filled.AutoAwesome, null) }, label = { Text("Эффекты") },
                    )
                    NavigationBarItem(
                        selected = tab.intValue == MainActivity.TAB_PLAYER, onClick = { tab.intValue = MainActivity.TAB_PLAYER },
                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }, label = { Text("Плеер") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when (tab.intValue) {
                    MainActivity.TAB_FX -> FxScreen(state) { tab.intValue = MainActivity.TAB_PLAYER }
                    MainActivity.TAB_PLAYER -> PlayerScreen(state, player)
                    else -> EqScreen(state)
                }
            }
        }
    }
}
