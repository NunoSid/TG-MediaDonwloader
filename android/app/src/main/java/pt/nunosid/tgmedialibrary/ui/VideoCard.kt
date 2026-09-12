package pt.nunosid.tgmedialibrary.ui

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.ui.theme.ReplayAcid
import pt.nunosid.tgmedialibrary.ui.theme.ReplayCyan
import pt.nunosid.tgmedialibrary.ui.theme.ReplayInk
import pt.nunosid.tgmedialibrary.ui.theme.ReplayMuted
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPanel
import pt.nunosid.tgmedialibrary.ui.theme.ReplayPink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VideoCard(item: VideoItem, onPlay: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(item.miniThumbnail) {
        item.miniThumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    Box(Modifier.padding(start = 2.dp, top = 2.dp, end = 6.dp, bottom = 6.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(4.dp, 4.dp)
                .background(ReplayInk)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(ReplayPanel)
                .border(2.dp, ReplayInk)
                .clickable { onPlay(item) }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(ReplayCyan)
                    .border(bottom = 2.dp, color = ReplayInk)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)))
                } else {
                    Text(
                        "▶",
                        modifier = Modifier.align(Alignment.Center),
                        color = ReplayInk,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    durationLabel(item.durationSeconds),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .background(ReplayAcid)
                        .border(2.dp, ReplayInk)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    color = ReplayInk,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    "VIDEO",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .background(ReplayPink)
                        .border(2.dp, ReplayInk)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    color = ReplayInk,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    item.fileName.ifBlank { "VÍDEO" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    item.chatTitle.uppercase(Locale.getDefault()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = ReplayMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    style = MaterialTheme.typography.labelSmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MetaTag(Formatter.formatFileSize(context, item.fileSize), ReplayAcid)
                    MetaTag("${item.width}×${item.height}", ReplayCyan)
                }

                Text(
                    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
                        .format(Date(item.dateUnix * 1000L)),
                    color = ReplayMuted,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MetaTag(text: String, background: Color) {
    Text(
        text,
        modifier = Modifier
            .background(background)
            .border(1.dp, ReplayInk)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        color = ReplayInk,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1
    )
}

private fun Modifier.border(bottom: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    this.drawBehind {
        val stroke = bottom.toPx()
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - stroke), size = androidx.compose.ui.geometry.Size(size.width, stroke))
    }

private fun durationLabel(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
