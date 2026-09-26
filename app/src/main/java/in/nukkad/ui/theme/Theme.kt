package `in`.nukkad.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightBazaar = darkColorScheme(
    primary = Color(0xFFFFA34D), onPrimary = Color(0xFF271405),
    background = Color(0xFF11120F), onBackground = Color(0xFFF3EEE4),
    surface = Color(0xFF1D201A), onSurface = Color(0xFFF3EEE4),
    secondary = Color(0xFFA8C595), error = Color(0xFFFFB4A5)
)
@Composable fun NukkadTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = NightBazaar, content = content) }
