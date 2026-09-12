package pt.nunosid.tgmedialibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.ui.LibraryViewModel
import pt.nunosid.tgmedialibrary.ui.PlayerScreen
import pt.nunosid.tgmedialibrary.ui.RootScreen
import pt.nunosid.tgmedialibrary.ui.theme.TelegramMediaLibraryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TelegramMediaLibraryTheme {
                val vm: LibraryViewModel = viewModel()
                var playing by remember { mutableStateOf<VideoItem?>(null) }
                BackHandler(enabled = playing != null) { playing = null }
                val selected = playing
                if (selected == null) RootScreen(vm) { playing = it }
                else PlayerScreen(selected, vm.engine)
            }
        }
    }
}
