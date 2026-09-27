package `in`.nukkad.ai

import `in`.nukkad.model.CommerceDomain

/** Candidate fields produced by an interpreter. It is never an authorized request. */
data class IntentDraft(
    val domain: CommerceDomain = CommerceDomain.OTHER,
    val item: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val budgetMax: Int? = null,
    val deadlineEpoch: Long? = null,
    val constraints: List<String> = emptyList(),
    val originalTranscript: String,
    val normalizedText: String? = null,
    val needsConfirmation: Boolean = true,
    val warnings: List<String> = emptyList(),
    val confidence: Double? = null,
    val deadlineText: String? = null,
    val interpreter: String = "Deterministic fallback"
)

interface LlmEngine {
    suspend fun extract(transcript: String, nowEpoch: Long, languageTag: String = "en-IN"): IntentDraft
    val name: String
}

/** M5 safety fallback. Money/quantity are accepted only when explicit in the transcript. */
class DeterministicIntentParser : LlmEngine {
    override val name = "Deterministic fallback"
    override suspend fun extract(transcript: String, nowEpoch: Long, languageTag: String): IntentDraft {
        val lower = transcript.trim().lowercase()
        val domain = when {
            listOf("cake", "bakery", "bread", "pastry").any(lower::contains) -> CommerceDomain.BAKERY
            listOf("dolo", "medicine", "tablet", "pharmacy").any(lower::contains) -> CommerceDomain.PHARMACY
            listOf("carpenter", "cupboard", "door repair").any(lower::contains) -> CommerceDomain.CARPENTRY

            else -> CommerceDomain.OTHER
        }
        val quantity = Regex("(?:^|\\s)(\\d+(?:[.,]\\d+)?)\\s*(?:kg|kilo|kilos|kilogram)").find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val budget = Regex("(?:₹|rs\\.?\\s*|rupees?\\s*)(\\d{2,6})").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        val constraints = listOf("eggless", "veg", "vegan", "urgent").filter(lower::contains)
        val item = when (domain) {
            CommerceDomain.BAKERY -> when { lower.contains("chocolate cake") -> "chocolate cake"; lower.contains("cake") -> "cake"; lower.contains("bread") -> "bread"; else -> "" }
            CommerceDomain.PHARMACY -> if (lower.contains("dolo")) "Dolo 650".takeIf { lower.contains("650") } ?: "Dolo" else ""
            CommerceDomain.CARPENTRY -> if (lower.contains("hinge")) "cupboard hinge repair" else ""
            else -> ""
        }
        val warnings = buildList {
            if (domain == CommerceDomain.OTHER) add("Choose a supported commerce category")
            if (item.isBlank()) add("Confirm the exact item")
            if (quantity == null) add("Confirm quantity and unit")
            if (budget == null) add("Confirm maximum budget")
        }
        return IntentDraft(domain, item, quantity, if (quantity != null) "kg" else null, budget, null, constraints, transcript, transcript, true, warnings)
    }
}

object IntentValidator {
    fun validate(draft: IntentDraft): List<String> = buildList {
        if (draft.domain == CommerceDomain.OTHER) add("Unsupported or ambiguous category")
        if (draft.item.isBlank()) add("Item is missing")
        if (draft.quantity == null || !draft.quantity.isFinite() || draft.quantity <= 0) add("Quantity must be positive")
        if (draft.budgetMax != null && draft.budgetMax <= 0) add("Budget must be positive")
        if (draft.deadlineEpoch != null && draft.deadlineEpoch <= System.currentTimeMillis()) add("Deadline must be in the future")
    }
}



