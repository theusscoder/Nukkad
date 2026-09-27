package `in`.nukkad.product

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.nukkad.HarnessField
import `in`.nukkad.debug.displayTime
import `in`.nukkad.ai.SpeechInput
import `in`.nukkad.model.*
import `in`.nukkad.viewmodel.*
import kotlinx.coroutines.launch

@Composable fun MerchantHome(store: ProductStore, id: String, runtime: HarnessSession?, online: Boolean, busy: Boolean, reconnect: () -> Unit, focusRequestVersion: Int = 0,
                            selectedTab: String = "Home", onTabChange: (String) -> Unit = {}, onImmersiveChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val locationClient = remember(context) { DeviceLocation(context) }
    var profile by remember(id) { mutableStateOf(store.profile(id)) }
    var notice by remember { mutableStateOf("") }
    var setupOpen by remember { mutableStateOf(false) }
    var setupText by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<ShopSetupDraft?>(null) }
    var listening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf("") }
    var itemEditor by remember { mutableIntStateOf(-2) }
    var showShopDetails by remember { mutableStateOf(false) }
    var scanOpen by remember { mutableStateOf(false) }
    var locationPending by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("") }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locationPending = true else locationMessage = "Location not saved. Your shop can still receive offers."
    }
    val speech = remember(context) { SpeechInput(context) }
    val speaker = remember(context) { MerchantSpeaker(context) }
    val language = store.read("speech", "en-IN")
    val guidance = store.read("merchant_guidance", "yes") == "yes"
    val activeState = runtime?.seller?.state?.collectAsStateWithLifecycle()?.value ?: SellerState.Live
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            speech.start(language, { result ->
                listening = false; setupText = result; draft = MerchantSetupParser.parse(result); speechError = ""
            }, { message -> listening = false; speechError = message }, { partial -> setupText = partial })
        } else speechError = "Allow microphone access, or enter the details below."
    }
    DisposableEffect(speech, speaker) { onDispose { speech.cancel(); speaker.close() } }
    LaunchedEffect(focusRequestVersion) { if (focusRequestVersion > 0) onTabChange("Home") }
    LaunchedEffect(scanOpen) { onImmersiveChange(scanOpen) }
    LaunchedEffect((activeState as? SellerState.Evaluated)?.request?.requestId, guidance) {
        val request = (activeState as? SellerState.Evaluated)?.request
        if (request != null && guidance) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            kotlinx.coroutines.delay(620)
            speaker.speakRequest(request, language)
        }
    }
    fun save(value: SellerProfile) { store.saveProfile(value); profile = value; notice = "Saved. Reconnecting your agent…"; reconnect() }
    fun sendOwnerOffer(request: Request, amount: Int, readyHours: Long) {
        val active = runtime ?: run { notice = "Reconnect before sending an offer."; return }
        scope.launch {
            runCatching { active.seller.sendOwnerOffer(request, amount,
                System.currentTimeMillis() + readyHours * 3_600_000L, System.currentTimeMillis()) }
                .onSuccess { notice = "Your offer was sent to the customer." }
                .onFailure { notice = it.message ?: "Offer couldn't be sent. Check the price and ready time." }
        }
    }
    LaunchedEffect(locationPending) {
        if (locationPending) {
            locationMessage = "Finding your shop location…"
            runCatching { locationClient.current() }.onSuccess { point ->
                if (point != null) { save(profile.copy(location = point)); locationMessage = "Shop location saved. Customers see an approximate area only." }
                else locationMessage = "Location unavailable. You can try again later."
            }.onFailure { locationMessage = "Location unavailable. You can try again later." }
            locationPending = false
        }
    }
    fun refreshLocation() {
        if (locationClient.hasPermission()) locationPending = true
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    fun startSetupSpeech() {
        setupOpen = true; speechError = ""
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listening = true
            speech.start(language, { result -> listening = false; setupText = result; draft = MerchantSetupParser.parse(result) },
                { message -> listening = false; speechError = message }, { partial -> setupText = partial })
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    if (selectedTab != "Rules") {
        Text(profile.shopName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(profile.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text(if (online) "● Agent live" else "○ Agent offline", color = if (online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
    } else TextButton(onClick = { onTabChange("Home") }) { Text("← Back to Home") }
    if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall)
    when(selectedTab) {
        "Home" -> {
            Text("TODAY AT YOUR SHOP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Choose how you want to respond.", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiscoveryMode.entries.forEach { mode ->
                    val selected = profile.discoveryMode == mode
                    val scale by animateFloatAsState(if (selected) 1.035f else 1f,
                        spring(stiffness = Spring.StiffnessMediumLow), label = "mode-${mode.name}")
                    val color = when(mode) { DiscoveryMode.EXACT -> Color(0xFF357A53); DiscoveryMode.FLEX -> Color(0xFFD19422); DiscoveryMode.OPEN -> Color(0xFFCE5A42) }
                    Surface(Modifier.weight(1f).scale(scale).clickable(enabled = !busy) {
                        if (profile.discoveryMode != mode) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            save(profile.copy(discoveryMode = mode))
                            if (guidance) speaker.announceMode(mode, language)
                        }
                    }.animateContentSize(), shape = MaterialTheme.shapes.large,
                        color = if (selected) color.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else color.copy(alpha = .45f))) {
                        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(when(mode) { DiscoveryMode.EXACT -> "●"; DiscoveryMode.FLEX -> "◉"; DiscoveryMode.OPEN -> "◎" }, color = color, style = MaterialTheme.typography.titleLarge)
                            Text(mode.name, fontWeight = FontWeight.Bold, color = color)
                            Text(when(mode) { DiscoveryMode.EXACT -> "What I sell"; DiscoveryMode.FLEX -> "Similar work"; DiscoveryMode.OPEN -> "My category" }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            runtime?.let { active ->
                val requests by active.seller.requestCount.collectAsStateWithLifecycle()
                val quotes by active.seller.quoteCount.collectAsStateWithLifecycle()
                val orders by active.seller.orders.collectAsStateWithLifecycle()
                SurfaceCard {
                    Text("TODAY", style = MaterialTheme.typography.labelLarge)
                    Text("$requests requests", style = MaterialTheme.typography.titleLarge)
                    Text("$quotes handled with an offer · ${orders.count { it.status == OrderStatus.ACCEPTED }} active orders")
                }
            }
            Button(onClick = ::startSetupSpeech, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("🎙  TELL NUKKAD") }
            Button(onClick = { onTabChange("Shop") }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("📷  SCAN MY SHOP") }
            IncomingCard(activeState, ::sendOwnerOffer)
            OutlinedButton(onClick = { onTabChange("Rules") }, modifier = Modifier.fillMaxWidth()) { Text("Shop preferences and rules") }
        }
        "Shop" -> {
            if (scanOpen) {
                MerchantScanner(onCancel = { scanOpen = false }) { candidates ->
                    val additions = candidates.filterNot { candidate -> profile.items.any { it.name.equals(candidate.name, ignoreCase = true) } }
                    save(profile.copy(items = profile.items + additions))
                    notice = "${additions.size} new items added. Existing catalogue items were kept."
                    scanOpen = false
                }
            } else {
            Text("YOUR CATALOGUE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("${profile.items.size} ${if (profile.items.size == 1) "item" else "items"}", style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = ::startSetupSpeech, modifier = Modifier.weight(1f).height(58.dp)) { Text("🎙  TELL NUKKAD") }
                OutlinedButton(onClick = { scanOpen = true }, modifier = Modifier.weight(1f).height(58.dp)) { Text("📷  SCAN MENU") }
            }
            OutlinedButton(onClick = ::refreshLocation, modifier = Modifier.fillMaxWidth()) {
                Text(if (profile.location == null) "USE MY SHOP LOCATION" else "REFRESH SHOP LOCATION")
            }
            Text(locationMessage.ifBlank { if (profile.location == null) "Optional · helps customers see approximate distance" else "Shop location saved" },
                style = MaterialTheme.typography.bodySmall)
            if (setupOpen) MerchantSetupCard(setupText, { setupText = it; draft = null }, listening, speechError, draft,
                onStart = ::startSetupSpeech, onStop = { speech.finish(); listening = false }, onDraft = { draft = it }, onClose = { setupOpen = false; listening = false; speech.cancel() }, onSave = { candidate ->
                    val item = candidate.validatedItem()
                    if (item == null) { speechError = "Check the item name, unit and price before saving." }
                    else {
                        val items = profile.items.filterNot { it.name.equals(item.name, ignoreCase = true) } + item
                        val rules = if (candidate.dailyCapacity != null && candidate.dailyCapacity > 0)
                            profile.rules.copy(maxDailyOrders = candidate.dailyCapacity.coerceAtMost(20)) else profile.rules
                        save(profile.copy(items = items, rules = rules)); draft = null; setupOpen = false
                    }
                })
            profile.items.forEachIndexed { index, item ->
                SurfaceCard {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text("₹${item.pricePerUnit} / ${item.unit}", style = MaterialTheme.typography.headlineMedium)
                    item.constraintSurcharges.forEach { (option, price) -> Text("$option +₹$price") }
                    item.aliases.takeIf { it.isNotEmpty() }?.let { Text("Similar names: ${it.joinToString()}", style = MaterialTheme.typography.bodySmall) }
                    Row {
                        TextButton(onClick = { itemEditor = index }, enabled = !busy) { Text("Edit") }
                        TextButton(onClick = { save(profile.copy(items = profile.items.filterIndexed { i, _ -> i != index })) }, enabled = !busy) { Text("Remove") }
                    }
                }
            }
            OutlinedButton(onClick = { itemEditor = -1 }) { Text("+ Add item") }
            if (itemEditor >= -1) ItemEditor(profile.items.getOrNull(itemEditor), busy, onCancel = { itemEditor = -2 }) { item ->
                val items = profile.items.toMutableList()
                if (itemEditor == -1) items.add(item) else items[itemEditor] = item
                save(profile.copy(items = items)); itemEditor = -2
            }
            TextButton(onClick = { showShopDetails = !showShopDetails }) { Text(if (showShopDetails) "Hide shop details" else "Shop details and rules") }
            if (showShopDetails) MerchantShopProfile(profile, busy, ::save)
            }
        }
        "Orders" -> {
            Text("YOUR ORDERS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            runtime?.let {
                val orders by it.seller.orders.collectAsStateWithLifecycle()
                val activeOrders = orders.filter { order -> order.status == OrderStatus.ACCEPTED && order.readyByEpoch > System.currentTimeMillis() }.reversed()
                val readyOrders = orders.filter { order -> order.status == OrderStatus.ACCEPTED && order.readyByEpoch <= System.currentTimeMillis() }.reversed()
                val pastOrders = orders.filter { order -> order.status != OrderStatus.ACCEPTED }.reversed()
                if (orders.isEmpty()) SurfaceCard {
                    Text("◷", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("No orders yet", style = MaterialTheme.typography.titleLarge)
                    Text("When a customer accepts one of your offers, the order will appear here.")
                }
                @Composable fun orderGroup(title: String, entries: List<`in`.nukkad.model.Order>) {
                    if (entries.isNotEmpty()) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        entries.forEach { order -> SurfaceCard {
                            Text(if (order.status == OrderStatus.ACCEPTED) title else order.status.name, color = MaterialTheme.colorScheme.secondary)
                            Text("₹${order.amount}", style = MaterialTheme.typography.displaySmall)
                            Text(order.reason)
                        } }
                    }
                }
                orderGroup("ACTIVE", activeOrders)
                orderGroup("READY", readyOrders)
                orderGroup("PAST", pastOrders)
            }
        }
        "Rules" -> RuleEditor(profile.rules, busy) { save(profile.copy(rules = it)) }
    }
}

@Composable private fun MerchantSetupCard(transcript: String, onTranscript: (String) -> Unit, listening: Boolean, error: String,
                                           draft: ShopSetupDraft?, onStart: () -> Unit, onStop: () -> Unit,
                                           onDraft: (ShopSetupDraft) -> Unit, onClose: () -> Unit, onSave: (ShopSetupDraft) -> Unit) {
    var item by remember(draft) { mutableStateOf(draft?.itemName.orEmpty()) }
    var price by remember(draft) { mutableStateOf(draft?.pricePerUnit?.toString().orEmpty()) }
    var unit by remember(draft) { mutableStateOf(draft?.unit ?: "kg") }
    var option by remember(draft) { mutableStateOf(draft?.optionName.orEmpty()) }
    var surcharge by remember(draft) { mutableStateOf(draft?.optionSurcharge?.toString().orEmpty()) }
    var capacity by remember(draft) { mutableStateOf(draft?.dailyCapacity?.toString().orEmpty()) }
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "voice").animateFloat(1f, 1.07f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(760), androidx.compose.animation.core.RepeatMode.Reverse), label = "voice-pulse")
    SurfaceCard {
        Text("SAY WHAT YOUR SHOP MAKES", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
            if (listening) Box(Modifier.size(76.dp).scale(pulse).background(Color(0x33FF6B2C), CircleShape))
            Button(onClick = if (listening) onStop else onStart, modifier = Modifier.height(62.dp).scale(if (listening) pulse else 1f)) {
                Text(if (listening) "● Listening · tap when done" else "🎙  TELL NUKKAD")
            }
        }
        OutlinedTextField(transcript, onTranscript, label = { Text("What we heard · edit if needed") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!listening && transcript.isNotBlank() && draft == null) TextButton(onClick = { onDraft(MerchantSetupParser.parse(transcript)) }) { Text("Make shop details") }
        draft?.let { candidate ->
            Text("CHECK THESE DETAILS", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            HarnessField("What do you sell?", item, { item = it })
            HarnessField("Price · ₹", price, { price = it }, KeyboardType.Number)
            HarnessField("Unit", unit, { unit = it })
            if (option.isNotBlank()) {
                HarnessField("Option · eggless", option, { option = it })
                HarnessField("Extra charge · ₹", surcharge, { surcharge = it }, KeyboardType.Number)
            }
            HarnessField("Items you can make each day (optional)", capacity, { capacity = it }, KeyboardType.Number)
            candidate.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val edited = candidate.copy(itemName = item, pricePerUnit = price.toIntOrNull(), unit = unit,
                    optionName = option, optionSurcharge = surcharge.toIntOrNull(), dailyCapacity = capacity.toIntOrNull())
                onSave(edited)
            }, enabled = item.isNotBlank() && (price.toIntOrNull() ?: 0) > 0, modifier = Modifier.fillMaxWidth()) { Text("LOOKS GOOD · SAVE TO SHOP") }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable private fun MerchantShopProfile(profile: SellerProfile, busy: Boolean, save: (SellerProfile) -> Unit) {
    var name by remember(profile) { mutableStateOf(profile.shopName) }
    var upi by remember(profile) { mutableStateOf(profile.upiId) }
    var domain by remember(profile) { mutableStateOf(CommerceDomain.from(profile.category)) }
    SurfaceCard {
        Text("Your shop. Your rules.", style = MaterialTheme.typography.titleLarge)
        Text("Keep your agent open to receive requests. Prices follow your saved catalogue.")
        HarnessField("Shop name", name, { name = it })
        HarnessField("UPI ID · optional", upi, { upi = it })
        Text("Your business category")
        listOf(CommerceDomain.BAKERY, CommerceDomain.PHARMACY, CommerceDomain.CARPENTRY).forEach { choice ->
            FilterChip(domain == choice, { domain = choice }, label = { Text(choice.label()) })
        }
        Text("Changing category clears the old catalogue.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { save(profile.copy(shopName = name.trim(), upiId = upi.trim(), category = domain.name.lowercase(),
            domains = setOf(domain), items = if (domain != CommerceDomain.from(profile.category)) emptyList() else profile.items)) },
            enabled = !busy && name.isNotBlank()) { Text("Save shop") }
    }
}

@Composable private fun IncomingCard(state: SellerState, sendOwnerOffer: (Request, Int, Long) -> Unit) {
    SurfaceCard {
        if (state is SellerState.Evaluated) {
            var revealedChecks by remember(state.request.requestId) { mutableIntStateOf(0) }
            var revealedFacts by remember(state.request.requestId) { mutableIntStateOf(0) }
            val haptics = LocalHapticFeedback.current
            LaunchedEffect(state.request.requestId) {
                for (index in 1..4) { kotlinx.coroutines.delay(130); revealedFacts = index }
            }
            LaunchedEffect(state.request.requestId, state.decision.checks.size) {
                for (index in state.decision.checks.indices) {
                    kotlinx.coroutines.delay(300)
                    revealedChecks = index + 1
                }
            }
            LaunchedEffect(state.request.requestId, state.quoteSent, revealedChecks) {
                if (state.quoteSent && revealedChecks == state.decision.checks.size)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            Text("NEW REQUEST", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text(state.request.item, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(revealedFacts >= 1, enter = slideInHorizontally { -it / 4 } + fadeIn()) {
                    FactPill("📦", state.request.quantity?.let { "$it ${state.request.unit.orEmpty()}" } ?: "Quantity?")
                }
                if (state.request.budgetMax != null) AnimatedVisibility(revealedFacts >= 2, enter = slideInHorizontally { -it / 4 } + fadeIn()) {
                    FactPill("₹", "Up to ${state.request.budgetMax}")
                }
                state.request.deadlineEpoch?.let { deadline ->
                    AnimatedVisibility(revealedFacts >= 3, enter = slideInHorizontally { -it / 4 } + fadeIn()) { FactPill("◷", displayTime(deadline)) }
                }
            }
            state.request.constraints.forEachIndexed { index, value ->
                AnimatedVisibility(revealedFacts >= 4, enter = slideInHorizontally { -it / 4 } + fadeIn()) { FactPill("✓", value) }
            }
            Text("${state.request.domain.label()} · ${state.request.area}", style = MaterialTheme.typography.bodySmall)
            Text(if (revealedChecks < state.decision.checks.size) "CHECKING YOUR SHOP" else "YOUR SHOP'S CHECKS",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            state.decision.checks.take(revealedChecks).forEachIndexed { index, check ->
                key(state.request.requestId, index) {
                    AnimatedVisibility(visible = true, enter = slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() + expandVertically()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(check.name, fontWeight = FontWeight.Medium)
                                Text(check.detail, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (check.passed) "✓" else "·", color = if (check.passed) Color(0xFF357A53) else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            if (revealedChecks < state.decision.checks.size) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (revealedChecks == state.decision.checks.size) when(val decision = state.decision) {
                is Decision.AutoQuote -> {
                    Text(if (state.quoteSent) "OFFER SENT · NUKKAD HANDLED IT" else "SENDING YOUR OFFER", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Text("₹${decision.amount}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                }
                is Decision.NeedsOwner -> { Text("NEEDS YOU", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(decision.reason) }
                is Decision.NoMatch -> Text("Can't take this one", style = MaterialTheme.typography.titleLarge)
            }
            if (state.ownerOffer != null) {
                Text("OWNER OFFER SENT", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text("₹${state.ownerOffer.amount}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                Text("Ready ${displayTime(state.ownerOffer.readyByEpoch)}")
            } else if (revealedChecks == state.decision.checks.size && state.decision is Decision.NeedsOwner) {
                OwnerOfferEditor(state.request) { amount, hours -> sendOwnerOffer(state.request, amount, hours) }
            }
        } else {
            Text("READY WHEN YOU ARE", style = MaterialTheme.typography.titleLarge)
            Text("A matching request will arrive here.")
        }
    }
}

@Composable private fun OwnerOfferEditor(request: Request, send: (Int, Long) -> Unit) {
    var price by remember(request.requestId) { mutableStateOf("") }
    var hours by remember(request.requestId) { mutableStateOf("") }
    val amount = price.toIntOrNull()
    val readyHours = hours.toLongOrNull()
    val withinBudget = amount != null && (request.budgetMax == null || amount <= request.budgetMax)
    val withinDeadline = readyHours != null && readyHours in 1..168 &&
        (request.deadlineEpoch == null || System.currentTimeMillis() + readyHours * 3_600_000L <= request.deadlineEpoch)
    Text("If you can do this, set your price and ready time.", style = MaterialTheme.typography.bodyMedium)
    Text("By sending, you confirm you can meet every option above.", style = MaterialTheme.typography.bodySmall)
    HarnessField("Your price · ₹", price, { price = it }, KeyboardType.Number)
    HarnessField("Ready in how many hours?", hours, { hours = it }, KeyboardType.Number)
    if (amount != null && !withinBudget) Text("This is above the customer's budget.", color = MaterialTheme.colorScheme.error)
    if (readyHours != null && !withinDeadline) Text("Choose a time before the customer's deadline.", color = MaterialTheme.colorScheme.error)
    Button(onClick = { send(amount!!, readyHours!!) }, enabled = withinBudget && withinDeadline, modifier = Modifier.fillMaxWidth()) {
        Text("SEND MY OFFER")
    }
}

@Composable private fun FactPill(icon: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Text("$icon  $value", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}
@Composable private fun ItemEditor(original: Item?, busy: Boolean, onCancel: () -> Unit, save: (Item) -> Unit) {
    key(original) {
        var name by remember { mutableStateOf(original?.name ?: "") }
        var price by remember { mutableStateOf(original?.pricePerUnit?.toString() ?: "") }
        var unit by remember { mutableStateOf(original?.unit ?: "kg") }
        var max by remember { mutableStateOf(original?.maxQuantity?.toString() ?: "5") }
        var options by remember { mutableStateOf(original?.constraintSurcharges?.entries?.joinToString(", ") { "${it.key}:${it.value}" } ?: "") }
        var aliases by remember { mutableStateOf(original?.aliases?.joinToString(", ") ?: "") }
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
            HarnessField("Similar names · for FLEX only", aliases, { aliases = it })
            Text("Only names you add here may use this item's listed price in Similar work mode.", style = MaterialTheme.typography.bodySmall)
            if (!optionsValid) Text("Use option:price, with a non-negative whole rupee price.")
            Button(onClick = { save(Item(name.trim(), unit.trim(), price.toInt(), max.toDouble(),
                pairs.associate { it[0].trim() to it[1].trim().toInt() }, aliases.split(',').map(String::trim).filter(String::isNotBlank).distinct())) }, enabled = valid && !busy) { Text("Save item") }
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



