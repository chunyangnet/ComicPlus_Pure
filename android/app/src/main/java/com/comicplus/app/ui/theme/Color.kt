package com.comicplus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val InkLight = Color(0xFF172033)
val InkSoftLight = Color(0xFF4B5568)
val MutedLight = Color(0xFF8A93A3)
val CanvasLight = Color(0xFFFCFCFD)
val SurfaceSoftLight = Color(0xFFF4F6F8)
val SurfaceMutedLight = Color(0xFFEEF1F5)
val LineLight = Color(0xFFE7EAF0)
val White = Color(0xFFFFFFFF)

val Ink: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

val InkSoft: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .76f)

val Canvas: Color
    @Composable get() = MaterialTheme.colorScheme.background

val SurfaceSoft: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val SurfaceMuted: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

val Line: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val Gold = Color(0xFFC99022)
val Silver = Color(0xFF8B95A5)
val Bronze = Color(0xFFB7744B)
val Success = Color(0xFF16866F)
val Warning = Color(0xFFB7791F)

enum class ComicPlusPalette(
    val key: String,
    val displayName: String,
    val primary: Color,
    val primaryDark: Color,
    val soft: Color,
) {
    Ocean("ocean", "海盐蓝", Color(0xFF2F6BFF), Color(0xFF1D4ED8), Color(0xFFEAF0FF)),
    Coral("coral", "珊瑚红", Color(0xFFE95D67), Color(0xFFC93F4B), Color(0xFFFDECEE)),
    Mint("mint", "青薄荷", Color(0xFF159A8C), Color(0xFF08796F), Color(0xFFE7F7F4)),
    Amber("amber", "琥珀橙", Color(0xFFD7792B), Color(0xFFB35D18), Color(0xFFFFF1E5)),
    Violet("violet", "鸢尾紫", Color(0xFF7457D9), Color(0xFF5B3FC1), Color(0xFFF0ECFC));

    companion object {
        fun fromKey(key: String): ComicPlusPalette = entries.firstOrNull { it.key == key } ?: Ocean
    }
}
