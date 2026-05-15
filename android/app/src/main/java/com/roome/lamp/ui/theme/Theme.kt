package com.roome.lamp.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaporwaveColorScheme = darkColorScheme(
    primary = VaporCyan,
    onPrimary = VaporNavy,
    primaryContainer = VaporMidPurple,
    onPrimaryContainer = VaporCyan,
    secondary = VaporPink,
    onSecondary = VaporNavy,
    secondaryContainer = VaporCardSurface,
    onSecondaryContainer = VaporPink,
    tertiary = VaporLavender,
    onTertiary = VaporNavy,
    tertiaryContainer = VaporMidPurple,
    onTertiaryContainer = VaporLavender,
    error = Color(0xFFFF5252),
    onError = Color.White,
    errorContainer = Color(0xFF5C0011),
    onErrorContainer = Color(0xFFFFB4AB),
    background = VaporNavy,
    onBackground = VaporTextPrimary,
    surface = VaporDeepPurple,
    onSurface = VaporTextPrimary,
    surfaceVariant = VaporDarkSurface,
    onSurfaceVariant = VaporTextSecondary,
    outline = VaporMidPurple,
    outlineVariant = VaporCardSurface,
    inverseSurface = VaporMint,
    inverseOnSurface = VaporNavy,
    inversePrimary = VaporPurple,
    surfaceTint = VaporCyan
)

@Composable
fun RoomeLampTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VaporwaveColorScheme,
        content = content
    )
}
