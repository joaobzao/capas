package com.joaobzao.capas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.CapasViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CapasScreen(
    viewModel: CapasViewModel,
    onCapaClick: (Capa) -> Unit
) {
    val state by viewModel.competitionsState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.getCapas()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capas", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        state.capas?.let { capasResponse ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp), // ~2 em phone, ~4 em tablet
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Seções como headers
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Jornais Nacionais")
                }
                items(capasResponse.mainNewspapers) { capa ->
                    CapaGridItem(capa, onCapaClick)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Desporto")
                }
                items(capasResponse.sportNewspapers) { capa ->
                    CapaGridItem(capa, onCapaClick)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Economia e Gestão")
                }
                items(capasResponse.economyNewspapers) { capa ->
                    CapaGridItem(capa, onCapaClick)
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun CapaGridItem(
    capa: Capa,
    onClick: (Capa) -> Unit
) {
    ElevatedCard(
        onClick = { onClick(capa) },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AsyncImage(
                model = capa.url,
                contentDescription = capa.nome,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(0.75f), // mantém todas do mesmo tamanho (como jornais)
                contentScale = ContentScale.Crop
            )
            Text(
                text = capa.nome,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
        }
    }
}

