package pt.nunosid.tgmedialibrary.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TelegramMediaActions(
    private val context: Context,
    private val engine: TelegramEngine
) {
    suspend fun downloadToDevice(
        videos: List<VideoItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Uri> = withContext(Dispatchers.IO) {
        val result = ArrayList<Uri>(videos.size)
        videos.forEachIndexed { index, video ->
            val sourcePath = engine.downloadFilePath(video.fileId, priority = 32)
            val source = File(sourcePath)
            result += copyToDownloads(source, safeName(video))
            onProgress(index + 1, videos.size)
        }
        result
    }

    suspend fun prepareShare(
        videos: List<VideoItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Uri> = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, "telegram-share").apply {
            deleteRecursively()
            mkdirs()
        }
        val result = ArrayList<Uri>(videos.size)
        videos.forEachIndexed { index, video ->
            val sourcePath = engine.downloadFilePath(video.fileId, priority = 32)
            val target = uniqueFile(shareDir, safeName(video))
            FileInputStream(sourcePath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            result += FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )
            onProgress(index + 1, videos.size)
        }
        result
    }

    fun shareViaTelegram(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                clipData = android.content.ClipData.newRawUri("video", uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = android.content.ClipData.newRawUri("video", uris.first())
                uris.drop(1).forEach { clipData?.addItem(android.content.ClipData.Item(it)) }
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("org.telegram.messenger")
        }

        runCatching { context.startActivity(intent) }
            .onFailure {
                val fallback = Intent.createChooser(
                    Intent(intent).apply { setPackage(null) },
                    "Partilhar vídeos"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(fallback)
            }
    }

    private fun copyToDownloads(source: File, displayName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFromName(displayName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Telegram Media Library")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            try {
                resolver.openOutputStream(uri, "w")!!.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Telegram Media Library").apply { mkdirs() }
            val target = uniqueFile(dir, displayName)
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }
    }

    private fun safeName(video: VideoItem): String {
        val fallback = "telegram_${video.chatId}_${video.messageId}.mp4"
        val raw = video.fileName.trim().ifBlank { fallback }
        val clean = raw.replace(Regex("[\\/:*?\"<>|]"), "_")
        return if (clean.contains('.')) clean else "$clean.mp4"
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (file.exists()) {
            file = File(dir, "${base}_$i$ext")
            i++
        }
        return file
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        else -> "video/mp4"
    }
}
