package `in`.nukkad.viewmodel

import `in`.nukkad.engine.OrderBook
import `in`.nukkad.model.*
import `in`.nukkad.persistence.*
import `in`.nukkad.transport.Transport
import kotlinx.coroutines.flow.*

sealed interface SellerState {
    data object Live : SellerState
    data class Evaluated(val request: Request, val decision: Decision, val quoteSent: Boolean = false) : SellerState
}
class SellerViewModel(private val transport: Transport, private val session: String, val profile: SellerProfile, store: StateStore<SellerLedger> = MemoryStore(SellerLedger())) {
    private val mutable = MutableStateFlow<SellerState>(SellerState.Live)
    val state = mutable.asStateFlow()
    private val book = OrderBook(profile, store)
    private val orderState = MutableStateFlow(book.snapshot().orders)
    val orders = orderState.asStateFlow()
    private val event = MutableStateFlow("No selection yet")
    val orderEvent = event.asStateFlow()
    suspend fun receive(request: Request, now: Long) {
        if (key(request.area) != key(profile.area) || request.domain !in profile.domains) return
        val (decision, offer) = book.quote(request, now)
        orderState.value = book.snapshot().orders
        mutable.value = SellerState.Evaluated(request, decision)
        offer?.let {
            transport.publish(Protocol.offers(session, request.customerId), Protocol.encode(Message(sessionId = session, sentAtEpoch = now, type = EventType.OFFER, offer = it)))
            mutable.value = SellerState.Evaluated(request, decision, quoteSent = true)
        }
    }
    suspend fun select(selection: Selection, now: Long) {
        val order = book.select(selection, now)
        orderState.value = book.snapshot().orders
        event.value = "${order.status}: ${order.reason}"
        publish(order, now)
    }
    suspend fun cancel(selection: Selection, now: Long) {
        val order = book.cancel(selection, now)
        orderState.value = book.snapshot().orders
        event.value = "${order.status}: ${order.reason}"
        publish(order, now)
    }
    suspend fun close(command: CloseOffer, now: Long) {
        book.close(command, now)
        event.value = "CLOSED: customer chose another seller · ${command.requestId.take(8)}"
        transport.publish(Protocol.offers(session, command.customerId), Protocol.encode(Message(sessionId = session, sentAtEpoch = now, type = EventType.CLOSED, close = command)))
    }
    suspend fun expire(now: Long) {
        val expired = book.expire(now)
        orderState.value = book.snapshot().orders
        expired.forEach { event.value = "EXPIRED: unpaid capacity released"; publish(it, now) }
    }
    private suspend fun publish(order: Order, now: Long) {
        transport.publish(Protocol.offers(session, order.selection.customerId), Protocol.encode(Message(sessionId = session, sentAtEpoch = now, type = EventType.ORDER_STATUS, order = order)))
    }
}

