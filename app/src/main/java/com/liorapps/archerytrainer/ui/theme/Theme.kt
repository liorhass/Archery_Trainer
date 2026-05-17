package com.liorapps.archerytrainer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
)

// Custom colors
@Immutable
data class CustomColors(
    val success: Color,
    val warning: Color,
    val surfaceVariantSecondary: Color,
    val badgeBackground: Color,
    val singleFrameSliderButtons: Color,
    val singleFrameSliderActiveTrackLine: Color,
    val singleFrameSliderInactiveTrackLine: Color,
    val singleFrameSliderThumb: Color,
    val singleFrameSliderThumbHighlight: Color,
    val singleFrameSliderThumbGlow: Color,
    val webLink: Color,
)
val LocalCustomColors = staticCompositionLocalOf {
    CustomColors(
        success = Color.Unspecified,
        warning = Color.Unspecified,
        surfaceVariantSecondary = Color.Unspecified,
        badgeBackground = Color.Unspecified,
        singleFrameSliderButtons = Color.Unspecified,
        singleFrameSliderActiveTrackLine = Color.Unspecified,
        singleFrameSliderInactiveTrackLine = Color.Unspecified,
        singleFrameSliderThumb = Color.Unspecified,
        singleFrameSliderThumbHighlight = Color.Unspecified,
        singleFrameSliderThumbGlow = Color.Unspecified,
        webLink = Color.Unspecified,
    )
}
private val LightCustomColors = CustomColors(
    success = Color(0xFF4CAF50),
    warning = Color(0xFFFFA000),
    surfaceVariantSecondary = Color(0xFFF5F5F5),
    badgeBackground = Color(0xFFB00020),
    singleFrameSliderButtons = Color(0xFFB00020),
    singleFrameSliderActiveTrackLine = Color(0xFFB00020),
    singleFrameSliderInactiveTrackLine = Color(0xFF444444),
    singleFrameSliderThumb = Color(0xFFFF3B30),
    singleFrameSliderThumbHighlight = Color(0xFFFF7A73),
    singleFrameSliderThumbGlow = Color(0x44FF3B30),
    webLink = Color(0xFF0044CC),
)
private val DarkCustomColors = CustomColors(
    success = Color(0xFF81C784),
    warning = Color(0xFFFFB74D),
    surfaceVariantSecondary = Color(0xFF2D2D2D),
    badgeBackground = Color(0xFFCF6679),
    singleFrameSliderButtons = Color(0xFFFF4B40),
    singleFrameSliderActiveTrackLine = Color(0xFFFF4B40),
    singleFrameSliderInactiveTrackLine = Color(0xFF888888),
    singleFrameSliderThumb = Color(0xFFFF3B30),
    singleFrameSliderThumbHighlight = Color(0xFFFF7A73),
    singleFrameSliderThumbGlow = Color(0x44FF3B30),
    webLink = Color(0xFF66B3FF),
)

@Composable
fun ArcheryTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val customColors = if (darkTheme) DarkCustomColors else LightCustomColors
    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }

//    MaterialTheme(
//        colorScheme = colorScheme,
//        typography = Typography,
//        content = content
//    )
}

// A custom object to make usage clean (similar to MaterialTheme.colorScheme). E.g:
//      Text(text = label,
//           color = AppTheme.colors.myBadgeBackground,
object AppTheme {
    val colors: CustomColors
        @Composable
        get() = LocalCustomColors.current
}
