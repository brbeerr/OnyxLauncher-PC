package com.onyx.launcher.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF), onPrimary = Color.White,
    primaryContainer = Color(0xFF5C35CC), onPrimaryContainer = Color.White,
    secondary = Color(0xFF03DAC6), tertiary = Color(0xFFFF7043),
    background = Color(0xFF121212), onBackground = Color.White,
    surface = Color(0xFF1E1E1E), onSurface = Color.White,
    surfaceVariant = Color(0xFF2D2D2D), onSurfaceVariant = Color(0xFFCACACA),
    error = Color(0xFFCF6679), onError = Color.Black
)

@Composable
fun OnyxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography(), content = content)
}
