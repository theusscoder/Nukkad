package `in`.nukkad.engine

import `in`.nukkad.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/** Pure Kotlin/JVM. The caller supplies time and local capacity for deterministic evaluation. */
class ShopAgent {
    fun decide(request: Request, seller: SellerProfile, state: MerchantState, nowEpoch: Long): Decision {
        val checks = mutableListOf<Check>()
        fun check(name: String, ok: Boolean, detail: String): Boolean {
            checks += Check(name, ok, detail)
            return ok
        }
        val rules = seller.rules
        if (!check("Merchant configuration", rules.minimumPrice >= 0 && rules.leadTimeMinutes in 0..10080 &&
                rules.maxDailyOrders > 0 && state.ordersOnReadyDate >= 0 && rules.openingHour in 0..23 &&
                rules.closingHour in 1..24 && rules.openingHour < rules.closingHour,
                "Valid price floor, capacity and same-day opening hours required")) return Decision.NoMatch(checks)
        val zone = runCatching { ZoneId.of(rules.zoneId) }.getOrNull()
            ?: return Decision.NeedsOwner("Invalid shop timezone", null, checks + Check("Timezone", false, rules.zoneId))
        if (!check("Routing", key(request.area) == key(seller.area) && request.domain in seller.domains, "${seller.area} · ${seller.domains.joinToString()}")) return Decision.NoMatch(checks)
        val item = seller.items.firstOrNull { key(it.name) == key(request.item) }
        if (!check("Item available", item != null, request.item)) return Decision.NoMatch(checks)
        item!!
        val quantity = request.quantity
        if (quantity == null || request.unit == null || request.budgetMax == null || request.deadlineEpoch == null) {
            check("Complete request", false, "Quantity, unit, budget and deadline must be confirmed")
            return Decision.NeedsOwner("Missing required fields", null, checks)
        }
        if (!check("Quantity possible", quantity.isFinite() && quantity > 0 && quantity <= item.maxQuantity && key(request.unit) == key(item.unit), "$quantity ${request.unit}; max ${item.maxQuantity} ${item.unit}")) return Decision.NoMatch(checks)
        if (!check("Valid numbers", request.budgetMax > 0 && request.deadlineEpoch > nowEpoch && item.pricePerUnit > 0 && item.constraintSurcharges.values.all { it >= 0 }, "Positive money and future deadline")) return Decision.NoMatch(checks)
        val required = request.constraints.map(::key).toSet()
        val surcharges = item.constraintSurcharges.mapKeys { key(it.key) }
        if (!check("Constraints supported", required.all { it in surcharges }, if (required.isEmpty()) "No extra constraints" else required.joinToString())) {
            return Decision.NeedsOwner("Unsupported constraint requires owner review", null, checks)
        }
        // Never binary floating-point arithmetic for prices; round fractional rupees UP.
        val amount = runCatching {
            BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(item.pricePerUnit.toLong()))
                .add(required.fold(BigDecimal.ZERO) { sum, constraint -> sum + BigDecimal.valueOf(surcharges.getValue(constraint).toLong()) })
                .setScale(0, RoundingMode.CEILING).intValueExact()
        }.getOrNull()
        if (!check("Calculated price", amount != null && amount > 0, amount?.let { "₹$it" } ?: "Price outside supported range")) return Decision.NoMatch(checks)
        amount!!
        val floorOk = check("Merchant minimum", amount >= rules.minimumPrice, "₹${rules.minimumPrice}")
        val budgetOk = check("Customer budget", amount <= request.budgetMax, "₹${request.budgetMax}")
        val capacityOk = check("Capacity", state.ordersOnReadyDate < rules.maxDailyOrders, "${state.ordersOnReadyDate}/${rules.maxDailyOrders} committed orders")
        // Start at the next opening, then require the whole lead time to fit one trading day.
        var start = Instant.ofEpochMilli(nowEpoch).atZone(zone)
        val open = start.toLocalDate().atTime(rules.openingHour, 0).atZone(zone)
        val close = start.toLocalDate().atStartOfDay(zone).plusHours(rules.closingHour.toLong())
        if (start.isBefore(open)) start = open
        if (!start.isBefore(close)) start = open.plusDays(1)
        var ready = start.plusMinutes(rules.leadTimeMinutes)
        var closing = start.toLocalDate().atStartOfDay(zone).plusHours(rules.closingHour.toLong())
        if (ready.isAfter(closing)) {
            start = start.toLocalDate().plusDays(1).atTime(rules.openingHour, 0).atZone(zone)
            ready = start.plusMinutes(rules.leadTimeMinutes)
            closing = start.toLocalDate().atStartOfDay(zone).plusHours(rules.closingHour.toLong())
        }
        val hoursOk = check("Opening hours", !ready.isAfter(closing), "${rules.openingHour}:00–${rules.closingHour}:00 ${rules.zoneId}")
        val deadlineOk = check("Deadline", ready.toInstant().toEpochMilli() <= request.deadlineEpoch, "Ready $ready")
        if (!budgetOk || !capacityOk || !hoursOk || !deadlineOk) return Decision.NoMatch(checks)
        if (!floorOk || !rules.autoQuoteEnabled) return Decision.NeedsOwner(if (!floorOk) "Price below merchant floor" else "AutoQuote disabled", amount, checks)
        if (rules.automations.isNotEmpty()) {
            val automation = rules.automations.firstOrNull {
                key(it.itemName) == key(request.item) && amount >= it.minimumOrderValue
            }
            check("Item automation", automation != null, automation?.action?.name ?: "No item rule matched")
            check("Automation permits quote", automation?.action in setOf(AutomationAction.AUTO_QUOTE, AutomationAction.AUTO_ACCEPT), automation?.action?.name ?: "Owner review")
            when (automation?.action) {
                null, AutomationAction.ASK_OWNER -> return Decision.NeedsOwner("Owner review required by item rules", amount, checks)
                AutomationAction.NO_MATCH -> return Decision.NoMatch(checks)
                AutomationAction.AUTO_QUOTE, AutomationAction.AUTO_ACCEPT -> Unit
            }
        }
        // AUTO_ACCEPT authorizes acceptance on customer selection, never unsolicited reservations.
        return Decision.AutoQuote(amount, ready.toInstant().toEpochMilli(), checks)
    }
}



