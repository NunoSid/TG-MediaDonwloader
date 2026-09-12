package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.model.VideoSort
import pt.nunosid.tgmedialibrary.ui.theme.ReplayAcid
import pt.nunosid.tgmedialibrary.ui.theme.ReplayCyan
import pt.nunosid.tgmedialibrary.ui.theme.ReplayInk
import pt.nunosid.tgmedialibrary.ui.theme.ReplayMuted
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPanel
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPink

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
        modifier = Modifier.border(2.dp, ReplayInk),
        shape = RectangleShape,
        containerColor = ReplayPanel,
        tonalElevation = 0.dp,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.background(ReplayPink).border(2.dp, ReplayInk).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text("FILTER//SET", fontWeight = FontWeight.Black, letterSpacing = 1.sp, style = MaterialTheme.typography.labelSmall)
                }
                Text("FILTROS", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                Text("Aplica-se a todo o catálogo indexado.", color = ReplayMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    ReplayFilterButton(
                        chats.firstOrNull { it.chatId == selectedChat }?.chatTitle ?: "TODAS AS CONVERSAS",
                        ReplayCyan,
                        { chatMenu = true }
                    )
                    DropdownMenu(
                        expanded = chatMenu,
                        onDismissRequest = { chatMenu = false },
                        modifier = Modifier.background(ReplayPanel).border(2.dp, ReplayInk)
                    ) {
                        DropdownMenuItem(text = { Text("TODAS AS CONVERSAS", fontWeight = FontWeight.Bold) }, onClick = { selectedChat = null; chatMenu = false })
                        chats.forEach { chat ->
                            DropdownMenuItem(text = { Text(chat.chatTitle, fontWeight = FontWeight.SemiBold) }, onClick = { selectedChat = chat.chatId; chatMenu = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minMb, { minMb = it }, "MIN. MB", Modifier.weight(1f))
                    NumberField(maxMb, { maxMb = it }, "MÁX. MB", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minMinutes, { minMinutes = it }, "MIN. MIN", Modifier.weight(1f))
                    NumberField(maxMinutes, { maxMinutes = it }, "MÁX. MIN", Modifier.weight(1f))
                }
                Box {
                    ReplayFilterButton(selectedSort.label().uppercase(), ReplayAcid, { sortMenu = true })
                    DropdownMenu(
                        expanded = sortMenu,
                        onDismissRequest = { sortMenu = false },
                        modifier = Modifier.background(ReplayPanel).border(2.dp, ReplayInk)
                    ) {
                        VideoSort.entries.forEach { sort ->
                            DropdownMenuItem(text = { Text(sort.label().uppercase(), fontWeight = FontWeight.Bold) }, onClick = { selectedSort = sort; sortMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(current.copy(
                        chatId = selectedChat,
                        minBytes = minMb.toLongOrNull()?.times(MB),
                        maxBytes = maxMb.toLongOrNull()?.times(MB),
                        minDuration = minMinutes.toIntOrNull()?.times(60),
                        maxDuration = maxMinutes.toIntOrNull()?.times(60),
                        sort = selectedSort
                    ))
                },
                shape = RectangleShape,
                border = BorderStroke(2.dp, ReplayInk),
                colors = ButtonDefaults.buttonColors(containerColor = ReplayAcid, contentColor = ReplayInk)
            ) { Text("APLICAR", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onApply(VideoFilters()) },
                    shape = RectangleShape,
                    border = BorderStroke(2.dp, ReplayInk),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = ReplayPink, contentColor = ReplayInk)
                ) { Text("LIMPAR", fontWeight = FontWeight.Black) }
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = ReplayInk)) {
                    Text("CANCELAR", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun ReplayFilterButton(text: String, background: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(2.dp, ReplayInk),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = background, contentColor = ReplayInk),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) =
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        modifier = modifier,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        singleLine = true,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ReplayCyan,
            unfocusedContainerColor = ReplayPanel,
            focusedBorderColor = ReplayInk,
            unfocusedBorderColor = ReplayInk,
            cursorColor = ReplayInk
        )
    )

private fun VideoSort.label() = when (this) {
    VideoSort.NEWEST -> "Mais recentes"
    VideoSort.OLDEST -> "Mais antigos"
    VideoSort.LARGEST -> "Maiores"
    VideoSort.SMALLEST -> "Menores"
    VideoSort.LONGEST -> "Mais longos"
    VideoSort.SHORTEST -> "Mais curtos"
}

private const val MB = 1024L * 1024L
