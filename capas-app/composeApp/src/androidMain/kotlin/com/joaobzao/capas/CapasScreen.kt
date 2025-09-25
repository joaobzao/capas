import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.CapasViewModel
import kotlinx.coroutines.launch

enum class CapasCategory(val label: String) {
    NATIONAL("Jornais Nacionais"),
    SPORT("Desporto"),
    ECONOMY("Economia e Gestão")
}

data class ItemInfo(val position: Offset, val size: DpSize)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapasScreen(
    viewModel: CapasViewModel,
    onCapaClick: (Capa) -> Unit
) {
    val state by viewModel.capasState.collectAsState()
    var selectedCategory by rememberSaveable { mutableStateOf(CapasCategory.NATIONAL) }
    var showRemoved by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Estado do drag
    var draggingCapa by remember { mutableStateOf<Capa?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    var previewSize by remember { mutableStateOf(DpSize.Zero) }
    var isOverTrash by remember { mutableStateOf(false) }
    var isShrinking by remember { mutableStateOf(false) }

    // Posições e tamanhos dos itens
    val itemInfos = remember { mutableStateMapOf<String, ItemInfo>() }

    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp.value

    LaunchedEffect(Unit) {
        viewModel.getCapas()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capas", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showRemoved = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Ver capas removidas")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            state.capas?.let { capasResponse ->
                val capasForCategory = when (selectedCategory) {
                    CapasCategory.NATIONAL -> capasResponse.mainNewspapers
                    CapasCategory.SPORT -> capasResponse.sportNewspapers
                    CapasCategory.ECONOMY -> capasResponse.economyNewspapers
                }

                // 🔑 sempre que muda a lista → limpar coordenadas
                LaunchedEffect(capasForCategory) {
                    itemInfos.clear()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CapasCategory.entries.size) { index ->
                            val category = CapasCategory.entries[index]
                            FilterChip(
                                selected = category == selectedCategory,
                                onClick = { selectedCategory = category },
                                label = { Text(category.label) }
                            )
                        }
                    }

                    // Grid principal
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(capasForCategory, key = { it.id }) { capa ->
                            CapaGridItemDraggable(
                                capa = capa,
                                isDragging = draggingCapa?.id == capa.id,
                                onClick = onCapaClick,
                                onDragStart = {
                                    draggingCapa = capa
                                    val info = itemInfos[capa.id]
                                    startOffset = info?.position ?: Offset.Zero
                                    previewSize = info?.size ?: DpSize.Zero
                                    dragOffset = Offset.Zero
                                    isShrinking = false
                                },
                                onDrag = { offset -> dragOffset += offset },
                                onDragEnd = {
                                    if (isOverTrash && draggingCapa != null) {
                                        val removed = draggingCapa!!
                                        isShrinking = true
                                        // 🔑 remove coordenadas da capa eliminada
                                        itemInfos.remove(removed.id)
                                        viewModel.removeCapa(removed)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "${removed.nome} removida",
                                                actionLabel = "Anular",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restoreCapa(removed)
                                            }
                                        }
                                    } else {
                                        // reset
                                        draggingCapa = null
                                        dragOffset = Offset.Zero
                                        startOffset = Offset.Zero
                                        previewSize = DpSize.Zero
                                        isOverTrash = false
                                        isShrinking = false
                                    }
                                },
                                onPositioned = { coords ->
                                    val pos = coords.localToWindow(Offset.Zero)
                                    val size = with(density) { coords.size.toSize().toDpSize() }
                                    itemInfos[capa.id] = ItemInfo(pos, size)
                                }
                            )
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

            // Área de remover
            if (draggingCapa != null) {
                val trashScale by animateFloatAsState(
                    targetValue = if (isShrinking && isOverTrash) 1.3f else 1f,
                    label = "trash-scale"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            if (isOverTrash) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remover",
                        tint = if (isOverTrash) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer(
                                scaleX = trashScale,
                                scaleY = trashScale
                            )
                    )
                }
            }

            // Preview enquanto arrastas
            draggingCapa?.let { capa ->
                val targetScale = if (isShrinking) 0f else 1.05f
                val scale by animateFloatAsState(targetValue = targetScale, label = "drag-scale") {
                    if (isShrinking) {
                        draggingCapa = null
                        dragOffset = Offset.Zero
                        startOffset = Offset.Zero
                        previewSize = DpSize.Zero
                        isShrinking = false
                    }
                }
                val alpha by animateFloatAsState(
                    targetValue = if (isOverTrash) 0.5f else 1f,
                    label = "drag-alpha"
                )

                AsyncImage(
                    model = capa.url,
                    contentDescription = capa.nome,
                    modifier = Modifier
                        .graphicsLayer(
                            translationX = startOffset.x + dragOffset.x,
                            translationY = startOffset.y + dragOffset.y,
                            shadowElevation = 12.dp.value,
                            scaleX = scale,
                            scaleY = scale,
                            alpha = alpha
                        )
                        .size(previewSize),
                    contentScale = ContentScale.Crop
                )

                LaunchedEffect(dragOffset) {
                    val threshold = screenHeight * 1.6f
                    isOverTrash = (startOffset.y + dragOffset.y) > threshold
                }
            }
        }
    }

    // Bottom sheet removidas
    if (showRemoved) {
        ModalBottomSheet(onDismissRequest = { showRemoved = false }) {
            Text(
                "Capas removidas",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            if (state.removed.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhuma capa removida")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxHeight(0.6f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.removed, key = { it.id }) { capa ->
                        ElevatedCard(
                            onClick = { viewModel.restoreCapa(capa) },
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
                                        .aspectRatio(0.75f),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = capa.nome,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapaGridItemDraggable(
    capa: Capa,
    isDragging: Boolean,
    onClick: (Capa) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0f else 1f,
        label = "item-alpha"
    )

    ElevatedCard(
        onClick = { onClick(capa) },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .onGloballyPositioned { coords -> onPositioned(coords) }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            }
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
                    .aspectRatio(0.75f),
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
