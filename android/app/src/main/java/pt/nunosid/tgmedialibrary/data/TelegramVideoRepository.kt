package pt.nunosid.tgmedialibrary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.model.VideoSort
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import java.util.concurrent.ConcurrentHashMap

class TelegramVideoRepository(private val engine: TelegramEngine) {
    private val chatNames = ConcurrentHashMap<Long, String>()
    private var nextOffset = ""
    private var exhausted = false
    private val all = LinkedHashMap<Pair<Long, Long>, VideoItem>()

    suspend fun resetAndLoad(): List<VideoItem> = withContext(Dispatchers.IO) {
        nextOffset = ""
        exhausted = false
        all.clear()
        loadNext()
    }

    suspend fun loadNext(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (exhausted) return@withContext all.values.toList()

        val request = TdApi.SearchMessages().apply {
            chatList = null
            query = ""
            offset = nextOffset
            limit = 100
            filter = TdApi.SearchMessagesFilterVideo()
            chatTypeFilter = null
            minDate = 0
            maxDate = 0
        }
        val found = engine.sendBlocking(request)
        nextOffset = found.nextOffset
        exhausted = found.nextOffset.isBlank()

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
        all.values.toList()
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
}
