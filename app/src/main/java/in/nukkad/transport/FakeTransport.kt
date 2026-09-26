package `in`.nukkad.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** A broker shared explicitly by fake clients. Never crosses devices/processes. */
class FakeBroker {
    private val clients = mutableMapOf<FakeTransport, MutableSet<String>>()
    @Synchronized internal fun connect(client: FakeTransport) { clients[client] = mutableSetOf() }
    @Synchronized internal fun subscribe(client: FakeTransport, topic: String) { checkNotNull(clients[client]).add(topic) }
    @Synchronized internal fun disconnect(client: FakeTransport) { clients.remove(client) }
    @Synchronized internal fun publish(packet: Packet) {
        clients.filterValues { packet.topic in it }.keys.forEach { it.receive(packet) }
    }
}
class FakeTransport(private val broker: FakeBroker) : Transport {
    private val inbox = Channel<Packet>(128)
    private val status = MutableStateFlow<ConnectionState>(ConnectionState.Offline)
    override val packets = inbox.receiveAsFlow()
    override val connection = status.asStateFlow()
    internal fun receive(packet: Packet) { check(inbox.trySend(packet).isSuccess) { "Fake inbox full" } }
    override suspend fun connect() { broker.connect(this); status.value = ConnectionState.Connected }
    override suspend fun subscribe(topic: String) { check(status.value == ConnectionState.Connected); broker.subscribe(this, topic) }
    override suspend fun publish(topic: String, payload: String) { check(status.value == ConnectionState.Connected); broker.publish(Packet(topic, payload)) }
    override suspend fun disconnect() { broker.disconnect(this); status.value = ConnectionState.Offline }
}
