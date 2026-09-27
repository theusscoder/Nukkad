package `in`.nukkad.product

import `in`.nukkad.model.Item

/** Untrusted candidate values extracted locally from a merchant transcript. */
data class ShopSetupDraft(
    val transcript: String,
    val itemName: String = "",
    val unit: String = "kg",
    val pricePerUnit: Int? = null,
    val maxQuantity: Double = 5.0,
    val optionName: String = "",
    val optionSurcharge: Int? = null,
    val dailyCapacity: Int? = null,
    val warnings: List<String> = emptyList()
) {
    fun validatedItem(): Item? {
        val price = pricePerUnit ?: return null
        if (itemName.isBlank() || unit.isBlank() || price <= 0 || !maxQuantity.isFinite() || maxQuantity <= 0) return null
        val extras = if (optionName.isBlank() || optionSurcharge == null) emptyMap()
            else if (optionSurcharge < 0) return null else mapOf(optionName.trim().lowercase() to optionSurcharge)
        return Item(itemName.trim(), unit.trim(), price, maxQuantity, extras)
    }
}

/** Conservative local extraction. Every value remains editable and requires merchant confirmation. */
object MerchantSetupParser {
    fun parse(transcript: String): ShopSetupDraft {
        val text = normalizeNumberWords(transcript.lowercase())
        val money = Regex("(?:₹|rs\\.?\\s*|rupees?\\s*|rupaye\\s*)?(\\d{1,6})(?:\\s*(?:rupees?|rupaye|rs))?").findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val price = money.firstOrNull { it > 0 }
        val option = listOf("eggless", "vegan", "veg", "urgent").firstOrNull(text::contains).orEmpty()
        val extra = if (option.isNotBlank()) {
            Regex("$option[^0-9]{0,24}(\\d{1,5})\\s*(?:extra|rupees?|rupaye|rs)?")
                .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it != price }
        } else null
        val item = listOf("chocolate cake", "black forest cake", "vanilla cake", "birthday cake", "cake", "cookies", "bread", "brownie", "dolo 650", "cupboard hinge repair", "cabinet hinge", "table repair")
            .firstOrNull(text::contains).orEmpty()
        val unit = when {
            Regex("\\b(kg|kilo|kilos|kilogram)\\b").containsMatchIn(text) -> "kg"
            Regex("\\b(piece|pieces|pc)\\b").containsMatchIn(text) -> "piece"
            Regex("\\b(strip|strips|tablet|tablets)\\b").containsMatchIn(text) -> "strip"
            item.contains("repair") -> "service"
            else -> "kg"
        }
        val capacity = Regex("(?:capacity|daily|per day|din mein)\\s*(?:of\\s*)?(\\d{1,3})|\\b(\\d{1,3})\\s*(?:orders|items|cakes)\\s*(?:a day|per day)")
            .find(text)?.let { it.groupValues.drop(1).firstNotNullOfOrNull(String::toIntOrNull) }
        val warnings = buildList {
            if (item.isBlank()) add("Check or enter the item name")
            if (price == null) add("Add the selling price")
            if (option.isNotBlank() && extra == null) add("Check the extra charge for $option")
            if (capacity == null) add("Daily capacity was not clear; you can set it in shop rules")
        }
        return ShopSetupDraft(transcript, item, unit, price, optionName = option, optionSurcharge = extra,
            dailyCapacity = capacity, warnings = warnings)
    }

    fun parseCatalogue(text: String): List<Item> {
        val result = mutableListOf<Item>()
        val pricePattern = Regex("(?:₹|rs\\.?\\s*)?(\\d{1,6})(?:\\s*(?:rupees?|rupaye))?\\s*(?:/\\s*|per\\s*)?(kg|kilo|kilogram|piece|pieces|pc|strip|service)?", RegexOption.IGNORE_CASE)
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isBlank()) continue
            val option = Regex("(eggless|vegan|veg|urgent)\\s*\\+\\s*(?:₹|rs\\.?\\s*)?(\\d{1,5})", RegexOption.IGNORE_CASE).find(line)
            if (option != null && result.isNotEmpty()) {
                val surcharge = option.groupValues[2].toInt()
                for (index in result.indices) {
                    val current = result[index]
                    result[index] = current.copy(constraintSurcharges = current.constraintSurcharges + (option.groupValues[1].lowercase() to surcharge))
                }
                continue
            }
            val match = pricePattern.find(line) ?: continue
            val price = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: continue
            val name = line.substring(0, match.range.first).trim().trim('-', ':', '·', ' ')
                .removeSuffix("-").trim()
            if (name.isBlank() || name.length > 60) continue
            val unit = when (match.groupValues[2].lowercase()) {
                "kg", "kilo", "kilogram" -> "kg"
                "strip" -> "strip"
                "service" -> "service"
                else -> "piece"
            }
            if (result.none { it.name.equals(name, true) }) result += Item(name, unit, price, 5.0)
        }
        return result
    }

    private fun normalizeNumberWords(input: String): String {
        var text = input.replace(Regex("\\b(seven hundred|seven-hundred)\\b"), "700")
            .replace(Regex("\\bsix hundred\\b"), "600")
            .replace(Regex("\\beight hundred\\b"), "800")
            .replace(Regex("\\bfive hundred\\b"), "500")
            .replace(Regex("\\bpaanch\\b"), "5")
            .replace(Regex("\\bpaanch\\b"), "5")
            .replace(Regex("\\bpachaas\\b"), "50")
            .replace(Regex("\\bsattar\\b"), "70")
        return text
    }
}
