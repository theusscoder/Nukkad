package `in`.nukkad.transport

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

/** Foreground dev harness. Reconnect is explicit so all subscriptions are re-established. */
class MqttTransport(host: String, port: Int, clientId: String, tls: Boolean) : Transport {
    private val status = MutableStateFlow<ConnectionState>(ConnectionState.Offline)
    private val inbox = Channel<Packet>(128)
    override val packets = inbox.receiveAsFlow()
    override val connection = status.asStateFlow()
    private val client = MqttClient.builder().useMqttVersion3().identifier(clientId)
        .serverHost(host).serverPort(port)
        .addDisconnectedListener { status.value = ConnectionState.Failed(it.cause.message ?: "Disconnected; reconnect to retry") }
        .apply { if (tls) useSslWithDefaultConfig() }
        .buildAsync()
    override suspend fun connect() {
        status.value = ConnectionState.Connecting
        try {
            withTimeout(15_000) { client.connectWith().cleanSession(true).keepAlive(30).send().await() }
            status.value = ConnectionState.Connected
        } catch (e: Exception) { status.value = ConnectionState.Failed(e.message ?: "Connection failed"); throw e }
    }
    override suspend fun subscribe(topic: String) {
        val ack = withTimeout(10_000) {
            client.subscribeWith().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).callback {
                val bytes = it.payloadAsBytes
                if (bytes.size <= 32_768 && !inbox.trySend(Packet(it.topic.toString(), bytes.toString(Charsets.UTF_8))).isSuccess) {
                    status.value = ConnectionState.Failed("Incoming queue full; reconnect and retry")
                }
            }.send().await()
        }
        check(ack.returnCodes.none { it.code == 128 }) { "Broker rejected subscription" }
    }
    override suspend fun publish(topic: String, payload: String) {
        check(status.value == ConnectionState.Connected) { "Not connected" }
        withTimeout(10_000) {
            client.publishWith().topic(topic).qos(MqttQos.AT_LEAST_ONCE).retain(false)
                .payload(payload.toByteArray(Charsets.UTF_8)).send().await()
        }
    }
    override suspend fun disconnect() {
        runCatching { withTimeout(3_000) { client.disconnect().await() } }
        status.value = ConnectionState.Offline
    }
}
