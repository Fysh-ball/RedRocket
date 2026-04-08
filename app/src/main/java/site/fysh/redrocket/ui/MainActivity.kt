package site.fysh.redrocket.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import site.fysh.redrocket.EmergencyApp
import site.fysh.redrocket.ui.theme.EmergencyAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the SplashScreen before super.onCreate so the system holds the
        // splash window until the first composition is ready. This gives a clean
        // Red Rocket → main UI transition instead of a brief black/white flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val app = context.applicationContext as EmergencyApp

            val viewModel: MainViewModel = viewModel(
                factory = MainViewModelFactory(app)
            )
            val uiState by viewModel.uiState.collectAsState()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiState.theme) {
                AppTheme.NIGHT  -> true
                AppTheme.LIGHT  -> false
                AppTheme.SYSTEM -> systemDark
                AppTheme.GRAY   -> true  // always dark, uses Discord-style palette
            }
            val trueDark = uiState.theme == AppTheme.NIGHT
            EmergencyAppTheme(darkTheme = darkTheme, trueDark = trueDark) {
                when {
                    uiState.isInitializing -> {
                        // Hold on a blank surface until DataStore emits the first value.
                        // Prevents the main screen flashing before the first-launch screen appears.
                        androidx.compose.material3.Surface(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize()
                        ) {}
                    }
                    uiState.isFirstLaunch -> {
                        // During setup, nothing fires automatically - all actions are user-initiated.
                        FirstLaunchScreen(viewModel)
                    }
                    else -> {
                        // Permissions are requested up-front by FirstLaunchScreen during setup.
                        // If the user later revokes them, the corresponding setup-status banners
                        // in MainScreen flag the issue and offer a one-tap path back to Settings.
                        MainScreen(viewModel)
                    }
                }
            }
        }
    }
}
