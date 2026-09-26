package `in`.nukkad.engine

import `in`.nukkad.model.*

data class RankedOffer(val offer: Offer, val reason: String)

object Ranker {
    fun rank(request: Request, offers: List<Offer>, nowEpoch: Long): List<RankedOffer> {
        val valid = offers.filter {
            it.expiresAtEpoch > nowEpoch && it.requestId == request.requestId && it.amount > 0 && it.readyByEpoch >= nowEpoch &&
                (request.budgetMax == null || it.amount <= request.budgetMax) &&
                (request.deadlineEpoch == null || it.readyByEpoch <= request.deadlineEpoch) &&
                it.fulfilledConstraints.map(::key).containsAll(request.constraints.map(::key))
        }.distinctBy { it.offerId }.sortedWith(compareBy<Offer> { it.amount }.thenBy { it.readyByEpoch }.thenBy { it.offerId })
        val fastest = valid.minOfOrNull { it.readyByEpoch }
        return valid.mapIndexed { index, offer -> RankedOffer(offer, when {
            index == 0 -> "Cheapest"
            offer.readyByEpoch == fastest -> "Fastest"
            else -> "Meets all needs"
        }) }
    }
}

