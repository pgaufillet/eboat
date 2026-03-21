package com.eboat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SeaLight,
    secondary = NavyLight,
    tertiary = Sand,
    background = DarkGray,
    surface = Navy,
    error = Coral
)

private val LightColorScheme = lightColorScheme(
    primary = Sea,
    secondary = Navy,
    tertiary = Sand,
    background = White,
    surface = White,
    error = Coral
)

@Composable
fun EboatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
