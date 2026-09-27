package `in`.nukkad.product

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import `in`.nukkad.model.GeoPoint
import `in`.nukkad.model.Receipt
import `in`.nukkad.engine.RankedOffer
import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng

private data class HexCellShape(val ring: Int, val points: List<Pair<Float, Float>>)
private data class OfferPin(val x: Float, val y: Float, val ranked: RankedOffer, val distance: Float, val uncertainty: Float)
private data class CheckingPin(val x: Float, val y: Float, val shop: String, val area: String)
private data class DiscoveryDrawing(val cells: List<HexCellShape>, val pins: List<OfferPin>, val checking: List<CheckingPin>)

@Composable
fun HexDiscoveryView(customer: GeoPoint, offers: List<RankedOffer>, respondingSellers: List<Receipt>, onChoose: (RankedOffer) -> Unit) {
    val drawing = remember(customer, offers.map { it.offer.offerId }, respondingSellers.map { it.sellerId }) { createDrawing(customer, offers, respondingSellers) }
    val animation = rememberInfiniteTransition(label = "discovery-motion")
    val pulse by animation.animateFloat(.8f, 1.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "discovery-pulse-size")
    val travel by animation.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "discovery-connection")
    val haptics = LocalHapticFeedback.current
    val offerIds = offers.map { it.offer.offerId }.toSet()
    var previousOfferIds by remember { mutableStateOf(offerIds) }
    LaunchedEffect(offerIds) {
        if ((offerIds - previousOfferIds).isNotEmpty()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        previousOfferIds = offerIds
    }
    SurfaceCard {
        Text("LOOKING AROUND YOU", style = MaterialTheme.typography.titleLarge)
        Text("Real offers from nearby shops · approximate straight-line distances", style = MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.fillMaxWidth().height(310.dp).background(Color(0xFFE8E4D4), RoundedCornerShape(28.dp))) {
            val width = maxWidth
            val height = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                drawing.cells.forEach { cell ->
                    if (cell.points.size >= 6) {
                        val path = Path()
                        val pts = cell.points
                        path.moveTo(pts.first().first * size.width, pts.first().second * size.height)
                        pts.drop(1).forEach { path.lineTo(it.first * size.width, it.second * size.height) }
                        path.close()
                        val color = if (cell.ring == 0) Color(0x22526B50) else Color(0x12526B50)
                        drawPath(path, color)
                        drawPath(path, Color(0x66526B50), style = Stroke(width = 1.dp.toPx()))
                    }
                }
                // Safe visual fallback when H3's native library is unavailable.
                if (drawing.cells.isEmpty()) {
                    for (ring in 1..3) {
                        val radius = size.minDimension * (.12f + ring * .10f)
                        drawCircle(Color(0x22526B50), radius, center = Offset(size.width / 2, size.height / 2), style = Stroke(1.dp.toPx()))
                    }
                }
                val center = Offset(size.width / 2, size.height / 2)
                drawing.pins.forEach { pin ->
                    val end = Offset(pin.x * size.width, pin.y * size.height)
                    drawLine(Color(0x886E553E), center, end, 1.dp.toPx())
                    drawCircle(Color(0xFFFF6B2C), 3.dp.toPx(),
                        Offset(center.x + (end.x - center.x) * travel, center.y + (end.y - center.y) * travel))
                }
                drawCircle(Color(0x22FF6B2C), size.minDimension * .055f * pulse, center = Offset(size.width / 2, size.height / 2))
                drawCircle(Color(0x55FF6B2C), size.minDimension * .07f * pulse, center = Offset(size.width / 2, size.height / 2), style = Stroke(1.dp.toPx()))
            }
            Box(Modifier.offset(x = width * .5f - 13.dp, y = height * .5f - 13.dp).size(26.dp)
                .background(Color(0xFFFF6B2C), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).background(Color.White, CircleShape))
            }
            drawing.pins.forEach { pin ->
                val reveal by androidx.compose.animation.core.animateFloatAsState(1f,
                    androidx.compose.animation.core.spring(dampingRatio = .58f, stiffness = 340f), label = "offer-pin-${pin.ranked.offer.offerId}")
                Surface(Modifier.offset(x = width * pin.x - 49.dp, y = height * pin.y - 17.dp).graphicsLayer { scaleX = reveal; scaleY = reveal }
                    .clickable { onChoose(pin.ranked) }, shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF11110F), shadowElevation = 3.dp) {
                    Text("₹${pin.ranked.offer.amount}", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
            drawing.checking.forEach { pin ->
                Surface(Modifier.offset(x = width * pin.x - 50.dp, y = height * pin.y - 18.dp),
                    shape = RoundedCornerShape(16.dp), color = Color(0xFF526B50)) {
                    Text("Checking ${pin.shop}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), shape = RoundedCornerShape(18.dp),
                color = Color(0xEEFFFDF5)) {
                val count = offers.size + respondingSellers.count { receipt -> offers.none { it.offer.sellerId == receipt.sellerId } }
                Text("$count ${if (count == 1) "real shop response" else "real shop responses"}",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("The orange dot is you. Shop pins appear only after a real offer arrives.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun OfferDetailsCard(customer: GeoPoint, ranked: RankedOffer, enabled: Boolean, onSpeak: (() -> Unit)?, onChoose: () -> Unit) {
    val offer = ranked.offer
    val location = offer.sellerLocation
    val distance = location?.let { straightLineDistanceMeters(customer, it) }
    val uncertainty = maxOf(customer.accuracyMeters ?: 0f, location?.accuracyMeters ?: 0f)
    SurfaceCard {
        Text(offer.sellerName, style = MaterialTheme.typography.titleLarge)
        Text(offer.sellerLocation?.let { "${approximateDistanceLabel(distance ?: 0f, uncertainty)} · ${offer.sellerArea ?: "nearby area"}" }
            ?: "Shop location unavailable")
        Text("₹${offer.amount}", style = MaterialTheme.typography.displaySmall)
        Text("Ready ${`in`.nukkad.debug.displayTime(offer.readyByEpoch)}")
        offer.fulfilledConstraints.forEach { Text("✓ $it") }
        if (ranked.reason == "Cheapest") Text("BEST VALUE", color = MaterialTheme.colorScheme.secondary)
        if (onSpeak != null) TextButton(onClick = onSpeak) { Text("Hear offer") }
        Button(onClick = onChoose, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("CHOOSE THIS OFFER") }
    }
}

private fun createDrawing(customer: GeoPoint, offers: List<RankedOffer>, respondingSellers: List<Receipt>): DiscoveryDrawing {
    if (!customer.isValid()) return DiscoveryDrawing(emptyList(), emptyList(), emptyList())
    val locations = offers.mapNotNull { ranked -> ranked.offer.sellerLocation?.takeIf(GeoPoint::isValid)?.let { ranked to it } }
    val distances = locations.mapNotNull { (ranked, point) -> straightLineDistanceMeters(customer, point)?.let { ranked to (point to it) } }
    val quotedIds = offers.map { it.offer.sellerId }.toSet()
    val checkingLocations = respondingSellers.filter { it.sellerId !in quotedIds }.mapNotNull { receipt ->
        receipt.sellerLocation?.takeIf(GeoPoint::isValid)?.let { receipt to it }
    }
    val checkingMeters = checkingLocations.mapNotNull { (_, point) -> straightLineDistanceMeters(customer, point) }
    val radius = maxOf(650f, distances.maxOfOrNull { it.second.second }?.times(1.15f) ?: 0f,
        checkingMeters.maxOrNull()?.times(1.15f) ?: 0f)
    fun project(lat: Double, lon: Double): Pair<Float, Float> {
        val north = ((lat - customer.latitude) * 111_320.0).toFloat()
        val east = ((lon - customer.longitude) * 111_320.0 * kotlin.math.cos(Math.toRadians(customer.latitude))).toFloat()
        return (.5f + east / (2f * radius)).coerceIn(.04f, .96f) to (.5f - north / (2f * radius)).coerceIn(.04f, .96f)
    }
    val pins = distances.map { (ranked, pair) ->
        val (location, distance) = pair
        val (x, y) = project(location.latitude, location.longitude)
        OfferPin(x, y, ranked, distance, maxOf(customer.accuracyMeters ?: 0f, location.accuracyMeters ?: 0f))
    }
    val checking = checkingLocations.map { (receipt, point) ->
        val (x, y) = project(point.latitude, point.longitude)
        CheckingPin(x, y, receipt.sellerName, receipt.sellerArea.orEmpty())
    }
    val cells = runCatching {
        val h3 = H3Core.newInstance()
        val origin = h3.latLngToCellAddress(customer.latitude, customer.longitude, 9)
        h3.gridDiskDistances(origin, 3).flatMapIndexed { ring, addresses -> addresses.map { address ->
            HexCellShape(ring, h3.cellToBoundary(address).map { point -> project(point.lat, point.lng) })
        } }
    }.getOrDefault(emptyList())
    return DiscoveryDrawing(cells, pins, checking)
}
