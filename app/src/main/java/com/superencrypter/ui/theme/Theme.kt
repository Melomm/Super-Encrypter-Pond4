package com.superencrypter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = VaultPrimary,
    secondary = VaultSecondary,
    background = VaultBackground,
    surface = VaultSurface,
    surfaceVariant = VaultSurfaceHigh,
    outline = VaultOutline,
    error = VaultDanger,
    onPrimary = Color(0xFF03201C),
    onSecondary = Color(0xFF271404),
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

@Composable
fun SuperEncrypterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = Typography,
        content = content
    )
}
