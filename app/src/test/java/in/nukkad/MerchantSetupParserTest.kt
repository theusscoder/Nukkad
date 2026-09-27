package `in`.nukkad

import `in`.nukkad.product.MerchantSetupParser
import org.junit.Assert.*
import org.junit.Test

class MerchantSetupParserTest {
    @Test fun extractsExplicitCakePriceOptionAndCapacity() {
        val draft = MerchantSetupParser.parse("Chocolate cake 700 rupees per kilo, eggless 50 extra, 5 cakes per day")
        assertEquals("chocolate cake", draft.itemName)
        assertEquals(700, draft.pricePerUnit)
        assertEquals("kg", draft.unit)
        assertEquals("eggless", draft.optionName)
        assertEquals(50, draft.optionSurcharge)
        assertEquals(5, draft.dailyCapacity)
        assertNotNull(draft.validatedItem())
    }

    @Test fun missingPriceNeverCreatesAnItem() {
        val draft = MerchantSetupParser.parse("I make chocolate cake")
        assertNull(draft.pricePerUnit)
        assertNull(draft.validatedItem())
        assertTrue(draft.warnings.isNotEmpty())
    }

    @Test fun printedMenuLinesBecomeEditableCatalogueCandidates() {
        val items = MerchantSetupParser.parseCatalogue("Chocolate Cake - 700/kg\nVanilla Cake - 650/kg\nEggless +50\nBrownie - 80 pc")
        assertEquals(3, items.size)
        assertEquals("Chocolate Cake", items[0].name)
        assertEquals(700, items[0].pricePerUnit)
        assertEquals(50, items[0].constraintSurcharges["eggless"])
        assertEquals("piece", items[2].unit)
    }
}
