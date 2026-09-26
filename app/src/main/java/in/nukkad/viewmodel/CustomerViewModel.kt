package `in`.nukkad.viewmodel

import `in`.nukkad.engine.Ranker
import `in`.nukkad.engine.RankedOffer
import `in`.nukkad.model.*
import `in`.nukkad.persistence.*
import `in`.nukkad.transport.Transport
import kotlinx.coroutines.flow.*

sealed interface CustomerState {
    data object Idle : CustomerState
    data class Searching(val request: Request) : CustomerState
    data class Quotes(val request: Request, val offers: List<RankedOffer>) : CustomerState
    data class Selecting(val request: Request, val selection: Selection, val cancelling: Boolean) : CustomerState
    data class Accepted(val request: Request, val order: Order, val pendingCloses: Int) : CustomerState
    data class Finished(val request: Request, val order: Order) : CustomerState
}
class CustomerViewModel(private val transport: Transport, private val session: String, val customerId: String, private val store: StateStore<CustomerLedger> = MemoryStore(CustomerLedger())) {
    private var ledger = store.load()
    private val mutable = MutableStateFlow<CustomerState>(CustomerState.Idle)
    val state = mutable.asStateFlow()
    private val deliveryState = MutableStateFlow("No request sent")
    val delivery = deliveryState.asStateFlow()
    private val receivers = linkedMapOf<String, String>()
    init { refresh(System.currentTimeMillis()) }
    private fun commit(next: CustomerLedger, now: Long) { store.save(next); ledger = next; refresh(now) }
    private fun refresh(now: Long) {
        val request = ledger.request
        val selection = ledger.selection
        val order = ledger.order
        mutable.value = when {
            request == null -> CustomerState.Idle
            selection != null && (ledger.cancelling || order == null) -> CustomerState.Selecting(request, selection, ledger.cancelling)
            order?.status == OrderStatus.ACCEPTED -> CustomerState.Accepted(request, order, ledger.pendingCloses.size)
            order?.status == OrderStatus.CANCELLED || order?.status == OrderStatus.EXPIRED -> CustomerState.Finished(request, order!!)
            ledger.offers.isNotEmpty() -> CustomerState.Quotes(request, Ranker.rank(request, ledger.offers, now))
            else -> CustomerState.Searching(request)
        }
    }
    fun receivedBy(receipt: Receipt) {
        val request = ledger.request ?: return
        if (receipt.customerId != customerId || receipt.requestId != request.requestId) return
        receivers[receipt.sellerId] = receipt.sellerName
        deliveryState.value = "Request received by: " + receivers.values.joinToString()
    }
    suspend fun send(request: Request, now: Long) {
        require(request.customerId == customerId)
        check(mutable.value !is CustomerState.Selecting && mutable.value !is CustomerState.Accepted) { "Resolve or cancel the current selection before starting another request" }
        if (ledger.request?.requestId != request.requestId) { receivers.clear(); commit(CustomerLedger(request = request), now) }
        deliveryState.value = "Publishing request…"
        try { transport.publish(Protocol.requests(session, request.area, request.domain), Protocol.encode(Message(sessionId = session, sentAtEpoch = now, type = EventType.REQUEST, request = request))) }
        catch (e: Exception) { deliveryState.value = "Publish failed; reconnect and retry"; throw e }
        deliveryState.value = if (receivers.isEmpty()) "Broker accepted request; awaiting seller receipt" else "Request received by: " + receivers.values.joinToString()
    }
    suspend fun receive(offer: Offer, now: Long) {
        val request = ledger.request ?: return
        if (offer.requestId != request.requestId || Ranker.rank(request, listOf(offer), now).isEmpty()) return
        if (ledger.order?.status == OrderStatus.ACCEPTED && offer.sellerId != ledger.selection?.sellerId) {
            commit(ledger.copy(pendingCloses = ledger.pendingCloses + offer.sellerId), now)
            closeLosers(now)
            return
        }
        if (ledger.order?.status == OrderStatus.CANCELLED || ledger.order?.status == OrderStatus.EXPIRED) return
        commit(ledger.copy(offers = ledger.offers.filterNot { it.sellerId == offer.sellerId } + offer), now)
    }
    suspend fun select(offerId: String, now: Long) {
        check(mutable.value is CustomerState.Quotes) { "Wait for current selection to finish" }
        val request = checkNotNull(ledger.request)
        val offer = Ranker.rank(request, ledger.offers, now).map { it.offer }.firstOrNull { it.offerId == offerId }
            ?: error("Quote expired or invalid; send a fresh request")
        val selection = Selection(newId(), request.requestId, customerId, offer.offerId, offer.sellerId)
        // Persist intent before publishing; retries reuse this order ID.
        commit(ledger.copy(selection = selection, order = null, cancelling = false), now)
        publishSelection(EventType.SELECT, selection, now)
    }
    suspend fun receiveOrder(order: Order, now: Long) {
        if (order.selection != ledger.selection) return
        val previous = ledger.order
        if (previous?.status == OrderStatus.CANCELLED || previous?.status == OrderStatus.EXPIRED || previous?.status == OrderStatus.REJECTED) return
        if (order.status == OrderStatus.ACCEPTED) {
            val quote = ledger.offers.firstOrNull { it.offerId == order.selection.offerId } ?: return
            if (order.amount != quote.amount || order.readyByEpoch != quote.readyByEpoch || order.holdUntilEpoch <= 0) return
            val losers = if (previous == null) ledger.offers.filter { it.sellerId != order.selection.sellerId }.map { it.sellerId }.toSet() else emptySet()
            commit(ledger.copy(order = order, pendingCloses = ledger.pendingCloses + losers), now)
            if (ledger.cancelling) publishSelection(EventType.CANCEL, order.selection, now) else closeLosers(now)
        } else {
            val remaining = ledger.offers.filterNot { it.sellerId == order.selection.sellerId }
            commit(ledger.copy(order = order, cancelling = false, offers = remaining, pendingCloses = emptySet()), now)
            deliveryState.value = "${order.status}: ${order.reason}"
        }
    }
    suspend fun cancel(now: Long) {
        val selection = checkNotNull(ledger.selection)
        check(mutable.value is CustomerState.Accepted || mutable.value is CustomerState.Selecting)
        commit(ledger.copy(cancelling = true), now)
        publishSelection(EventType.CANCEL, selection, now)
    }
    suspend fun retry(now: Long) {
        val selection = ledger.selection
        if (selection != null && (ledger.order == null || ledger.order?.status == OrderStatus.ACCEPTED || ledger.cancelling)) {
            publishSelection(if (ledger.cancelling) EventType.CANCEL else EventType.SELECT, selection, now)
            if (ledger.order?.status == OrderStatus.ACCEPTED && !ledger.cancelling) closeLosers(now)
        } else send(checkNotNull(ledger.request) { "No request to retry" }, now)
    }
    private suspend fun publishSelection(type: EventType, selection: Selection, now: Long) {
        transport.publish(Protocol.sellerOrders(session, selection.sellerId), Protocol.encode(Message(sessionId = session, sentAtEpoch = now, type = type, selection = selection)))
    }
    private suspend fun closeLosers(now: Long) {
        val request = checkNotNull(ledger.request)
        for (seller in ledger.pendingCloses) {
            val close = CloseOffer(request.requestId, customerId, seller)
            transport.publish(Protocol.sellerOrders(session, seller), Protocol.encode(Message(sessionId = session, sentAtEpoch = now, type = EventType.CLOSE, close = close)))
        }
    }
    fun closed(command: CloseOffer, now: Long) {
        if (command.customerId != customerId || command.requestId != ledger.request?.requestId) return
        commit(ledger.copy(pendingCloses = ledger.pendingCloses - command.sellerId), now)
    }
}

