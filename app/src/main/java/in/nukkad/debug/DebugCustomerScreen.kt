package `in`.nukkad.debug

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import `in`.nukkad.HarnessField
import `in`.nukkad.ai.DeterministicIntentParser
import `in`.nukkad.ai.IntentValidator
import `in`.nukkad.ai.SpeechInput
import `in`.nukkad.model.CommerceDomain
import `in`.nukkad.viewmodel.CustomerState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun displayTime(epoch: Long): String = DateTimeFormatter.ofPattern("dd MMM · h:mm a").withZone(ZoneId.of("Asia/Kolkata")).format(Instant.ofEpochMilli(epoch)) + " IST"

@Composable
fun DebugCustomerScreen(state: CustomerState, enabled: Boolean, send: (String, String, String, String, String, String) -> Unit, retry: () -> Unit, select: (String) -> Unit, cancel: () -> Unit) {
    var item by rememberSaveable { mutableStateOf("chocolate cake") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var budget by rememberSaveable { mutableStateOf("800") }
    var hours by rememberSaveable { mutableStateOf("24") }
    var constraints by rememberSaveable { mutableStateOf("eggless") }
    var domain by rememberSaveable { mutableStateOf("bakery") }
    var language by rememberSaveable { mutableStateOf("hi-IN") }
    var transcript by rememberSaveable { mutableStateOf("") }
    var voiceError by rememberSaveable { mutableStateOf("") }
    var listening by rememberSaveable { mutableStateOf(false) }
    var cooldownUntil by rememberSaveable { mutableStateOf(0L) }
    val context = LocalContext.current
    val speech = remember(context) { SpeechInput(context) }
    val scope = rememberCoroutineScope()
    val parser = remember { DeterministicIntentParser() }
    val now = System.currentTimeMillis()
    val cooldown = cooldownUntil > now
    val pulse by rememberInfiniteTransition(label = "voice").animateFloat(1f, 1.18f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { listening = true; speech.start(language, { text ->
            transcript = text
            scope.launch {
                val draft = parser.extract(text, System.currentTimeMillis())
                domain = draft.domain.name.lowercase()
                if (draft.item.isNotBlank()) item = draft.item
                draft.quantity?.let { quantity = it.toString() }
                draft.budgetMax?.let { budget = it.toString() }
                if (draft.constraints.isNotEmpty()) constraints = draft.constraints.joinToString()
                listening = false
                cooldownUntil = System.currentTimeMillis() + 1800
                voiceError = IntentValidator.validate(draft).joinToString(" · ")
            }
        }, { listening = false; cooldownUntil = System.currentTimeMillis() + 1200; voiceError = it }) } else voiceError = "Microphone permission was denied"
    }
    val locked = state is CustomerState.Selecting || state is CustomerState.Accepted
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("02 / CUSTOMER", color = MaterialTheme.colorScheme.primary)
        Text("${domain.replaceFirstChar { it.uppercase() }} · Kondapur", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = language == "hi-IN", onClick = { language = "hi-IN" }, label = { Text("हिन्दी") })
            FilterChip(selected = language == "te-IN", onClick = { language = "te-IN" }, label = { Text("తెలుగు") })
            FilterChip(selected = language == "en-IN", onClick = { language = "en-IN" }, label = { Text("English") })
        }
        OutlinedButton(onClick = {
            voiceError = ""
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { listening = true; speech.start(language, { text ->
                transcript = text
                scope.launch {
                    val draft = parser.extract(text, System.currentTimeMillis())
                    domain = draft.domain.name.lowercase(); if (draft.item.isNotBlank()) item = draft.item
                    draft.quantity?.let { quantity = it.toString() }; draft.budgetMax?.let { budget = it.toString() }
                    if (draft.constraints.isNotEmpty()) constraints = draft.constraints.joinToString()
                    listening = false
                cooldownUntil = System.currentTimeMillis() + 1800
                voiceError = IntentValidator.validate(draft).joinToString(" · ")
                }
            }, { listening = false; cooldownUntil = System.currentTimeMillis() + 1200; voiceError = it }) } else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = enabled && !locked && !listening && !cooldown, modifier = Modifier.fillMaxWidth()) {
            if (listening) { CircularProgressIndicator(Modifier.size(22.dp * pulse), strokeWidth = 3.dp) ; Spacer(Modifier.width(8.dp)); Text("Listening… speak now") }
            else if (cooldown) Text("Processing voice…")
            else Text("🎙 Speak request")
        }
        if (transcript.isNotBlank()) Text("Heard: $transcript", style = MaterialTheme.typography.bodySmall)
        if (voiceError.isNotBlank()) Text(voiceError, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Text("Review before sending. Voice currently uses Android speech plus a deterministic fallback; Gemma will replace the interpreter when the on-device runtime is verified.", style = MaterialTheme.typography.bodySmall)
        HarnessField("Item / alias", item, { item = it })
        HarnessField("Quantity · kg", quantity, { quantity = it }, KeyboardType.Decimal)
        HarnessField("Maximum budget · INR", budget, { budget = it }, KeyboardType.Number)
        HarnessField("Deadline · hours from now", hours, { hours = it }, KeyboardType.Number)
        HarnessField("Constraints · comma separated", constraints, { constraints = it })
        Button(onClick = { send(item, quantity, budget, hours, constraints, domain) }, enabled = enabled && !locked, modifier = Modifier.fillMaxWidth()) { Text("Confirm and send request") }
        when (state) {
            CustomerState.Idle -> Text("Connect, then speak or type a request.")
            is CustomerState.Searching -> { Text("Request sent · waiting for valid offers", style = MaterialTheme.typography.titleMedium); Text(state.request.requestId, style = MaterialTheme.typography.bodySmall) }
            is CustomerState.Selecting -> { Text(if (state.cancelling) "Waiting for cancellation acknowledgement" else "Waiting for seller acceptance", style = MaterialTheme.typography.titleLarge); Text("Order ${state.selection.orderId.take(8)}"); OutlinedButton(onClick = cancel, enabled = enabled && !state.cancelling) { Text("Cancel selection") } }
            is CustomerState.Accepted -> { Text("SELLER ACCEPTED · ${state.order.sellerName}", color = MaterialTheme.colorScheme.secondary); Text("₹${state.order.amount}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text("Ready ${displayTime(state.order.readyByEpoch)}"); Text("Capacity held until ${displayTime(state.order.holdUntilEpoch)}"); OutlinedButton(onClick = cancel, enabled = enabled) { Text("Cancel order") } }
            is CustomerState.Finished -> { Text("${state.order.status}: ${state.order.reason}"); Text("Send a new request when ready.") }
            is CustomerState.Quotes -> {
                Text("${state.offers.size} VALID OFFER(S)", color = MaterialTheme.colorScheme.secondary)
                state.offers.forEach { ranked -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(ranked.offer.sellerName, style = MaterialTheme.typography.titleLarge); Text("₹${ranked.offer.amount}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text("Ready ${displayTime(ranked.offer.readyByEpoch)}"); Text("Quote expires ${displayTime(ranked.offer.expiresAtEpoch)}", style = MaterialTheme.typography.bodySmall); Button(onClick = { select(ranked.offer.offerId) }, enabled = enabled) { Text("Select this offer") }; Text(ranked.reason, color = MaterialTheme.colorScheme.secondary) } } }
            }
        }
        if (state != CustomerState.Idle && state !is CustomerState.Finished) OutlinedButton(onClick = retry, enabled = enabled) { Text(if (locked) "Retry / refresh order status" else "Retry same request") }
    }
}






