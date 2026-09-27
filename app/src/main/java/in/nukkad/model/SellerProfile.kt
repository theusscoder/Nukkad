package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiscoveryMode { EXACT, FLEX, OPEN }

@Serializable
data class SellerProfile(
    val sellerId: String,
    val shopName: String,
    val area: String,
    val category: String,
    val upiId: String,
    val items: List<Item>,
    val rules: MerchantRules,
    val domains: Set<CommerceDomain> = setOf(CommerceDomain.from(category)),
    val discoveryMode: DiscoveryMode = DiscoveryMode.EXACT,
    val location: GeoPoint? = null
)

/** Capacity belongs to the local merchant, not the coordination layer. */
data class MerchantState(val ordersOnReadyDate: Int = 0)


