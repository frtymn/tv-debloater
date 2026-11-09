package com.frtymn.tvdebloater.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SafeColor = Color(0xFF4CAF50)
val CautionColor = Color(0xFFFFB300)
val ProtectedColor = Color(0xFFEF5350)
val UnknownColor = Color(0xFF90A4AE)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1A33),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF0F1115),
    surface = Color(0xFF15181D),
    surfaceVariant = Color(0xFF22262D),
    onSurfaceVariant = Color(0xFFC4C8D0),
    error = Color(0xFFFF6E6E),
)

@Composable
fun TvDebloaterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
