package com.joaobzao.capas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.joaobzao.capas.capas.Capa

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CapaDetailScreen(
    capas: List<Capa>,
    initialPage: Int,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { capas.size }
    var isZoomed by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (!isZoomed) {
                TopAppBar(
                title = { 
                    Text(
                        capas.getOrNull(pagerState.currentPage)?.nome ?: "", 
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
                )
            }
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isZoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) { page ->
                val capa = capas[page]
                ZoomableCapaImage(
                    capa = capa,
                    isPageVisible = page == pagerState.currentPage,
                    onZoomChange = { zoomed ->
                        if (page == pagerState.currentPage) {
                            isZoomed = zoomed
                        }
                    }
                )
            }
            
            // Navigation Buttons
            if (!isZoomed) {
                Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous Button
                if (pagerState.currentPage > 0) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.8f),
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anterior")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp)) // Spacer to maintain layout if needed, or just let SpaceBetween handle it
                }
                
                // Next Button
                if (pagerState.currentPage < capas.size - 1) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.8f),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Próxima")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Próxima")
                    }
                }
                }
            }
        }
    }
}

@Composable
fun ZoomableCapaImage(
    capa: Capa,
    isPageVisible: Boolean,
    onZoomChange: (Boolean) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // We need a way to cancel the double-tap animation if user starts dragging
    val animationJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Reset zoom when page is not visible
    androidx.compose.runtime.LaunchedEffect(isPageVisible) {
        if (!isPageVisible) {
            scale = 1f
            offset = Offset.Zero
            onZoomChange(false)
        }
    }
    
    // Notify parent about zoom state changes
    androidx.compose.runtime.LaunchedEffect(scale) {
        onZoomChange(scale > 1f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            // Synchronous update for smooth zoom/pan with 2 fingers
            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
            scale = newScale

            val maxX = (viewportWidth * newScale - viewportWidth) / 2
            val maxY = (viewportHeight * newScale - viewportHeight) / 2
            
            val newOffset = offset + panChange
            offset = Offset(
                x = newOffset.x.coerceIn(-maxX, maxX),
                y = newOffset.y.coerceIn(-maxY, maxY)
            )
        }

        AsyncImage(
            model = capa.url,
            contentDescription = capa.nome,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            // Cancel any running animation
                            animationJob.value?.cancel()
                            
                            animationJob.value = coroutineScope.launch {
                                if (scale > 1f) {
                                    // Animate zoom out
                                    val startScale = scale
                                    val startOffset = offset
                                    
                                    animate(0f, 1f) { value, _ ->
                                        // Interpolate from start to 1.0/Zero
                                        scale = androidx.compose.ui.util.lerp(startScale, 1f, value)
                                        offset = androidx.compose.ui.geometry.lerp(startOffset, Offset.Zero, value)
                                    }
                                } else {
                                    // Animate zoom in
                                    val targetScale = 3f
                                    val center = Offset(viewportWidth / 2, viewportHeight / 2)
                                    val targetOffset = (center - tapOffset) * (targetScale - 1)
                                    
                                    val maxOffsetX = (viewportWidth * targetScale - viewportWidth) / 2
                                    val maxOffsetY = (viewportHeight * targetScale - viewportHeight) / 2
                                    val clampedTargetOffset = Offset(
                                        x = targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                        y = targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                    
                                    val startScale = scale
                                    val startOffset = offset
                                    
                                    animate(0f, 1f) { value, _ ->
                                        scale = androidx.compose.ui.util.lerp(startScale, targetScale, value)
                                        offset = androidx.compose.ui.geometry.lerp(startOffset, clampedTargetOffset, value)
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(scale > 1f) {
                    if (scale > 1f) {
                        detectDragGestures(
                            onDragStart = { 
                                animationJob.value?.cancel()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Synchronous update!
                                val maxX = (viewportWidth * scale - viewportWidth) / 2
                                val maxY = (viewportHeight * scale - viewportHeight) / 2
                                
                                val newOffset = offset + dragAmount
                                offset = Offset(
                                    x = newOffset.x.coerceIn(-maxX, maxX),
                                    y = newOffset.y.coerceIn(-maxY, maxY)
                                )
                            }
                        )
                    }
                }
                .transformable(state = transformableState)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}
