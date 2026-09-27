package `in`.nukkad.viewmodel

import `in`.nukkad.model.*
import `in`.nukkad.transport.*
import `in`.nukkad.persistence.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Role { CUSTOMER, SELLER, LOCAL_LOOP }

class HarnessSession(
    val transport: Transport,
    val sessionId: String,
    val role: Role,
    customerId: String,
    private val profile: SellerProfile,
    private val scope: CoroutineScope,
    customerStore: StateStore<CustomerLedger> = MemoryStore(CustomerLedger()),
    sellerStore: StateStore<SellerLedger> = MemoryStore(SellerLedger()),
    private val now: () -> Long = System::currentTimeMillis
) {
    val customer = CustomerViewModel(transport, sessionId, customerId, customerStore)
    val seller = SellerViewModel(transport, sessionId, profile, sellerStore)
    private val failures = MutableStateFlow<String?>(null)
    val error = failures.asStateFlow()
    private val readyState = MutableStateFlow(false)
    val ready = readyState.asStateFlow()
    private val events = MutableStateFlow<List<String>>(emptyList())
    val diagnostics = events.asStateFlow()
    private val peer = MutableStateFlow("Seller connection not checked")
    val peerStatus = peer.asStateFlow()
    private var probeId: String? = null
    private var probeTimeout: Job? = null
    private val window = MessageWindow()
    private var reader: Job? = null
    private var expiryTicker: Job? = null
    private val respondingSellers = linkedMapOf<String, String>()
    private val controlTopic = Protocol.sellerOrders(sessionId, profile.sellerId)
    val requestTopic = Protocol.requests(sessionId, profile.area, profile.domains.first())
    val subscribedRequestTopics = profile.domains.map { Protocol.requests(sessionId, profile.area, it) }.toSet()
    private val offerTopic = Protocol.offers(sessionId, customerId)
    private fun record(event: String) { events.update { (listOf(event) + it).take(8) } }
    suspend fun start() {
        Protocol.safe(sessionId)
        reader = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.packets.collect { packet ->
                try {
                    val message = Protocol.decode(packet.payload)
                    val rejection = window.rejection(message, sessionId, now())
                    if (rejection != null) { failures.value = rejection; record("Rejected: $rejection"); return@collect }
                    if (!window.accept(message, sessionId, now())) return@collect
                    when (message.type) {
                        EventType.SELECT -> if (role != Role.CUSTOMER && packet.topic == controlTopic && message.selection!!.sellerId == profile.sellerId) {
                            seller.select(message.selection, now()); record("Selection handled; see order status")
                        }
                        EventType.CANCEL -> if (role != Role.CUSTOMER && packet.topic == controlTopic && message.selection!!.sellerId == profile.sellerId) seller.cancel(message.selection, now())
                        EventType.CLOSE -> if (role != Role.CUSTOMER && packet.topic == controlTopic && message.close!!.sellerId == profile.sellerId) seller.close(message.close, now())
                        EventType.ORDER_STATUS -> if (role != Role.SELLER && packet.topic == offerTopic) { customer.receiveOrder(message.order!!, now()); record("Order status: ${message.order.status}") }
                        EventType.CLOSED -> if (role != Role.SELLER && packet.topic == offerTopic) { customer.closed(message.close!!, now()); record("Losing seller acknowledged closure") }
                        EventType.REQUEST -> if (role != Role.CUSTOMER && packet.topic in subscribedRequestTopics) {
                            val request = message.request!!
                            record("REQUEST received · ${request.requestId.take(8)}")
                            acknowledge(Receipt(request.customerId, profile.sellerId, profile.shopName, requestId = request.requestId,
                                sellerLocation = profile.location?.takeIf(GeoPoint::isValid)?.publicApproximation(), sellerArea = profile.area))
                            seller.receive(request, now())
                            record("Merchant evaluation finished; see checks below")
                        }
                        EventType.OFFER -> if (role != Role.SELLER && packet.topic == offerTopic) {
                            customer.receive(message.offer!!, now())
                            record("OFFER received · ${message.offer.sellerName}")
                        }
                        EventType.PROBE -> if (role != Role.CUSTOMER && packet.topic in subscribedRequestTopics) {
                            record("Connection check received; replying")
                            val probe = message.probe!!
                            acknowledge(Receipt(probe.customerId, profile.sellerId, profile.shopName, probeId = probe.probeId,
                                sellerLocation = profile.location?.takeIf(GeoPoint::isValid)?.publicApproximation(), sellerArea = profile.area))
                        }
                        EventType.RECEIPT -> if (role != Role.SELLER && packet.topic == offerTopic) {
                            val receipt = message.receipt!!
                            if (receipt.customerId != customer.customerId) return@collect
                            if (receipt.probeId != null && receipt.probeId == probeId) {
                                probeTimeout?.cancel()
                                respondingSellers[receipt.sellerId] = receipt.sellerName
                                peer.value = "Seller replied: ${respondingSellers.values.joinToString()} (last check; not continuous presence)"
                                record("Connection check passed · ${receipt.sellerName}")
                            }
                            if (receipt.requestId != null) {
                                customer.receivedBy(receipt)
                                record("${receipt.sellerName} acknowledged request ${receipt.requestId.take(8)}")
                            }
                        }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (e: Exception) { failures.value = e.message ?: "Message rejected"; record("Error: ${failures.value}") }
            }
        }
        transport.connect()
        if (role != Role.SELLER) transport.subscribe(offerTopic)
        if (role != Role.CUSTOMER) subscribedRequestTopics.forEach { transport.subscribe(it) }
        if (role != Role.CUSTOMER) transport.subscribe(controlTopic)
        readyState.value = true
        if (role != Role.CUSTOMER) expiryTicker = scope.launch {
            while (isActive) {
                delay(5_000)
                try { seller.expire(now()) }
                catch (cancel: CancellationException) { throw cancel }
                catch (e: Exception) { failures.value = e.message }
            }
        }
        record("Broker connected; subscriptions acknowledged")
    }
    private suspend fun acknowledge(receipt: Receipt) {
        transport.publish(Protocol.offers(sessionId, receipt.customerId), Protocol.encode(Message(
            sessionId = sessionId, sentAtEpoch = now(), type = EventType.RECEIPT, receipt = receipt
        )))
    }
    suspend fun checkSeller() {
        check(role != Role.SELLER && ready.value && transport.connection.value == ConnectionState.Connected) { "Connect as customer first" }
        probeTimeout?.cancel()
        val id = newId()
        probeId = id
        respondingSellers.clear()
        peer.value = "Checking seller response…"
        record("Checking seller on $requestTopic")
        probeTimeout = scope.launch {
            delay(8_000)
            peer.value = "No seller reply. Compare connected session codes, broker and roles; connect seller first and retry."
        }
        try {
            transport.publish(requestTopic, Protocol.encode(Message(sessionId = sessionId, sentAtEpoch = now(), type = EventType.PROBE, probe = Probe(id, customer.customerId))))
        } catch (e: Exception) {
            probeTimeout?.cancel()
            peer.value = "Connection check could not be sent"
            throw e
        }
    }
    suspend fun close() { readyState.value = false; probeTimeout?.cancelAndJoin(); reader?.cancelAndJoin(); expiryTicker?.cancelAndJoin(); transport.disconnect() }
}





