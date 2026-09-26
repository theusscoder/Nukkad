package `in`.nukkad.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.nukkad.model.*
import `in`.nukkad.viewmodel.SellerState

@Composable
fun DebugSellerScreen(profile: SellerProfile, state: SellerState, orders: List<Order>, orderEvent: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("03 / SELLER AGENT", color = MaterialTheme.colorScheme.primary)
        Text(profile.shopName, style = MaterialTheme.typography.headlineMedium)
        Text("${profile.area} · ${profile.category} · ${profile.rules.leadTimeMinutes} min lead time")
        Text("Floor ₹${profile.rules.minimumPrice} · ${profile.rules.maxDailyOrders} orders/day · ${profile.rules.openingHour}:00–${profile.rules.closingHour}:00 IST", style = MaterialTheme.typography.bodySmall)
        Text(orderEvent, color = MaterialTheme.colorScheme.primary)
        orders.takeLast(5).reversed().forEach { order ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${order.status} · ${order.selection.orderId.take(8)}", style = MaterialTheme.typography.titleMedium)
                Text("₹${order.amount} · ${order.reason}")
                if (order.status == OrderStatus.ACCEPTED) Text("Capacity held until ${displayTime(order.holdUntilEpoch)}")
            } }
        }
        when (state) {
            SellerState.Live -> Text("Waiting for a request. Evaluation and quoting are automatic.")
            is SellerState.Evaluated -> {
                HorizontalDivider()
                Text("${state.request.quantity} ${state.request.unit} ${state.request.item}", style = MaterialTheme.typography.titleLarge)
                Text("Budget ₹${state.request.budgetMax} · ${state.request.constraints.joinToString()}")
                state.request.deadlineEpoch?.let { Text("Deadline ${displayTime(it)}") }
                Text("ACTUAL SHOPAGENT CHECKS", style = MaterialTheme.typography.labelMedium)
                state.decision.checks.forEach { check ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (check.passed) "✓" else "×", color = if (check.passed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                        Column { Text(check.name, fontWeight = FontWeight.SemiBold); Text(check.detail, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                when (val result = state.decision) {
                    is Decision.AutoQuote -> {
                        Text(if (state.quoteSent) "AUTO-QUOTED" else "QUOTE READY · sending", color = MaterialTheme.colorScheme.secondary)
                        Text("₹${result.amount}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    }
                    is Decision.NeedsOwner -> { Text("NEEDS OWNER", color = MaterialTheme.colorScheme.primary); Text(result.reason) }
                    is Decision.NoMatch -> Text("NO MATCH · no offer sent", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

