package com.mrjack.dressflow.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary          = Blue600,
    onPrimary        = Color.White,
    primaryContainer = Blue50,
    secondary        = Indigo600,
    onSecondary      = Color.White,
    background       = Gray50,
    onBackground     = Gray900,
    surface          = Color.White,
    onSurface        = Gray900,
    surfaceVariant   = Gray100,
    outline          = Gray200,
    error            = Red500,
    onError          = Color.White,
)

@Composable
fun DressFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography(),
        content     = content,
    )
}
