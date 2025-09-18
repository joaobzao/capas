import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.CapasViewModel

enum class CapasCategory(val label: String) {
    NATIONAL("Jornais Nacionais"),
    SPORT("Desporto"),
    ECONOMY("Economia e Gestão")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CapasScreen(
    viewModel: CapasViewModel,
    onCapaClick: (Capa) -> Unit
) {
    val state by viewModel.competitionsState.collectAsState()
    var selectedCategory by rememberSaveable { mutableStateOf(CapasCategory.NATIONAL) }

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
            val capasForCategory = when (selectedCategory) {
                CapasCategory.NATIONAL -> capasResponse.mainNewspapers
                CapasCategory.SPORT -> capasResponse.sportNewspapers
                CapasCategory.ECONOMY -> capasResponse.economyNewspapers
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Scrollable Chips row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CapasCategory.values().size) { index ->
                        val category = CapasCategory.values()[index]
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) }
                        )
                    }
                }

                // Grid of covers
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(capasForCategory) { capa ->
                        CapaGridItem(capa, onCapaClick)
                    }
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
                    .fillMaxWidth()
                    .aspectRatio(0.75f), // mantém proporção
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
