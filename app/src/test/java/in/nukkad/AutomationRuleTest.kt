package `in`.nukkad

import `in`.nukkad.debug.Seeds
import `in`.nukkad.engine.ShopAgent
import `in`.nukkad.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class AutomationRuleTest {
    private val now = ZonedDateTime.parse("2026-09-26T10:00:00+05:30[Asia/Kolkata]").toInstant().toEpochMilli()
    private val base = Seeds.seller(newId())
    private val request = Request(newId(), newId(), "kondapur", "bakery", "chocolate cake", 1.0, "kg", 1500, now + 86400000, listOf("eggless"))
    private fun decide(action: AutomationAction, threshold: Int = 750, capacity: Int = 0): Decision {
        val seller = base.copy(rules = base.rules.copy(automations = listOf(AutomationRule("chocolate cake", threshold, action))))
        return ShopAgent().decide(request, seller, MerchantState(capacity), now)
    }
    @Test fun thresholdUsesCalculatedPriceRatherThanCustomerBudget() {
        assertTrue(decide(AutomationAction.AUTO_QUOTE, 1000) is Decision.NeedsOwner)
        assertEquals(750, (decide(AutomationAction.AUTO_QUOTE) as Decision.AutoQuote).amount)
    }
    @Test fun ownerAndNoMatchNeverPublishAutomaticQuote() {
        assertTrue(decide(AutomationAction.ASK_OWNER) is Decision.NeedsOwner)
        assertTrue(decide(AutomationAction.NO_MATCH) is Decision.NoMatch)
    }
    @Test fun autoAcceptanceCannotBypassCapacityChecks() {
        assertTrue(decide(AutomationAction.AUTO_ACCEPT, capacity = 3) is Decision.NoMatch)
    }
}
