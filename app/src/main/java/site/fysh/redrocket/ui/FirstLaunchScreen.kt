package site.fysh.redrocket.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import site.fysh.redrocket.utils.PermissionUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FirstLaunchScreen(viewModel: MainViewModel) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    BackHandler {}

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false   // manual navigation only
            ) { page ->
                when (page) {
                    0 -> WelcomePage(
                        onNext = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    1 -> PermissionsPage(
                        onComplete = { scope.launch { pagerState.animateScrollToPage(2) } }
                    )
                    2 -> ReadyPage(
                        onLaunch = { viewModel.completeFirstLaunch() },
                        onStartTutorial = {
                            viewModel.startTutorial()
                            viewModel.completeFirstLaunch()
                        }
                    )
                }
            }

            // Page indicator dots
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

// ── Page 1: Welcome ──────────────────────────────────────────────────────────

@Composable
fun WelcomePage(onNext: () -> Unit) {
    var showDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        RedRocketLogo(modifier = Modifier.wrapContentWidth())

        Spacer(Modifier.height(28.dp))

        Text(
            "Welcome to Red Rocket",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Automatically send emergency messages to your contacts when an alert is detected.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(28.dp))

        val pillShape = RoundedCornerShape(50)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("About", "FAQ", "Disclaimer").forEach { label ->
                OutlinedButton(
                    onClick = { showDialog = label },
                    modifier = Modifier.weight(1f),
                    shape = pillShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(label, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Next →", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))
    }

    showDialog?.let { title ->
        val dialogText = when (title) {
            "About" -> """## Red Rocket: Automated Emergency Response

Red Rocket is an automated emergency response app designed to help you keep your family and loved ones safe during critical situations.

Red Rocket monitors emergency broadcast alerts and matches them to your custom trigger words. When a match is detected, it instantly sends your pre-written message to the contacts you choose — so you can act without hesitation when every second counts."""

            "FAQ" -> """Q: Are you gonna steal my data?
A: All data is stored locally. I do not want your data.

Q: Will my messages be automatically sent?
A: When a filter matches an alert, the app will first assess if it's a false alarm and if not, it'll send the message.

Q: What's Global Keyword Detection?
A: While it's not needed or recommended, you can enable the app to listen to every other notification for keywords.

Q: Will this work when even when my phone is locked?
A: Yes it should. But to be safe check the app dashboard to see if it's waiting for responses."""

            "Disclaimer" -> """IMPORTANT — PLEASE READ

Red Rocket is designed for legitimate emergency communications only. You, the user, take sole responsibility for all messages sent through this app. By using Red Rocket you agree to use the app only for lawful purposes and that you'll only send messages to people that have given you explicit consent in receiving emergency notifications from you.

Misuse through harassment, spam messaging, or other violations is strictly prohibited and may violate applicable laws.

The developers of Red Rocket accept no liability for any misuse of this application or for any failed message deliveries during an actual emergency."""

            else -> "No information available."
        }

        AlertDialog(
            onDismissRequest = { showDialog = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(dialogText, fontSize = 14.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = null }) { Text("Close") }
            }
        )
    }
}

// ── Page 2: Permissions ───────────────────────────────────────────────────────

@Composable
fun PermissionsPage(onComplete: () -> Unit) {
    val context = LocalContext.current

    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var contactsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    var notificationAccessGranted by remember {
        mutableStateOf(PermissionUtils.isNotificationServiceEnabled(context))
    }
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var batteryOptDisabled by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        smsGranted = results[Manifest.permission.SEND_SMS] == true &&
                     results[Manifest.permission.RECEIVE_SMS] == true
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted = granted
    }
    val postNotificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    // Poll notification access while this page is visible (user may return from Settings)
    LaunchedEffect(Unit) {
        while (true) {
            notificationAccessGranted = PermissionUtils.isNotificationServiceEnabled(context)
            batteryOptDisabled = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            delay(1000)
        }
    }

    val canProceed = smsGranted && notificationAccessGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        Text(
            "Set Up Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Grant the permissions below. SMS and Notification Access are required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        PermissionCard(
            title = "Notification Access",
            description = "Required — lets the app detect emergency alerts from other apps and carrier broadcasts.",
            isGranted = notificationAccessGranted,
            accentGranted = MaterialTheme.colorScheme.primary,
            onGrant = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )

        PermissionCard(
            title = "SMS (Send & Receive)",
            description = "Required — lets the app send emergency messages and receive 1/2/3 replies from contacts.",
            isGranted = smsGranted,
            accentGranted = MaterialTheme.colorScheme.primary,
            onGrant = { smsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)) }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = "Post Notifications",
                description = "Lets the app show its own status and progress alerts.",
                isGranted = notificationsGranted,
                accentGranted = MaterialTheme.colorScheme.primary,
                onGrant = { postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }

        PermissionCard(
            title = "Contacts",
            description = "Lets you pick recipients from your contact list.",
            isGranted = contactsGranted,
            accentGranted = MaterialTheme.colorScheme.primary,
            onGrant = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
        )

        PermissionCard(
            title = "Battery Optimization",
            description = "Disable battery optimization so Red Rocket can send alerts even when the phone is idle.",
            isGranted = batteryOptDisabled,
            accentGranted = MaterialTheme.colorScheme.primary,
            onGrant = {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("BatteryOpt", "Failed to open battery optimization settings", e)
                    context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                }
            }
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onComplete,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Next →", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (!canProceed) {
            Spacer(Modifier.height(8.dp))
            Text(
                "SMS and Notification Access are required to continue.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    accentGranted: Color,
    onGrant: () -> Unit
) {
    val accentColor = if (isGranted) accentGranted else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!isGranted) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onGrant() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Grant Access", fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = accentGranted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ── Page 3: Ready ─────────────────────────────────────────────────────────────

@Composable
fun ReadyPage(onLaunch: () -> Unit, onStartTutorial: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(48.dp))

        RedRocketLogo(modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(40.dp))

        Text(
            "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Red Rocket is ready to protect you.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStartTutorial,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Start Tutorial (Recommended)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLaunch,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Skip Tutorial", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(80.dp))
    }
}
