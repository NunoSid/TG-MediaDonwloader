package pt.nunosid.tgmedialibrary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import pt.nunosid.tgmedialibrary.model.HistoryScope
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.model.VideoSort
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import java.util.concurrent.ConcurrentHashMap

class TelegramVideoRepository(private val engine: TelegramEngine) {
    private val chatNames = ConcurrentHashMap<Long, String>()
    private val all = LinkedHashMap<Pair<Long, Long>, VideoItem>()

    suspend fun loadScope(
        scope: HistoryScope,
        onProgress: (loaded: Int, pages: Int) -> Unit = { _, _ -> }
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        all.clear()
        var nextOffset = ""
        var pages = 0
        val seenOffsets = HashSet<String>()
        val targetCount = scope.targetCount()
        val minDate = scope.minDateUnix()

        while (true) {
            if (!seenOffsets.add(nextOffset)) break

            val request = TdApi.SearchMessages().apply {
                chatList = null
                query = ""
                offset = nextOffset
                limit = 100
                filter = TdApi.SearchMessagesFilterVideo()
                chatTypeFilter = null
                this.minDate = minDate
                maxDate = 0
            }

            val found = engine.sendBlocking(request, timeoutSeconds = 60)
            pages += 1

            found.messages.orEmpty().forEach { message ->
                val content = message.content as? TdApi.MessageVideo ?: return@forEach
                if (content.isSecret) return@forEach
                val video = content.video
                val file = video.video
                val size = when {
                    file.expectedSize > 0 -> file.expectedSize
                    file.size > 0 -> file.size
                    else -> 0L
                }
                val chatTitle = chatNames.getOrPut(message.chatId) {
                    runCatching { engine.sendBlocking(TdApi.GetChat(message.chatId)).title }
                        .getOrDefault("Conversa ${message.chatId}")
                }
                all[message.chatId to message.id] = VideoItem(
                    messageId = message.id,
                    chatId = message.chatId,
                    chatTitle = chatTitle,
                    dateUnix = message.date,
                    fileId = file.id,
                    fileSize = size,
                    durationSeconds = video.duration,
                    width = video.width,
                    height = video.height,
                    fileName = video.fileName.orEmpty(),
                    mimeType = video.mimeType.orEmpty().ifBlank { "video/mp4" },
                    caption = content.caption?.text.orEmpty(),
                    supportsStreaming = video.supportsStreaming,
                    miniThumbnail = video.minithumbnail?.data
                )
            }

            onProgress(all.size, pages)

            if (targetCount != null && all.size >= targetCount) break
            val newOffset = found.nextOffset
            if (newOffset.isBlank()) break
            nextOffset = newOffset
        }

        val values = all.values.toList()
        if (targetCount == null) values else values.take(targetCount)
    }

    fun applyFilters(source: List<VideoItem>, filters: VideoFilters): List<VideoItem> {
        val q = filters.query.trim().lowercase()
        val filtered = source.asSequence().filter { item ->
            (q.isBlank() || item.fileName.lowercase().contains(q) || item.caption.lowercase().contains(q) || item.chatTitle.lowercase().contains(q)) &&
                (filters.chatId == null || filters.chatId == item.chatId) &&
                (filters.minBytes == null || item.fileSize >= filters.minBytes) &&
                (filters.maxBytes == null || item.fileSize <= filters.maxBytes) &&
                (filters.minDuration == null || item.durationSeconds >= filters.minDuration) &&
                (filters.maxDuration == null || item.durationSeconds <= filters.maxDuration)
        }

        return when (filters.sort) {
            VideoSort.NEWEST -> filtered.sortedByDescending { it.dateUnix }
            VideoSort.OLDEST -> filtered.sortedBy { it.dateUnix }
            VideoSort.LARGEST -> filtered.sortedByDescending { it.fileSize }
            VideoSort.SMALLEST -> filtered.sortedBy { it.fileSize }
            VideoSort.LONGEST -> filtered.sortedByDescending { it.durationSeconds }
            VideoSort.SHORTEST -> filtered.sortedBy { it.durationSeconds }
        }.toList()
    }

    private fun HistoryScope.targetCount(): Int? = when (this) {
        HistoryScope.LAST_100 -> 100
        HistoryScope.LAST_500 -> 500
        HistoryScope.LAST_1000 -> 1000
        else -> null
    }

    private fun HistoryScope.minDateUnix(): Int {
        val now = System.currentTimeMillis() / 1000L
        val seconds = when (this) {
            HistoryScope.LAST_30_DAYS -> 30L * 24L * 60L * 60L
            HistoryScope.LAST_90_DAYS -> 90L * 24L * 60L * 60L
            HistoryScope.LAST_YEAR -> 365L * 24L * 60L * 60L
            else -> return 0
        }
        return (now - seconds).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
