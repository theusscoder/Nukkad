package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable
enum class CommerceDomain {
    BAKERY, FOOD, PHARMACY, GROCERY, TAILORING, CARPENTRY, PLUMBING, ELECTRICAL, HOME_SERVICE, OTHER;

    companion object {
        fun from(value: String): CommerceDomain = entries.firstOrNull { it.name.equals(value.trim(), true) } ?: OTHER
    }
}
