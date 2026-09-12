package pt.nunosid.tgmedialibrary.ui

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.nunosid.tgmedialibrary.model.VideoItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VideoCard(item: VideoItem, onPlay: (VideoItem) -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.padding(horizontal = 4.dp).clickable { onPlay(item) },
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFE5E7EB))) {
                val bitmap = remember(item.miniThumbnail) {
                    item.miniThumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Text(
                    durationLabel(item.durationSeconds),
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White
                )
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.fileName.ifBlank { "Vídeo" }, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(item.chatTitle, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Row {
                    Text(Formatter.formatFileSize(context, item.fileSize), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(8.dp))
                    Text("${item.width}×${item.height}", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.dateUnix * 1000L)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun durationLabel(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
