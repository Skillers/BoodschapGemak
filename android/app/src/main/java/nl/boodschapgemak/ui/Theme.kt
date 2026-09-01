package nl.boodschapgemak.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B34),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0B2),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF39656B),
    background = Color(0xFFF7FBF2),
    surface = Color(0xFFF7FBF2),
    surfaceVariant = Color(0xFFDDE5D8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D498),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF14521F),
    onPrimaryContainer = Color(0xFFB4F0B2),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA1CED4),
    background = Color(0xFF10140F),
    surface = Color(0xFF10140F),
    surfaceVariant = Color(0xFF424940),
)

@Composable
fun BoodschapGemakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
