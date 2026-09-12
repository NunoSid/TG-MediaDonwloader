package pt.nunosid.tgmedialibrary.model

data class VideoItem(
    val messageId: Long,
    val chatId: Long,
    val chatTitle: String,
    val dateUnix: Int,
    val fileId: Int,
    val fileSize: Long,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val fileName: String,
    val mimeType: String,
    val caption: String,
    val supportsStreaming: Boolean,
    val thumbnailFileId: Int?,
    val thumbnailLocalPath: String?
)

enum class VideoSort {
    NEWEST, OLDEST, LARGEST, SMALLEST, LONGEST, SHORTEST
}

enum class HistoryScope {
    LAST_100,
    LAST_500,
    LAST_1000,
    LAST_30_DAYS,
    LAST_90_DAYS,
    LAST_YEAR,
    ALL
}

data class VideoFilters(
    val query: String = "",
    val chatId: Long? = null,
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val minDuration: Int? = null,
    val maxDuration: Int? = null,
    val sort: VideoSort = VideoSort.NEWEST
)

data class FilterPreset(
    val id: String,
    val name: String,
    val filters: VideoFilters
)
