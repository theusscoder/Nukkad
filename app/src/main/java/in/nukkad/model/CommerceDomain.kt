package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable
enum class CommerceDomain {
    BAKERY, PHARMACY, CARPENTRY, OTHER;

    companion object {
        fun from(value: String): CommerceDomain = when (value.trim().lowercase()) {
            "bakery", "baker", "cake", "food" -> BAKERY
            "medicine", "medicines", "pharmacy", "medical" -> PHARMACY
            "carpentry", "carpenter", "woodwork" -> CARPENTRY
            else -> OTHER
        }
    }
}
