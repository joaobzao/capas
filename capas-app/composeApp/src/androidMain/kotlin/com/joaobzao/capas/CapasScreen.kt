import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.joaobzao.capas.AboutSheet
import com.joaobzao.capas.R
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.CapasViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class CapasCategory(val labelResId: Int) {
    NATIONAL(R.string.category_national),
    SPORT(R.string.category_sport),
    ECONOMY(R.string.category_economy)
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
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Drag state
    var draggingCapa by remember { mutableStateOf<Capa?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    var previewSize by remember { mutableStateOf(DpSize.Zero) }
    var isOverTrash by remember { mutableStateOf(false) }
    var isShrinking by remember { mutableStateOf(false) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }

    // Item positions and sizes
    val itemInfos = remember { mutableStateMapOf<String, ItemInfo>() }

    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp.value

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.getCapas()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow) // Premium background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Custom Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val date = remember {
                        LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
                    }
                    Text(
                        text = date.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.title_capas),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Header Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showRemoved = true },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_view_removed),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Mais opções",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.title_about)) },
                                onClick = {
                                    showMenu = false
                                    showAbout = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Minimalist Category Picker
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                items(CapasCategory.entries.size) { index ->
                    val category = CapasCategory.entries[index]
                    val isSelected = category == selectedCategory
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedCategory = category }
                    ) {
                        Text(
                            text = stringResource(category.labelResId),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }
            }
            
            // Shadow separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .shadow(elevation = 4.dp)
            )

            // Grid
            state.capas?.let { capasResponse ->
                val capasForCategory = when (selectedCategory) {
                    CapasCategory.NATIONAL -> capasResponse.mainNewspapers
                    CapasCategory.SPORT -> capasResponse.sportNewspapers
                    CapasCategory.ECONOMY -> capasResponse.economyNewspapers
                }

                // Local state for optimistic reordering
                var localCapas by remember { mutableStateOf(capasForCategory) }

                // Sync local state when category or source data changes
                LaunchedEffect(capasForCategory) {
                    localCapas = capasForCategory
                    itemInfos.clear()
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    userScrollEnabled = draggingCapa == null
                ) {
                    items(localCapas, key = { it.id }) { capa ->
                        CapaGridItemDraggable(
                            modifier = Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = 0.75f,
                                    stiffness = 600f
                                )
                            ),
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
                            onDrag = { offset -> 
                                dragOffset += offset
                                
                                // Reordering Logic
                                val currentDragPosition = startOffset + dragOffset + Offset(previewSize.width.value / 2, previewSize.height.value / 2)
                                
                                // Find item under drag position
                                val targetItem = itemInfos.entries.firstOrNull { (id, info) ->
                                    id != draggingCapa?.id &&
                                    currentDragPosition.x >= info.position.x &&
                                    currentDragPosition.x <= info.position.x + info.size.width.value &&
                                    currentDragPosition.y >= info.position.y &&
                                    currentDragPosition.y <= info.position.y + info.size.height.value
                                }?.key

                                if (targetItem != null && draggingCapa != null) {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastSwapTime > 250) {
                                        val fromIndex = localCapas.indexOfFirst { it.id == draggingCapa!!.id }
                                        val toIndex = localCapas.indexOfFirst { it.id == targetItem }
                                    
                                    if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                                        val mutableList = localCapas.toMutableList()
                                        mutableList.add(toIndex, mutableList.removeAt(fromIndex))
                                        localCapas = mutableList
                                        lastSwapTime = currentTime

                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        
                                        // Update startOffset to the new position to keep the drag relative
                                        // Actually, if we swap, the item in the grid moves.
                                        // But the drag ghost is separate.
                                        // However, we rely on `onDragStart` setting `startOffset` from `itemInfos`.
                                        // If grid re-composes, `itemInfos` updates?
                                        // `onPositioned` will update `itemInfos`.
                                        // But we assume the layout shift happens.
                                    }
                                    }
                                    }

                            },
                            onDragEnd = {
                                if (isOverTrash && draggingCapa != null) {
                                    val removed = draggingCapa!!
                                    isShrinking = true
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
                                    // Save new order
                                    if (draggingCapa != null) {
                                        viewModel.updateCapaOrder(localCapas)
                                    }
                                    
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
                    
                    item { Spacer(modifier = Modifier.height(80.dp)) } // Space for trash
                }
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Trash Drop Zone
        AnimatedVisibility(
            visible = draggingCapa != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val trashScale by animateFloatAsState(
                targetValue = if (isOverTrash) 1.2f else 1f,
                label = "trash-scale",
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
            )

            Box(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .size(80.dp)
                    .shadow(
                        elevation = if (isOverTrash) 16.dp else 8.dp,
                        shape = CircleShape,
                        spotColor = if (isOverTrash) Color.Red.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f)
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remover",
                    tint = if (isOverTrash) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer(scaleX = trashScale, scaleY = trashScale)
                )
            }
        }

        // Draggable Preview
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
                        shadowElevation = 20.dp.value,
                        scaleX = scale,
                        scaleY = scale,
                        alpha = alpha,
                        shape = RoundedCornerShape(20.dp),
                        clip = true
                    )
                    .size(previewSize),
                contentScale = ContentScale.Crop
            )

            LaunchedEffect(dragOffset) {
                // Calculate threshold based on screen height in pixels minus trash area
                val screenHeightPx = with(density) { screenHeight.dp.toPx() }
                val threshold = screenHeightPx - with(density) { 150.dp.toPx() }
                
                // Check if the bottom of the dragged item crosses the threshold
                val itemBottom = startOffset.y + dragOffset.y + previewSize.height.value
                isOverTrash = itemBottom > threshold
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Removed Capas Sheet
    if (showRemoved) {
        ModalBottomSheet(
            onDismissRequest = { showRemoved = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        "Recuperar Capas",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    IconButton(
                        onClick = { showRemoved = false },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Fechar")
                    }
                }
                
                if (state.removed.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Nenhuma capa removida",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(state.removed, key = { it.id }) { capa ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { viewModel.restoreCapa(capa) }
                            ) {
                                AsyncImage(
                                    model = capa.url,
                                    contentDescription = capa.nome,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                                startY = 300f
                                            )
                                        )
                                )
                                Text(
                                    text = capa.nome,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    maxLines = 2,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                )
                                
                                Icon(
                                    Icons.Default.Refresh, // Was Restore
                                    contentDescription = "Restaurar",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                        .padding(8.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAbout) {
        AboutSheet(
            viewModel = viewModel,
            onDismiss = { showAbout = false }
        )
    }
}

@Composable
fun CapaGridItemDraggable(
    capa: Capa,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
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

    Card(
        onClick = { onClick(capa) },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
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
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = capa.url,
                contentDescription = capa.nome,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 200f
                        )
                    )
            )
            
            Text(
                text = capa.nome,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}
