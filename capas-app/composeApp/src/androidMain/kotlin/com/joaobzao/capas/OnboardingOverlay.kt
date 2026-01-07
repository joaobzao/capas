package com.joaobzao.capas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.lerp
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.ItemInfo
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

enum class OnboardingStep {
    SORT,
    REMOVE,
    RESTORE,
    COMPLETED
}

@Composable
fun OnboardingOverlay(
    itemInfos: Map<String, ItemInfo>,
    capas: List<Capa>,
    refreshIconBounds: Rect?,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.SORT) }
    
    // Auto-advance logic could be timer-based or easy tap
    // For "immersive", let's use a tap-anywhere to advance, but also animate automatically?
    // User request: "drag an item to sort", "drag to bin", "restore"
    // Best is to show the animation loop and let user tap to next.
    
    val density = LocalDensity.current 
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (currentStep != OnboardingStep.COMPLETED) {
                    currentStep = OnboardingStep.entries[currentStep.ordinal + 1]
                    if (currentStep == OnboardingStep.COMPLETED) {
                        onDismiss()
                    }
                }
            }
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize()
        ) { step ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (step) {
                    OnboardingStep.SORT -> SortTip(itemInfos, capas)
                    OnboardingStep.REMOVE -> RemoveTip(itemInfos, capas)
                    OnboardingStep.RESTORE -> RestoreTip(refreshIconBounds)
                    else -> {}
                }
            }
        }
        
        // Skip button / Tap text
        Text(
            text = "Toque para continuar",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )
    }
}

@Composable
fun SortTip(itemInfos: Map<String, ItemInfo>, capas: List<Capa>) {
    Box(modifier = Modifier.fillMaxSize()) {
        val targetItem = capas.firstOrNull { itemInfos.containsKey(it.id) }
        val itemInfo = targetItem?.let { itemInfos[it.id] } ?: return@Box
        
        val startPos = itemInfo.position + Offset(itemInfo.size.width.value * 1.5f, itemInfo.size.height.value * 1.5f) // Convert dp to px? No, Offset is px usually.
        // Wait, ItemInfo position is likely Px. Size is DpSize.
        // Ideally ItemInfo should store Px size or we convert.
        // In CapasScreen: val size = with(density) { coords.size.toSize().toDpSize() }
        // So position is Px (localToWindow), Size is Dp.
        
        val density = LocalDensity.current
        val itemWidthPx = with(density) { itemInfo.size.width.toPx() }
        val itemHeightPx = with(density) { itemInfo.size.height.toPx() }
        val centerPos = itemInfo.position + Offset(itemWidthPx / 2f, itemHeightPx / 2f)
        
        // Animate drag to next item position?
        // Let's just move right.
        val endPos = centerPos + Offset(itemWidthPx + 20f, 0f)

        val infiniteTransition = rememberInfiniteTransition(label = "sort")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sort-progress"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Highlight item
            drawRect(
                color = Color.Transparent,
                topLeft = itemInfo.position,
                size = androidx.compose.ui.geometry.Size(itemWidthPx, itemHeightPx),
                style = Stroke(width = 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f)))
            )
            
            // Draw Hand/Cursor
            // Simple circle optimization
            val currentPos = lerp(centerPos, endPos, progress)
            
            drawCircle(
                color = Color.White,
                radius = 20.dp.toPx(),
                center = currentPos,
                alpha = 0.8f
            )
            drawCircle(
                color = Color.White,
                radius = 25.dp.toPx(),
                center = currentPos,
                style = Stroke(width = 2.dp.toPx()),
                alpha = (1f - progress) // Ripple
            )
        }
        
        Text(
            text = "Arraste para organizar",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun RemoveTip(itemInfos: Map<String, ItemInfo>, capas: List<Capa>) {
    Box(modifier = Modifier.fillMaxSize()) {
        val targetItem = capas.firstOrNull { itemInfos.containsKey(it.id) }
        val itemInfo = targetItem?.let { itemInfos[it.id] } ?: return@Box
        
        val density = LocalDensity.current
        val itemWidthPx = with(density) { itemInfo.size.width.toPx() }
        val itemHeightPx = with(density) { itemInfo.size.height.toPx() }
        val centerPos = itemInfo.position + Offset(itemWidthPx / 2f, itemHeightPx / 2f)
        
        // Drag to bottom center (approx trash location)
        // We don't have exact trash rect here, but we can assume bottom center of screen.
        // Note: Canvas size gives us screen size roughly.
        
        val infiniteTransition = rememberInfiniteTransition(label = "remove")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "remove-progress"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val bottomCenter = Offset(size.width / 2f, size.height - 100.dp.toPx())
            val currentPos = lerp(centerPos, bottomCenter, progress)
            
            drawCircle(
                color = Color.White,
                radius = 20.dp.toPx(),
                center = currentPos,
                alpha = 0.8f
            )
        }

        Text(
            text = "Arraste para o lixo\npara remover",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun RestoreTip(refreshIconBounds: Rect?) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (refreshIconBounds == null) return@Box
        
        val centerPos = refreshIconBounds.center
        
        val infiniteTransition = rememberInfiniteTransition(label = "restore")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "restore-scale"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White,
                radius = 30.dp.toPx() * scale,
                center = centerPos,
                style = Stroke(width = 2.dp.toPx()),
                alpha = 0.5f
            )
        }

        Text(
            text = "Recupere capas removidas\naqui",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 100.dp)
        )
        
        // Draw arrow pointing to it?
        // Simplified for now.
    }
}
