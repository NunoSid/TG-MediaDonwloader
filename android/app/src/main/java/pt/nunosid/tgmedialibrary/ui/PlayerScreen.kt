package pt.nunosid.tgmedialibrary.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import pt.nunosid.tgmedialibrary.telegram.TelegramStreamingDataSource

@Composable
fun PlayerScreen(video: VideoItem, engine: TelegramEngine) {
    val context = LocalContext.current
    val player = remember(video.fileId) {
        ExoPlayer.Builder(context).build().apply {
            val factory = androidx.media3.datasource.DataSource.Factory {
                TelegramStreamingDataSource(engine, video.fileId, video.fileSize)
            }
            val source = ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(Uri.parse("tdlib://video/${video.fileId}")))
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.weight(1f).fillMaxSize()
        )
        Text(video.chatTitle, Modifier.padding(12.dp))
        if (video.caption.isNotBlank()) Text(video.caption, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}
