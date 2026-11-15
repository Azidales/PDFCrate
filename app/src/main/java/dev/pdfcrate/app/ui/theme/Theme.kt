package dev.pdfcrate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF285C43), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EBDD), onPrimaryContainer = Color(0xFF1A3928),
    secondary = Color(0xFF526653), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8D8), onSecondaryContainer = Color(0xFF1E3523),
    tertiary = Color(0xFF3B6470), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E8EF), onTertiaryContainer = Color(0xFF0F2A33),
    background = Color(0xFFF6F7F2), onBackground = Color(0xFF191D19),
    surface = Color(0xFFF6F7F2), onSurface = Color(0xFF191D19),
    surfaceVariant = Color(0xFFDDE5DA), onSurfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F2EC),
    surfaceContainer = Color(0xFFEBEEE6), surfaceContainerHigh = Color(0xFFE5E9E0),
    surfaceContainerHighest = Color(0xFFDFE3DA),
    outline = Color(0xFF727970), outlineVariant = Color(0xFFDCE2D7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8D2BC), onPrimary = Color(0xFF123526),
    primaryContainer = Color(0xFF254C3D), onPrimaryContainer = Color(0xFFD9EDDF),
    secondary = Color(0xFFB7CBB5), onSecondary = Color(0xFF233627),
    secondaryContainer = Color(0xFF384B3A), onSecondaryContainer = Color(0xFFDAE8D6),
    tertiary = Color(0xFFA3CDDA), onTertiary = Color(0xFF033640),
    tertiaryContainer = Color(0xFF224C57), onTertiaryContainer = Color(0xFFBFE9F6),
    background = Color(0xFF121714), onBackground = Color(0xFFE1E4DD),
    surface = Color(0xFF121714), onSurface = Color(0xFFE1E4DD),
    surfaceVariant = Color(0xFF424940), onSurfaceVariant = Color(0xFFC1C9BE),
    surfaceContainerLowest = Color(0xFF0D110E), surfaceContainerLow = Color(0xFF19201B),
    surfaceContainer = Color(0xFF1C241F), surfaceContainerHigh = Color(0xFF262E29),
    surfaceContainerHighest = Color(0xFF313934),
    outline = Color(0xFF8B9389), outlineVariant = Color(0xFF424940),
)

/** Colors outside the Material scheme: the canvas behind PDF pages and overlay accents. */
@Immutable
data class ReaderColors(val backdrop: Color, val highlight: Color)

private val LightReader = ReaderColors(backdrop = Color(0xFFE2E6DE), highlight = Color(0xFFFFD54F))
private val DarkReader = ReaderColors(backdrop = Color(0xFF242925), highlight = Color(0xFFFFD54F))

val LocalReaderColors = staticCompositionLocalOf { LightReader }

private val PdfCrateShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Interface theme only. Page contents are never recolored, also in dark mode. */
@Composable
fun PdfCrateTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalReaderColors provides if (dark) DarkReader else LightReader) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, shapes = PdfCrateShapes, content = content)
    }
}
