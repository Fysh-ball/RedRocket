package site.fysh.redrocket.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Browser-style tab bar with rounded pill tabs, plus a settings button on the right. */
@Composable
fun BrowserTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    hasUnreadResponses: Boolean,
    onSettingsClick: () -> Unit,
    onDashboardTabPositioned: ((LayoutCoordinates) -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf("Alert System", "Dashboard")
                tabs.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    val showDot = index == 1 && hasUnreadResponses
                    val tabWeight by animateFloatAsState(
                        targetValue = if (isSelected) 1.4f else 0.8f,
                        label = "tabWeight$index"
                    )
                    val tabBackground by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        label = "tabBackground$index"
                    )
                    val tabTextColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        label = "tabTextColor$index"
                    )

                    val tabPositionModifier = if (index == 1 && onDashboardTabPositioned != null)
                        Modifier.onGloballyPositioned { onDashboardTabPositioned(it) }
                    else Modifier

                    Box(
                        modifier = Modifier
                            .weight(tabWeight)
                            .fillMaxHeight()
                            .then(tabPositionModifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tabBackground)
                            .clickable { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (isSelected) 16.sp else 15.sp,
                                color = tabTextColor
                            )
                            if (showDot) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
