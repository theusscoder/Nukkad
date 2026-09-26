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

class MqttThreePhoneTest {
    @Test fun twoMerchantsQuoteOneAcceptsOtherCloses() {
        assumeTrue(System.getProperty("nukkad.mqttSmoke") == "true")
        runBlocking {
            withTimeout(90_000) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val code = newSessionCode(); val customerId = newId()
                fun transport() = MqttTransport("broker.hivemq.com", 8883, newId(), true)
                val a = HarnessSession(transport(), code, Role.CUSTOMER, customerId, Seeds.seller(newId()), scope)
                val b = HarnessSession(transport(), code, Role.SELLER, newId(), Seeds.seller(newId()), scope)
                val c = HarnessSession(transport(), code, Role.SELLER, newId(), Seeds.seller(newId(), 1), scope)
                try {
                    println("Connecting merchant B")
                    b.start()
                    println("Connecting merchant C")
                    c.start()
                    println("Connecting customer A")
                    a.start()
                    println("Sending request")
                    val now = System.currentTimeMillis()
                    a.customer.send(Request(newId(), customerId, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 172800000, listOf("eggless")), now)
                    val quotes = withTimeout(15000) { a.customer.state.first { it is CustomerState.Quotes && it.offers.size == 2 } } as CustomerState.Quotes
                    println("Received both quotes; selecting")
                    a.customer.select(quotes.offers.first().offer.offerId, System.currentTimeMillis())
                    val accepted = withTimeout(15000) { a.customer.state.first { it is CustomerState.Accepted && it.pendingCloses == 0 } } as CustomerState.Accepted
                    assertEquals(750, accepted.order.amount)
                    assertTrue(c.seller.orderEvent.value.startsWith("CLOSED:"))
                    println("Accepted and closed loser; cancelling")
                    a.customer.cancel(System.currentTimeMillis())
                    val finished = withTimeout(15000) { a.customer.state.first { it is CustomerState.Finished } } as CustomerState.Finished
                    assertEquals(OrderStatus.CANCELLED, finished.order.status)
                } finally { withContext(NonCancellable) { a.close(); b.close(); c.close(); scope.cancel() } }
            }
        }
    }
}

