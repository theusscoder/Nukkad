package `in`.nukkad.model

import kotlinx.serialization.Serializable

@Serializable enum class AutomationAction { AUTO_QUOTE, AUTO_ACCEPT, ASK_OWNER, NO_MATCH }

@Serializable data class AutomationRule(
    val itemName: String,
    val minimumOrderValue: Int = 0,
    val action: AutomationAction = AutomationAction.AUTO_QUOTE
)
