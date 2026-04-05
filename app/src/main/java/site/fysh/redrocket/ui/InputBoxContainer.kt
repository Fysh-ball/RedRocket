package site.fysh.redrocket.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared scrollable container used by TriggerInput, MessageInput, and RecipientsInput.
 *
 * - Expands naturally up to [maxHeight]; beyond that the interior scrolls.
 * - [minHeight] matches Material3 TextField's intrinsic height so all three boxes appear
 *   the same size in their empty state.
 * - Draws a subtle scrollbar indicator when content overflows.
 * - Consumes all vertical scroll events so the outer page Column never scrolls while the
 *   user's finger is inside this container.
 */
@Composable
fun InputBoxContainer(
    modifier: Modifier = Modifier,
    minHeight: Dp = 56.dp,
    maxHeight: Dp = 120.dp,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit
) {
    // Consumes scroll only when content overflows — if the box doesn't need scrolling,
    // events pass through to the parent so the page can scroll normally.
    val blockParent = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (scrollState.maxValue > 0) available else Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight)
            .nestedScroll(blockParent)
            .drawWithContent {
                drawContent()
                // Scrollbar — only rendered when content overflows
                val range = scrollState.maxValue.toFloat()
                if (range > 0f) {
                    val barH = (size.height * size.height / (size.height + range))
                        .coerceAtLeast(24.dp.toPx())
                    val barTop = (scrollState.value.toFloat() / range) * (size.height - barH)
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.45f),
                        topLeft = Offset(size.width - 4.dp.toPx(), barTop + 2.dp.toPx()),
                        size = Size(3.dp.toPx(), (barH - 4.dp.toPx()).coerceAtLeast(4f)),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            content()
        }
    }
}
