package `in`.nukkad

import `in`.nukkad.debug.Seeds
import `in`.nukkad.model.*
import `in`.nukkad.transport.*
import `in`.nukkad.viewmodel.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DomainRoutingTest {
    @Test fun bakeryRequestDoesNotReachPharmacySeller() = runTest {
        val broker = FakeBroker(); val code = newSessionCode(); val customerId = newId()
        val bakery = HarnessSession(FakeTransport(broker), code, Role.SELLER, newId(), Seeds.seller(newId()), backgroundScope)
        val pharmacy = Seeds.seller(newId()).copy(shopName = "HealthPlus", category = "pharmacy", domains = setOf(CommerceDomain.PHARMACY))
        val pharmacySession = HarnessSession(FakeTransport(broker), code, Role.SELLER, newId(), pharmacy, backgroundScope)
        val customer = HarnessSession(FakeTransport(broker), code, Role.CUSTOMER, customerId, Seeds.seller(newId()), backgroundScope)
        bakery.start(); pharmacySession.start(); customer.start()
        val now = System.currentTimeMillis()
        customer.customer.send(Request(newId(), customerId, "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 86400000, listOf("eggless")), now)
        runCurrent()
        assertTrue(bakery.seller.state.value is SellerState.Evaluated)
        assertEquals(SellerState.Live, pharmacySession.seller.state.value)
        assertEquals(CommerceDomain.BAKERY, CommerceDomain.from("bakery"))
        assertEquals(CommerceDomain.PHARMACY, CommerceDomain.from("pharmacy"))
        bakery.close(); pharmacySession.close(); customer.close()
    }
}



