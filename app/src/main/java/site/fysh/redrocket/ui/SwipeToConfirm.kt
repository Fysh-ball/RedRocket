package site.fysh.redrocket.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SwipeToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val thumbSizeDp = 68.dp
    var trackWidthPx by remember { mutableStateOf(0f) }
    val thumbSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { thumbSizeDp.toPx() }
    val maxOffset = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)

    val offsetX = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1.0f,
        label = "thumbScale"
    )
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val progress = if (maxOffset > 0f) (offsetX.value / maxOffset).coerceIn(0f, 1f) else 0f

    val containerColor = if (enabled)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val thumbColor = if (enabled) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.outline

    val textColor = if (enabled) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(80.dp)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
            .clip(RoundedCornerShape(40.dp))
            .background(containerColor),
        contentAlignment = Alignment.CenterStart
    ) {
        if (enabled && progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f * progress))
            )
        }

        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = textColor.copy(alpha = (1f - progress * 0.5f).coerceIn(0.4f, 1f)),
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSizeDp)
                .scale(thumbScale)
                .padding(6.dp)
                .clip(CircleShape)
                .background(thumbColor)
                .pointerInput(enabled, maxOffset) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                val confirmed = maxOffset > 0f && offsetX.value >= maxOffset * 0.85f
                                if (confirmed) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm()
                                }
                                offsetX.animateTo(
                                    0f,
                                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f)
                                )
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f)
                                )
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                // If maxOffset is not yet measured (very first frame),
                                // clamp to 0 so the thumb cannot escape the track.
                                val upperBound = if (maxOffset > 0f) maxOffset else 0f
                                val newValue = (offsetX.value + dragAmount.x)
                                    .coerceIn(0f, upperBound)
                                offsetX.snapTo(newValue)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (enabled) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onError
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
