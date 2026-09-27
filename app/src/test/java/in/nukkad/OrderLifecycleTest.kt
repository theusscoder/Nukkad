package `in`.nukkad

import `in`.nukkad.debug.Seeds
import `in`.nukkad.engine.OrderBook
import `in`.nukkad.model.*
import `in`.nukkad.persistence.*
import `in`.nukkad.transport.*
import `in`.nukkad.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class OrderLifecycleTest {
    private val now = ZonedDateTime.parse("2026-09-26T10:00:00+05:30[Asia/Kolkata]").toInstant().toEpochMilli()
    private fun profile() = Seeds.seller(newId()).let { it.copy(rules = it.rules.copy(maxDailyOrders = 1)) }
    private fun request(customer: String = newId()) = Request(newId(), customer, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 86400000, listOf("eggless"))
    private fun selection(request: Request, offer: Offer) = Selection(newId(), request.requestId, request.customerId, offer.offerId, offer.sellerId)

    @Test fun twoSelectionsCompeteForOneSlot() = runTest {
        val book = OrderBook(profile())
        val a = request(); val b = request()
        val qa = book.quote(a, now).second!!; val qb = book.quote(b, now).second!!
        val outcomes = awaitAll(async { book.select(selection(a, qa), now + 1000) }, async { book.select(selection(b, qb), now + 1000) })
        assertEquals(1, outcomes.count { it.status == OrderStatus.ACCEPTED })
        assertEquals(1, outcomes.count { it.status == OrderStatus.REJECTED })
    }
    @Test fun duplicateSelectionDoesNotReserveTwiceAndSurvivesRestart() = runTest {
        val profile = profile(); val store = MemoryStore(SellerLedger())
        val book = OrderBook(profile, store)
        val request = request(); val offer = book.quote(request, now).second!!
        val selection = selection(request, offer)
        val accepted = book.select(selection, now + 1000)
        assertEquals(accepted, book.select(selection, now + 2000))
        val restarted = OrderBook(profile, store)
        assertEquals(accepted, restarted.select(selection, now + 3000))
        assertEquals(1, restarted.snapshot().orders.size)
    }
    @Test fun expiredQuoteCannotReserve() = runTest {
        val book = OrderBook(profile()); val request = request(); val offer = book.quote(request, now).second!!
        assertEquals(OrderStatus.REJECTED, book.select(selection(request, offer), offer.expiresAtEpoch).status)
    }
    @Test fun ruleChangeInvalidatesQuoteWithoutChangingItsPrice() = runTest {
        val profile = profile(); val store = MemoryStore(SellerLedger()); val book = OrderBook(profile, store)
        val request = request(); val offer = book.quote(request, now).second!!
        val changed = OrderBook(profile.copy(rules = profile.rules.copy(minimumPrice = 760)), store)
        assertEquals(OrderStatus.REJECTED, changed.select(selection(request, offer), now + 1000).status)
        assertEquals(750, store.load().quotes.single().offer.amount)
    }
    @Test fun cancellationBeforeSelectionCannotLaterCreateOrder() = runTest {
        val book = OrderBook(profile()); val request = request(); val offer = book.quote(request, now).second!!
        val selection = selection(request, offer)
        assertEquals(OrderStatus.CANCELLED, book.cancel(selection, now + 1000).status)
        assertEquals(OrderStatus.CANCELLED, book.select(selection, now + 2000).status)
    }
    @Test fun cancelReleasesSlotForAnotherCustomer() = runTest {
        val book = OrderBook(profile()); val a = request(); val b = request()
        val qa = book.quote(a, now).second!!; val qb = book.quote(b, now).second!!
        val selected = selection(a, qa)
        book.select(selected, now + 1000); book.cancel(selected, now + 2000)
        assertEquals(OrderStatus.ACCEPTED, book.select(selection(b, qb), now + 3000).status)
    }
    @Test fun unpaidHoldExpiresAndDoesNotReappearOnRetry() = runTest {
        val book = OrderBook(profile()); val request = request(); val offer = book.quote(request, now).second!!
        val selection = selection(request, offer); val accepted = book.select(selection, now + 1000)
        assertEquals(1, book.expire(accepted.holdUntilEpoch).size)
        assertEquals(OrderStatus.EXPIRED, book.select(selection, accepted.holdUntilEpoch + 1).status)
        val fresh = request(); assertNotNull(book.quote(fresh, accepted.holdUntilEpoch + 2).second)
    }
    @Test fun closedQuoteCannotBeSelected() = runTest {
        val book = OrderBook(profile()); val request = request(); val offer = book.quote(request, now).second!!
        book.close(CloseOffer(request.requestId, request.customerId, offer.sellerId), now)
        assertEquals(OrderStatus.REJECTED, book.select(selection(request, offer), now + 1000).status)
    }
    @Test fun persistenceFailureNeverAcceptsOrConsumesSlot() = runTest {
        val initial = MemoryStore(SellerLedger()); val profile = profile(); val request = request()
        val offer = OrderBook(profile, initial).quote(request, now).second!!
        val failing = object : StateStore<SellerLedger> {
            override fun load() = initial.load()
            override fun save(value: SellerLedger) { error("Disk unavailable") }
        }
        val book = OrderBook(profile, failing)
        assertTrue(runCatching { book.select(selection(request, offer), now + 1000) }.isFailure)
        assertTrue(book.snapshot().orders.isEmpty())
    }
    @Test fun wrongCustomerCannotUseAnotherQuote() = runTest {
        val book = OrderBook(profile()); val request = request(); val offer = book.quote(request, now).second!!
        assertEquals(OrderStatus.REJECTED, book.select(selection(request, offer).copy(customerId = newId()), now).status)
    }
    @Test fun threePhoneLoopAcceptsOneClosesOtherAndCancels() = runTest {
        val broker = FakeBroker(); val session = newSessionCode(); val customerId = newId()
        val a = HarnessSession(FakeTransport(broker), session, Role.CUSTOMER, customerId, Seeds.seller(newId()), backgroundScope) { now }
        val b = HarnessSession(FakeTransport(broker), session, Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope) { now }
        val c = HarnessSession(FakeTransport(broker), session, Role.SELLER, newId(), Seeds.seller(newId(), 1), backgroundScope) { now }
        a.start(); b.start(); c.start()
        a.customer.send(request(customerId), now); runCurrent()
        val quotes = a.customer.state.value as CustomerState.Quotes
        assertEquals(2, quotes.offers.size)
        a.customer.select(quotes.offers.first().offer.offerId, now + 1000); runCurrent()
        val accepted = a.customer.state.value as CustomerState.Accepted
        assertEquals(0, accepted.pendingCloses)
        assertEquals(1, b.seller.orders.value.count { it.status == OrderStatus.ACCEPTED })
        assertTrue(c.seller.orderEvent.value.startsWith("CLOSED:"))
        a.customer.retry(now + 2000); runCurrent()
        assertEquals(1, b.seller.orders.value.size)
        a.customer.cancel(now + 3000); runCurrent()
        assertTrue(a.customer.state.value is CustomerState.Finished)
        assertEquals(OrderStatus.CANCELLED, b.seller.orders.value.single().status)
        a.close(); b.close(); c.close()
    }
    @Test fun customerSelectionIntentSurvivesRestartAndRetriesSameId() = runTest {
        val broker = FakeBroker(); val transport = FakeTransport(broker); transport.connect()
        val customerId = newId(); val store = MemoryStore(CustomerLedger()); val session = newSessionCode()
        val customer = CustomerViewModel(transport, session, customerId, store)
        val request = request(customerId)
        val offer = OrderBook(profile()).quote(request, now).second!!
        customer.send(request, now); customer.receive(offer, now); customer.select(offer.offerId, now)
        val id = (customer.state.value as CustomerState.Selecting).selection.orderId
        val restarted = CustomerViewModel(transport, session, customerId, store)
        assertEquals(id, (restarted.state.value as CustomerState.Selecting).selection.orderId)
        restarted.retry(now + 1000)
        assertEquals(id, (restarted.state.value as CustomerState.Selecting).selection.orderId)
        assertTrue(runCatching { restarted.send(request(), now) }.isFailure)
        transport.disconnect()
    }
    @Test fun closeDoesNotCancelAcceptedOrder() = runTest {
        val book = OrderBook(profile()); val request = request(); val offer = book.quote(request, now).second!!
        val selected = selection(request, offer); book.select(selected, now)
        book.close(CloseOffer(request.requestId, request.customerId, offer.sellerId), now)
        assertEquals(OrderStatus.ACCEPTED, book.select(selected, now + 1000).status)
    }
    @Test fun openModeManualOfferRequiresOwnerAndCanBeAccepted() = runTest {
        val profile = profile().copy(discoveryMode = DiscoveryMode.OPEN)
        val book = OrderBook(profile)
        val request = request().copy(item = "brownies")
        val (decision, automatic) = book.quote(request, now)
        assertTrue(decision is Decision.NeedsOwner)
        assertNull(automatic)
        val offer = book.quoteByOwner(request, 780, now + 3 * 3_600_000, now)
        assertFalse(offer.autoQuoted)
        assertEquals(780, offer.amount)
        assertEquals(OrderStatus.ACCEPTED, book.select(selection(request, offer), now + 1000).status)
    }
    @Test fun ownerOfferCannotExceedCustomerBudget() = runTest {
        val book = OrderBook(profile().copy(discoveryMode = DiscoveryMode.OPEN))
        val request = request().copy(item = "brownies", budgetMax = 750)
        assertTrue(book.quote(request, now).first is Decision.NeedsOwner)
        assertTrue(runCatching { book.quoteByOwner(request, 751, now + 3 * 3_600_000, now) }.isFailure)
    }
}
