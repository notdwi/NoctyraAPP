package com.noctyra.app.ui.screens.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.userMessage
import com.noctyra.app.data.repository.AnimeRepository
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUi(
    val items: List<Anime> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null
)

class CategoryViewModel : ViewModel() {
    private val _ui = MutableStateFlow(CategoryUi())
    val ui: StateFlow<CategoryUi> = _ui.asStateFlow()

    private var slug: String? = null
    private var nextPage = 1

    fun start(categorySlug: String) {
        if (slug == categorySlug) return
        slug = categorySlug
        nextPage = 1
        _ui.value = CategoryUi()
        loadMore()
    }

    fun retry() {
        _ui.update { it.copy(error = null) }
        loadMore()
    }

    fun loadMore() {
        val s = slug ?: return
        val state = _ui.value
        if (state.loading || state.endReached || state.error != null) return
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val page = AnimeRepository.getCategory(s, nextPage)
                val known = _ui.value.items.mapTo(HashSet()) { it.slug }
                val fresh = page.filter { it.slug !in known }
                nextPage++
                _ui.update { it.copy(items = it.items + fresh, loading = false, endReached = fresh.isEmpty()) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.userMessage("Falha ao carregar a categoria")) }
            }
        }
    }
}

@Composable
fun CategoryScreen(
    slug: String,
    name: String,
    onBack: () -> Unit,
    onAnimeClick: (String) -> Unit,
    viewModel: CategoryViewModel = viewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val favorites by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(slug) { viewModel.start(slug) }

    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 9
        }
    }
    LaunchedEffect(nearEnd, ui.items.size) { if (nearEnd) viewModel.loadMore() }

    Column(Modifier.fillMaxSize().background(BackgroundDark).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack, background = SurfaceCard)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.headlineMedium)
                if (ui.items.isNotEmpty()) Text("${ui.items.size}${if (ui.endReached) "" else "+"} títulos", style = MaterialTheme.typography.bodySmall)
            }
        }

        val error = ui.error
        when {
            ui.items.isEmpty() && error != null -> ErrorScreen(error, onRetry = viewModel::retry)
            ui.items.isEmpty() && ui.endReached ->
                EmptyState(Icons.Default.SearchOff, "Nada por aqui", "Essa categoria ainda não tem títulos.")
            ui.items.isEmpty() -> LoadingScreen()
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(108.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(ui.items, key = { it.slug }, contentType = { "poster" }) { anime ->
                    PosterCard(
                        anime = anime,
                        isFavorite = anime.slug in favorites,
                        onClick = { onAnimeClick(anime.slug) },
                        onToggleFavorite = { LibraryStore.toggleFavorite(anime) },
                        width = null
                    )
                }
                if (error != null || ui.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                        if (error != null) ErrorScreen(error, onRetry = viewModel::retry)
                        else Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Pink, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}
