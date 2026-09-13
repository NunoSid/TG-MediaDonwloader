package pt.nunosid.tgmedialibrary.telegram

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.drinkless.tdlib.TdApi
import java.io.RandomAccessFile

/**
 * Media3 DataSource backed by TDLib partial file requests.
 * Only the range needed by the player is fetched. Segment closes only cancel the current
 * range; the enclosing player screen purges the TDLib file when playback ends/locks.
 */
@UnstableApi
class TelegramStreamingDataSource(
    private val engine: TelegramEngine,
    private val fileId: Int,
    private val totalSize: Long
) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var position = 0L
    private var opened = false
    private var randomAccessFile: RandomAccessFile? = null
    private var activePath: String? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        engine.markTransientFile(fileId)
        uri = dataSpec.uri
        position = dataSpec.position
        opened = true
        transferStarted(dataSpec)
        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else (totalSize - position).coerceAtLeast(0)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= totalSize) return C.RESULT_END_OF_INPUT

        engine.markTransientFile(fileId)
        val requested = minOf(length.toLong(), 1024L * 1024L, totalSize - position).toInt()
        val file = engine.sendBlocking(TdApi.DownloadFile(fileId, 32, position, requested.toLong(), true), 60)
        val path = file.local.path
        if (path.isBlank() || file.local.downloadedPrefixSize <= 0) return C.RESULT_END_OF_INPUT

        if (activePath != path) {
            randomAccessFile?.close()
            randomAccessFile = RandomAccessFile(path, "r")
            activePath = path
        }

        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        raf.seek(position)
        val available = minOf(requested.toLong(), file.local.downloadedPrefixSize, totalSize - position).toInt()
        if (available <= 0) return C.RESULT_END_OF_INPUT
        val read = raf.read(buffer, offset, available)
        if (read <= 0) return C.RESULT_END_OF_INPUT
        position += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        randomAccessFile?.close()
        randomAccessFile = null
        activePath = null
        if (opened) {
            opened = false
            transferEnded()
        }
        runCatching { engine.sendBlocking(TdApi.CancelDownloadFile(fileId, true), 5) }
    }
}
