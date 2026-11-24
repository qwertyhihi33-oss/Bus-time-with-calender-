package com.hkbuscal.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Hong Kong Bus Operator Brand Accents
val KmbRed = Color(0xFFD92B2B)
val LwbOrange = Color(0xFFE67E22)
val CitybusYellow = Color(0xFFF4C430)
val CitybusNavy = Color(0xFF003B73)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F4C81),
    onPrimary = Color.White,
    secondary = KmbRed,
    tertiary = LwbOrange,
    background = Color(0xFFF6F7FA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF002F54),
    secondary = Color(0xFFFF8A80),
    tertiary = Color(0xFFFFB74D)
)

@Composable
fun HKBusCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
