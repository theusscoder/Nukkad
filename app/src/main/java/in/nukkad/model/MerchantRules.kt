package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable
data class MerchantRules(
    val minimumPrice: Int,
    val leadTimeMinutes: Long,
    val maxDailyOrders: Int,
    val autoQuoteEnabled: Boolean = true,
    val openingHour: Int = 8,
    val closingHour: Int = 22,
    val zoneId: String = "Asia/Kolkata",
    val automations: List<AutomationRule> = emptyList()
)

