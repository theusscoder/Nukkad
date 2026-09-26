package `in`.nukkad.debug

import `in`.nukkad.model.*

object Seeds {
    fun seller(id: String, variant: Int = 0): SellerProfile = SellerProfile(
        sellerId = id,
        shopName = listOf("Sweet Crumbs", "HomeBake", "Cake House")[variant],
        area = "kondapur", category = "bakery", upiId = "",
        items = listOf(Item("chocolate cake", "kg", listOf(700, 730, 850)[variant], 5.0, mapOf("eggless" to 50))),
        rules = MerchantRules(minimumPrice = 700, leadTimeMinutes = if (variant == 1) 120 else 180, maxDailyOrders = 3),
        domains = setOf(CommerceDomain.BAKERY)
    )
}




