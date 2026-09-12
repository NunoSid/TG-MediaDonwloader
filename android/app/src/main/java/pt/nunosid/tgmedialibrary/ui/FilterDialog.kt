package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.model.VideoSort

@Composable
fun FilterDialog(current: VideoFilters, videos: List<VideoItem>, onDismiss: () -> Unit, onApply: (VideoFilters) -> Unit) {
    var minMb by remember(current) { mutableStateOf(current.minBytes?.div(1024 * 1024)?.toString().orEmpty()) }
    var maxMb by remember(current) { mutableStateOf(current.maxBytes?.div(1024 * 1024)?.toString().orEmpty()) }
    var minMinutes by remember(current) { mutableStateOf(current.minDuration?.div(60)?.toString().orEmpty()) }
    var maxMinutes by remember(current) { mutableStateOf(current.maxDuration?.div(60)?.toString().orEmpty()) }
    var selectedChat by remember(current) { mutableStateOf(current.chatId) }
    var selectedSort by remember(current) { mutableStateOf(current.sort) }
    var chatMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val chats = videos.distinctBy { it.chatId }.sortedBy { it.chatTitle.lowercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { chatMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(chats.firstOrNull { it.chatId == selectedChat }?.chatTitle ?: "Todas as conversas")
                    }
                    DropdownMenu(expanded = chatMenu, onDismissRequest = { chatMenu = false }) {
                        DropdownMenuItem(text = { Text("Todas as conversas") }, onClick = { selectedChat = null; chatMenu = false })
                        chats.forEach { chat ->
                            DropdownMenuItem(text = { Text(chat.chatTitle) }, onClick = { selectedChat = chat.chatId; chatMenu = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minMb, { minMb = it }, "Tamanho mín. MB", Modifier.weight(1f))
                    NumberField(maxMb, { maxMb = it }, "Tamanho máx. MB", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minMinutes, { minMinutes = it }, "Duração mín. min", Modifier.weight(1f))
                    NumberField(maxMinutes, { maxMinutes = it }, "Duração máx. min", Modifier.weight(1f))
                }
                Box {
                    OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedSort.label()) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        VideoSort.entries.forEach { sort ->
                            DropdownMenuItem(text = { Text(sort.label()) }, onClick = { selectedSort = sort; sortMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(current.copy(
                    chatId = selectedChat,
                    minBytes = minMb.toLongOrNull()?.times(MB),
                    maxBytes = maxMb.toLongOrNull()?.times(MB),
                    minDuration = minMinutes.toIntOrNull()?.times(60),
                    maxDuration = maxMinutes.toIntOrNull()?.times(60),
                    sort = selectedSort
                ))
            }) { Text("Aplicar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(VideoFilters()) }) { Text("Limpar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) =
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit)) }, modifier, label = { Text(label) }, singleLine = true)

private fun VideoSort.label() = when (this) {
    VideoSort.NEWEST -> "Mais recentes"
    VideoSort.OLDEST -> "Mais antigos"
    VideoSort.LARGEST -> "Maiores"
    VideoSort.SMALLEST -> "Menores"
    VideoSort.LONGEST -> "Mais longos"
    VideoSort.SHORTEST -> "Mais curtos"
}

private const val MB = 1024L * 1024L
