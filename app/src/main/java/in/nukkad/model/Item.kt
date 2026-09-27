package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val name: String,
    val unit: String,
    val pricePerUnit: Int,
    val maxQuantity: Double,
    /** Fixed per-order surcharge, including zero for constraints supported free. */
    val constraintSurcharges: Map<String, Int> = emptyMap(),
    /** Explicit merchant-approved alternate names; used only in FLEX mode. */
    val aliases: List<String> = emptyList()
)
