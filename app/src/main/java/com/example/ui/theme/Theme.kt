package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PassportLightColorScheme = lightColorScheme(
    primary = TakePhotoBlue,
    onPrimary = Color.White,
    primaryContainer = TakePhotoBlueContainer,
    onPrimaryContainer = InkDark,
    secondary = GalleryGreen,
    onSecondary = Color.White,
    error = SavePrintRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorContent,
    background = SurfaceBg,
    onBackground = InkDark,
    surface = CardSurface,
    onSurface = InkDark,
    surfaceVariant = CardMutedSurface,
    onSurfaceVariant = InkMuted,
    outline = BorderSubtle,
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun PassportPhotoTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PassportLightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
