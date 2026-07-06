package com.joaobzao.capas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joaobzao.capas.analytics.CapasAnalytics
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.CapasViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MyCoversScreen(
    viewModel: CapasViewModel,
    onCapaClick: (Capa) -> Unit,
    onDraggingChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.capasState.collectAsState()
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val closeSearch = {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    // Regista pesquisas com debounce para não enviar cada tecla.
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }.collectLatest { query ->
            if (query.isNotBlank()) {
                delay(1_000)
                CapasAnalytics.trackSearch(query, "favorites")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    val date = remember {
                        LocalDate.now().format(
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                                .withLocale(Locale.getDefault())
                        )
                    }
                    Text(
                        text = date.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.my_covers_title),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.action_search),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(visible = isSearchActive) {
            CoverSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    isSearchActive = false
                    searchQuery = ""
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        val filtered = state.favorites.matching(searchQuery)

        when {
            state.favorites.isEmpty() ->
                EmptyFavoritesState(modifier = Modifier.fillMaxSize())
            filtered.isEmpty() ->
                SearchNoResults(query = searchQuery, modifier = Modifier.fillMaxSize())
            else ->
                DraggableCapaGrid(
                    capas = filtered,
                    favoriteIds = state.favoriteIds,
                    onToggleFavorite = {
                        // No ecrã de favoritos, alternar remove sempre dos favoritos.
                        CapasAnalytics.trackFavoriteRemoved(it.id, it.nome, "favorites")
                        viewModel.toggleFavorite(it)
                    },
                    onCapaClick = onCapaClick,
                    onReorder = {
                        CapasAnalytics.trackReorder("favorites", null)
                        viewModel.updateFavoriteOrder(it)
                    },
                    onDraggingChange = onDraggingChange,
                    onInteraction = closeSearch,
                    dragEnabled = searchQuery.isBlank()
                )
        }
    }
}

@Composable
private fun EmptyFavoritesState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.my_covers_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.my_covers_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
