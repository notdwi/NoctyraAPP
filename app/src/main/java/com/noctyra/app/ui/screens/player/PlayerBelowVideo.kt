package com.noctyra.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.cleanTitle
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.party.ChatMessage
import com.noctyra.app.party.Party
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*

@Composable
internal fun PlayerBelowVideo(
    slug: String,
    season: Int,
    episode: Int,
    meta: EpisodeMeta,
    detail: AnimeDetail?,
    isOffline: Boolean,
    inParty: Boolean,
    chat: List<ChatMessage>,
    previous: Episode?,
    next: Episode?,
    onEpisode: (Episode?) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (inParty) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabPill("Chat${if (chat.isEmpty()) "" else " • ${chat.count { !it.isSystem }}"}", tab == 0) { tab = 0 }
                TabPill("Episódios", tab == 1) { tab = 1 }
            }
            if (tab == 0) {
                PartyChatPanel(chat, onSend = Party::sendChat, modifier = Modifier.fillMaxSize())
            } else {
                EpisodeList(slug, season, episode, meta, detail, isOffline, previous, next, onEpisode)
            }
        }
    } else {
        EpisodeList(slug, season, episode, meta, detail, isOffline, previous, next, onEpisode)
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else TextSecondary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pink else SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

@Composable
private fun EpisodeList(
    slug: String,
    season: Int,
    episode: Int,
    meta: EpisodeMeta,
    detail: AnimeDetail?,
    isOffline: Boolean,
    previous: Episode?,
    next: Episode?,
    onEpisode: (Episode?) -> Unit
) {
    val favorites by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()
    val history by LibraryStore.history.collectAsStateWithLifecycle()
    val downloads by DownloadCenter.downloads.collectAsStateWithLifecycle()
    val seasonEpisodes = remember(detail, season) { detail?.episodes?.filter { it.season == season }.orEmpty() }
    val progressByKey = remember(history, slug) { history.asSequence().filter { it.slug == slug }.associateBy { it.key } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "info") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(cleanTitle(meta.title), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("T$season • EP $episode", color = Pink.copy(alpha = 0.16f), textColor = PinkLight)
                    if (isOffline) {
                        Spacer(Modifier.width(8.dp))
                        Pill("Offline", color = SuccessGreen.copy(alpha = 0.14f), textColor = SuccessGreen)
                    }
                }
                if (meta.episodeTitle.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(meta.episodeTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (previous != null) {
                        GhostButton("EP ${previous.number}", { onEpisode(previous) }, icon = Icons.Default.SkipPrevious, height = 46.dp)
                    }
                    if (next != null) {
                        PinkButton("Próximo: EP ${next.number}", { onEpisode(next) }, icon = Icons.Default.SkipNext, modifier = Modifier.weight(1f), height = 46.dp)
                    }
                    if (detail != null) {
                        val isFavorite = slug in favorites
                        CircleIconButton(
                            if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            "Minha Lista",
                            { LibraryStore.toggleFavorite(detail.anime) },
                            tint = if (isFavorite) Pink else TextPrimary,
                            background = SurfaceCard,
                            size = 46.dp
                        )
                    }
                }
            }
        }
        if (seasonEpisodes.isNotEmpty()) {
            item(key = "header") { SectionHeader("Episódios • Temporada $season") }
            items(seasonEpisodes, key = { "${it.season}/${it.number}" }, contentType = { "episode" }) { ep ->
                val id = DownloadCenter.downloadId(slug, ep.season, ep.number)
                EpisodeRow(
                    episode = ep,
                    fallbackThumb = detail?.anime?.posterUrl.orEmpty(),
                    progress = progressByKey[id],
                    download = downloads[id],
                    isCurrent = ep.number == episode,
                    onClick = { if (ep.number != episode) onEpisode(ep) },
                    onDownload = null,
                    onDeleteDownload = null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }
        }
    }
}
