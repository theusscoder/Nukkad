package `in`.nukkad.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.nukkad.HarnessField
import `in`.nukkad.model.*
import `in`.nukkad.viewmodel.*

@Composable fun MerchantHome(store: ProductStore, id: String, runtime: HarnessSession?, online: Boolean, busy: Boolean, reconnect: () -> Unit) {
    var profile by remember(id) { mutableStateOf(store.profile(id)) }
    var tab by remember { mutableStateOf("Home") }
    var notice by remember { mutableStateOf("") }
    fun save(value: SellerProfile) { store.saveProfile(value); profile = value; notice = "Saved. Reconnecting your agent…"; reconnect() }
    Text(profile.shopName, style = MaterialTheme.typography.headlineLarge)
    Text(if (online) "● Agent live · ${profile.category}" else "○ Agent offline", color = MaterialTheme.colorScheme.secondary)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Home", "Catalogue", "Rules", "Orders", "Profile").forEach { label ->
            FilterChip(tab == label, { tab = label }, label = { Text(label) })
        }
    }
    if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall)
    when(tab) {
        "Home" -> {
            runtime?.let { active ->
                val requests by active.seller.requestCount.collectAsStateWithLifecycle()
                val quotes by active.seller.quoteCount.collectAsStateWithLifecycle()
                val orders by active.seller.orders.collectAsStateWithLifecycle()
                SurfaceCard {
                    Text("SINCE CONNECTING", style = MaterialTheme.typography.labelLarge)
                    Text("$requests requests · $quotes quotes", style = MaterialTheme.typography.titleLarge)
                    Text("${orders.count { it.status == OrderStatus.ACCEPTED }} active orders")
                }
                val state by active.seller.state.collectAsStateWithLifecycle()
                IncomingCard(state)
            }
            Text("Your catalogue", style = MaterialTheme.typography.titleLarge)
            profile.items.forEach { item ->
                SurfaceCard {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text("₹${item.pricePerUnit} / ${item.unit}")
                    Text(if (profile.rules.autoQuoteEnabled) "Automatic quotes enabled" else "Owner review enabled")
                }
            }
            Button(onClick = { tab = "Catalogue" }) { Text("Manage catalogue") }
        }
        "Profile" -> {
            SurfaceCard {
                Text("Your shop. Your rules.", style = MaterialTheme.typography.titleLarge)
                Text("Keep your agent open to receive requests. Prices and automatic quotes follow your saved catalogue.")
                var name by remember(profile) { mutableStateOf(profile.shopName) }
                var upi by remember(profile) { mutableStateOf(profile.upiId) }
                var domain by remember(profile) { mutableStateOf(CommerceDomain.from(profile.category)) }
                HarnessField("Shop name", name, { name = it })
                HarnessField("UPI ID · optional", upi, { upi = it })
                Text("Your business category")
                listOf(CommerceDomain.BAKERY, CommerceDomain.PHARMACY, CommerceDomain.CARPENTRY).forEach { choice ->
                    FilterChip(domain == choice, { domain = choice }, label = { Text(choice.label()) })
                }
                Text("Changing category clears the old catalogue. Add items for your new business.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { save(profile.copy(shopName = name.trim(), upiId = upi.trim(), category = domain.name.lowercase(),
                    domains = setOf(domain), items = if (domain != CommerceDomain.from(profile.category)) emptyList() else profile.items)) },
                    enabled = !busy && name.isNotBlank()) { Text("Save shop") }
            }
            runtime?.let { active ->
                val state by active.seller.state.collectAsStateWithLifecycle()
                IncomingCard(state)
            }
        }
        "Catalogue" -> {
            Text("Made by you.", style = MaterialTheme.typography.headlineMedium)
            var editing by remember { mutableIntStateOf(-2) }
            profile.items.forEachIndexed { index, item ->
                SurfaceCard {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text("₹${item.pricePerUnit} / ${item.unit}", style = MaterialTheme.typography.headlineMedium)
                    Text("Up to ${item.maxQuantity} ${item.unit} per request")
                    item.constraintSurcharges.forEach { (option, price) -> Text("$option +₹$price") }
                    Row {
                        TextButton(onClick = { editing = index }, enabled = !busy) { Text("Edit item") }
                        TextButton(onClick = { save(profile.copy(items = profile.items.filterIndexed { i, _ -> i != index })) }, enabled = !busy) { Text("Remove") }
                    }
                }
            }
            OutlinedButton(onClick = { editing = -1 }) { Text("+ Add item") }
            if (editing >= -1) {
                ItemEditor(profile.items.getOrNull(editing), busy, onCancel = { editing = -2 }) { item ->
                    val items = profile.items.toMutableList()
                    if (editing == -1) items.add(item) else items[editing] = item
                    save(profile.copy(items = items)); editing = -2
                }
            }
        }
        "Rules" -> RuleEditor(profile.rules, busy) { save(profile.copy(rules = it)) }
        "Orders" -> {
            Text("Your order book.", style = MaterialTheme.typography.headlineMedium)
            runtime?.let {
                val orders by it.seller.orders.collectAsStateWithLifecycle()
                if (orders.isEmpty()) Text("Accepted orders will appear here.")
                orders.reversed().forEach { order ->
                    SurfaceCard {
                        Text(order.status.name, color = MaterialTheme.colorScheme.secondary)
                        Text("₹${order.amount}", style = MaterialTheme.typography.displaySmall)
                        Text(order.reason)
                    }
                }
            }
        }
    }
}

