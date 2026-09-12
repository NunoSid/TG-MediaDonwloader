package pt.nunosid.tgmedialibrary.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.nunosid.tgmedialibrary.data.TelegramMediaActions
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import pt.nunosid.tgmedialibrary.telegram.TelegramStreamingDataSource
import pt.nunosid.tgmedialibrary.ui.theme.ReplayAcid
import pt.nunosid.tgmedialibrary.ui.theme.ReplayCyan
import pt.nunosid.tgmedialibrary.ui.theme.ReplayInk
import pt.nunosid.tgmedialibrary.ui.theme.ReplayMuted
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPanel
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPaper
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPink

@Composable
fun PlayerScreen(video: VideoItem, engine: TelegramEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaActions = remember(engine) { TelegramMediaActions(context.applicationContext, engine) }
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

    var currentMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableFloatStateOf(0f) }
    var resumeAfterScrub by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var transferBusy by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            if (duration > 0) durationMs = duration
            if (!scrubbing) {
                currentMs = player.currentPosition.coerceAtLeast(0L)
                scrubMs = currentMs.toFloat()
            }
            isPlaying = player.isPlaying
            delay(200)
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650)
            seekFeedback = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, max)
        player.seekTo(target)
        currentMs = target
        scrubMs = target.toFloat()
        seekFeedback = if (deltaMs > 0) "+5s" else "−5s"
    }

    Column(Modifier.fillMaxSize().background(ReplayPaper)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .border(2.dp, ReplayInk)
                .pointerInput(player, durationMs) {
                    detectTapGestures { offset ->
                        when {
                            offset.x < size.width * 0.42f -> seekBy(-5_000L)
                            offset.x > size.width * 0.58f -> seekBy(5_000L)
                            player.isPlaying -> player.pause()
                            else -> player.play()
                        }
                    }
                }
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Text(
                "PLAY//TG",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(ReplayPink)
                    .border(2.dp, ReplayInk)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelSmall
            )

            seekFeedback?.let { feedback ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .background(ReplayAcid)
                        .border(2.dp, ReplayInk)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(feedback, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(ReplayPanel)
                .border(2.dp, ReplayInk)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeLabel(if (scrubbing) scrubMs.toLong() else currentMs), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = if (durationMs > 0) scrubMs.coerceIn(0f, durationMs.toFloat()) else 0f,
                    onValueChange = { value ->
                        if (!scrubbing) {
                            resumeAfterScrub = player.isPlaying || player.playWhenReady
                            scrubbing = true
                        }
                        scrubMs = value
                    },
                    onValueChangeFinished = {
                        player.seekTo(scrubMs.toLong())
                        currentMs = scrubMs.toLong()
                        scrubbing = false
                        if (resumeAfterScrub) {
                            player.playWhenReady = true
                            player.play()
                        }
                    },
                    valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = ReplayInk,
                        activeTrackColor = ReplayPink,
                        inactiveTrackColor = ReplayCyan
                    )
                )
                Text(timeLabel(durationMs), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerButton("◀ 5S", ReplayCyan, Modifier.weight(1f)) { seekBy(-5_000L) }
                PlayerButton(if (isPlaying) "PAUSE" else "PLAY", ReplayAcid, Modifier.weight(1.2f)) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                PlayerButton("5S ▶", ReplayCyan, Modifier.weight(1f)) { seekBy(5_000L) }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(ReplayPanel)
                .border(2.dp, ReplayInk)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                Text(video.caption, color = ReplayMuted, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerButton("DOWNLOAD", ReplayCyan, Modifier.weight(1f), enabled = !transferBusy) {
                    scope.launch {
                        transferBusy = true
                        transferStatus = "A DESCARREGAR…"
                        runCatching { mediaActions.downloadToDevice(listOf(video)) }
                            .onSuccess { transferStatus = "GUARDADO EM DOWNLOADS / TELEGRAM MEDIA LIBRARY" }
                            .onFailure { transferStatus = "ERRO: ${it.message}" }
                        transferBusy = false
                    }
                }
                PlayerButton("TELEGRAM", ReplayPink, Modifier.weight(1f), enabled = !transferBusy) {
                    scope.launch {
                        transferBusy = true
                        transferStatus = "A PREPARAR PARA TELEGRAM…"
                        runCatching { mediaActions.prepareShare(listOf(video)) }
                            .onSuccess {
                                transferStatus = null
                                mediaActions.shareViaTelegram(it)
                            }
                            .onFailure { transferStatus = "ERRO: ${it.message}" }
                        transferBusy = false
                    }
                }
            }

            transferStatus?.let {
                Text(it, color = ReplayMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PlayerButton(
    text: String,
    background: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(2.dp, ReplayInk),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = ReplayInk,
            disabledContainerColor = background.copy(alpha = .4f),
            disabledContentColor = ReplayInk.copy(alpha = .45f)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, letterSpacing = .4.sp, maxLines = 1)
    }
}

private fun timeLabel(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
