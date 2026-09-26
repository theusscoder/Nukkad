package `in`.nukkad.engine

import `in`.nukkad.model.*
import `in`.nukkad.persistence.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** Local merchant authority. Persistence completes BEFORE any acceptance can be published. */
class OrderBook(private val profile: SellerProfile, private val store: StateStore<SellerLedger> = MemoryStore(SellerLedger())) {
    companion object { const val QUOTE_TTL = 300_000L; const val HOLD_TTL = 600_000L }
    private val mutex = Mutex()
    private var ledger = store.load()
    private val agent = ShopAgent()
    private val policy = MessageDigest.getInstance("SHA-256").digest(Protocol.json.encodeToString(SellerProfile.serializer(), profile).toByteArray()).joinToString("") { "%02x".format(it) }
    fun snapshot(): SellerLedger = ledger
    private fun commit(next: SellerLedger) { store.save(next); ledger = next }
    private fun date(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.of(profile.rules.zoneId)).toLocalDate()
    private fun count(epoch: Long, now: Long) = ledger.orders.count { it.status == OrderStatus.ACCEPTED && it.holdUntilEpoch > now && date(it.readyByEpoch) == date(epoch) }
    private fun expireLocked(now: Long): List<Order> {
        val expired = ledger.orders.filter { it.status == OrderStatus.ACCEPTED && it.holdUntilEpoch <= now }.map { it.copy(status = OrderStatus.EXPIRED, reason = "Unpaid reservation expired") }
        if (expired.isNotEmpty()) commit(ledger.copy(orders = ledger.orders.map { old -> expired.find { it.selection.orderId == old.selection.orderId } ?: old }))
        return expired
    }
    suspend fun expire(now: Long): List<Order> = mutex.withLock { expireLocked(now) }
    suspend fun quote(request: Request, now: Long): Pair<Decision, Offer?> = mutex.withLock {
        expireLocked(now)
        if (request.requestId in ledger.closedRequests) return@withLock Decision.NoMatch(listOf(Check("Request open", false, "Customer closed this request"))) to null
        ledger.quotes.find { it.request.requestId == request.requestId }?.let {
            require(it.request == request) { "Request ID reused with different content" }
            if (it.offer.expiresAtEpoch <= now) return@withLock Decision.NeedsOwner("Quote expired; customer must send a new request", null, it.checks) to null
            return@withLock Decision.AutoQuote(it.offer.amount, it.offer.readyByEpoch, it.checks) to it.offer
        }
        // Allow the customer the full quote window before work must begin.
        // This makes the quoted readiness achievable if selected near quote expiry.
        val preliminary = agent.decide(request, profile, MerchantState(), now + QUOTE_TTL)
        val decision = if (preliminary is Decision.AutoQuote) agent.decide(request, profile, MerchantState(count(preliminary.readyByEpoch, now)), now + QUOTE_TTL) else preliminary
        val offer = (decision as? Decision.AutoQuote)?.let {
            Offer(newId(), request.requestId, profile.sellerId, profile.shopName, it.amount, it.readyByEpoch, true,
                it.checks.map { check -> "${check.name}: ${check.detail}" }, request.constraints.map(::key), now + QUOTE_TTL, policy)
        }
        if (offer != null) commit(ledger.copy(quotes = ledger.quotes + SavedQuote(request, offer, decision.checks)))
        decision to offer
    }
    suspend fun select(selection: Selection, now: Long): Order = mutex.withLock {
        require(selection.sellerId == profile.sellerId)
        expireLocked(now)
        ledger.orders.find { it.selection.orderId == selection.orderId }?.let {
            require(it.selection == selection) { "Order ID reused with different selection" }
            return@withLock it
        }
        val quote = ledger.quotes.find { it.offer.offerId == selection.offerId }
        fun reject(reason: String): Order {
            val result = Order(selection, OrderStatus.REJECTED, profile.shopName, reason = reason)
            commit(ledger.copy(orders = ledger.orders + result))
            return result
        }
        if (quote == null || quote.request.customerId != selection.customerId || quote.request.requestId != selection.requestId) return@withLock reject("Unknown quote or mismatched customer/request")
        if (selection.requestId in ledger.closedRequests) return@withLock reject("Request is already closed")
        if (ledger.orders.any { it.selection.requestId == selection.requestId && it.status == OrderStatus.ACCEPTED && it.holdUntilEpoch > now }) return@withLock reject("Request already has a reservation")
        if (quote.offer.expiresAtEpoch <= now) return@withLock reject("Quote expired; request fresh offers")
        if (quote.offer.policyVersion != policy) return@withLock reject("Merchant rules changed; request fresh offers")
        val decision = agent.decide(quote.request, profile, MerchantState(count(quote.offer.readyByEpoch, now)), now)
        if (decision !is Decision.AutoQuote) return@withLock reject("Merchant cannot accept: " + decision.checks.filter { !it.passed }.joinToString { it.name }.ifBlank { "owner review required" })
        if (decision.amount != quote.offer.amount || decision.readyByEpoch > quote.offer.readyByEpoch) return@withLock reject("Quoted price or ready time can no longer be honored")
        val result = Order(selection, OrderStatus.ACCEPTED, profile.shopName, quote.offer.amount, quote.offer.readyByEpoch, minOf(now + HOLD_TTL, quote.offer.readyByEpoch), "Capacity reserved; payment not implemented in M2")
        commit(ledger.copy(orders = ledger.orders + result))
        result
    }
    suspend fun cancel(selection: Selection, now: Long): Order = mutex.withLock {
        require(selection.sellerId == profile.sellerId)
        expireLocked(now)
        val existing = ledger.orders.find { it.selection.orderId == selection.orderId }
        require(existing == null || existing.selection == selection) { "Order ID reused" }
        if (existing != null && existing.status != OrderStatus.ACCEPTED) return@withLock existing
        // Tombstone also handles CANCEL arriving before SELECT.
        val result = existing?.copy(status = OrderStatus.CANCELLED, reason = "Customer cancelled")
            ?: Order(selection, OrderStatus.CANCELLED, profile.shopName, reason = "Cancelled before selection arrived")
        commit(ledger.copy(orders = ledger.orders.filterNot { it.selection.orderId == selection.orderId } + result))
        result
    }
    suspend fun close(command: CloseOffer, now: Long) = mutex.withLock {
        require(command.sellerId == profile.sellerId)
        expireLocked(now)
        val quote = ledger.quotes.find { it.request.requestId == command.requestId }
        require(quote == null || quote.request.customerId == command.customerId)
        // CLOSE is only for unselected sellers; it cannot cancel an accepted order.
        if (ledger.orders.none { it.selection.requestId == command.requestId && it.status == OrderStatus.ACCEPTED }) {
            commit(ledger.copy(closedRequests = ledger.closedRequests + command.requestId))
        }
    }
}
