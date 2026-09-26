package com.noctyra.app.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctyra.app.data.model.UiState
import com.noctyra.app.ui.components.AnimeCard
import com.noctyra.app.ui.components.ErrorScreen
import com.noctyra.app.ui.components.LoadingScreen
import com.noctyra.app.ui.theme.Purple
import com.noctyra.app.ui.theme.SurfaceVariantDark

@Composable
fun SearchScreen(
    onAnimeClick: (String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text("Buscar", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::updateQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Procurar anime...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Purple) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = SurfaceVariantDark,
                unfocusedContainerColor = SurfaceVariantDark
            )
        )

        Spacer(Modifier.height(16.dp))

        when (val state = results) {
            null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Digite para pesquisar animes", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is UiState.Loading -> LoadingScreen()
            is UiState.Error -> ErrorScreen(message = state.message, onRetry = { viewModel.updateQuery(query) })
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum resultado encontrado", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(130.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.data) { anime ->
                            AnimeCard(anime = anime, onClick = { onAnimeClick(anime.slug) })
                        }
                    }
                }
            }
        }
    }
}
