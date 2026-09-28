package com.noctyra.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noctyra.app.data.local.WatchProgress
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.cleanTitle
import com.noctyra.app.data.model.displayTitle
import com.noctyra.app.data.model.isDubbed
import com.noctyra.app.download.DownloadItem
import com.noctyra.app.download.DownloadStatus
import com.noctyra.app.ui.theme.*

@Composable
fun EpisodeRow(
    episode: Episode,
    fallbackThumb: String,
    progress: WatchProgress?,
    download: DownloadItem?,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onDownload: (() -> Unit)?,
    onDeleteDownload: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isCurrent) Pink.copy(alpha = 0.12f) else SurfaceCard)
            .border(1.dp, if (isCurrent) Pink.copy(alpha = 0.6f) else CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(130.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            NetImage(episode.thumbUrl.ifEmpty { fallbackThumb }, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            if (progress != null && progress.fraction > 0.02f) {
                ProgressLine(
                    progress.fraction,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Episódio ${episode.number}",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, color = if (isCurrent) PinkLight else TextPrimary)
            )
            if (episode.title.isNotEmpty() && !episode.title.equals("Episódio ${episode.number}", true)) {
                Spacer(Modifier.height(2.dp))
                Text(episode.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (progress?.isFinished == true) {
                Spacer(Modifier.height(4.dp))
                Text("Assistido", style = MaterialTheme.typography.labelMedium.copy(color = Pink, fontWeight = FontWeight.SemiBold))
            }
        }
        if (onDownload != null) DownloadButton(download, onDownload, onDeleteDownload)
    }
}

@Composable
fun DownloadButton(item: DownloadItem?, onDownload: () -> Unit, onDelete: (() -> Unit)?) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable {
                when (item?.status) {
                    null, DownloadStatus.FAILED -> onDownload()
                    else -> onDelete?.invoke()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (item?.status) {
            null -> Icon(Icons.Default.Download, "Baixar", tint = TextSecondary, modifier = Modifier.size(24.dp))
            DownloadStatus.COMPLETED -> Icon(Icons.Default.DownloadDone, "Baixado", tint = Pink, modifier = Modifier.size(24.dp))
            DownloadStatus.FAILED -> Icon(Icons.Default.ErrorOutline, "Falhou, tentar de novo", tint = ErrorRed, modifier = Modifier.size(24.dp))
            DownloadStatus.QUEUED, DownloadStatus.PAUSED -> CircularProgressIndicator(
                color = Pink, trackColor = SurfaceElevated, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp)
            )
            DownloadStatus.DOWNLOADING -> {
                if (item.percent > 0f) {
                    CircularProgressIndicator(
                        progress = { item.percent / 100f },
                        color = Pink, trackColor = SurfaceElevated, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp)
                    )
                    Text("${item.percent.toInt()}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                } else {
                    CircularProgressIndicator(color = Pink, trackColor = SurfaceElevated, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
