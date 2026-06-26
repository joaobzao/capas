package com.joaobzao.capas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.joaobzao.capas.capas.Capa

/**
 * A grid of covers with the same gestures as the main screen: long-press to drag and
 * reorder. When [onRemove] is provided, dropping on the trash zone removes the cover;
 * pass null to disable the trash entirely (reorder only). Positions are tracked relative
 * to this component's own container so it can be placed anywhere (e.g. below a header).
 *
 * The parent is responsible for showing any "removed" snackbar via [onRemove], so it
 * survives even when the last item leaves this grid.
 */
@Composable
fun DraggableCapaGrid(
    capas: List<Capa>,
    favoriteIds: Set<String>,
    onToggleFavorite: (Capa) -> Unit,
    onCapaClick: (Capa) -> Unit,
    onReorder: (List<Capa>) -> Unit,
    onRemove: ((Capa) -> Unit)? = null,
    onDraggingChange: (Boolean) -> Unit = {},
    onInteraction: () -> Unit = {},
    dragEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 120.dp)
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var localCapas by remember(capas) { mutableStateOf(capas) }
    val itemInfos = remember { mutableStateMapOf<String, ItemInfo>() }
    val gridState = rememberLazyGridState()

    var draggingCapa by remember { mutableStateOf<Capa?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    var previewSize by remember { mutableStateOf(DpSize.Zero) }
    var isOverTrash by remember { mutableStateOf(false) }
    var isShrinking by remember { mutableStateOf(false) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }

    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    LaunchedEffect(draggingCapa) { onDraggingChange(draggingCapa != null) }

    // Scrolling counts as interacting with the content (used to dismiss search).
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) onInteraction()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                containerOrigin = it.positionInWindow()
                containerHeightPx = it.size.height.toFloat()
            }
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
            userScrollEnabled = draggingCapa == null
        ) {
            items(localCapas, key = { it.id }) { capa ->
                CapaGridItemDraggable(
                    modifier = Modifier.animateItem(
                        placementSpec = spring(dampingRatio = 0.75f, stiffness = 600f)
                    ),
                    capa = capa,
                    isDragging = draggingCapa?.id == capa.id,
                    isFavorite = capa.id in favoriteIds,
                    onToggleFavorite = { onInteraction(); onToggleFavorite(it) },
                    dragEnabled = dragEnabled,
                    onClick = { onInteraction(); onCapaClick(it) },
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

                        val currentDragPosition = startOffset + dragOffset + Offset(
                            previewSize.width.value / 2,
                            previewSize.height.value / 2
                        )

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
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (onRemove != null && isOverTrash && draggingCapa != null) {
                            val removed = draggingCapa!!
                            isShrinking = true
                            itemInfos.remove(removed.id)
                            onRemove(removed)
                        } else {
                            if (draggingCapa != null) {
                                onReorder(localCapas)
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
                        val pos = coords.positionInWindow() - containerOrigin
                        val size = with(density) { coords.size.toSize().toDpSize() }
                        itemInfos[capa.id] = ItemInfo(pos, size)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // Trash Drop Zone
        AnimatedVisibility(
            visible = onRemove != null && draggingCapa != null,
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
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                        CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_remove),
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
                    isOverTrash = false
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

            if (onRemove != null) {
                LaunchedEffect(dragOffset) {
                    val threshold = containerHeightPx - with(density) { 150.dp.toPx() }
                    val itemBottom = startOffset.y + dragOffset.y + previewSize.height.value
                    isOverTrash = itemBottom > threshold
                }
            }
        }
    }
}
