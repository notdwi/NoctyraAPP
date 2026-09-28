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

private val PosterShape = RoundedCornerShape(14.dp)

fun Anime.cardSubtitle(): String = when {
    subtitle.isNotEmpty() && latestEpisode > 0 -> subtitle
    totalEps > 0 -> if (isMovie) "Filme" else "$totalEps Episódios"
    genres.isNotEmpty() -> genres.first()
    isMovie -> "Filme"
    else -> "Anime"
}

@Composable
fun PosterCard(
    anime: Anime,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 150.dp,
    rank: Int? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = if (width != null) modifier.width(width) else modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(PosterShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), PosterShape)
                .clickable(onClick = onClick)
        ) {
            NetImage(anime.posterUrl, Modifier.fillMaxSize(), contentDescription = anime.title)
            if (anime.isDubbed) DubBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            else if (anime.isMovie) DubBadge(Modifier.align(Alignment.TopStart).padding(8.dp), text = "FILME")
            if (anime.rating.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(anime.rating, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (rank != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(CardBottomFade)
                )
                Text(
                    "$rank",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    anime.displayTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    anime.cardSubtitle(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Opções",
                    tint = TextSecondary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true }
                )
                if (menuOpen) {
                    AnimeMenu(
                        isFavorite = isFavorite,
                        onDismiss = { menuOpen = false },
                        onToggleFavorite = onToggleFavorite,
                        onDetails = onClick
                    )
                }
            }
        }
    }
}

@Composable
fun AnimeMenu(isFavorite: Boolean, onDismiss: () -> Unit, onToggleFavorite: () -> Unit, onDetails: () -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remover da Minha Lista" else "Adicionar à Minha Lista") },
            leadingIcon = {
                Icon(if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, tint = Pink)
            },
            onClick = { onDismiss(); onToggleFavorite() }
        )
        DropdownMenuItem(
            text = { Text("Ver detalhes") },
            leadingIcon = { Icon(Icons.Default.Info, null, tint = TextSecondary) },
            onClick = { onDismiss(); onDetails() }
        )
    }
}

@Composable
fun NewEpisodeCard(anime: Anime, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = 230.dp) {
    Column(modifier = if (width != null) modifier.width(width) else modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(PosterShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), PosterShape)
                .clickable(onClick = onClick)
        ) {
            NetImage(anime.posterUrl, Modifier.fillMaxSize(), contentDescription = anime.title)
            Box(Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter).background(CardBottomFade))
            if (anime.isDubbed) DubBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            Text(
                anime.subtitle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Pink),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            anime.displayTitle,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(1.dp, CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(0.48f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
        ) {
            NetImage(progress.thumbUrl.ifEmpty { progress.posterUrl }, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            ProgressLine(
                progress.fraction,
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(0.52f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    cleanTitle(progress.title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Icon(
                        Icons.Default.MoreVert, "Opções", tint = TextPrimary,
                        modifier = Modifier.size(24.dp).clip(CircleShape).clickable { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = SurfaceElevated) {
                        DropdownMenuItem(
                            text = { Text("Remover de Continue assistindo") },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val epLabel = buildString {
                append("T${progress.season} E${progress.episode}")
                if (progress.episodeTitle.isNotEmpty()) append(" - ").append(progress.episodeTitle)
            }
            Text(epLabel, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(progress.fraction, Modifier.weight(1f), track = SurfaceElevated)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (progress.remainingMinutes > 0) "${progress.remainingMinutes} min restantes" else "Quase no fim",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CategoryTile(name: String, colors: List<Color>, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val brush = remember(colors) { Brush.linearGradient(colors) }
    Box(
        modifier
            .height(76.dp)
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.align(Alignment.TopStart))
        Icon(
            icon, null, tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.BottomEnd).size(34.dp)
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Pink.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Pink, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke()
    }
}
