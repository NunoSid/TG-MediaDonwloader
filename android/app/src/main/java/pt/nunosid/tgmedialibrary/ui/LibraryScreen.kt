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
import pt.nunosid.tgmedialibrary.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibrary(viewModel: LibraryViewModel, onPlay: (VideoItem) -> Unit) {
    val items by viewModel.visibleVideos.collectAsState(initial = emptyList())
    val filters by viewModel.filters.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val connection by viewModel.connectionLabel.collectAsState()
    val error by viewModel.error.collectAsState()
    var filtersOpen by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("Telegram Media Library", fontWeight = FontWeight.Bold)
                Text("${items.size} vídeos · $connection", style = MaterialTheme.typography.labelSmall)
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = { q -> viewModel.updateFilters { it.copy(query = q) } },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pesquisar vídeos, legendas ou conversas") },
                    singleLine = true
                )
                OutlinedButton(onClick = { filtersOpen = true }) { Text("Filtros") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { "${it.chatId}:${it.messageId}" }) { VideoCard(it, onPlay) }
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator()
                        else OutlinedButton(onClick = viewModel::loadMore) { Text("Carregar mais") }
                    }
                }
            }
        }
    }

    if (filtersOpen) FilterDialog(filters, items, { filtersOpen = false }) {
        viewModel.updateFilters { _ -> it }
        filtersOpen = false
    }
}
