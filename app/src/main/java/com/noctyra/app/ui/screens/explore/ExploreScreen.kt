package com.noctyra.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.screens.home.HomeViewModel
import com.noctyra.app.ui.theme.*

@Composable
fun ExploreScreen(
    homeViewModel: HomeViewModel,
    onSearch: () -> Unit,
    onCategory: (Category) -> Unit,
    onAnimeClick: (String) -> Unit
) {
    val ui by homeViewModel.ui.collectAsStateWithLifecycle()
    val favorites by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize().background(BackgroundDark),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarSpace),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
            Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                Text("Explorar", style = MaterialTheme.typography.headlineLarge)
                Text("Descubra seu próximo anime favorito", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                val shape = RoundedCornerShape(26.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(shape)
                        .background(SurfaceCard)
                        .border(1.dp, CardBorder, shape)
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Pink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Buscar animes, filmes…", color = TextMuted, fontSize = 15.sp)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "cat_header") { SectionHeader("Categorias", inset = 0.dp) }
        items(Categories, key = { it.name }, span = { GridItemSpan(3) }) { c ->
            CategoryTile(c.name, c.colors, c.icon, onClick = { onCategory(c) })
        }
        if (ui.hot.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hot_header") { SectionHeader("Em alta agora", inset = 0.dp) }
            itemsIndexed(ui.hot, key = { _, a -> a.slug }, span = { _, _ -> GridItemSpan(2) }) { index, anime ->
                PosterCard(
                    anime = anime,
                    isFavorite = anime.slug in favorites,
                    onClick = { onAnimeClick(anime.slug) },
                    onToggleFavorite = { LibraryStore.toggleFavorite(anime) },
                    width = null,
                    rank = if (index < 10) index + 1 else null
                )
            }
        }
    }
}
