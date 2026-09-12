package pt.nunosid.tgmedialibrary.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import pt.nunosid.tgmedialibrary.telegram.TelegramStreamingDataSource
import pt.nunosid.tgmedialibrary.ui.theme.ReplayAcid
import pt.nunosid.tgmedialibrary.ui.theme.ReplayInk
import pt.nunosid.tgmedialibrary.ui.theme.ReplayMuted
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPanel
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPaper
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPink

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

    Column(Modifier.fillMaxSize().background(ReplayPaper)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ReplayInk)
                .border(2.dp, ReplayInk)
        ) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player } },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "PLAY//TG",
                modifier = Modifier
                    .padding(10.dp)
                    .background(ReplayPink)
                    .border(2.dp, ReplayInk)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(ReplayPanel)
                .border(2.dp, ReplayInk)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.background(ReplayAcid).border(2.dp, ReplayInk).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(video.chatTitle.uppercase(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                video.fileName.ifBlank { "VÍDEO" },
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
            if (video.caption.isNotBlank()) {
                Text(video.caption, color = ReplayMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
