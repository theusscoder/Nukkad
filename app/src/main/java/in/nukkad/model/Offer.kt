package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable
data class Offer(
    val offerId: String,
    val requestId: String,
    val sellerId: String,
    val sellerName: String,
    val amount: Int,
    val readyByEpoch: Long,
    val autoQuoted: Boolean,
    val explanation: List<String>,
    val fulfilledConstraints: List<String> = emptyList(),
    val expiresAtEpoch: Long = Long.MAX_VALUE,
    val policyVersion: String = "",
    val sellerLocation: GeoPoint? = null,
    val sellerArea: String? = null
)

