package `in`.nukkad

import `in`.nukkad.ai.DeterministicIntentParser
import `in`.nukkad.model.CommerceDomain
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class IntentParserTest {
    @Test fun parsesHindiOrEnglishLikeBakeryTextWithoutNetwork() = runTest {
        val draft = DeterministicIntentParser().extract("1 kg eggless cake under Rs 800", System.currentTimeMillis())
        assertEquals(CommerceDomain.BAKERY, draft.domain)
        assertEquals(1.0, draft.quantity)
        assertEquals(800, draft.budgetMax)
        assertTrue("eggless" in draft.constraints)
        assertTrue(draft.needsConfirmation)
    }
}

