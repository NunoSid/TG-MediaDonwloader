package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pt.nunosid.tgmedialibrary.data.TelegramMediaActions
import pt.nunosid.tgmedialibrary.model.HistoryScope
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.ui.theme.ReplayAcid
import pt.nunosid.tgmedialibrary.ui.theme.ReplayCyan
import pt.nunosid.tgmedialibrary.ui.theme.ReplayInk
import pt.nunosid.tgmedialibrary.ui.theme.ReplayMuted
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPanel
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPaper
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibrary(viewModel: LibraryViewModel, onPlay: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val scopeCoroutine = rememberCoroutineScope()
    val mediaActions = remember(viewModel.engine) { TelegramMediaActions(context.applicationContext, viewModel.engine) }
    val items by viewModel.visibleVideos.collectAsState(initial = emptyList())
    val catalog by viewModel.videos.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val scope by viewModel.historyScope.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadedCount by viewModel.loadedCount.collectAsState()
    val pages by viewModel.indexedPages.collectAsState()
    val connection by viewModel.connectionLabel.collectAsState()
    val error by viewModel.error.collectAsState()
    var filtersOpen by remember { mutableStateOf(false) }
    var historyMenu by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, VideoItem>() }
    var transferBusy by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }

    fun toggleSelection(video: VideoItem) {
        val key = videoKey(video)
        if (selected.containsKey(key)) selected.remove(key) else selected[key] = video
    }

    fun finishSelection() {
        selected.clear()
        selectionMode = false
    }

    Scaffold(
        containerColor = ReplayPaper,
        topBar = {
            TopAppBar(
                modifier = Modifier.border(width = 2.dp, color = ReplayInk),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReplayPaper),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "TG//LIBRARY",
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                if (selectionMode) "${selected.size} SELECTED" else "TELEGRAM MEDIA REPLAY",
                                color = ReplayMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Box(
                            Modifier
                                .padding(end = 12.dp)
                                .background(if (loading || transferBusy) ReplayPink else ReplayAcid)
                                .border(2.dp, ReplayInk)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                when {
                                    transferBusy -> "TRANSFER"
                                    loading -> "INDEXING"
                                    else -> "READY"
                                },
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .replayGridBackground()
        ) {
            StatusStrip(
                visible = items.size,
                indexed = catalog.size,
                connection = connection
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = { q -> viewModel.updateFilters { it.copy(query = q) } },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("PESQUISAR VÍDEOS / CONVERSAS") },
                    singleLine = true,
                    enabled = !selectionMode && !transferBusy,
                    shape = RectangleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ReplayCyan,
                        unfocusedContainerColor = ReplayPanel,
                        focusedBorderColor = ReplayInk,
                        unfocusedBorderColor = ReplayInk,
                        cursorColor = ReplayInk
                    )
                )
                ReplayButton(
                    text = if (selectionMode) "CANCEL" else "FILTROS",
                    background = if (selectionMode) ReplayPink else ReplayAcid,
                    enabled = !transferBusy,
                    onClick = {
                        if (selectionMode) finishSelection() else filtersOpen = true
                    }
                )
            }

            if (selectionMode) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .weight(.7f)
                            .height(48.dp)
                            .background(ReplayPanel)
                            .border(2.dp, ReplayInk),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${selected.size} SEL.", fontWeight = FontWeight.Black)
                    }
                    ReplayButton(
                        "DOWNLOAD",
                        ReplayCyan,
                        enabled = selected.isNotEmpty() && !transferBusy,
                        modifier = Modifier.weight(1.2f),
                        onClick = {
                            val batch = selected.values.toList()
                            scopeCoroutine.launch {
                                transferBusy = true
                                transferStatus = "A DESCARREGAR ${batch.size} VÍDEO(S)…"
                                runCatching { mediaActions.downloadToDevice(batch) }
                                    .onSuccess {
                                        transferStatus = "${batch.size} VÍDEO(S) GUARDADO(S) EM DOWNLOADS / TELEGRAM MEDIA LIBRARY"
                                        finishSelection()
                                    }
                                    .onFailure { transferStatus = "ERRO: ${it.message}" }
                                transferBusy = false
                            }
                        }
                    )
                    ReplayButton(
                        "TELEGRAM",
                        ReplayPink,
                        enabled = selected.isNotEmpty() && !transferBusy,
                        modifier = Modifier.weight(1.1f),
                        onClick = {
                            val batch = selected.values.toList()
                            scopeCoroutine.launch {
                                transferBusy = true
                                transferStatus = "A PREPARAR ${batch.size} VÍDEO(S) PARA TELEGRAM…"
                                runCatching { mediaActions.prepareShare(batch) }
                                    .onSuccess {
                                        transferStatus = null
                                        finishSelection()
                                        mediaActions.shareViaTelegram(it)
                                    }
                                    .onFailure { transferStatus = "ERRO: ${it.message}" }
                                transferBusy = false
                            }
                        }
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        ReplayButton(
                            text = scope.shortLabel(),
                            background = ReplayPanel,
                            enabled = !loading && !transferBusy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { historyMenu = true }
                        )
                        DropdownMenu(
                            expanded = historyMenu,
                            onDismissRequest = { historyMenu = false },
                            modifier = Modifier.background(ReplayPanel).border(2.dp, ReplayInk)
                        ) {
                            HistoryScope.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label().uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    onClick = {
                                        historyMenu = false
                                        viewModel.selectHistoryScope(option)
                                    }
                                )
                            }
                        }
                    }
                    ReplayButton("↻", ReplayCyan, viewModel::refresh, enabled = !loading && !transferBusy)
                    ReplayButton("SELECT", ReplayAcid, { selectionMode = true }, enabled = !loading && !transferBusy)
                    ReplayButton("LOCK", ReplayPink, viewModel::lockPrivacy, enabled = !transferBusy)
                }
            }

            transferStatus?.let {
                Text(
                    it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .background(ReplayPanel)
                        .border(2.dp, ReplayInk)
                        .padding(8.dp),
                    color = ReplayMuted,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (loading) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(ReplayPanel)
                        .border(2.dp, ReplayInk)
                        .padding(10.dp)
                ) {
                    Text(
                        "INDEXANDO ${scope.label().uppercase()}",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(10.dp).border(2.dp, ReplayInk),
                        color = ReplayPink,
                        trackColor = ReplayPanel
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$loadedCount vídeos encontrados · $pages páginas processadas",
                        color = ReplayMuted,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            error?.let {
                Text(
                    it,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error)
                        .border(2.dp, ReplayInk)
                        .padding(10.dp)
                )
            }

            if (!loading && catalog.isEmpty() && error == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .padding(24.dp)
                            .background(ReplayPanel)
                            .border(2.dp, ReplayInk)
                            .padding(24.dp)
                    ) {
                        Text("SEM VÍDEOS NESTE PERÍODO", fontWeight = FontWeight.Black)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(176.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { "${it.chatId}:${it.messageId}" }) { video ->
                        VideoCard(
                            item = video,
                            engine = viewModel.engine,
                            selectionMode = selectionMode,
                            selected = selected.containsKey(videoKey(video)),
                            onPlay = onPlay,
                            onToggleSelection = ::toggleSelection
                        )
                    }
                }
            }
        }
    }

    if (filtersOpen) FilterDialog(filters, catalog, { filtersOpen = false }) {
        viewModel.updateFilters { _ -> it }
        filtersOpen = false
    }
}

