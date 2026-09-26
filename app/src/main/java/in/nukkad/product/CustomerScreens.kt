package `in`.nukkad.product

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.nukkad.HarnessField
import `in`.nukkad.ai.*
import `in`.nukkad.debug.displayTime
import `in`.nukkad.model.CommerceDomain
import `in`.nukkad.viewmodel.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun CustomerHome(store: ProductStore, runtime: HarnessSession?, enabled: Boolean, vm: HarnessViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf(store.read("speech", "en-IN")) }
    var phase by remember { mutableStateOf("idle") }
    var feedback by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<IntentDraft?>(null) }
    var composing by remember { mutableStateOf(false) }
    val current = runtime?.customer?.state?.collectAsStateWithLifecycle()?.value ?: CustomerState.Idle
    if (draft == null && !composing && current != CustomerState.Idle) {
        CustomerOffers(current, enabled, vm)
        if (current !is CustomerState.Selecting && current !is CustomerState.Accepted) {
            OutlinedButton(onClick = { composing = true }) { Text("Create another request") }
        }
        return
    }
    val context = LocalContext.current
    val speech = remember { SpeechInput(context) }
    val scope = rememberCoroutineScope()
    DisposableEffect(speech) { onDispose { speech.cancel() } }
    fun interpret(input: String) {
        if (input.isBlank()) return
        phase = "processing"
        scope.launch {
            try { draft = DeterministicIntentParser().extract(input, System.currentTimeMillis()) }
            finally { delay(700); phase = "idle" }
        }
    }
    fun listen() {
        phase = "listening"; feedback = ""
        speech.start(language, { result -> text = result; interpret(result) }, { message -> phase = "idle"; feedback = message })
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else feedback = "Allow microphone access to speak. You can also type below."
    }
    Text("What do you\nneed today?", style = MaterialTheme.typography.headlineLarge)
    Text("Medicines · Bakery · Carpentry", color = MaterialTheme.colorScheme.secondary)
    LanguageChoice(language) { language = it; store.write("speech", it) }
    val pulse by rememberInfiniteTransition(label = "microphone").animateFloat(1f, 1.06f,
        infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "breathing")
    Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) },
        enabled = phase == "idle", modifier = Modifier.fillMaxWidth().height(100.dp).scale(if (phase == "listening") pulse else 1f)) {
        Text(when (phase) { "listening" -> "Listening · take your time"; "processing" -> "Preparing your request"; else -> "Tap to speak" },
            style = MaterialTheme.typography.titleLarge)
    }
    if (phase == "listening") TextButton(onClick = { speech.finish() }) { Text("Done speaking") }
    if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.error)
    OutlinedTextField(text, { text = it }, label = { Text("Or tell us in writing") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { interpret(text) }, enabled = text.isNotBlank() && phase == "idle") { Text("Review my request →") }
    if (text.isBlank()) {
        Text("TRY SAYING", style = MaterialTheme.typography.labelLarge)
        listOf("1 kg eggless chocolate cake under ₹800", "Dolo 650 one strip by 10 PM", "Cupboard hinge repair tomorrow").forEach { sample ->
            OutlinedButton(onClick = { text = sample; interpret(sample) }, modifier = Modifier.fillMaxWidth()) { Text(sample) }
        }
    }
    AnimatedVisibility(draft != null, enter = fadeIn() + expandVertically()) {
        draft?.let { value -> ReviewCard(value, enabled) { reviewed -> vm.sendDraft(reviewed); draft = null; composing = false } }
    }
}

