package `in`.nukkad.model

sealed interface Decision {
    val checks: List<Check>
    data class AutoQuote(val amount: Int, val readyByEpoch: Long, override val checks: List<Check>) : Decision
    data class NeedsOwner(val reason: String, val suggestedAmount: Int?, override val checks: List<Check>) : Decision
    data class NoMatch(override val checks: List<Check>) : Decision
}
