package com.sarilacivert.galeri.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy900 = Color(0xFF081426)
val Navy800 = Color(0xFF0D1B2A)
val Navy700 = Color(0xFF14263A)
val Surface = Color(0xFF111C2B)
val SurfaceAlt = Color(0xFF182638)
val Yellow500 = Color(0xFFF4C430)
val Yellow400 = Color(0xFFFFD84D)
val TextPrimary = Color(0xFFF7F9FC)
val TextSecondary = Color(0xFFAEB8C4)
val Danger = Color(0xFFD94A4A)

private val GalleryColors = darkColorScheme(
    primary = Yellow500,
    onPrimary = Navy900,
    secondary = Yellow400,
    onSecondary = Navy900,
    background = Navy900,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextSecondary,
    error = Danger
)

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GalleryColors,
        content = content
    )
}
