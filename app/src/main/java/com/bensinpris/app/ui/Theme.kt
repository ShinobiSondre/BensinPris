package com.bensinpris.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep petrol blue with a warm amber for prices: calm, readable in sunlight, and not any chain's colours.
val Petrol = Color(0xFF0F4C5C)
val PetrolDeep = Color(0xFF072830)
val Amber = Color(0xFFF5B841)
val AmberDeep = Color(0xFF8A5A00)
val Fresh = Color(0xFF1E8E5A)
val FreshDark = Color(0xFF6FD3A0)

private val Light = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E9EE),
    onPrimaryContainer = PetrolDeep,
    secondary = AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3B0),
    onSecondaryContainer = Color(0xFF2B1B00),
    tertiary = Fresh,
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1A1C1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1C),
    surfaceVariant = Color(0xFFECE8E0),
    onSurfaceVariant = Color(0xFF5B5F60),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EEE7),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFC9C5BC),
    outlineVariant = Color(0xFFE2DED6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86CFDF),
    onPrimary = PetrolDeep,
    primaryContainer = Color(0xFF16414B),
    onPrimaryContainer = Color(0xFFD3E9EE),
    secondary = Amber,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFF5C3F00),
    onSecondaryContainer = Color(0xFFFFE3B0),
    tertiary = FreshDark,
    background = Color(0xFF0D1214),
    onBackground = Color(0xFFE2E3E3),
    surface = Color(0xFF161C1E),
    onSurface = Color(0xFFE2E3E3),
    surfaceVariant = Color(0xFF242B2D),
    onSurfaceVariant = Color(0xFFA9B0B2),
    surfaceContainerLow = Color(0xFF161C1E),
    surfaceContainer = Color(0xFF1C2325),
    surfaceContainerHigh = Color(0xFF1E2527),
    outline = Color(0xFF3B4446),
    outlineVariant = Color(0xFF2A3133),
)

@Composable
fun BensinPrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
