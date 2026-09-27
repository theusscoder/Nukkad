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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.nukkad.AppAudience
import `in`.nukkad.viewmodel.*
import `in`.nukkad.transport.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

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
    var selectedTab by rememberSaveable { mutableStateOf(if (merchant) "Home" else "Home") }
    var immersive by remember { mutableStateOf(false) }
    var modelPath by remember { mutableStateOf(store.read("gemma_model_path").takeIf { it.isNotBlank() }) }
    var modelMessage by remember { mutableStateOf("") }
    val notificationFocus by ProductNavigation.incomingFocus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            modelMessage = "Copying model into app storage…"
            val destination = File(context.filesDir, "models/gemma3-1b-it.litertlm")
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    destination.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use(input::copyTo) }
                        ?: error("Could not read the selected file")
                    require(destination.length() > 1024 * 1024) { "Selected file is too small to be a Gemma model" }
                    destination.absolutePath
                }
            }
            copied.onSuccess { modelPath = it; store.write("gemma_model_path", it); modelMessage = "Gemma model imported. It will load on the next request." }
                .onFailure { modelMessage = "Model import failed: ${it.message ?: "unknown error"}" }
        }
    }
    val connect = { vm.connect(config.copy(sessionId = "demo-kondapur", role = if (merchant) Role.SELLER else Role.CUSTOMER)) }
    LaunchedEffect(onboarded) { if (onboarded && runtime == null) connect() }
    LaunchedEffect(onboarded, merchant) {
        if (onboarded && merchant && Build.VERSION.SDK_INT >= 33 && store.read("notification_permission_asked") != "yes") {
            store.write("notification_permission_asked", "yes")
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(merchant) {
        val activity = context as? Activity
        if (merchant && activity?.intent?.getBooleanExtra(MerchantNotifications.EXTRA_OPEN_REQUEST, false) == true) {
            activity.intent.removeExtra(MerchantNotifications.EXTRA_OPEN_REQUEST)
            ProductNavigation.focusIncomingRequest()
        }
    }
    val observedRuntime = runtime
    val incomingRequest = if (merchant && observedRuntime != null) {
        val sellerState by observedRuntime.seller.state.collectAsStateWithLifecycle()
        (sellerState as? SellerState.Evaluated)?.request
    } else null
    LaunchedEffect(merchant, incomingRequest?.requestId) {
        incomingRequest?.let { request ->
            MerchantNotifications.show(context, observedRuntime?.seller?.profile?.shopName ?: "your shop", request)
        }
    }
    LaunchedEffect(notificationFocus) { if (notificationFocus > 0) page = "home" }
    Scaffold(bottomBar = {
        if (onboarded && page != "settings" && !immersive) {
            NukkadBottomNavigation(merchant, selectedTab) { selectedTab = it }
        }
    }) { padding ->
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
                SettingsScreen(store, merchant, error, runtime, busy, connect, modelPath, modelMessage,
                    onChooseModel = { modelPicker.launch(arrayOf("application/octet-stream", "application/x-tflite", "*/*")) },
                    onRemoveModel = { modelPath?.let { File(it).delete() }; modelPath = null; store.write("gemma_model_path", ""); modelMessage = "Gemma model removed." })
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
                if (merchant) MerchantHome(store, config.sellerId, active, online, busy, connect, notificationFocus,
                    selectedTab, { selectedTab = it }, { immersive = it })
                else CustomerHome(store, active, online && !busy, vm, modelPath, selectedTab,
                    { selectedTab = it })
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

@Composable private fun SettingsScreen(store: ProductStore, merchant: Boolean, error: String?, runtime: HarnessSession?, busy: Boolean, reconnect: () -> Unit,
                                       modelPath: String?, modelMessage: String, onChooseModel: () -> Unit, onRemoveModel: () -> Unit) {
    var diagnostics by remember { mutableStateOf(false) }
    Text("Make it yours.", style = MaterialTheme.typography.headlineLarge)
    SurfaceCard {
        Text("Language & voice", style = MaterialTheme.typography.titleLarge)
        Text("Interface language: English")
        Text("Hindi and Telugu speech input are available on the request screen.")
        var speech by remember { mutableStateOf(store.read("speech", "en-IN")) }
        LanguageChoice(speech) { speech = it; store.write("speech", it) }
        var spokenSummary by remember { mutableStateOf(store.read("spoken_offers") == "yes") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Speak offer summaries", modifier = Modifier.weight(1f))
            Switch(checked = spokenSummary, onCheckedChange = { spokenSummary = it; store.write("spoken_offers", if (it) "yes" else "no") })
        }
        Text("Offer summaries use the selected English, Hindi or Telugu speech language.", style = MaterialTheme.typography.bodySmall)
        if (merchant) {
            var spokenGuidance by remember { mutableStateOf(store.read("merchant_guidance", "yes") == "yes") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Speak new requests and shop mode", modifier = Modifier.weight(1f))
                Switch(checked = spokenGuidance, onCheckedChange = {
                    spokenGuidance = it; store.write("merchant_guidance", if (it) "yes" else "no")
                })
            }
            Text("Reads a short request summary after it appears on Home.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (!merchant) SurfaceCard {
        Text("Request interpreter", style = MaterialTheme.typography.titleLarge)
        Text("Deterministic fallback · Always available", color = MaterialTheme.colorScheme.secondary)
        Text(if (modelPath != null && File(modelPath).exists()) "Gemma 3 1B · Model imported; loads on first use" else "Gemma 3 1B · Model not installed")
        Text("Runs locally with LiteRT-LM CPU. If model loading or extraction fails, the request uses the deterministic fallback. No API key or database is used.")
        Button(onClick = onChooseModel) { Text(if (modelPath == null) "Choose Gemma .litertlm model" else "Replace Gemma model") }
        if (modelPath != null) TextButton(onClick = onRemoveModel) { Text("Remove model") }
        if (modelMessage.isNotBlank()) Text(modelMessage, style = MaterialTheme.typography.bodySmall)
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


