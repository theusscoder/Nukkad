package `in`.nukkad

import `in`.nukkad.debug.Seeds
import `in`.nukkad.engine.*
import `in`.nukkad.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class ShopAgentTest {
    private val now = ZonedDateTime.parse("2026-09-26T10:00:00+05:30[Asia/Kolkata]").toInstant().toEpochMilli()
    private val seller = Seeds.seller(newId())
    private val request = Request(newId(), newId(), "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 24 * 3_600_000, listOf("eggless"))
    private val agent = ShopAgent()
    private fun decide(r: Request = request, s: SellerProfile = seller, capacity: Int = 0, time: Long = now) = agent.decide(r, s, MerchantState(capacity), time)

    @Test fun seedQuotes750WithRealChecks() {
        val result = decide() as Decision.AutoQuote
        assertEquals(750, result.amount)
        assertEquals(now + 3 * 3_600_000, result.readyByEpoch)
        assertTrue(result.checks.all { it.passed })
    }
    @Test fun differentPhonesHaveIndependentPrices() {
        assertEquals(780, (decide(s = Seeds.seller(newId(), 1)) as Decision.AutoQuote).amount)
        assertTrue(decide(s = Seeds.seller(newId(), 2)) is Decision.NoMatch)
    }
    @Test fun rejectsInsufficientBudget() { assertTrue(decide(request.copy(budgetMax = 749)) is Decision.NoMatch) }
    @Test fun budgetBoundaryPasses() { assertTrue(decide(request.copy(budgetMax = 750)) is Decision.AutoQuote) }
    @Test fun merchantFloorNeedsOwnerAndNeverInventsDiscount() {
        val result = decide(s = seller.copy(rules = seller.rules.copy(minimumPrice = 760))) as Decision.NeedsOwner
        assertEquals(750, result.suggestedAmount)
    }
    @Test fun fullCapacityRejects() { assertTrue(decide(capacity = 3) is Decision.NoMatch) }
    @Test fun shortDeadlineRejects() { assertTrue(decide(request.copy(deadlineEpoch = now + 3_600_000)) is Decision.NoMatch) }
    @Test fun exactDeadlinePasses() { assertTrue(decide(request.copy(deadlineEpoch = now + 3 * 3_600_000)) is Decision.AutoQuote) }
    @Test fun unknownConstraintNeedsOwner() { assertTrue(decide(request.copy(constraints = listOf("nut-free"))) is Decision.NeedsOwner) }
    @Test fun duplicateConstraintChargedOnce() { assertEquals(750, (decide(request.copy(constraints = listOf("eggless", " EGGLESS "))) as Decision.AutoQuote).amount) }
    @Test fun missingNumbersNeedsOwner() { assertTrue(decide(request.copy(budgetMax = null)) is Decision.NeedsOwner) }
    @Test fun invalidQuantitiesNeverQuote() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 6.0).forEach { assertTrue(decide(request.copy(quantity = it)) is Decision.NoMatch) }
    }
    @Test fun wrongUnitOrRoutingNeverQuotes() {
        assertTrue(decide(request.copy(unit = "pieces")) is Decision.NoMatch)
        assertTrue(decide(request.copy(area = "other")) is Decision.NoMatch)
        assertTrue(decide(request.copy(item = "brownie")) is Decision.NoMatch)
    }
    @Test fun disabledAutoQuoteNeedsOwner() { assertTrue(decide(s = seller.copy(rules = seller.rules.copy(autoQuoteEnabled = false))) is Decision.NeedsOwner) }
    @Test fun fractionalPriceRoundsUp() {
        assertEquals(284, (decide(request.copy(quantity = 0.333, constraints = emptyList()), seller.copy(items = listOf(Item("chocolate cake", "kg", 851, 5.0)), rules = seller.rules.copy(minimumPrice = 0))) as Decision.AutoQuote).amount)
    }
    @Test fun overflowIsRejected() {
        assertTrue(decide(request.copy(quantity = 2.0), seller.copy(items = seller.items.map { it.copy(pricePerUnit = Int.MAX_VALUE) })) is Decision.NoMatch)
    }
    @Test fun lateRequestStartsAtNextOpening() {
        val evening = now + 11 * 3_600_000
        val result = decide(request.copy(deadlineEpoch = now + 48 * 3_600_000), time = evening) as Decision.AutoQuote
        assertEquals(now + 25 * 3_600_000, result.readyByEpoch) // tomorrow 11 AM
    }
    @Test fun impossibleTradingWindowRejects() { assertTrue(decide(s = seller.copy(rules = seller.rules.copy(leadTimeMinutes = 900))) is Decision.NoMatch) }
    @Test fun invalidMerchantSettingsReject() { assertTrue(decide(s = seller.copy(rules = seller.rules.copy(minimumPrice = -1))) is Decision.NoMatch) }
}

