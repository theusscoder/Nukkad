package `in`.nukkad

import `in`.nukkad.debug.Seeds
import `in`.nukkad.model.*
import `in`.nukkad.transport.*
import `in`.nukkad.viewmodel.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryDiagnosticsTest {
    private val now = ZonedDateTime.parse("2026-09-26T10:00:00+05:30[Asia/Kolkata]").toInstant().toEpochMilli()
    @Test fun sessionCodesAreShortAndLegacyCodesStillWork() {
        repeat(100) { assertTrue(newSessionCode().matches(Regex("[0-9]{8}"))) }
        assertEquals("abc123", normalizeSession(" AbC123 "))
        val uuid = newId()
        assertEquals(uuid, normalizeSession(uuid))
        assertTrue(runCatching { normalizeSession("123/456") }.isFailure)
    }
    @Test fun sellerProbeConfirmsRoundTripWithoutCreatingRequest() = runTest {
        val broker = FakeBroker()
        val customer = HarnessSession(FakeTransport(broker), "12345678", Role.CUSTOMER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        val seller = HarnessSession(FakeTransport(broker), "12345678", Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        customer.start(); seller.start(); customer.checkSeller(); runCurrent()
        assertTrue(customer.peerStatus.value.startsWith("Seller replied: Sweet Crumbs"))
        assertEquals(SellerState.Live, seller.seller.state.value)
        advanceTimeBy(9000); runCurrent()
        assertTrue(customer.peerStatus.value.startsWith("Seller replied:"))
        customer.close(); seller.close()
    }
    @Test fun wrongSessionTimesOutDespiteBothBrokerConnections() = runTest {
        val broker = FakeBroker()
        val customer = HarnessSession(FakeTransport(broker), "12345678", Role.CUSTOMER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        val seller = HarnessSession(FakeTransport(broker), "87654321", Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        customer.start(); seller.start(); customer.checkSeller(); runCurrent()
        advanceTimeBy(8001); runCurrent()
        assertTrue(customer.ready.value && seller.ready.value)
        assertTrue(customer.peerStatus.value.startsWith("No seller reply"))
        customer.close(); seller.close()
    }
    @Test fun requestDeliveryIsAcknowledgedEvenWhenBudgetIsRejected() = runTest {
        val broker = FakeBroker()
        val customerId = newId()
        val customer = HarnessSession(FakeTransport(broker), "12345678", Role.CUSTOMER, customerId, Seeds.seller(newId()), backgroundScope) { now }
        val seller = HarnessSession(FakeTransport(broker), "12345678", Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        customer.start(); seller.start()
        customer.customer.send(Request(newId(), customerId, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 700, now + 86400000, listOf("eggless")), now)
        runCurrent()
        assertEquals("Request received by: Sweet Crumbs", customer.customer.delivery.value)
        assertTrue(customer.customer.state.value is CustomerState.Searching)
        assertTrue((seller.seller.state.value as SellerState.Evaluated).decision is Decision.NoMatch)
        customer.close(); seller.close()
    }
    @Test fun futureClockProducesVisibleDiagnostic() = runTest {
        val broker = FakeBroker()
        val transport = FakeTransport(broker)
        val seller = HarnessSession(transport, "12345678", Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        seller.start()
        transport.publish(seller.requestTopic, Protocol.encode(Message(sessionId = "12345678", sentAtEpoch = now + 120_000, type = EventType.PROBE, probe = Probe(newId(), newId()))))
        runCurrent()
        assertTrue(seller.error.value!!.contains("clock is ahead"))
        assertTrue(seller.diagnostics.value.any { it.startsWith("Rejected:") })
        seller.close()
    }
    @Test fun receiptsMustHaveOneCorrelationIdAndRoundTrip() {
        val receipt = Receipt(newId(), newId(), "Shop", probeId = newId())
        val message = Message(sessionId = "12345678", sentAtEpoch = now, type = EventType.RECEIPT, receipt = receipt)
        assertEquals(message, Protocol.decode(Protocol.encode(message)))
        assertTrue(runCatching { Protocol.decode(Protocol.encode(message.copy(receipt = receipt.copy(requestId = newId())))) }.isFailure)
    }
    @Test fun unrelatedReceiptDoesNotConfirmCustomerRequest() = runTest {
        val transport = FakeTransport(FakeBroker())
        transport.connect()
        val customerId = newId()
        val customer = CustomerViewModel(transport, "12345678", customerId)
        customer.send(Request(newId(), customerId, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 86400000, listOf("eggless")), now)
        customer.receivedBy(Receipt(customerId, newId(), "Other", requestId = newId()))
        assertTrue(customer.delivery.value.startsWith("Broker accepted"))
        transport.disconnect()
    }
}
