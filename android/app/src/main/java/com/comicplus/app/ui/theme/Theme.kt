package com.comicplus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalComicPlusPalette = staticCompositionLocalOf { ComicPlusPalette.Ocean }

private val ComicPlusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ComicPlusTheme(
    paletteKey: String = ComicPlusPalette.Ocean.key,
    darkMode: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = ComicPlusPalette.fromKey(paletteKey)
    val targetColors = if (darkMode) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primaryDark,
            onPrimaryContainer = Color.White,
            secondary = palette.primary,
            onSecondary = Color.White,
            tertiary = Success,
            background = Color(0xFF0D1014),
            onBackground = Color(0xFFE9EDF3),
            surface = Color(0xFF15191F),
            onSurface = Color(0xFFE9EDF3),
            surfaceVariant = Color(0xFF222831),
            onSurfaceVariant = Color(0xFFB4BCC8),
            outline = Color(0xFF3A434F),
            outlineVariant = Color(0xFF2B323C),
            error = Color(0xFFFF8A80),
            onError = Color(0xFF3B0805),
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = White,
            primaryContainer = palette.soft,
            onPrimaryContainer = palette.primaryDark,
            secondary = palette.primaryDark,
            onSecondary = White,
            tertiary = Success,
            background = CanvasLight,
            onBackground = InkLight,
            surface = White,
            onSurface = InkLight,
            surfaceVariant = SurfaceSoftLight,
            onSurfaceVariant = InkSoftLight,
            outline = LineLight,
            outlineVariant = SurfaceMutedLight,
            error = ComicPlusPalette.Coral.primary,
            onError = White,
        )
    }
    val colors = targetColors.animated(reduceMotion)

    CompositionLocalProvider(LocalComicPlusPalette provides palette) {
        MaterialTheme(
            colorScheme = colors,
            typography = ComicPlusTypography,
            shapes = ComicPlusShapes,
            content = content,
        )
    }
}

@Composable
private fun ColorScheme.animated(reduceMotion: Boolean): ColorScheme {
    val duration = if (reduceMotion) 0 else 360

    @Composable
    fun animate(target: Color, label: String): Color {
        val value by animateColorAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = duration),
            label = label,
        )
        return value
    }

    return copy(
        primary = animate(primary, "theme-primary"),
        onPrimary = animate(onPrimary, "theme-on-primary"),
        primaryContainer = animate(primaryContainer, "theme-primary-container"),
        onPrimaryContainer = animate(onPrimaryContainer, "theme-on-primary-container"),
        inversePrimary = animate(inversePrimary, "theme-inverse-primary"),
        secondary = animate(secondary, "theme-secondary"),
        onSecondary = animate(onSecondary, "theme-on-secondary"),
        secondaryContainer = animate(secondaryContainer, "theme-secondary-container"),
        onSecondaryContainer = animate(onSecondaryContainer, "theme-on-secondary-container"),
        tertiary = animate(tertiary, "theme-tertiary"),
        onTertiary = animate(onTertiary, "theme-on-tertiary"),
        tertiaryContainer = animate(tertiaryContainer, "theme-tertiary-container"),
        onTertiaryContainer = animate(onTertiaryContainer, "theme-on-tertiary-container"),
        background = animate(background, "theme-background"),
        onBackground = animate(onBackground, "theme-on-background"),
        surface = animate(surface, "theme-surface"),
        onSurface = animate(onSurface, "theme-on-surface"),
        surfaceVariant = animate(surfaceVariant, "theme-surface-variant"),
        onSurfaceVariant = animate(onSurfaceVariant, "theme-on-surface-variant"),
        surfaceTint = animate(surfaceTint, "theme-surface-tint"),
        inverseSurface = animate(inverseSurface, "theme-inverse-surface"),
        inverseOnSurface = animate(inverseOnSurface, "theme-inverse-on-surface"),
        error = animate(error, "theme-error"),
        onError = animate(onError, "theme-on-error"),
        errorContainer = animate(errorContainer, "theme-error-container"),
        onErrorContainer = animate(onErrorContainer, "theme-on-error-container"),
        outline = animate(outline, "theme-outline"),
        outlineVariant = animate(outlineVariant, "theme-outline-variant"),
        scrim = animate(scrim, "theme-scrim"),
        surfaceBright = animate(surfaceBright, "theme-surface-bright"),
        surfaceDim = animate(surfaceDim, "theme-surface-dim"),
        surfaceContainer = animate(surfaceContainer, "theme-surface-container"),
        surfaceContainerHigh = animate(surfaceContainerHigh, "theme-surface-container-high"),
        surfaceContainerHighest = animate(surfaceContainerHighest, "theme-surface-container-highest"),
        surfaceContainerLow = animate(surfaceContainerLow, "theme-surface-container-low"),
        surfaceContainerLowest = animate(surfaceContainerLowest, "theme-surface-container-lowest"),
    )
}
