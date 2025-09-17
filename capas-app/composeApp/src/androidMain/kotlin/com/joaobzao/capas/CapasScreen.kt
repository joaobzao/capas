package com.joaobzao.capas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

import coil.compose.AsyncImage
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.CapasViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun CapasScreen(
    viewModel: CapasViewModel,
    onCapaClick: (Capa) -> Unit
) {
    val state by viewModel.competitionsState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.getCapas()
    }

    state.capas?.let { capasResponse ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            section("Jornais Nacionais", capasResponse.mainNewspapers, onCapaClick)
            section("Desporto", capasResponse.sportNewspapers, onCapaClick)
            section("Economia e Gestão", capasResponse.economyNewspapers, onCapaClick)
        }
    } ?: Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private fun LazyListScope.section(
    title: String,
    capas: List<Capa>,
    onCapaClick: (Capa) -> Unit
) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
    items(capas) { capa ->
        CapaItem(capa) { onCapaClick(capa) }
    }
}

@Composable
fun CapaItem(capa: Capa, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = capa.url,
            contentDescription = capa.nome,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = capa.nome,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

