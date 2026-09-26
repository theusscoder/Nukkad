package `in`.nukkad

import `in`.nukkad.debug.Seeds
import `in`.nukkad.model.*
import `in`.nukkad.transport.MqttTransport
import `in`.nukkad.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Two real TCP/TLS clients, public broker, synthetic data, unique session. Not a device test. */
class MqttSmokeTest {
    @Test fun fiveRealBrokerRequestQuoteCycles() {
        assumeTrue("Enable with -PmqttSmoke=true", System.getProperty("nukkad.mqttSmoke") == "true")
        runBlocking {
            withTimeout(120_000) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val session = newSessionCode()
                val customerId = newId()
                val customer = HarnessSession(MqttTransport("broker.hivemq.com", 8883, newId(), true), session, Role.CUSTOMER, customerId, Seeds.seller(newId()), scope)
                val seller = HarnessSession(MqttTransport("broker.hivemq.com", 8883, newId(), true), session, Role.SELLER, newId(), Seeds.seller(newId()), scope)
                try {
                    seller.start(); customer.start()
                    customer.checkSeller()
                    withTimeout(15_000) { customer.peerStatus.first { it.startsWith("Seller replied:") } }
                    repeat(5) {
                        val now = System.currentTimeMillis()
                        val request = Request(newId(), customerId, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 48 * 3_600_000, listOf("eggless"))
                        customer.customer.send(request, now)
                        val quotes = withTimeout(15_000) { customer.customer.state.first { it is CustomerState.Quotes && it.request.requestId == request.requestId } } as CustomerState.Quotes
                        assertEquals(750, quotes.offers.single().offer.amount)
                        withTimeout(15_000) { customer.customer.delivery.first { it.startsWith("Request received by:") } }
                    }
                } finally {
                    withContext(NonCancellable) { customer.close(); seller.close(); scope.cancel() }
                }
            }
        }
    }
}

