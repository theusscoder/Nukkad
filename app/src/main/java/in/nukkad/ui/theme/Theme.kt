package `in`.nukkad.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val NukkadColors = lightColorScheme(
    primary = Color(0xFFFF6B2C), onPrimary = Color(0xFF11110F),
    background = Color(0xFFF3F0DF), onBackground = Color(0xFF11110F),
    surface = Color(0xFFFFFDF5), onSurface = Color(0xFF11110F),
    surfaceVariant = Color(0xFFE8E4D4), onSurfaceVariant = Color(0xFF56574C),
    secondary = Color(0xFF526B50), onSecondary = Color.White,
    error = Color(0xFFA93224), outline = Color(0xFFB5B4A7)
)
@Composable fun NukkadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NukkadColors,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            displaySmall = TextStyle(fontSize = 46.sp, fontWeight = FontWeight.Black)
        ),
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content)
}
