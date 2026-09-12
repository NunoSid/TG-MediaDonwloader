package pt.nunosid.tgmedialibrary

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
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
    private var privacyLockAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots, screen recording and readable recent-app snapshots.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            TelegramMediaLibraryTheme {
                val vm: LibraryViewModel = viewModel()
                var playing by remember { mutableStateOf<VideoItem?>(null) }

                SideEffect {
                    privacyLockAction = {
                        playing = null
                        vm.lockPrivacy()
                    }
                }

                BackHandler(enabled = playing != null) { playing = null }
                val selected = playing
                if (selected == null) RootScreen(vm) { playing = it }
                else PlayerScreen(selected, vm.engine)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            privacyLockAction?.invoke()
        }
    }

    override fun onDestroy() {
        privacyLockAction = null
        super.onDestroy()
    }
}
