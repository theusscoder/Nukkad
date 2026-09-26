package `in`.nukkad

import `in`.nukkad.debug.Seeds
import `in`.nukkad.model.*
import `in`.nukkad.transport.*
import `in`.nukkad.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HarnessLoopTest {
    private val now = ZonedDateTime.parse("2026-09-26T10:00:00+05:30[Asia/Kolkata]").toInstant().toEpochMilli()
    @Test fun fiveIndependentSessionsCompleteLoopAndDeduplicateRetry() = runTest {
        val broker = FakeBroker()
        repeat(5) {
            val id = newId()
            val customerId = newId()
            val customer = HarnessSession(FakeTransport(broker), id, Role.CUSTOMER, customerId, Seeds.seller(newId()), backgroundScope) { now }
            val seller = HarnessSession(FakeTransport(broker), id, Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
            customer.start(); seller.start()
            val request = Request(newId(), customerId, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 24 * 3_600_000, listOf("eggless"))
            customer.customer.send(request, now)
            runCurrent()
            val quote = (customer.customer.state.value as CustomerState.Quotes).offers.single().offer
            assertEquals(750, quote.amount)
            assertTrue((seller.seller.state.value as SellerState.Evaluated).quoteSent)
            customer.customer.retry(now)
            runCurrent()
            assertEquals(quote.offerId, (customer.customer.state.value as CustomerState.Quotes).offers.single().offer.offerId)
            customer.close(); seller.close()
        }
    }
    @Test fun fakeBrokerIsolatesSessionsAndDoesNotRetainRequests() = runTest {
        val broker = FakeBroker()
        val customerId = newId()
        val customer = HarnessSession(FakeTransport(broker), "session-a", Role.CUSTOMER, customerId, Seeds.seller(newId()), backgroundScope) { now }
        val wrongSeller = HarnessSession(FakeTransport(broker), "session-b", Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        customer.start(); wrongSeller.start()
        customer.customer.send(Request(newId(), customerId, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 86400000, listOf("eggless")), now)
        runCurrent()
        assertEquals(SellerState.Live, wrongSeller.seller.state.value)
        val lateSeller = HarnessSession(FakeTransport(broker), "session-a", Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        lateSeller.start(); runCurrent()
        assertEquals(SellerState.Live, lateSeller.seller.state.value)
        customer.customer.retry(now); runCurrent()
        assertTrue(customer.customer.state.value is CustomerState.Quotes)
        customer.close(); wrongSeller.close(); lateSeller.close()
    }
    @Test fun malformedMessageDoesNotKillReceiver() = runTest {
        val broker = FakeBroker()
        val transport = FakeTransport(broker)
        val seller = HarnessSession(transport, "demo", Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        seller.start()
        transport.publish(Protocol.requests("demo", "kondapur", "bakery"), "not JSON")
        runCurrent()
        assertNotNull(seller.error.value)
        val request = Request(newId(), newId(), "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 86400000, listOf("eggless"))
        transport.publish(Protocol.requests("demo", "kondapur", "bakery"), Protocol.encode(Message(sessionId = "demo", sentAtEpoch = now, type = EventType.REQUEST, request = request)))
        runCurrent()
        assertTrue(seller.seller.state.value is SellerState.Evaluated)
        seller.close()
    }
    @Test fun windowRejectsStaleWrongSessionAndDuplicateMessages() {
        val message = Message(sessionId = "demo", sentAtEpoch = now, type = EventType.REQUEST)
        val window = MessageWindow()
        assertFalse(window.accept(message, "other", now))
        assertFalse(window.accept(message.copy(sentAtEpoch = now - 600001), "demo", now))
        assertFalse(window.accept(message.copy(sentAtEpoch = now + 60001), "demo", now))
        assertTrue(window.accept(message, "demo", now))
        assertFalse(window.accept(message, "demo", now))
    }
    @Test fun protocolRoundTripAndTopicValidation() {
        val request = Request(newId(), newId(), "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 86400000, listOf("eggless"))
        val message = Message(sessionId = newId(), sentAtEpoch = now, type = EventType.REQUEST, request = request)
        assertEquals(message, Protocol.decode(Protocol.encode(message)))
        assertTrue(runCatching { Protocol.requests("bad/#", "kondapur", "bakery") }.isFailure)
    }
}
