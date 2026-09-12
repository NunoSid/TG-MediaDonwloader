package pt.nunosid.tgmedialibrary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.nunosid.tgmedialibrary.model.FilterPreset
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
fun FilterDialog(
    current: VideoFilters,
    videos: List<VideoItem>,
    presets: List<FilterPreset>,
    onDismiss: () -> Unit,
    onApply: (VideoFilters) -> Unit,
    onSavePreset: (String, VideoFilters) -> Unit,
    onDeletePreset: (String) -> Unit
) {
    val chats = remember(videos) { videos.distinctBy { it.chatId }.sortedBy { it.chatTitle.lowercase() } }
    val allChatIds = remember(chats) { chats.mapTo(linkedSetOf()) { it.chatId } }
    val chatCounts = remember(videos) { videos.groupingBy { it.chatId }.eachCount() }

    var minMb by remember(current) { mutableStateOf(current.minBytes?.div(MB)?.toString().orEmpty()) }
    var maxMb by remember(current) { mutableStateOf(current.maxBytes?.div(MB)?.toString().orEmpty()) }
    var minMinutes by remember(current) { mutableStateOf(current.minDuration?.div(60)?.toString().orEmpty()) }
    var maxMinutes by remember(current) { mutableStateOf(current.maxDuration?.div(60)?.toString().orEmpty()) }
    var selectedChats by remember(current, allChatIds) {
        mutableStateOf(if (current.chatIds.isEmpty()) allChatIds.toSet() else current.chatIds)
    }
    var selectedSort by remember(current) { mutableStateOf(current.sort) }
    var chatSearch by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var presetName by remember { mutableStateOf("") }
    val selectedPreset = presets.firstOrNull { it.id == selectedPresetId }
    val visibleChats = remember(chats, chatSearch) {
        val q = chatSearch.trim().lowercase()
        if (q.isBlank()) chats else chats.filter { it.chatTitle.lowercase().contains(q) }
    }

    fun normalizedChatIds(): Set<Long> {
        return if (
            chats.isNotEmpty() &&
            selectedChats.size == allChatIds.size &&
            selectedChats.containsAll(allChatIds)
        ) emptySet() else selectedChats
    }

    fun draftFilters(): VideoFilters = current.copy(
        chatIds = normalizedChatIds(),
        minBytes = minMb.toLongOrNull()?.times(MB),
        maxBytes = maxMb.toLongOrNull()?.times(MB),
        minDuration = minMinutes.toIntOrNull()?.times(60),
        maxDuration = maxMinutes.toIntOrNull()?.times(60),
        sort = selectedSort
    )

    fun loadPreset(filters: VideoFilters) {
        minMb = filters.minBytes?.div(MB)?.toString().orEmpty()
        maxMb = filters.maxBytes?.div(MB)?.toString().orEmpty()
        minMinutes = filters.minDuration?.div(60)?.toString().orEmpty()
        maxMinutes = filters.maxDuration?.div(60)?.toString().orEmpty()
        selectedChats = if (filters.chatIds.isEmpty()) allChatIds.toSet() else filters.chatIds
        selectedSort = filters.sort
        chatSearch = ""
    }

    fun toggleChat(chatId: Long) {
        selectedChats = if (chatId in selectedChats) selectedChats - chatId else selectedChats + chatId
    }

    val chatSelectionLabel = when {
        chats.isEmpty() -> "SEM CONVERSAS INDEXADAS"
        normalizedChatIds().isEmpty() -> "TODAS AS CONVERSAS · ${chats.size}"
        selectedChats.size == 1 -> chats.firstOrNull { it.chatId in selectedChats }?.chatTitle ?: "1 CONVERSA"
        else -> "${selectedChats.size} CONVERSAS SELECIONADAS"
    }

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
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text("ATALHOS", fontWeight = FontWeight.Black, letterSpacing = .7.sp, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SmallPresetButton(">500 MB", ReplayAcid, Modifier.weight(1f)) {
                        minMb = "500"
                        maxMb = ""
                    }
                    SmallPresetButton(">20 MIN", ReplayCyan, Modifier.weight(1f)) {
                        minMinutes = "20"
                        maxMinutes = ""
                    }
                    SmallPresetButton("RESET", ReplayPink, Modifier.weight(1f)) {
                        minMb = ""
                        maxMb = ""
                        minMinutes = ""
                        maxMinutes = ""
                        selectedChats = allChatIds.toSet()
                        selectedSort = VideoSort.NEWEST
                        selectedPresetId = null
                        chatSearch = ""
                    }
                }

                Box {
                    ReplayFilterButton(
                        selectedPreset?.name?.uppercase() ?: "PRESETS GUARDADOS",
                        ReplayPanel,
                        { presetMenu = true }
                    )
                    DropdownMenu(
                        expanded = presetMenu,
                        onDismissRequest = { presetMenu = false },
                        modifier = Modifier.background(ReplayPanel).border(2.dp, ReplayInk)
                    ) {
                        if (presets.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("SEM PRESETS", color = ReplayMuted) },
                                enabled = false,
                                onClick = { }
                            )
                        } else {
                            presets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name.uppercase(), fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        selectedPresetId = preset.id
                                        presetName = preset.name
                                        loadPreset(preset.filters)
                                        presetMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(40) },
                        modifier = Modifier.weight(1f),
                        label = { Text("NOME DO PRESET", fontWeight = FontWeight.Bold) },
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
                    SmallPresetButton("SAVE", ReplayAcid, Modifier.width(78.dp).height(56.dp)) {
                        if (presetName.isNotBlank()) onSavePreset(presetName, draftFilters())
                    }
                }

                if (selectedPreset != null) {
                    SmallPresetButton("APAGAR PRESET · ${selectedPreset.name.uppercase()}", ReplayPink, Modifier.fillMaxWidth()) {
                        onDeletePreset(selectedPreset.id)
                        selectedPresetId = null
                        presetName = ""
                    }
                }

                HorizontalDivider(color = ReplayInk.copy(alpha = .3f))

                Text("GRUPOS / CANAIS / CHATS", fontWeight = FontWeight.Black, letterSpacing = .7.sp, style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(ReplayCyan)
                        .border(2.dp, ReplayInk)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text(chatSelectionLabel, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedTextField(
                    value = chatSearch,
                    onValueChange = { chatSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("PESQUISAR GRUPO / CANAL / CHAT") },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ReplayPanel,
                        unfocusedContainerColor = ReplayPanel,
                        focusedBorderColor = ReplayInk,
                        unfocusedBorderColor = ReplayInk,
                        cursorColor = ReplayInk
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SmallPresetButton("TODAS", ReplayAcid, Modifier.weight(1f)) {
                        selectedChats = allChatIds.toSet()
                    }
                    SmallPresetButton("MARCAR VISÍVEIS", ReplayCyan, Modifier.weight(1.45f)) {
                        selectedChats = selectedChats + visibleChats.map { it.chatId }
                    }
                    SmallPresetButton("DESMARCAR VISÍVEIS", ReplayPink, Modifier.weight(1.65f)) {
                        selectedChats = selectedChats - visibleChats.map { it.chatId }.toSet()
                    }
                }

                if (visibleChats.isEmpty()) {
                    Text("SEM RESULTADOS", color = ReplayMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                } else {
                    visibleChats.forEach { chat ->
                        val checked = chat.chatId in selectedChats
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (checked) ReplayCyan else ReplayPanel)
                                .border(2.dp, ReplayInk)
                                .clickable { toggleChat(chat.chatId) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ReplayInk,
                                    uncheckedColor = ReplayInk,
                                    checkmarkColor = ReplayAcid
                                )
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    chat.chatTitle,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${chatCounts[chat.chatId] ?: 0} MEDIA",
                                    color = ReplayMuted,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
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
                onClick = { onApply(draftFilters()) },
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
private fun SmallPresetButton(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RectangleShape,
        border = BorderStroke(2.dp, ReplayInk),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = background, contentColor = ReplayInk),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
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
