package `in`.nukkad.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.security.SecureRandom

fun newId(): String = UUID.randomUUID().toString()
fun key(value: String): String = value.trim().lowercase(java.util.Locale.ROOT)
/** Human-readable demo room code, not an authentication secret. Device/message IDs remain UUIDs. */
fun newSessionCode(): String = (10_000_000 + SecureRandom().nextInt(90_000_000)).toString()
fun normalizeSession(value: String): String = Protocol.safe(key(value))

@Serializable
enum class EventType { REQUEST, OFFER, PROBE, RECEIPT, SELECT, ORDER_STATUS, CANCEL, CLOSE, CLOSED }
@Serializable
data class Probe(val probeId: String, val customerId: String)
@Serializable
data class Receipt(val customerId: String, val sellerId: String, val sellerName: String, val requestId: String? = null, val probeId: String? = null)

@Serializable
data class Message(
    val messageId: String = newId(),
    val sessionId: String,
    val sentAtEpoch: Long,
    val type: EventType,
    val request: Request? = null,
    val offer: Offer? = null,
    val protocolVersion: Int = 1,
    val probe: Probe? = null,
    val receipt: Receipt? = null,
    val selection: Selection? = null,
    val order: Order? = null,
    val close: CloseOffer? = null
)

object Protocol {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val segment = Regex("[a-zA-Z0-9_-]{1,80}")
    fun safe(value: String): String = value.also { require(segment.matches(it)) { "Use letters, digits, hyphens or underscores (1–80 characters)." } }
    fun requests(session: String, area: String, category: String) = "nukkad/${safe(session)}/${safe(key(area))}/${safe(key(category))}/requests"
    fun requests(session: String, area: String, domain: CommerceDomain) = requests(session, area, domain.name.lowercase())
    fun offers(session: String, customer: String) = "nukkad/${safe(session)}/customers/${safe(customer)}/offers"
    fun sellerOrders(session: String, seller: String) = "nukkad/${safe(session)}/sellers/${safe(seller)}/orders"
    private fun validate(selection: Selection) {
        listOf(selection.orderId, selection.requestId, selection.customerId, selection.offerId, selection.sellerId).forEach { UUID.fromString(it) }
    }
    fun encode(message: Message): String = json.encodeToString(Message.serializer(), message)
    fun decode(payload: String): Message {
        require(payload.length <= 32_768) { "Message too large" }
        return json.decodeFromString(Message.serializer(), payload).also {
            require(it.protocolVersion == 1)
            UUID.fromString(it.messageId)
            safe(it.sessionId)
            require(listOfNotNull(it.request, it.offer, it.probe, it.receipt, it.selection, it.order, it.close).size == 1) { "Expected exactly one event payload" }
            when (it.type) {
                EventType.SELECT, EventType.CANCEL -> validate(requireNotNull(it.selection))
                EventType.ORDER_STATUS -> validate(requireNotNull(it.order).selection)
                EventType.CLOSE, EventType.CLOSED -> {
                    val command = requireNotNull(it.close)
                    listOf(command.requestId, command.customerId, command.sellerId).forEach { id -> UUID.fromString(id) }
                }
                EventType.REQUEST -> { require(it.request != null); UUID.fromString(it.request.requestId); UUID.fromString(it.request.customerId) }
                EventType.OFFER -> { require(it.offer != null); UUID.fromString(it.offer.offerId); UUID.fromString(it.offer.requestId); UUID.fromString(it.offer.sellerId) }
                EventType.PROBE -> { require(it.probe != null); UUID.fromString(it.probe.probeId); UUID.fromString(it.probe.customerId) }
                EventType.RECEIPT -> {
                    val receipt = requireNotNull(it.receipt)
                    UUID.fromString(receipt.customerId); UUID.fromString(receipt.sellerId)
                    require((receipt.requestId == null) != (receipt.probeId == null))
                    UUID.fromString(receipt.requestId ?: receipt.probeId)
                }
            }
        }
    }
}

class MessageWindow(private val limit: Int = 512) {
    private val seen = LinkedHashSet<String>()
    fun rejection(message: Message, session: String, now: Long): String? = when {
        message.sessionId != session -> "Different session code"
        message.sentAtEpoch < now - 600_000 -> "Message too old: check automatic date/time on both phones, then retry"
        message.sentAtEpoch > now + 60_000 -> "Sender clock is ahead: enable automatic date/time on both phones, then retry"
        else -> null
    }
    fun accept(message: Message, session: String, now: Long): Boolean {
        if (rejection(message, session, now) != null) return false
        if (!seen.add(message.messageId)) return false
        if (seen.size > limit) seen.remove(seen.first())
        return true
    }
}



