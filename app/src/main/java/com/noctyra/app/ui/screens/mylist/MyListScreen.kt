package com.noctyra.app.ui.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.model.cleanTitle
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.download.DownloadItem
import com.noctyra.app.download.DownloadStatus
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*

private val Tabs = listOf("Favoritos", "Histórico", "Downloads")

@Composable
fun MyListScreen(
    initialTab: Int,
    onAnimeClick: (String) -> Unit,
    onPlay: (String, Int, Int) -> Unit,
    onExplore: () -> Unit
) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val favorites by LibraryStore.favorites.collectAsStateWithLifecycle()
    val favoriteSlugs by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()
    val history by LibraryStore.history.collectAsStateWithLifecycle()
    val downloads by DownloadCenter.downloads.collectAsStateWithLifecycle()

    val historyByAnime = remember(history) { history.distinctBy { it.slug } }
    val downloadList = remember(downloads) {
        downloads.values.sortedWith(compareBy<DownloadItem>({ it.meta.title }, { it.meta.season }, { it.meta.episode }))
    }
    val favoriteAnimes = remember(favorites) { favorites.map { it.toAnime() } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        modifier = Modifier.fillMaxSize().background(BackgroundDark),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
            Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                Text("Minha Lista", style = MaterialTheme.typography.headlineLarge)
                Text("${favorites.size} salvos • ${downloadList.count { it.status == DownloadStatus.COMPLETED }} baixados", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tabs.forEachIndexed { i, label ->
                        val selected = i == tab
                        Text(
                            label,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Pink else SurfaceCard)
                                .then(if (selected) Modifier else Modifier.border(1.dp, CardBorder, RoundedCornerShape(50)))
                                .clickable { tab = i }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        when (tab) {
            0 -> if (favoriteAnimes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Outlined.BookmarkBorder, "Sua lista está vazia",
                        "Toque em \"Minha Lista\" em qualquer anime para salvá-lo aqui.",
                        action = { PinkButton("Explorar animes", onExplore, height = 44.dp) }
                    )
                }
            } else {
                items(favoriteAnimes, key = { it.slug }) { anime ->
                    PosterCard(
                        anime = anime,
                        isFavorite = anime.slug in favoriteSlugs,
                        onClick = { onAnimeClick(anime.slug) },
                        onToggleFavorite = { LibraryStore.toggleFavorite(anime) },
                        width = null
                    )
                }
            }

            1 -> if (historyByAnime.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Outlined.History, "Nada assistido ainda", "Os episódios que você assistir aparecem aqui para continuar depois.")
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recentes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = LibraryStore::clearHistory) { Text("Limpar tudo", color = Pink) }
                    }
                }
                items(historyByAnime, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { p ->
                    ContinueWatchingCard(
                        progress = p,
                        onClick = { onPlay(p.slug, p.season, p.episode) },
                        onRemove = { LibraryStore.removeFromHistory(p.slug) }
                    )
                }
            }

            else -> if (downloadList.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Outlined.CloudDownload, "Nenhum download",
                        "Abra um anime e toque no ícone de download de um episódio para assistir offline."
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Espaço usado: ${formatBytes(DownloadCenter.usedBytes())}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                items(downloadList, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { item ->
                    DownloadRow(
                        item = item,
                        onClick = {
                            if (item.status == DownloadStatus.COMPLETED) onPlay(item.meta.slug, item.meta.season, item.meta.episode)
                            else onAnimeClick(item.meta.slug)
                        },
                        onDelete = { DownloadCenter.remove(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, onClick: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(1.dp, CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetImage(
            item.meta.thumbUrl.ifEmpty { item.meta.posterUrl },
            Modifier.width(116.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cleanTitle(item.meta.title), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("T${item.meta.season} • EP ${item.meta.episode}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            when (item.status) {
                DownloadStatus.COMPLETED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DownloadDone, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pronto • ${formatBytes(item.bytes)}", style = MaterialTheme.typography.labelMedium.copy(color = SuccessGreen))
                }
                DownloadStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Falhou — toque para tentar de novo no anime", style = MaterialTheme.typography.labelMedium.copy(color = ErrorRed))
                }
                else -> {
                    ProgressLine(item.percent / 100f, Modifier.fillMaxWidth(), track = SurfaceElevated)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (item.status) {
                            DownloadStatus.QUEUED -> "Na fila"
                            DownloadStatus.PAUSED -> "Pausado (sem conexão?)"
                            else -> "${item.percent.toInt()}% • ${formatBytes(item.bytes)}"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        IconButton(onClick = { confirm = true }) {
            Icon(Icons.Default.Delete, "Remover", tint = TextSecondary)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = SurfaceElevated,
            title = { Text("Remover download?") },
            text = { Text("${cleanTitle(item.meta.title)} • EP ${item.meta.episode} será apagado do aparelho.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Remover", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar", color = TextSecondary) } }
        )
    }
}
