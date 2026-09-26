package `in`.nukkad

import `in`.nukkad.engine.Ranker
import `in`.nukkad.model.*
import org.junit.Assert.*
import org.junit.Test

class RankerTest {
    private val now = 1_000_000L
    private val request = Request(newId(), newId(), "kondapur", "bakery", "chocolate cake", 1.0, "kg", 800, now + 10_000, listOf("eggless"))
    private fun offer(id: String = newId(), amount: Int = 750, ready: Long = now + 5_000) = Offer(id, request.requestId, newId(), "Shop", amount, ready, true, emptyList(), listOf("eggless"))
    @Test fun invalidOffersAreDroppedRatherThanRankedLow() {
        val valid = offer()
        val invalid = listOf(offer(amount = 801), offer(amount = 0), offer(ready = now - 1), offer(ready = now + 10_001), offer().copy(fulfilledConstraints = emptyList()), offer().copy(requestId = newId()))
        assertEquals(listOf(valid), Ranker.rank(request, invalid + valid, now).map { it.offer })
    }
    @Test fun deterministicPriceThenTimeThenId() {
        val a = offer("a", 750, now + 3_000)
        val b = offer("b", 750, now + 3_000)
        val c = offer("c", 780, now + 1_000)
        assertEquals(listOf(a, b, c), Ranker.rank(request, listOf(c, b, a, a), now).map { it.offer })
        assertEquals("Fastest", Ranker.rank(request, listOf(a, c), now).last().reason)
    }
}
