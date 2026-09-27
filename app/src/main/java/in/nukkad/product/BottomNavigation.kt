package `in`.nukkad.product

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val NavInk = Color(0xFF11110F)
private val NavCream = Color(0xFFF3F0DF)

@Composable
fun NukkadBottomNavigation(merchant: Boolean, selected: String, onSelect: (String) -> Unit) {
    val labels = if (merchant) listOf("Home", "Shop", "Orders") else listOf("Home", "Search", "Requests")
    Surface(color = Color.Transparent, modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(68.dp)) {
            val segment = maxWidth / 3
            val selectedIndex = labels.indexOf(selected).coerceAtLeast(0)
            val indicatorWidth = 58.dp
            val targetX = segment * selectedIndex + (segment - indicatorWidth) / 2
            val indicatorX by animateDpAsState(targetX, spring(dampingRatio = .72f, stiffness = 420f), label = "nav-indicator")
            Row(Modifier.fillMaxSize().background(NavInk, RoundedCornerShape(38.dp)), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { index, label ->
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(label) }, contentAlignment = Alignment.Center) {
                        if (index == selectedIndex) {
                            Box(Modifier.offset(x = indicatorX - segment * selectedIndex)
                                .size(indicatorWidth, 48.dp)
                                .background(NavCream, CircleShape))
                        }
                        NavGlyph(index, merchant, selected = index == selectedIndex,
                            modifier = Modifier.size(25.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavGlyph(index: Int, merchant: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) NavInk else Color.White
    Canvas(modifier) {
        val s = size.minDimension
        val left = (size.width - s) / 2f
        val top = (size.height - s) / 2f
        fun p(x: Float, y: Float) = Offset(left + x * s, top + y * s)
        val stroke = Stroke(width = s * .085f, cap = StrokeCap.Round)
        when (index) {
            0 -> {
                drawLine(color, p(.12f,.46f), p(.5f,.14f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.5f,.14f), p(.88f,.46f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.22f,.43f), p(.22f,.86f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.78f,.43f), p(.78f,.86f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.22f,.86f), p(.78f,.86f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.43f,.86f), p(.43f,.61f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.57f,.86f), p(.57f,.61f), s*.085f, StrokeCap.Round)
            }
            1 -> if (merchant) {
                drawLine(color, p(.16f,.4f), p(.84f,.4f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.23f,.4f), p(.23f,.84f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.77f,.4f), p(.77f,.84f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.23f,.84f), p(.77f,.84f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.18f,.4f), p(.3f,.18f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.3f,.18f), p(.7f,.18f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.7f,.18f), p(.82f,.4f), s*.085f, StrokeCap.Round)
                drawLine(color, p(.48f,.55f), p(.48f,.84f), s*.075f, StrokeCap.Round)
            } else {
                drawCircle(color, radius = s*.27f, center = p(.43f,.43f), style = stroke)
                drawLine(color, p(.62f,.62f), p(.88f,.88f), s*.085f, StrokeCap.Round)
            }
            else -> {
                drawRoundRect(color, topLeft = p(.18f,.2f), size = androidx.compose.ui.geometry.Size(s*.64f,s*.67f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s*.08f), style = stroke)
                drawLine(color, p(.31f,.4f), p(.68f,.4f), s*.075f, StrokeCap.Round)
                drawLine(color, p(.31f,.57f), p(.68f,.57f), s*.075f, StrokeCap.Round)
                drawLine(color, p(.31f,.74f), p(.55f,.74f), s*.075f, StrokeCap.Round)
            }
        }
    }
}
