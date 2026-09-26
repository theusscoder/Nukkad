package `in`.nukkad.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.nukkad.HarnessField
import `in`.nukkad.viewmodel.CustomerState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun displayTime(epoch: Long): String = DateTimeFormatter.ofPattern("dd MMM · h:mm a").withZone(ZoneId.of("Asia/Kolkata")).format(Instant.ofEpochMilli(epoch)) + " IST"

@Composable
fun DebugCustomerScreen(state: CustomerState, enabled: Boolean, send: (String, String, String, String, String) -> Unit, retry: () -> Unit, select: (String) -> Unit, cancel: () -> Unit) {
    var item by rememberSaveable { mutableStateOf("chocolate cake") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var budget by rememberSaveable { mutableStateOf("800") }
    var hours by rememberSaveable { mutableStateOf("24") }
    var constraints by rememberSaveable { mutableStateOf("eggless") }
    val locked = state is CustomerState.Selecting || state is CustomerState.Accepted
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("02 / CUSTOMER", color = MaterialTheme.colorScheme.primary)
        Text("Kondapur · Bakery", style = MaterialTheme.typography.titleLarge)
        HarnessField("Item (exact catalogue name)", item, { item = it })
        HarnessField("Quantity · kg", quantity, { quantity = it }, KeyboardType.Decimal)
        HarnessField("Maximum budget · INR", budget, { budget = it }, KeyboardType.Number)
        HarnessField("Deadline · hours from now", hours, { hours = it }, KeyboardType.Number)
        HarnessField("Constraints · comma separated", constraints, { constraints = it })
        Button(onClick = { send(item, quantity, budget, hours, constraints) }, enabled = enabled && !locked, modifier = Modifier.fillMaxWidth()) { Text("Send structured request") }
        when (state) {
            CustomerState.Idle -> Text("Connect, then send a request to the seller agent.")
            is CustomerState.Searching -> {
                Text("Request sent · waiting for valid offers", style = MaterialTheme.typography.titleMedium)
                Text(state.request.requestId, style = MaterialTheme.typography.bodySmall)
                Text("A seller may decline silently in this basic protocol. Check the seller’s checklist if no offer arrives.", style = MaterialTheme.typography.bodySmall)
            }
            is CustomerState.Selecting -> {
                Text(if (state.cancelling) "Waiting for cancellation acknowledgement" else "Waiting for seller acceptance", style = MaterialTheme.typography.titleLarge)
                Text("Order ${state.selection.orderId.take(8)}. Retry uses this same order; do not select another seller while the outcome is unknown.")
                OutlinedButton(onClick = cancel, enabled = enabled && !state.cancelling) { Text("Cancel selection") }
            }
            is CustomerState.Accepted -> {
                Text("SELLER ACCEPTED · ${state.order.sellerName}", color = MaterialTheme.colorScheme.secondary)
                Text("₹${state.order.amount}", style = MaterialTheme.typography.displaySmall)
                Text("Ready ${displayTime(state.order.readyByEpoch)}")
                Text("Capacity held until ${displayTime(state.order.holdUntilEpoch)}. Payment is not included in M2.")
                Text(if (state.pendingCloses == 0) "Other offers closed" else "Waiting for ${state.pendingCloses} seller closure acknowledgement(s)")
                OutlinedButton(onClick = cancel, enabled = enabled) { Text("Cancel order · release capacity") }
            }
            is CustomerState.Finished -> {
                Text("${state.order.status}: ${state.order.reason}")
                Text("Send a new request when you are ready.")
            }
            is CustomerState.Quotes -> {
                Text("${state.offers.size} VALID OFFER(S)", color = MaterialTheme.colorScheme.secondary)
                state.offers.forEach { ranked ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ranked.offer.sellerName, style = MaterialTheme.typography.titleLarge)
                        Text("₹${ranked.offer.amount}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("Ready ${displayTime(ranked.offer.readyByEpoch)}")
                        Text("Quote expires ${displayTime(ranked.offer.expiresAtEpoch)}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { select(ranked.offer.offerId) }, enabled = enabled) { Text("Select this offer") }
                        Text(ranked.reason, color = MaterialTheme.colorScheme.secondary)
                        Text("AutoQuote · ${ranked.offer.offerId.take(8)}", style = MaterialTheme.typography.labelSmall)
                    } }
                }
            }
        }
        if (state != CustomerState.Idle && state !is CustomerState.Finished) OutlinedButton(onClick = retry, enabled = enabled) { Text(if (locked) "Retry / refresh order status" else "Retry same request") }
    }
}

