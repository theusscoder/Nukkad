package `in`.nukkad

import androidx.compose.foundation.layout.*
import in.nukkad.BuildConfig
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import `in`.nukkad.model.newSessionCode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.nukkad.debug.DebugCustomerScreen
import `in`.nukkad.debug.DebugSellerScreen
import `in`.nukkad.transport.ConnectionState
import `in`.nukkad.viewmodel.*

@Composable
fun NukkadApp(vm: HarnessViewModel = viewModel()) {
    val clipboard = LocalClipboardManager.current
    val saved by vm.config.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var sessionText by rememberSaveable(saved.sessionId) { mutableStateOf(saved.sessionId) }
    var host by rememberSaveable(saved.host) { mutableStateOf(saved.host) }
    var port by rememberSaveable(saved.port) { mutableStateOf(saved.port.toString()) }
    var tls by rememberSaveable(saved.tls) { mutableStateOf(saved.tls) }
    var role by rememberSaveable(saved.role) { mutableStateOf(if (BuildConfig.NUKKAD_ROLE == "merchant") Role.SELLER else if (BuildConfig.NUKKAD_ROLE == "customer") Role.CUSTOMER else saved.role) }
    var variant by rememberSaveable(saved.sellerVariant) { mutableIntStateOf(saved.sellerVariant) }
    var capacity by rememberSaveable(saved.maxDailyOrders) { mutableStateOf(saved.maxDailyOrders.toString()) }
    var showConfig by rememberSaveable { mutableStateOf(BuildConfig.NUKKAD_ROLE == "dev") }
    LaunchedEffect(BuildConfig.NUKKAD_ROLE) {
        if (BuildConfig.NUKKAD_ROLE != "dev" && session == null) vm.connect(saved.copy(sessionId = "demo-kondapur", role = role, port = 1883, tls = false))
    }
    val settingsChanged = session != null && (sessionText.trim().lowercase() != saved.sessionId || host.trim() != saved.host || port != saved.port.toString() || tls != saved.tls || role != saved.role || variant != saved.sellerVariant || capacity != saved.maxDailyOrders.toString())
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("NUKKAD", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("M2 · SELECTION & CAPACITY · 0.3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                if (BuildConfig.NUKKAD_ROLE == "dev") TextButton(onClick = { showConfig = !showConfig }) { Text(if (showConfig) "Hide setup" else "Setup") } else Text(if (BuildConfig.NUKKAD_ROLE == "merchant") "MERCHANT" else "CUSTOMER", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("One request. Independent shop rules.", style = MaterialTheme.typography.titleMedium)
            if (showConfig && BuildConfig.NUKKAD_ROLE == "dev") {
                Text("01 / CONNECT", color = MaterialTheme.colorScheme.secondary)
                Role.entries.forEach { choice ->
                    FilterChip(selected = role == choice, enabled = !busy, onClick = { role = choice }, label = { Text(when(choice) { Role.LOCAL_LOOP -> "Local loop · fake broker"; Role.CUSTOMER -> "Phone A · customer / MQTT"; Role.SELLER -> "Phone B · seller / MQTT" }) })
                }
                HarnessField("Shared session code", sessionText, { sessionText = it }, enabled = !busy)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { sessionText = newSessionCode() }, enabled = !busy) { Text("New 8-digit code") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(sessionText.trim())) }) { Text("Copy code") }
                }
                Text("Create a code on ONE phone and enter it on the other. Then tap Connect on BOTH phones. Existing saved codes still work.", style = MaterialTheme.typography.bodySmall)
                if (role != Role.LOCAL_LOOP) {
                    HarnessField("MQTT broker hostname", host, { host = it }, enabled = !busy)
                    HarnessField("Port", port, { port = it }, KeyboardType.Number, !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = tls, onCheckedChange = { tls = it; port = if (it) "8883" else "1883" }, enabled = !busy)
                        Text("TLS", Modifier.padding(top = 12.dp))
                    }
                    Text("Public demo broker: use synthetic requests only. Recommended mobile setting is TLS off on port 1883. If TLS/8883 is refused, switch TLS off, reconnect both phones, and use the same session code.", style = MaterialTheme.typography.bodySmall)
                }
                if (role != Role.CUSTOMER) {
                    HarnessField("Merchant capacity · orders/day", capacity, { capacity = it }, KeyboardType.Number, !busy)
                    listOf("Sweet Crumbs · ₹750", "HomeBake · ₹780", "Cake House · over budget").forEachIndexed { index, label ->
                        FilterChip(selected = variant == index, onClick = { variant = index }, enabled = !busy, label = { Text(label) })
                    }
                }
                Button(onClick = { vm.connect(saved.copy(sessionId = sessionText.trim(), host = host.trim(), port = port.toIntOrNull() ?: 0, tls = tls, role = role, sellerVariant = variant, maxDailyOrders = capacity.toIntOrNull() ?: 0)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Working…" else "Connect / reconnect") }
            }
            if (settingsChanged) Text("Setup changed. Tap Connect / reconnect to apply it before sending.", color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            session?.let { active ->
                val connection by active.transport.connection.collectAsStateWithLifecycle()
                val ready by active.ready.collectAsStateWithLifecycle()
                val incomingError by active.error.collectAsStateWithLifecycle()
                val online = ready && connection == ConnectionState.Connected
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (online) "● BROKER READY · ${active.role}" else when(val status = connection) { is ConnectionState.Failed -> "Connection failed: ${status.reason}"; else -> "${connection}" }, color = if (online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                    if (BuildConfig.NUKKAD_ROLE == "dev") SelectionContainer { Text("CONNECTED CODE: ${active.sessionId}", style = MaterialTheme.typography.titleMedium) }
                    if (BuildConfig.NUKKAD_ROLE == "dev") Text("Broker ready does not confirm the other phone is reachable.", style = MaterialTheme.typography.bodySmall)
                    if (BuildConfig.NUKKAD_ROLE == "dev") Text(if (active.role == Role.LOCAL_LOOP) "IN-MEMORY · This phone only" else "MQTT · ${saved.host}:${saved.port}", style = MaterialTheme.typography.labelMedium)
                } }
                val log by active.diagnostics.collectAsStateWithLifecycle()
                if (active.role != Role.SELLER) {
                    val peerStatus by active.peerStatus.collectAsStateWithLifecycle()
                    Text(peerStatus, color = MaterialTheme.colorScheme.secondary)
                    OutlinedButton(onClick = vm::checkSeller, enabled = online && !busy && !settingsChanged) { Text("Check seller connection") }
                }
                if (BuildConfig.NUKKAD_ROLE == "dev") Text("Delivery diagnostics", style = MaterialTheme.typography.titleMedium)
                if (BuildConfig.NUKKAD_ROLE == "dev") SelectionContainer { Text("Request topic: ${active.requestTopic}", style = MaterialTheme.typography.bodySmall) }
                if (BuildConfig.NUKKAD_ROLE == "dev") log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                incomingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (active.role != Role.SELLER) {
                    val customer by active.customer.state.collectAsStateWithLifecycle()
                    val delivery by active.customer.delivery.collectAsStateWithLifecycle()
                    Text(delivery, color = MaterialTheme.colorScheme.primary)
                    DebugCustomerScreen(customer, online && !busy && !settingsChanged, { item, quantity, budget, hours, constraints, domain -> vm.send(item, quantity, budget, hours, constraints, domain) }, vm::retry, vm::selectOffer, vm::cancelOrder)
                }
                if (active.role != Role.CUSTOMER) {
                    val seller by active.seller.state.collectAsStateWithLifecycle()
                    val orders by active.seller.orders.collectAsStateWithLifecycle()
                    val orderEvent by active.seller.orderEvent.collectAsStateWithLifecycle()
                    DebugSellerScreen(active.seller.profile, seller, orders, orderEvent)
                }
                HorizontalDivider()
                if (BuildConfig.NUKKAD_ROLE == "dev") OutlinedButton(onClick = vm::reset, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reset harness · new session") }
                Text("Reset clears this phone’s requests and offers. Copy the new session ID to the other phones and reconnect them. M2 reserves capacity after seller acceptance. Unpaid holds expire after 10 minutes. A new demo session starts a fresh isolated ledger; do not reset during an order test.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun HarnessField(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth())
}





