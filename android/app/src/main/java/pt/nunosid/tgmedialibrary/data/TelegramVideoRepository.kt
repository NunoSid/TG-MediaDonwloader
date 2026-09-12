package pt.nunosid.tgmedialibrary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import pt.nunosid.tgmedialibrary.model.HistoryScope
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.model.VideoSort
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TelegramVideoRepository(private val engine: TelegramEngine) {
    private val chatNames = ConcurrentHashMap<Long, String>()
    private val all = LinkedHashMap<Pair<Long, Long>, VideoItem>()

    suspend fun loadScope(
        scope: HistoryScope,
        onProgress: (loaded: Int, pages: Int) -> Unit = { _, _ -> }
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        all.clear()
        var pages = 0
        val targetCount = scope.targetCount()
        val minDate = scope.minDateUnix()

        val globalFilters: List<TdApi.SearchMessagesFilter> = listOf(
            TdApi.SearchMessagesFilterVideo(),
            TdApi.SearchMessagesFilterAnimation(),
            TdApi.SearchMessagesFilterDocument(),
            TdApi.SearchMessagesFilterVideoNote()
        )

        for (filter in globalFilters) {
            var nextOffset = ""
            val seenOffsets = HashSet<String>()
            var matchedForFilter = 0

            while (seenOffsets.add(nextOffset)) {
                val request = TdApi.SearchMessages().apply {
                    chatList = null
                    query = ""
                    offset = nextOffset
                    limit = 100
                    this.filter = filter
                    chatTypeFilter = null
                    this.minDate = minDate
                    maxDate = 0
                }

                val found = engine.sendBlocking(request, timeoutSeconds = 60)
                pages += 1
                found.messages.orEmpty().forEach { message ->
                    val item = message.toVideoItem() ?: return@forEach
                    if (all.put(message.chatId to message.id, item) == null) matchedForFilter += 1
                }
                onProgress(all.size, pages)

                if (targetCount != null && matchedForFilter >= targetCount) break
                val newOffset = found.nextOffset
                if (newOffset.isBlank()) break
                nextOffset = newOffset
            }
        }

        val secretPages = scanSecretChats(minDate, targetCount) {
            onProgress(all.size, pages + it)
        }
        pages += secretPages
        onProgress(all.size, pages)

        val values = all.values.sortedWith(
            compareByDescending<VideoItem> { it.dateUnix }
                .thenByDescending { it.chatId }
                .thenByDescending { it.messageId }
        )
        if (targetCount == null) values else values.take(targetCount)
    }

    private fun scanSecretChats(
        minDate: Int,
        targetCount: Int?,
        onPage: (pages: Int) -> Unit
    ): Int {
        var pageCount = 0
        val secretChatIds = linkedSetOf<Long>()

        listOf<TdApi.ChatList>(TdApi.ChatListMain(), TdApi.ChatListArchive()).forEach { list ->
            for (round in 0 until MAX_CHAT_LOAD_ROUNDS) {
                val loaded = runCatching {
                    engine.sendBlocking(TdApi.LoadChats(list, 100), timeoutSeconds = 60)
                }.isSuccess
                if (!loaded) break
            }

            val ids = runCatching {
                engine.sendBlocking(TdApi.GetChats(list, 10_000), timeoutSeconds = 60).chatIds
            }.getOrDefault(LongArray(0))

            ids.forEach { chatId ->
                val chat = runCatching { engine.sendBlocking(TdApi.GetChat(chatId), timeoutSeconds = 30) }.getOrNull()
                if (chat?.type is TdApi.ChatTypeSecret) secretChatIds += chatId
            }
        }

        secretChatIds.forEach { chatId ->
            var fromMessageId = 0L
            var matched = 0
            val seenMessageIds = HashSet<Long>()

            while (true) {
                val history = runCatching {
                    engine.sendBlocking(
                        TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false),
                        timeoutSeconds = 60
                    )
                }.getOrNull() ?: break

                pageCount += 1
                onPage(pageCount)
                val messages = history.messages.orEmpty()
                if (messages.isEmpty()) break

                var oldestDate = Int.MAX_VALUE
                var nextFrom = 0L
                messages.forEach { message ->
                    if (!seenMessageIds.add(message.id)) return@forEach
                    oldestDate = minOf(oldestDate, message.date)
                    nextFrom = message.id
                    val item = message.toVideoItem() ?: return@forEach
                    if (all.put(message.chatId to message.id, item) == null) matched += 1
                }

                if (targetCount != null && matched >= targetCount) break
                if (minDate > 0 && oldestDate < minDate) break
                if (nextFrom == 0L || nextFrom == fromMessageId) break
                fromMessageId = nextFrom
            }
        }

        return pageCount
    }

    private fun TdApi.Message.toVideoItem(): VideoItem? {
        val descriptor = when (val c = content) {
            is TdApi.MessageVideo -> MediaDescriptor(
                file = c.video.video,
                fileName = c.video.fileName.orEmpty(),
                mimeType = c.video.mimeType.orEmpty().ifBlank { "video/mp4" },
                duration = c.video.duration,
                width = c.video.width,
                height = c.video.height,
                caption = c.caption?.text.orEmpty(),
                supportsStreaming = c.video.supportsStreaming,
                thumbnail = c.video.thumbnail?.file
            )

            is TdApi.MessageAnimation -> MediaDescriptor(
                file = c.animation.animation,
                fileName = c.animation.fileName.orEmpty(),
                mimeType = c.animation.mimeType.orEmpty().ifBlank { "video/mp4" },
                duration = c.animation.duration,
                width = c.animation.width,
                height = c.animation.height,
                caption = c.caption?.text.orEmpty(),
                supportsStreaming = true,
                thumbnail = c.animation.thumbnail?.file
            )

            is TdApi.MessageVideoNote -> MediaDescriptor(
                file = c.videoNote.video,
                fileName = "video_note_${chatId}_${id}.mp4",
                mimeType = "video/mp4",
                duration = c.videoNote.duration,
                width = c.videoNote.length,
                height = c.videoNote.length,
                caption = "",
                supportsStreaming = true,
                thumbnail = c.videoNote.thumbnail?.file
            )

            is TdApi.MessageDocument -> {
                val document = c.document
                if (!document.mimeType.orEmpty().startsWith("video/", ignoreCase = true)) return null
                MediaDescriptor(
                    file = document.document,
                    fileName = document.fileName.orEmpty(),
                    mimeType = document.mimeType.orEmpty().ifBlank { "video/mp4" },
                    duration = 0,
                    width = 0,
                    height = 0,
                    caption = c.caption?.text.orEmpty(),
                    supportsStreaming = false,
                    thumbnail = document.thumbnail?.file
                )
            }

            else -> return null
        }

        val file = descriptor.file
        val size = when {
            file.expectedSize > 0 -> file.expectedSize
            file.size > 0 -> file.size
            else -> 0L
        }
        val chatTitle = chatNames.getOrPut(chatId) {
            runCatching { engine.sendBlocking(TdApi.GetChat(chatId)).title }
                .getOrDefault("Conversa $chatId")
        }

        purgeLegacyThumbnail(descriptor.thumbnail)

        return VideoItem(
            messageId = id,
            chatId = chatId,
            chatTitle = chatTitle,
            dateUnix = date,
            fileId = file.id,
            fileSize = size,
            durationSeconds = descriptor.duration,
            width = descriptor.width,
            height = descriptor.height,
            fileName = descriptor.fileName,
            mimeType = descriptor.mimeType,
            caption = descriptor.caption,
            supportsStreaming = descriptor.supportsStreaming,
            thumbnailFileId = descriptor.thumbnail?.id,
            thumbnailLocalPath = null
        )
    }

    private fun purgeLegacyThumbnail(thumbnail: TdApi.File?) {
        val path = thumbnail?.local?.path?.takeIf { it.isNotBlank() } ?: return
        runCatching { File(path).delete() }
        engine.deleteLocalFileAsync(thumbnail.id)
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

    private data class MediaDescriptor(
        val file: TdApi.File,
        val fileName: String,
        val mimeType: String,
        val duration: Int,
        val width: Int,
        val height: Int,
        val caption: String,
        val supportsStreaming: Boolean,
        val thumbnail: TdApi.File?
    )

    companion object {
        private const val MAX_CHAT_LOAD_ROUNDS = 200
    }
}
