package com.jakspinning.wakemypc

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Plain MaterialTheme {} falls back to Material 3's default purple baseline
// palette. This is a router/network app, not a purple one — blue instead.
private val RouterWakeColorScheme = lightColorScheme(
    primary = Color(0xFF0B5FA5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF4C6D92),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF2E7D62),
    onTertiary = Color(0xFFFFFFFF),
)

@Composable
fun RouterWakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RouterWakeColorScheme, content = content)
}