@Composable private fun ReviewCard(draft: IntentDraft, enabled: Boolean, send: (IntentDraft) -> Unit) {
    var category by remember(draft) { mutableStateOf(draft.domain) }
    var item by remember(draft) { mutableStateOf(draft.item) }
    var quantity by remember(draft) { mutableStateOf(draft.quantity?.toString() ?: "") }
    var unit by remember(draft) { mutableStateOf(draft.unit ?: "") }
    var budget by remember(draft) { mutableStateOf(draft.budgetMax?.toString() ?: "") }
    var hours by remember(draft) { mutableStateOf("") }
    var constraints by remember(draft) { mutableStateOf(draft.constraints.joinToString(", ")) }
    var edit by remember(draft) { mutableStateOf(draft.domain == CommerceDomain.OTHER || draft.item.isBlank() || draft.quantity == null || draft.unit.isNullOrBlank()) }
    val valid = category != CommerceDomain.OTHER && item.isNotBlank() && (quantity.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true) &&
        unit.isNotBlank() && (budget.isBlank() || (budget.toIntOrNull() ?: 0) > 0) &&
        (hours.isBlank() || (hours.toLongOrNull() ?: 0) in 1..168)
    SurfaceCard {
        Text("YOUR REQUEST", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        Text(draft.originalTranscript)
        Text("Confirm the details below. Missing values stay empty.", style = MaterialTheme.typography.bodySmall)
        if (edit) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(CommerceDomain.BAKERY, CommerceDomain.PHARMACY, CommerceDomain.CARPENTRY).forEach { domain ->
                    FilterChip(category == domain, { category = domain }, label = { Text(domain.label()) })
                }
            }
            HarnessField("Item or service", item, { item = it })
            HarnessField("Quantity", quantity, { quantity = it }, KeyboardType.Decimal)
            HarnessField("Unit · kg / strip / service", unit, { unit = it })
            HarnessField("Maximum budget · ₹ (optional)", budget, { budget = it }, KeyboardType.Number)
            HarnessField("Needed within hours (optional)", hours, { hours = it }, KeyboardType.Number)
            HarnessField("Options · comma separated", constraints, { constraints = it })
            TextButton(onClick = { edit = false }, enabled = valid) { Text("Preview request") }
        } else {
            Text(category.label(), color = MaterialTheme.colorScheme.secondary)
            Text(item, style = MaterialTheme.typography.headlineMedium)
            Text("$quantity $unit · $constraints")
            Text(if (budget.isBlank()) "Budget to discuss" else "Up to ₹$budget", style = MaterialTheme.typography.titleLarge)
            Text(if (hours.isBlank()) "Time to discuss" else "Within $hours hours")
            TextButton(onClick = { edit = true }) { Text("Edit details") }
        }
        Button(onClick = {
            send(draft.copy(domain = category, item = item, quantity = quantity.toDouble(), unit = unit,
                budgetMax = budget.toIntOrNull(), deadlineEpoch = hours.toLongOrNull()?.let { System.currentTimeMillis() + it * 3_600_000 },
                constraints = constraints.split(',').map { it.trim() }.filter { it.isNotBlank() }))
        }, enabled = valid && enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Find sellers") }
    }
}
fun CommerceDomain.label() = when(this) { CommerceDomain.PHARMACY -> "Medicines"; CommerceDomain.BAKERY -> "Bakery"; CommerceDomain.CARPENTRY -> "Carpentry"; else -> "Choose category" }

@Composable private fun CustomerOffers(state: CustomerState, enabled: Boolean, vm: HarnessViewModel) {
    when(state) {
        CustomerState.Idle -> Unit
        is CustomerState.Searching -> SurfaceCard {
            Text("YOUR REQUEST IS LIVE", color = MaterialTheme.colorScheme.secondary)
            Text(state.request.item, style = MaterialTheme.typography.headlineMedium)
            Text("Waiting for matching shops to reply. Only received offers appear here.")
            TextButton(onClick = vm::retry, enabled = enabled) { Text("Send again") }
        }
        is CustomerState.Quotes -> {
            Text("Real offers.\nYour choice.", style = MaterialTheme.typography.headlineLarge)
            if (state.offers.isEmpty()) Text("No current offers. Send your request again.")
            state.offers.forEach { ranked ->
                key(ranked.offer.offerId) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }
                    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 3 }) {
                        SurfaceCard {
                            Text(if (ranked.reason == "Cheapest") "BEST VALUE" else ranked.reason.uppercase(), color = MaterialTheme.colorScheme.secondary)
                            Text(ranked.offer.sellerName, style = MaterialTheme.typography.titleLarge)
                            Text("₹${ranked.offer.amount}", style = MaterialTheme.typography.displaySmall)
                            Text("Ready ${displayTime(ranked.offer.readyByEpoch)}")
                            Button(onClick = { vm.selectOffer(ranked.offer.offerId) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Choose this shop") }
                        }
                    }
                }
            }
        }
        is CustomerState.Selecting -> SurfaceCard {
            Text(if (state.cancelling) "Cancelling your selection" else "Confirming with the shop", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = vm::retry, enabled = enabled) { Text("Check again") }
            TextButton(onClick = vm::cancelOrder, enabled = enabled && !state.cancelling) { Text("Cancel selection") }
        }
        is CustomerState.Accepted -> SurfaceCard {
            Text("CONFIRMED", color = MaterialTheme.colorScheme.secondary)
            Text(state.order.sellerName, style = MaterialTheme.typography.titleLarge)
            Text("₹${state.order.amount}", style = MaterialTheme.typography.displaySmall)
            Text("Ready ${displayTime(state.order.readyByEpoch)}")
            Text("Payment is not enabled in this preview. Reservation expires ${displayTime(state.order.holdUntilEpoch)}.")
            TextButton(onClick = vm::cancelOrder, enabled = enabled) { Text("Cancel order") }
        }
        is CustomerState.Finished -> SurfaceCard {
            Text("Order ${state.order.status.name.lowercase()}", style = MaterialTheme.typography.titleLarge)
            Text("You can create another request above.")
        }
    }
}