@Composable private fun IncomingCard(state: SellerState) {
    SurfaceCard {
        if (state is SellerState.Evaluated) {
            Text("LATEST REQUEST", color = MaterialTheme.colorScheme.secondary)
            Text(state.request.item, style = MaterialTheme.typography.headlineMedium)
            Text("${state.request.quantity ?: "Confirm quantity"} ${state.request.unit.orEmpty()} · ${state.request.constraints.joinToString()}")
            Text(state.request.budgetMax?.let { "Budget ₹$it" } ?: "Budget to discuss")
            state.decision.checks.forEach { check ->
                Text("${if (check.passed) "✓" else "–"} ${check.name}", color = if (check.passed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
            }
            when(val decision = state.decision) {
                is Decision.AutoQuote -> {
                    Text(if (state.quoteSent) "AUTO-QUOTED" else "SENDING QUOTE", color = MaterialTheme.colorScheme.secondary)
                    Text("₹${decision.amount}", style = MaterialTheme.typography.displaySmall)
                }
                is Decision.NeedsOwner -> { Text("Needs your attention", style = MaterialTheme.typography.titleLarge); Text(decision.reason) }
                is Decision.NoMatch -> Text("Request doesn't match your shop rules. No offer sent.")
            }
        } else {
            Text("Ready for the next request.", style = MaterialTheme.typography.titleLarge)
            Text("Matching customer requests will appear here.")
        }
    }
}
@Composable private fun ItemEditor(original: Item?, busy: Boolean, onCancel: () -> Unit, save: (Item) -> Unit) {
    key(original) {
        var name by remember { mutableStateOf(original?.name ?: "") }
        var price by remember { mutableStateOf(original?.pricePerUnit?.toString() ?: "") }
        var unit by remember { mutableStateOf(original?.unit ?: "kg") }
        var max by remember { mutableStateOf(original?.maxQuantity?.toString() ?: "5") }
        var options by remember { mutableStateOf(original?.constraintSurcharges?.entries?.joinToString(", ") { "${it.key}:${it.value}" } ?: "") }
        val pairs = options.split(',').filter { it.isNotBlank() }.map { it.trim().split(':') }
        val optionsValid = pairs.all { it.size == 2 && it[0].isNotBlank() && (it[1].trim().toIntOrNull() ?: -1) >= 0 }
        val valid = name.isNotBlank() && unit.isNotBlank() && (price.toIntOrNull() ?: 0) > 0 &&
            max.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true && optionsValid
        SurfaceCard {
            Text(if (original == null) "Add to your catalogue" else "Edit item", style = MaterialTheme.typography.titleLarge)
            HarnessField("Item name", name, { name = it })
            HarnessField("Price per unit · ₹", price, { price = it }, KeyboardType.Number)
            HarnessField("Unit", unit, { unit = it })
            HarnessField("Maximum quantity per order", max, { max = it }, KeyboardType.Decimal)
            HarnessField("Options · eggless:50, vegan:100", options, { options = it })
            if (!optionsValid) Text("Use option:price, with a non-negative whole rupee price.")
            Button(onClick = { save(Item(name.trim(), unit.trim(), price.toInt(), max.toDouble(),
                pairs.associate { it[0].trim() to it[1].trim().toInt() })) }, enabled = valid && !busy) { Text("Save item") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
@Composable private fun RuleEditor(original: MerchantRules, busy: Boolean, save: (MerchantRules) -> Unit) {
    var floor by remember(original) { mutableStateOf(original.minimumPrice.toString()) }
    var lead by remember(original) { mutableStateOf(original.leadTimeMinutes.toString()) }
    var capacity by remember(original) { mutableStateOf(original.maxDailyOrders.toString()) }
    var open by remember(original) { mutableStateOf(original.openingHour.toString()) }
    var close by remember(original) { mutableStateOf(original.closingHour.toString()) }
    var auto by remember(original) { mutableStateOf(original.autoQuoteEnabled) }
    var automations by remember(original) { mutableStateOf(original.automations) }
    var ruleItem by remember { mutableStateOf("") }
    var ruleMinimum by remember { mutableStateOf("1000") }
    var ruleAction by remember { mutableStateOf(AutomationAction.AUTO_QUOTE) }
    val valid = (floor.toIntOrNull() ?: -1) >= 0 && (lead.toLongOrNull() ?: -1) in 0..10080 &&
        (capacity.toIntOrNull() ?: 0) in 1..20 && (open.toIntOrNull() ?: -1) in 0..23 &&
        (close.toIntOrNull() ?: 0) in 1..24 && (open.toIntOrNull() ?: 99) < (close.toIntOrNull() ?: 0)
    SurfaceCard {
        Text("Your agent's rules.", style = MaterialTheme.typography.headlineMedium)
        Text("IF the item and options match, the price fits the budget, capacity is available and the deadline is achievable:")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (auto) "Auto quote" else "Ask owner")
            Switch(auto, { auto = it })
        }
        Text("Auto quote sends an offer. Auto accept pre-authorizes the order when the customer selects it; capacity is reserved only then.")
        Text("Item automations · first matching rule wins", style = MaterialTheme.typography.titleMedium)
        automations.forEachIndexed { index, rule ->
            Text("${rule.itemName} · order ≥ ₹${rule.minimumOrderValue} → ${rule.action.name.replace('_', ' ')}")
            TextButton(onClick = { automations = automations.filterIndexed { i, _ -> i != index } }) { Text("Remove rule") }
        }
        HarnessField("Rule item · exact catalogue name", ruleItem, { ruleItem = it })
        HarnessField("Calculated order value at least · ₹", ruleMinimum, { ruleMinimum = it }, KeyboardType.Number)
        AutomationAction.entries.forEach { action ->
            FilterChip(ruleAction == action, { ruleAction = action }, label = { Text(action.name.replace('_', ' ')) })
        }
        OutlinedButton(onClick = {
            automations = automations + AutomationRule(ruleItem.trim(), ruleMinimum.toInt(), ruleAction)
            ruleItem = ""
        }, enabled = ruleItem.isNotBlank() && (ruleMinimum.toIntOrNull() ?: -1) >= 0) { Text("Add rule to draft") }
        Text("Save automation below to apply these rules. If item rules exist and none match, the request needs owner review.")
        HarnessField("Minimum order price · ₹", floor, { floor = it }, KeyboardType.Number)
        HarnessField("Preparation time · minutes", lead, { lead = it }, KeyboardType.Number)
        HarnessField("Orders per day", capacity, { capacity = it }, KeyboardType.Number)
        HarnessField("Opens · hour 0–23", open, { open = it }, KeyboardType.Number)
        HarnessField("Closes · hour 1–24", close, { close = it }, KeyboardType.Number)
        Button(onClick = { save(original.copy(minimumPrice = floor.toInt(), leadTimeMinutes = lead.toLong(),
            maxDailyOrders = capacity.toInt(), autoQuoteEnabled = auto, openingHour = open.toInt(), closingHour = close.toInt(), automations = automations)) },
            enabled = valid && !busy) { Text("Save automation") }
    }
}



