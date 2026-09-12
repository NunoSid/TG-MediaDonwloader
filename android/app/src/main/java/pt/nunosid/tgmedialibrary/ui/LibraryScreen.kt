package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.nunosid.tgmedialibrary.model.HistoryScope
import pt.nunosid.tgmedialibrary.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibrary(viewModel: LibraryViewModel, onPlay: (VideoItem) -> Unit) {
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

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("Telegram Media Library", fontWeight = FontWeight.Bold)
                Text("${items.size} visíveis · ${catalog.size} indexados · $connection", style = MaterialTheme.typography.labelSmall)
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = { q -> viewModel.updateFilters { it.copy(query = q) } },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pesquisar vídeos, legendas ou conversas") },
                    singleLine = true
                )
                OutlinedButton(onClick = { filtersOpen = true }) { Text("Filtros") }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { historyMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) { Text("Histórico: ${scope.label()}") }
                    DropdownMenu(expanded = historyMenu, onDismissRequest = { historyMenu = false }) {
                        HistoryScope.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label()) },
                                onClick = {
                                    historyMenu = false
                                    viewModel.selectHistoryScope(option)
                                }
                            )
                        }
                    }
                }
                OutlinedButton(onClick = viewModel::refresh, enabled = !loading) { Text("Atualizar") }
                OutlinedButton(onClick = viewModel::lockPrivacy) { Text("Bloquear") }
            }

            if (loading) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A indexar ${scope.label().lowercase()}… $loadedCount vídeos encontrados · $pages páginas",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }

            if (!loading && catalog.isEmpty() && error == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Não foram encontrados vídeos neste período.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { "${it.chatId}:${it.messageId}" }) { VideoCard(it, onPlay) }
                }
            }
        }
    }

    if (filtersOpen) FilterDialog(filters, catalog, { filtersOpen = false }) {
        viewModel.updateFilters { _ -> it }
        filtersOpen = false
    }
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
