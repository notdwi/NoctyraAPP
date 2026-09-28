package com.noctyra.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.model.UiState
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*

@Composable
fun SearchScreen(
    initialQuery: String,
    onBack: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onCategory: (Category) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val favorites by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.applyInitial(initialQuery)
        else runCatching { focusRequester.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(BackgroundDark).statusBarsPadding()) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack, background = SurfaceCard)
            Spacer(Modifier.width(10.dp))
            val shape = RoundedCornerShape(26.dp)
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(shape)
                    .background(SurfaceCard)
                    .border(1.dp, CardBorder, shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = Pink, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar animes, filmes…", color = TextMuted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::updateQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        cursorBrush = SolidColor(Pink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            viewModel.updateQuery(query, immediate = true)
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                }
                if (query.isNotEmpty()) {
                    CircleIconButton(
                        Icons.Default.Clear, "Limpar", { viewModel.updateQuery("") },
                        tint = TextSecondary, background = SurfaceElevated, size = 30.dp
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = results) {
                null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Categorias", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(Categories, key = { it.name }, span = { GridItemSpan(maxOf(1, maxLineSpan / 2)) }) { c ->
                        CategoryTile(c.name, c.colors, c.icon, onClick = { keyboard?.hide(); onCategory(c) })
                    }
                }
                is UiState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingScreen(Modifier.height(300.dp)) }
                is UiState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorScreen(state.message, onRetry = { viewModel.updateQuery(query, immediate = true) })
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(Icons.Default.SearchOff, "Nada encontrado", "Tente outro nome ou confira a ortografia.")
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("${state.data.size} resultados", style = MaterialTheme.typography.bodyMedium)
                        }
                        items(state.data, key = { it.slug }) { anime ->
                            PosterCard(
                                anime = anime,
                                isFavorite = anime.slug in favorites,
                                onClick = { onAnimeClick(anime.slug) },
                                onToggleFavorite = { LibraryStore.toggleFavorite(anime) },
                                width = null
                            )
                        }
                    }
                }
            }
        }
    }
}
