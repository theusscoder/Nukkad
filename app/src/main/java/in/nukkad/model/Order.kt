package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable
data class Selection(val orderId: String, val requestId: String, val customerId: String, val offerId: String, val sellerId: String)
@Serializable
enum class OrderStatus { ACCEPTED, REJECTED, CANCELLED, EXPIRED }
@Serializable
data class Order(val selection: Selection, val status: OrderStatus, val sellerName: String, val amount: Int = 0, val readyByEpoch: Long = 0, val holdUntilEpoch: Long = 0, val reason: String = "")
@Serializable
data class CloseOffer(val requestId: String, val customerId: String, val sellerId: String)
@Serializable
data class SavedQuote(val request: Request, val offer: Offer, val checks: List<Check>, val ownerApproved: Boolean = false)
@Serializable
data class SellerLedger(val quotes: List<SavedQuote> = emptyList(), val orders: List<Order> = emptyList(), val closedRequests: Set<String> = emptySet())
@Serializable
data class CustomerLedger(
    val request: Request? = null,
    val offers: List<Offer> = emptyList(),
    val selection: Selection? = null,
    val order: Order? = null,
    val cancelling: Boolean = false,
    val pendingCloses: Set<String> = emptySet()
)
