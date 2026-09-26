package `in`.nukkad.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.nukkad.AppAudience
import `in`.nukkad.viewmodel.*
import `in`.nukkad.transport.ConnectionState

@Composable
fun ProductApp(vm: HarnessViewModel) {
    val context = LocalContext.current
    val store = remember { ProductStore(context) }
    val merchant = AppAudience.NUKKAD_ROLE == "merchant"
    val config by vm.config.collectAsStateWithLifecycle()
    val runtime by vm.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var onboarded by remember { mutableStateOf(store.read("onboarded") == "yes") }
    var page by rememberSaveable { mutableStateOf("home") }
    val connect = { vm.connect(config.copy(sessionId = "demo-kondapur", role = if (merchant) Role.SELLER else Role.CUSTOMER)) }
    LaunchedEffect(onboarded) { if (onboarded && runtime == null) connect() }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("nukkad.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (onboarded) TextButton(onClick = { page = if (page == "home") "settings" else "home" }) {
                    Text(if (page == "home") "Settings" else "Back home")
                }
            }
            if (!onboarded) {
                Onboarding(merchant) { store.write("onboarded", "yes"); onboarded = true }
            } else if (page == "settings") {
                SettingsScreen(store, merchant, error, runtime, busy, connect)
            } else {
                val active = runtime
                val connection = active?.transport?.connection?.collectAsStateWithLifecycle()?.value
                val ready = active?.ready?.collectAsStateWithLifecycle()?.value == true
                val online = ready && connection == ConnectionState.Connected
                if (!online) {
                    SurfaceCard {
                        Text(if (busy) "Connecting to your market" else if (merchant) "Your agent is offline" else "Can't reach nearby sellers",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Keep this app open and check your internet connection.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = connect, enabled = !busy) { Text("Try again") }
                    }
                } else if (error != null) {
                    SurfaceCard {
                        Text("That action couldn't be completed.")
                        Text("Check your request details, then try again.")
                        TextButton(onClick = { vm.retry() }, enabled = !busy) { Text("Retry last request") }
                    }
                }
                if (merchant) MerchantHome(store, config.sellerId, active, online, busy, connect)
                else CustomerHome(store, active, online && !busy, vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable private fun Onboarding(merchant: Boolean, done: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val titles = if (merchant) listOf("Your shop.\nYour rules.\nYour agent.", "Set your\nrules once.", "Make room\nfor more orders.")
        else listOf("Your neighbourhood.\nOne request.", "Speak once.\nBe understood.", "Local shops.\nReal quotes.")
    val copy = if (merchant) listOf("Choose what you sell and how your shop responds.", "Set prices, supported options and capacity. Your agent checks every request.", "Your phone can quote while you're busy. Keep the app open during this demo.")
        else listOf("Tell local businesses what you need. They respond with real offers.", "Hindi · Telugu · English\nSpeak or type, review your request, then send.", "Compare prices and ready times without endless calls.")
    Spacer(Modifier.height(40.dp))
    Text("0${index + 1} / 03", color = MaterialTheme.colorScheme.secondary)
    Text(titles[index], style = MaterialTheme.typography.headlineLarge)
    Text(copy[index], style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(48.dp))
    Button(onClick = { if (index < 2) index++ else done() }, modifier = Modifier.fillMaxWidth().height(58.dp)) {
        Text(if (index < 2) "Continue" else if (merchant) "Set up shop" else "Get started")
    }
}

@Composable private fun SettingsScreen(store: ProductStore, merchant: Boolean, error: String?, runtime: HarnessSession?, busy: Boolean, reconnect: () -> Unit) {
    var diagnostics by remember { mutableStateOf(false) }
    Text("Make it yours.", style = MaterialTheme.typography.headlineLarge)
    SurfaceCard {
        Text("Language & voice", style = MaterialTheme.typography.titleLarge)
        Text("Interface language: English")
        Text("Hindi and Telugu speech input are available on the request screen.")
        var speech by remember { mutableStateOf(store.read("speech", "en-IN")) }
        LanguageChoice(speech) { speech = it; store.write("speech", it) }
        Text("Spoken offer responses arrive in the voice-summary pass.", style = MaterialTheme.typography.bodySmall)
    }
    SurfaceCard {
        Text("Request interpreter", style = MaterialTheme.typography.titleLarge)
        Text("Deterministic fallback · Active", color = MaterialTheme.colorScheme.secondary)
        Text("Gemma 3 1B · Unavailable")
        Text("No model loaded. Backend and inference latency are not available.")
    }
    SurfaceCard {
        Text("About Nukkad", style = MaterialTheme.typography.titleLarge)
        Text("AI interprets. Merchant rules authorize. Nukkad coordinates.")
        Text("Product preview · ${if (merchant) "Merchant" else "Customer"}")
        Text("Demo market uses internet. Local Wi-Fi discovery and payments are not enabled.")
        TextButton(onClick = { diagnostics = !diagnostics }) { Text("Developer / Diagnostics") }
        if (diagnostics) {
            Text(error ?: "No action error")
            runtime?.let {
                val log by it.diagnostics.collectAsStateWithLifecycle()
                val problem by it.error.collectAsStateWithLifecycle()
                Text("Market: ${it.sessionId}")
                Text("Topic: ${it.requestTopic}")
                problem?.let { message -> Text(message) }
                log.forEach { message -> Text(message, style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = reconnect, enabled = !busy) { Text("Reconnect") }
        }
    }
}
@Composable fun LanguageChoice(value: String, changed: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en-IN" to "English", "hi-IN" to "हिन्दी", "te-IN" to "తెలుగు").forEach { (tag, label) ->
            FilterChip(selected = value == tag, onClick = { changed(tag) }, label = { Text(label) })
        }
    }
}


