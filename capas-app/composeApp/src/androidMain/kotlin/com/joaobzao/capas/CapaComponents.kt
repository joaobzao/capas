package com.joaobzao.capas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.RelativeDateFormatter
import androidx.compose.foundation.layout.Column
import java.util.Locale

/**
 * Core visual of a cover: image, gradient overlay, name/date, and a favorite heart.
 * Shared by the draggable grid item (main screen) and the My Covers grid.
 */
@Composable
fun CapaCoverContent(
    capa: Capa,
    isFavorite: Boolean,
    onToggleFavorite: (Capa) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
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

        // Favorite star
        IconButton(
            onClick = { onToggleFavorite(capa) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(
                    if (isFavorite) R.string.action_unfavorite else R.string.action_favorite
                ),
                tint = if (isFavorite) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 56.dp)
        ) {
            Text(
                text = capa.nome,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 2,
            )
            val relativeDate = RelativeDateFormatter.formatRelativeDate(
                dateString = capa.lastUpdated,
                todayString = java.time.LocalDate.now().toString(),
                language = Locale.getDefault().language
            )
            if (relativeDate != null) {
                Text(
                    text = relativeDate,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun CapaGridItemDraggable(
    capa: Capa,
    isDragging: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: (Capa) -> Unit,
    modifier: Modifier = Modifier,
    dragEnabled: Boolean = true,
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
            .then(
                if (dragEnabled) Modifier.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    )
                } else Modifier
            )
    ) {
        CapaCoverContent(
            capa = capa,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite
        )
    }
}
