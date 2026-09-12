package pt.nunosid.tgmedialibrary.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(video: VideoItem, engine: TelegramEngine) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
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
    var overlayFeedback by remember { mutableStateOf<String?>(null) }
    var transferBusy by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    fun applyFullscreenMode(fullscreen: Boolean) {
        val host = activity ?: return
        val window = host.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            host.requestedOrientation = if (video.width > 0 && video.height > video.width) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(isFullscreen, configuration.orientation) {
        applyFullscreenMode(isFullscreen)
    }

    DisposableEffect(player, video.fileId) {
        onDispose {
            player.release()
            engine.purgeTransientFileAsync(video.fileId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isFullscreen) applyFullscreenMode(false)
        }
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

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

    LaunchedEffect(overlayFeedback) {
        if (overlayFeedback != null) {
            delay(850)
            overlayFeedback = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, max)
        player.seekTo(target)
        currentMs = target
        scrubMs = target.toFloat()
        overlayFeedback = if (deltaMs > 0) "+5s" else "−5s"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else ReplayPaper)
    ) {
        Box(
            (if (isFullscreen) Modifier.fillMaxSize() else Modifier.weight(1f).fillMaxWidth())
                .background(Color.Black)
                .then(if (isFullscreen) Modifier else Modifier.border(2.dp, ReplayInk))
                .pointerInput(player, durationMs) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            when {
                                offset.x < size.width * 0.5f -> seekBy(-5_000L)
                                else -> seekBy(5_000L)
                            }
                        },
                        onTap = { offset ->
                            when {
                                offset.x < size.width * 0.32f -> seekBy(-5_000L)
                                offset.x > size.width * 0.68f -> seekBy(5_000L)
                                player.isPlaying -> player.pause()
                                else -> player.play()
                            }
                        }
                    )
                }
                .pointerInput(player, durationMs) {
                    var startX = 0f
                    var totalX = 0f
                    var totalY = 0f
                    var gestureMode = 0
                    var startPosition = 0L
                    var wasPlaying = false
                    var startBrightness = 0.5f
                    var startVolume = 0
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

                    fun finishHorizontalGesture() {
                        if (gestureMode == 1) {
                            val target = scrubMs.toLong().coerceAtLeast(0L)
                            player.seekTo(target)
                            currentMs = target
                            scrubbing = false
                            if (wasPlaying) {
                                player.playWhenReady = true
                                player.play()
                            }
                        }
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            totalX = 0f
                            totalY = 0f
                            gestureMode = 0
                            startPosition = player.currentPosition.coerceAtLeast(0L)
                            wasPlaying = player.isPlaying || player.playWhenReady
                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            startBrightness = if (currentBrightness in 0f..1f) currentBrightness else 0.5f
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        },
                        onDragEnd = {
                            finishHorizontalGesture()
                            gestureMode = 0
                        },
                        onDragCancel = {
                            finishHorizontalGesture()
                            gestureMode = 0
                        },
                        onDrag = { change, dragAmount ->
                            totalX += dragAmount.x
                            totalY += dragAmount.y

                            if (gestureMode == 0 && (abs(totalX) > 12.dp.toPx() || abs(totalY) > 12.dp.toPx())) {
                                gestureMode = if (abs(totalX) >= abs(totalY)) {
                                    if (wasPlaying) player.pause()
                                    resumeAfterScrub = wasPlaying
                                    scrubbing = true
                                    1
                                } else if (startX < size.width / 2f) {
                                    2
                                } else {
                                    3
                                }
                            }

                            when (gestureMode) {
                                1 -> {
                                    val spanMs = 60_000f
                                    val delta = if (size.width > 0) (totalX / size.width) * spanMs else 0f
                                    val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
                                    val target = (startPosition + delta.toLong()).coerceIn(0L, max)
                                    scrubMs = target.toFloat()
                                    currentMs = target
                                    overlayFeedback = "SEEK · ${timeLabel(target)}"
                                }
                                2 -> {
                                    val delta = if (size.height > 0) -totalY / size.height else 0f
                                    val brightness = (startBrightness + delta).coerceIn(0.05f, 1f)
                                    activity?.window?.let { window ->
                                        val attrs = window.attributes
                                        attrs.screenBrightness = brightness
                                        window.attributes = attrs
                                    }
                                    overlayFeedback = "BRILHO · ${(brightness * 100).roundToInt()}%"
                                }
                                3 -> {
                                    val delta = if (size.height > 0) -totalY / size.height else 0f
                                    val volume = (startVolume + delta * maxVolume)
                                        .roundToInt()
                                        .coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                    overlayFeedback = "VOLUME · ${(volume * 100f / maxVolume).roundToInt()}%"
                                }
                            }
                            if (gestureMode != 0) change.consume()
                        }
                    )
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

            if (!isFullscreen) {
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
            } else {
                OutlinedButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    shape = RectangleShape,
                    border = BorderStroke(2.dp, Color.White),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("EXIT ⛶", fontWeight = FontWeight.Black)
                }
            }

            overlayFeedback?.let { feedback ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .background(ReplayAcid)
                        .border(2.dp, ReplayInk)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(feedback, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
        }

        if (!isFullscreen) {
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

                PlayerButton("⛶ FULL SCREEN", ReplayPink, Modifier.fillMaxWidth()) {
                    isFullscreen = true
                }

                Text(
                    "GESTOS · swipe horizontal = tempo · vertical esq. = brilho · vertical dir. = volume",
                    color = ReplayMuted,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
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
