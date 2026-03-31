package site.fysh.redrocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TutorialSpotlightOverlay(
    step: Int,
    scenarioBounds: ComposeRect?,
    triggerBounds: ComposeRect?,
    groupHeaderBounds: ComposeRect?,
    messageBounds: ComposeRect?,
    tabBarBounds: ComposeRect?,
    uiState: MainUiState,
    onAdvance: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalSteps = 6
    val stepIndex = step.coerceIn(0, totalSteps - 1)

    val title = when (stepIndex) {
        0 -> "1 / 6 — Scenarios"
        1 -> "2 / 6 — Rename It"
        2 -> "3 / 6 — Trigger Keywords"
        3 -> "4 / 6 — Contact Groups"
        4 -> "5 / 6 — Your Message"
        else -> "6 / 6 — Dashboard"
    }
    val body = when (stepIndex) {
        0 -> "Tap this bar to open the scenario list. Switch to a different scenario, or tap '+ Add New Scenario' at the bottom to create one."
        1 -> "Long-press this bar (not the list) to rename the current scenario. Give it a name like 'Family' or 'Work Team'."
        2 -> "Tap + to add keywords. Red Rocket auto-sends when these words appear in an emergency alert notification."
        3 -> "Long-press the group pill to rename it from 'Default'. Each group can have its own message and recipients."
        4 -> "Type your emergency message here. It's sent to all contacts in this group when the trigger fires."
        else -> "Tap Dashboard below to see delivery status and replies. Contacts reply 1 (Safe), 2 (Updates), or 3 (Urgent)."
    }
    val isComplete = when (stepIndex) {
        1 -> uiState.currentScenario.name != uiState.tutorialInitialScenarioName
        // Step 2: advance only via onSheetDismissed callback (not while the sheet is still open)
        3 -> uiState.currentScenario.groups.any { it.name != "Default" }
        4 -> uiState.currentScenario.groups.any { it.message.isNotBlank() }
        else -> false  // steps 0, 2, and 5 handled via external callbacks
    }

    val prevComplete = remember(stepIndex) { mutableStateOf(isComplete) }
    LaunchedEffect(isComplete) {
        if (isComplete && !prevComplete.value && stepIndex < totalSteps - 1) {
            delay(700)
            onAdvance()
        }
        prevComplete.value = isComplete
    }

    val targetBounds = when (stepIndex) {
        0, 1 -> scenarioBounds
        2 -> triggerBounds
        3 -> groupHeaderBounds
        4 -> messageBounds
        5 -> tabBarBounds
        else -> null
    }
    val density = LocalDensity.current
    // Match the corner radius of the highlighted element for each step
    val spotlightCornerPx = with(density) {
        when (stepIndex) {
            0, 1 -> 8.dp.toPx()   // ScenarioDropdown surface: RoundedCornerShape(8.dp)
            2    -> 12.dp.toPx()  // TriggerInput outer surface: RoundedCornerShape(12.dp)
            3    -> 8.dp.toPx()   // GroupsSection header pill: RoundedCornerShape(8.dp)
            4    -> 12.dp.toPx()  // MessageInput outer surface: RoundedCornerShape(12.dp)
            else -> 10.dp.toPx()  // Dashboard tab: RoundedCornerShape(10.dp)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightDp = maxHeight
        val screenWidthDp  = maxWidth

        // Touch-blocking modifier (no visual — only used for pointer interception)
        val touchBlock = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).consume()
                waitForUpOrCancellation()
            }
        }

        if (targetBounds != null) {
            // Step 4 (message section): expand the spotlight so the full MessageInput row —
            // including the Surface's vertical padding and the upload button — is shown
            // without clipping. Other steps use exact element bounds.
            val expandPx = with(density) {
                if (stepIndex == 4) 10.dp.toPx() else 0f
            }
            val leftPx   = targetBounds.left   - expandPx
            val topPx    = targetBounds.top    - expandPx
            val rightPx  = targetBounds.right  + expandPx
            val bottomPx = targetBounds.bottom + expandPx

            val topDp             = with(density) { topPx.toDp() }
            val leftDp            = with(density) { leftPx.toDp() }
            val spotlightWidthDp  = with(density) { (rightPx - leftPx).toDp() }
            val spotlightHeightDp = with(density) { (bottomPx - topPx).toDp() }
            val rightDp           = (screenWidthDp - leftDp - spotlightWidthDp).coerceAtLeast(0.dp)

            // Visual dim: single Canvas punches a rounded transparent hole via BlendMode.Clear.
            // CompositingStrategy.Offscreen renders to an off-screen buffer first so BlendMode.Clear
            // cuts to true transparency (rather than compositing against the background).
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                drawRect(Color.Black.copy(alpha = 0.70f))
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(rightPx - leftPx, bottomPx - topPx),
                    cornerRadius = CornerRadius(spotlightCornerPx),
                    blendMode = BlendMode.Clear
                )
            }

            // Touch blockers — invisible, 5-region layout so the spotlight gap has no
            // composable on top of it and touches fall through to the Scaffold below.
            Column(modifier = Modifier.fillMaxSize()) {
                if (topDp > 0.dp) {
                    Box(modifier = Modifier.fillMaxWidth().height(topDp).then(touchBlock))
                }
                Row(modifier = Modifier.fillMaxWidth().height(spotlightHeightDp)) {
                    if (leftDp > 0.dp) {
                        Box(modifier = Modifier.width(leftDp).fillMaxHeight().then(touchBlock))
                    }
                    Spacer(modifier = Modifier.width(spotlightWidthDp).fillMaxHeight())
                    if (rightDp > 0.dp) {
                        Box(modifier = Modifier.width(rightDp).fillMaxHeight().then(touchBlock))
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f).then(touchBlock))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .then(touchBlock)
            )
        }

        // ── Tutorial card — float close to spotlight ──────────────────────────
        if (targetBounds != null) {
            val spotlightBottomDp = with(density) { targetBounds.bottom.toDp() }
            val spotlightTopDp    = with(density) { targetBounds.top.toDp() }
            val spaceBelow = screenHeightDp - spotlightBottomDp

            if (spaceBelow >= 180.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(spotlightBottomDp + 12.dp))
                    TutorialCard(
                        title = title,
                        body = body,
                        isLastStep = stepIndex == totalSteps - 1,
                        onAdvance = onAdvance,
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((spotlightTopDp - 12.dp).coerceAtLeast(100.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    TutorialCard(
                        title = title,
                        body = body,
                        isLastStep = stepIndex == totalSteps - 1,
                        onAdvance = onAdvance,
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            TutorialCard(
                title = title,
                body = body,
                isLastStep = stepIndex == totalSteps - 1,
                onAdvance = onAdvance,
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 80.dp)
            )
        }
    }
}

@Composable
private fun TutorialCard(
    title: String,
    body: String,
    isLastStep: Boolean,
    onAdvance: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (!isLastStep) {
                    TextButton(
                        onClick = onAdvance,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Skip",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Exit Tutorial",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Button(
                    onClick = onAdvance,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isLastStep) "Done!" else "Next →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float, val y: Float,
    val vx: Float, val vy: Float,
    val rotation: Float, val vRotation: Float,
    val color: Color, val w: Float, val h: Float
)

@Composable
fun TutorialCompleteOverlay(onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val particleW = with(density) { 14.dp.toPx() }
    val particleH = with(density) { 7.dp.toPx() }

    val confettiColors = remember {
        listOf(
            Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
            Color(0xFF6BCB77), Color(0xFF4D96FF), Color(0xFFFF8CC6),
            Color(0xFFFF9F43), Color(0xFFA29BFE)
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }

        var particles by remember {
            val rand = java.util.Random()
            mutableStateOf(
                (0 until 64).map { i ->
                    val fromLeft = i < 32
                    ConfettiParticle(
                        x = if (fromLeft) -particleW else screenW,
                        y = rand.nextFloat() * screenH * 0.35f,
                        vx = if (fromLeft) rand.nextFloat() * 10f + 4f else -(rand.nextFloat() * 10f + 4f),
                        vy = -(rand.nextFloat() * 10f + 3f),
                        rotation = rand.nextFloat() * 360f,
                        vRotation = (rand.nextFloat() - 0.5f) * 12f,
                        color = confettiColors[i % confettiColors.size],
                        w = particleW * (0.7f + rand.nextFloat() * 0.6f),
                        h = particleH * (0.7f + rand.nextFloat() * 0.6f)
                    )
                }
            )
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(16)
                particles = particles.map { p ->
                    p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy,
                        vy = p.vy + 0.45f,
                        rotation = (p.rotation + p.vRotation) % 360f
                    )
                }
            }
        }

        // Dim background — absorbs taps outside the card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        waitForUpOrCancellation()
                    }
                }
        )

        // Congratulations card — above dim background
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉", fontSize = 52.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "You're all set!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Red Rocket is configured and ready to protect you and your contacts.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Let's Go!", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Confetti drawn on top of everything — Canvas has no pointer input so the
        // card's button remains fully clickable.
        val currentParticles = particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            currentParticles.forEach { p ->
                withTransform({
                    translate(left = p.x, top = p.y)
                    rotate(degrees = p.rotation, pivot = Offset(p.w / 2f, p.h / 2f))
                }) {
                    drawRect(color = p.color, size = Size(p.w, p.h))
                }
            }
        }
    }
}
