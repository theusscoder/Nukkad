package `in`.nukkad.product

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import `in`.nukkad.engine.RankedOffer
import `in`.nukkad.model.GeoPoint
import `in`.nukkad.model.Receipt

/** Merchant APK excludes the customer-only H3 native library. */
@Composable
fun HexDiscoveryView(customer: GeoPoint, offers: List<RankedOffer>, respondingSellers: List<Receipt>, onChoose: (RankedOffer) -> Unit) {
    Text("Nearby offer map is available in the customer app.")
}

@Composable
fun OfferDetailsCard(customer: GeoPoint, ranked: RankedOffer, enabled: Boolean, onSpeak: (() -> Unit)?, onChoose: () -> Unit) {
    Text("Offer details are shown in the customer app.")
}
