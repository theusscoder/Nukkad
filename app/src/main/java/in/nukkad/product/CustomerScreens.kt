package `in`.nukkad.product

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.nukkad.HarnessField
import `in`.nukkad.ai.*
import `in`.nukkad.debug.displayTime
import `in`.nukkad.engine.RankedOffer
import `in`.nukkad.model.CommerceDomain
import `in`.nukkad.viewmodel.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable fun CustomerHome(store: ProductStore, runtime: HarnessSession?, enabled: Boolean, vm: HarnessViewModel,
                            modelPath: String? = null, selectedTab: String = "Home", onTabChange: (String) -> Unit = {}) {
    var text by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf(store.read("speech", "en-IN")) }
    var phase by remember { mutableStateOf("idle") }
    var feedback by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<IntentDraft?>(null) }
    var composing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationClient = remember(context) { DeviceLocation(context) }
    var customerLocation by remember { mutableStateOf<`in`.nukkad.model.GeoPoint?>(null) }
    var locationMessage by remember { mutableStateOf("") }
    fun readLocation() {
        if (!locationClient.hasPermission()) {
            locationMessage = "Allow location to show approximate distances. Offers still work without it."
            return
        }
        locationMessage = "Finding your location…"
        scope.launch {
            runCatching { locationClient.current() }.onSuccess { location ->
                customerLocation = location
                locationMessage = if (location == null) "Location unavailable. Your offer list still works." else "Location ready · distances are approximate."
            }.onFailure { locationMessage = "Location unavailable. Your offer list still works." }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) readLocation() else locationMessage = "Location not shared. Your offer list still works."
    }
    fun requestLocation() {
        if (locationClient.hasPermission()) readLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    val speaker = remember(context) { OfferSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    val current = runtime?.customer?.state?.collectAsStateWithLifecycle()?.value ?: CustomerState.Idle
    val respondingSellers = runtime?.customer?.respondingSellers?.collectAsStateWithLifecycle()?.value.orEmpty()
    if (selectedTab != "Home") {
        val speak: ((`in`.nukkad.model.Offer) -> Unit)? = if (store.read("spoken_offers") == "yes") {
            { offer -> speaker.speak(offer, language) }
        } else null
        if (selectedTab == "Search") {
            Text("SEARCH NEARBY", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Text("Your neighbourhood,\nin motion.", style = MaterialTheme.typography.headlineLarge)
            if (current == CustomerState.Idle) {
                Text("Start with a request. Nearby shops appear here when they actually respond.")
                Button(onClick = { onTabChange("Home") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Make a request") }
                OutlinedButton(onClick = ::requestLocation) { Text("Set my approximate location") }
            } else CustomerOffers(current, enabled, vm, speak, customerLocation, locationMessage, ::requestLocation, respondingSellers)
        } else {
            Text("YOUR REQUESTS", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Text("Requests and replies", style = MaterialTheme.typography.headlineLarge)
            if (current == CustomerState.Idle) {
                SurfaceCard {
                    Text("No requests yet", style = MaterialTheme.typography.titleLarge)
                    Text("Your sent requests and shop replies will show up here.")
                    TextButton(onClick = { onTabChange("Home") }) { Text("Create a request →") }
                }
            } else {
                CustomerOffers(current, enabled, vm, speak, customerLocation, locationMessage, ::requestLocation, respondingSellers)
                if (current !is CustomerState.Selecting && current !is CustomerState.Accepted)
                    TextButton(onClick = { onTabChange("Home"); composing = true }) { Text("Create another request") }
            }
        }
        return
    }
    val speech = remember { SpeechInput(context) }
    val gemma = remember(modelPath) { modelPath?.let { File(it).takeIf(File::exists)?.let { file -> GemmaLlmEngine(context, file) } } }
    DisposableEffect(gemma) { onDispose { gemma?.close() } }
    DisposableEffect(speech) { onDispose { speech.cancel() } }
    fun interpret(input: String) {
        if (input.isBlank()) return
        phase = "processing"
        feedback = if (gemma == null) "Preparing request with the offline fallback…" else "Loading Gemma on this phone…"
        scope.launch {
            try {
                draft = try {
                    gemma?.extract(input, System.currentTimeMillis(), language) ?: DeterministicIntentParser().extract(input, System.currentTimeMillis(), language)
                } catch (failure: Throwable) {
                    gemma?.close()
                    feedback = "Gemma could not run; using the offline fallback. ${failure.message.orEmpty().take(100)}"
                    DeterministicIntentParser().extract(input, System.currentTimeMillis(), language)
                }
            }
            finally { delay(700); phase = "idle" }
        }
    }
    fun listen() {
        phase = "listening"; feedback = ""
        speech.start(language, { result -> text = result; interpret(result) }, { message -> phase = "idle"; feedback = message },
            { partial -> text = partial })
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else feedback = "Allow microphone access to speak. You can also type below."
    }
    Text("What do you\nneed today?", style = MaterialTheme.typography.headlineLarge)
    Text("Goods and services from local shops", color = MaterialTheme.colorScheme.secondary)
    LanguageChoice(language) { language = it; store.write("speech", it) }
    val listening = phase == "listening"
    val pulse by rememberInfiniteTransition(label = "microphone").animateFloat(if (listening) 1.06f else 1f, if (listening) 1.2f else 1.045f,
        infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "breathing")
    Box(Modifier.fillMaxWidth().height(202.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(190.dp).scale(pulse), shape = CircleShape, color = Color(0x22FF6B2C)) {}
        Surface(Modifier.size(158.dp).clickable(enabled = phase == "idle") { permission.launch(Manifest.permission.RECORD_AUDIO) },
            shape = CircleShape, color = Color(0xFFFF6B2C), shadowElevation = 10.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (phase == "processing") "···" else "🎙", style = MaterialTheme.typography.headlineLarge)
                Text(when (phase) { "listening" -> "Listening"; "processing" -> "Understanding…"; else -> "Tap to speak" },
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (phase == "listening") Text(text.ifBlank { "Listening…" }, style = MaterialTheme.typography.titleMedium)
    if (phase == "listening") TextButton(onClick = { speech.finish() }) { Text("Done speaking") }
    if (feedback.isNotBlank()) Text(feedback, color = if (feedback.startsWith("Gemma could not run")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
    OutlinedTextField(text, { text = it }, label = { Text("Or tell us in writing") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { interpret(text) }, enabled = text.isNotBlank() && phase == "idle") { Text("Review my request →") }
    if (text.isBlank()) {
        Text("TRY SAYING", style = MaterialTheme.typography.labelLarge)
        listOf("1 kg eggless chocolate cake under ₹800", "Dolo 650 one strip by 10 PM", "Cupboard hinge repair tomorrow").forEach { sample ->
            OutlinedButton(onClick = { text = sample; interpret(sample) }, modifier = Modifier.fillMaxWidth()) { Text(sample) }
        }
    }
    AnimatedVisibility(draft != null, enter = fadeIn() + expandVertically()) {
        draft?.let { value -> ReviewCard(value, enabled) {
            reviewed -> vm.sendDraft(reviewed); draft = null; composing = false; onTabChange("Search")
        } }
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
    var edit by remember(draft) { mutableStateOf(false) }
    val valid = category != CommerceDomain.OTHER && item.isNotBlank() && (quantity.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true) &&
        unit.isNotBlank() && (budget.isBlank() || (budget.toIntOrNull() ?: 0) > 0) &&
        (hours.isBlank() || (hours.toLongOrNull() ?: 0) in 1..168)
    SurfaceCard {
        Text("YOUR REQUEST", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        Text("Interpreted by ${draft.interpreter}${draft.confidence?.let { " · ${(it * 100).toInt()}% confidence" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        Text(draft.originalTranscript)
        draft.deadlineText?.let { Text("Time phrase heard: $it · please set the deadline below.", style = MaterialTheme.typography.bodySmall) }
        if (edit) {
            Text("EDIT REQUEST", style = MaterialTheme.typography.labelLarge)
            Text("CATEGORY · ${category.label()} selected from your request", color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge)
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
        } else if (valid) {
            Text("GOT IT", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Text(category.label(), color = MaterialTheme.colorScheme.secondary)
            Text(item, style = MaterialTheme.typography.headlineMedium)
            Text(listOf("$quantity $unit", constraints).filter(String::isNotBlank).joinToString(" · "))
            Text(if (budget.isBlank()) "Budget to discuss" else "Up to ₹$budget", style = MaterialTheme.typography.titleLarge)
            Text(if (hours.isBlank()) "Time to discuss" else "Within $hours hours")
            TextButton(onClick = { edit = true }) { Text("Edit details") }
        } else {
            Text("I GOT MOST OF IT", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            val missing = when {
                category == CommerceDomain.OTHER -> "Which kind of shop should receive this?"
                item.isBlank() -> "What item or service do you need?"
                quantity.toDoubleOrNull() == null -> "How much do you need?"
                unit.isBlank() -> "What unit should I use? (kg, pack, service…)"
                else -> "Please check the request details."
            }
            Text(missing, style = MaterialTheme.typography.titleMedium)
            when {
                category == CommerceDomain.OTHER -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(CommerceDomain.BAKERY, CommerceDomain.PHARMACY, CommerceDomain.CARPENTRY).forEach { domain ->
                        FilterChip(category == domain, { category = domain }, label = { Text(domain.label()) })
                    }
                }
                item.isBlank() -> HarnessField("Item or service", item, { item = it })
                quantity.toDoubleOrNull() == null -> HarnessField("Quantity", quantity, { quantity = it }, KeyboardType.Decimal)
                unit.isBlank() -> HarnessField("Unit · kg / strip / service", unit, { unit = it })
            }
            TextButton(onClick = { edit = true }) { Text("Edit all details") }
        }
        Button(onClick = {
            send(draft.copy(domain = category, item = item, quantity = quantity.toDouble(), unit = unit,
                budgetMax = budget.toIntOrNull(), deadlineEpoch = hours.toLongOrNull()?.let { System.currentTimeMillis() + it * 3_600_000 },
                constraints = constraints.split(',').map { it.trim() }.filter { it.isNotBlank() }))
        }, enabled = valid && enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Find sellers") }
    }
}
fun CommerceDomain.label() = when(this) { CommerceDomain.PHARMACY -> "Medicines"; CommerceDomain.BAKERY -> "Bakery"; CommerceDomain.CARPENTRY -> "Carpentry"; else -> "Choose category" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CustomerOffers(state: CustomerState, enabled: Boolean, vm: HarnessViewModel,
                                       speak: ((`in`.nukkad.model.Offer) -> Unit)?, location: `in`.nukkad.model.GeoPoint?,
                                       locationMessage: String, requestLocation: () -> Unit,
                                       respondingSellers: List<`in`.nukkad.model.Receipt>) {
    var selectedOffer by remember(state) { mutableStateOf<RankedOffer?>(null) }
    when(state) {
        CustomerState.Idle -> Unit
        is CustomerState.Searching -> SurfaceCard {
            Text("SEARCHING FOR SHOPS", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Text(state.request.item, style = MaterialTheme.typography.headlineMedium)
            Text("Nearby shops appear as they reply. Nukkad only shows real responses.")
            if (location == null) OutlinedButton(onClick = requestLocation) { Text("Set my approximate location") }
            if (locationMessage.isNotBlank()) Text(locationMessage, style = MaterialTheme.typography.bodySmall)
            if (location != null) HexDiscoveryView(location, emptyList(), respondingSellers) { }
            TextButton(onClick = vm::retry, enabled = enabled) { Text("Send again") }
        }
        is CustomerState.Quotes -> {
            Text("Real offers.\nYour choice.", style = MaterialTheme.typography.headlineLarge)
            if (state.offers.isEmpty()) Text("No current offers. Send your request again.")
            if (location == null) {
                OutlinedButton(onClick = requestLocation) { Text("Show nearby hex view") }
                if (locationMessage.isNotBlank()) Text(locationMessage, style = MaterialTheme.typography.bodySmall)
            } else {
                HexDiscoveryView(location, state.offers, respondingSellers) { selected -> selectedOffer = selected }
                selectedOffer?.let { chosen -> ModalBottomSheet(onDismissRequest = { selectedOffer = null }) {
                    OfferDetailsCard(location, chosen, enabled,
                        onSpeak = if (speak == null) null else ({ speak(chosen.offer) }),
                        onChoose = { vm.selectOffer(chosen.offer.offerId); selectedOffer = null })
                } }
                TextButton(onClick = requestLocation) { Text("Refresh location") }
            }
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
                            if (speak != null) TextButton(onClick = { speak(ranked.offer) }) { Text("Hear offer") }
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



