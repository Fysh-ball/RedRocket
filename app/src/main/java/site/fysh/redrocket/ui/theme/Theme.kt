package site.fysh.redrocket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Discord-inspired dark scheme */
private val SystemDarkColorScheme = darkColorScheme(
    primary             = DiscordPrimary,
    primaryContainer    = DiscordPrimaryDark,
    secondary           = DiscordPink,
    secondaryContainer  = DiscordPinkDark,
    tertiary            = DiscordPrimaryDark,
    background          = DiscordBackground,
    surface             = DiscordSurface,
    surfaceVariant      = DiscordSurfaceVariant,
    onPrimary           = DiscordWhite,
    onPrimaryContainer  = DiscordWhite,
    onSecondary         = DiscordWhite,
    onSecondaryContainer = DiscordText,
    onTertiary          = DiscordWhite,
    onBackground        = DiscordText,
    onSurface           = DiscordText,
    onSurfaceVariant    = DiscordTextMuted,
    error               = DiscordRed,
    onError             = DiscordWhite,
    errorContainer      = DiscordRedDark,
    onErrorContainer    = DiscordText,
    outline             = DiscordOutline,
    outlineVariant      = DiscordSurface,
    inverseSurface      = DiscordText,
    inverseOnSurface    = DiscordBackground,
    inversePrimary      = DiscordPrimaryDark
)

/** True Dark (AMOLED) scheme */
private val TrueDarkColorScheme = darkColorScheme(
    primary             = TrueDarkPrimary,
    primaryContainer    = TrueDarkPrimaryContainer,
    background          = TrueDarkBackground,
    surface             = TrueDarkSurface,
    surfaceVariant      = TrueDarkSurfaceVariant,
    onBackground        = TrueDarkOnBackground,
    onSurface           = TrueDarkOnBackground,
    onSurfaceVariant    = TrueDarkOnSurfaceVariant,
    outline             = TrueDarkOutline,
    error               = TrueDarkError,
    onError             = DiscordWhite,
    errorContainer      = DiscordRedDark,
    onErrorContainer    = TrueDarkOnBackground,
    onPrimary           = DiscordWhite,
    onPrimaryContainer  = DiscordWhite,
    inverseSurface      = TrueDarkOnBackground,
    inverseOnSurface    = TrueDarkBackground,
    inversePrimary      = TrueDarkPrimaryContainer
)

/** Neutral Gray dark scheme - follows system dark mode, more muted than Discord */
private val GrayColorScheme = darkColorScheme(
    primary             = GrayPrimary,
    primaryContainer    = GrayPrimaryContainer,
    background          = GrayBackground,
    surface             = GraySurface,
    surfaceVariant      = GraySurfaceVariant,
    onBackground        = GrayOnBackground,
    onSurface           = GrayOnBackground,
    onSurfaceVariant    = GrayOnSurfaceVariant,
    outline             = GrayOutline,
    outlineVariant      = GraySurfaceVariant,
    error               = GrayError,
    onError             = DiscordWhite,
    errorContainer      = DiscordRedDark,
    onErrorContainer    = GrayOnBackground,
    onPrimary           = DiscordWhite,
    onPrimaryContainer  = DiscordWhite,
    inverseSurface      = GrayOnBackground,
    inverseOnSurface    = GrayBackground,
    inversePrimary      = GrayPrimaryContainer
)

/** Amazon-inspired light scheme */
private val LightColorScheme = lightColorScheme(
    primary             = AmazonOrange,
    primaryContainer    = AmazonOrangeLight,
    secondary           = AmazonNavy,
    secondaryContainer  = AmazonNavyLight,
    tertiary            = AmazonNavy,
    background          = AmazonBackground,
    surface             = AmazonSurface,
    surfaceVariant      = AmazonSurfaceVariant,
    onPrimary           = AmazonText,
    onPrimaryContainer  = AmazonText,
    onSecondary         = DiscordWhite,
    onSecondaryContainer = DiscordWhite,
    onTertiary          = DiscordWhite,
    onBackground        = AmazonText,
    onSurface           = AmazonText,
    onSurfaceVariant    = AmazonTextMuted,
    error               = AmazonRed,
    onError             = DiscordWhite,
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),
    outline             = AmazonOutline,
    outlineVariant      = AmazonSurfaceVariant,
    inverseSurface      = AmazonText,
    inverseOnSurface    = AmazonBackground,
    inversePrimary      = AmazonOrange
)

/**
 * Theme auto-switches based on system dark-mode setting.
 * Colors are fully hardcoded here - nothing is read from DataStore.
 */
@Composable
fun EmergencyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    trueDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme && trueDark -> TrueDarkColorScheme
        darkTheme -> SystemDarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
