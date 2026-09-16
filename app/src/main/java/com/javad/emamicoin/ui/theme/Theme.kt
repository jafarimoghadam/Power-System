package com.javad.emamicoin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF745B12),
    secondary = Color(0xFF665F4B),
    background = Color(0xFFFAF9F5),
    surface = Color(0xFFFAF9F5),
    surfaceVariant = Color(0xFFF0EEDF)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFE6C969),
    secondary = Color(0xFFD2C7A6)
)

@Composable fun EmamiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