private fun videoKey(video: VideoItem) = "${video.chatId}:${video.messageId}"

@Composable
private fun StatusStrip(visible: Int, indexed: Int, connection: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ReplayPanel)
            .bottomBorder(2.dp, ReplayInk)
            .height(IntrinsicSize.Min)
    ) {
        StatusCell("VISIBLE", visible.toString(), ReplayAcid, Modifier.weight(0.8f))
        StatusCell("INDEXED", indexed.toString(), ReplayCyan, Modifier.weight(0.8f))
        StatusCell("SESSION", connection.uppercase(), ReplayPink, Modifier.weight(1.4f), final = true)
    }
}

@Composable
private fun StatusCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier,
    final: Boolean = false
) {
    Column(
        modifier
            .then(if (!final) Modifier.endBorder(2.dp, ReplayInk) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(Modifier.background(accent).padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text(label, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ReplayButton(
    text: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(2.dp, ReplayInk),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = ReplayInk,
            disabledContainerColor = background.copy(alpha = 0.45f),
            disabledContentColor = ReplayInk.copy(alpha = 0.45f)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp, maxLines = 1)
    }
}

private fun Modifier.replayGridBackground(): Modifier =
    background(ReplayPaper).drawBehind {
        val step = 28.dp.toPx()
        val line = ReplayInk.copy(alpha = 0.035f)
        var x = 0f
        while (x <= size.width) {
            drawLine(line, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(line, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }

private fun Modifier.bottomBorder(width: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    drawBehind {
        val stroke = width.toPx()
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - stroke), size = androidx.compose.ui.geometry.Size(size.width, stroke))
    }

private fun Modifier.endBorder(width: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    drawBehind {
        val stroke = width.toPx()
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width - stroke, 0f), size = androidx.compose.ui.geometry.Size(stroke, size.height))
    }

private fun HistoryScope.shortLabel() = when (this) {
    HistoryScope.LAST_100 -> "HISTORY · 100"
    HistoryScope.LAST_500 -> "HISTORY · 500"
    HistoryScope.LAST_1000 -> "HISTORY · 1000"
    HistoryScope.LAST_30_DAYS -> "HISTORY · 30D"
    HistoryScope.LAST_90_DAYS -> "HISTORY · 90D"
    HistoryScope.LAST_YEAR -> "HISTORY · 1Y"
    HistoryScope.ALL -> "HISTORY · ALL"
}

private fun HistoryScope.label() = when (this) {
    HistoryScope.LAST_100 -> "Últimos 100 vídeos"
    HistoryScope.LAST_500 -> "Últimos 500 vídeos"
    HistoryScope.LAST_1000 -> "Últimos 1000 vídeos"
    HistoryScope.LAST_30_DAYS -> "Últimos 30 dias"
    HistoryScope.LAST_90_DAYS -> "Últimos 90 dias"
    HistoryScope.LAST_YEAR -> "Último ano"
    HistoryScope.ALL -> "Todo o histórico"
}
