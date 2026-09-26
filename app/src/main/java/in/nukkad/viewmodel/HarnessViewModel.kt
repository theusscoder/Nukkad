package `in`.nukkad.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.nukkad.debug.Seeds
import `in`.nukkad.model.*
import `in`.nukkad.transport.*
import `in`.nukkad.persistence.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class HarnessConfig(
    val sessionId: String = "demo-kondapur",
    val customerId: String = newId(),
    val sellerId: String = newId(),
    val host: String = "broker.hivemq.com",
    val port: Int = 1883,
    val tls: Boolean = false,
    val role: Role = Role.LOCAL_LOOP,
    val sellerVariant: Int = 0,
    val maxDailyOrders: Int = 3
)

class HarnessViewModel(application: Application) : AndroidViewModel(application) {
    private val configFile = File(application.filesDir, "harness.json")
    private val stored = runCatching { Protocol.json.decodeFromString(HarnessConfig.serializer(), configFile.readText()) }.getOrDefault(HarnessConfig()).let { if (it.host == "broker.hivemq.com" && it.port == 8883) it.copy(port = 1883, tls = false) else it }
    private val mutableConfig = MutableStateFlow(stored)
    val config = mutableConfig.asStateFlow()
    private val active = MutableStateFlow<HarnessSession?>(null)
    val session = active.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private var operation: Job? = null
    fun connect(input: HarnessConfig) {
        if (working.value) return
        working.value = true
        operation = viewModelScope.launch {
            failure.value = null
            try {
                val config = input.copy(sessionId = normalizeSession(input.sessionId))
                require(config.host.isNotBlank() && config.port in 1..65535)
                require(config.sellerVariant in 0..2)
                require(config.maxDailyOrders in 1..20) { "Capacity must be 1–20" }
                active.value?.close()
                active.value = null
                mutableConfig.value = config
                withContext(Dispatchers.IO) { configFile.writeText(Protocol.json.encodeToString(HarnessConfig.serializer(), config)) }
                val transport = if (config.role == Role.LOCAL_LOOP) FakeTransport(FakeBroker()) else MqttTransport(config.host.trim(), config.port, newId(), config.tls)
                val baseProfile = Seeds.seller(config.sellerId, config.sellerVariant)
                val profile = baseProfile.copy(rules = baseProfile.rules.copy(maxDailyOrders = config.maxDailyOrders))
                val directory = getApplication<Application>().filesDir
                val storagePrefix = if (config.role == Role.LOCAL_LOOP) "fake" else "mqtt"
                val runtime = HarnessSession(transport, config.sessionId, config.role, config.customerId, profile, viewModelScope,
                    customerStore = JsonStateStore(File(directory, "customer-$storagePrefix-${config.sessionId}.json"), CustomerLedger.serializer()) { CustomerLedger() },
                    sellerStore = JsonStateStore(File(directory, "seller-$storagePrefix-${config.sessionId}.json"), SellerLedger.serializer()) { SellerLedger() })
                active.value = runtime
                try { runtime.start() } catch (e: Exception) { runtime.close(); throw e }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { failure.value = e.message ?: "Connection failed" }
            finally { working.value = false }
        }
    }
    fun send(item: String, quantity: String, budget: String, hours: String, constraints: String, domain: String = "bakery") = action {
        val current = checkNotNull(active.value)
        check(current.ready.value && current.transport.connection.value == ConnectionState.Connected) { "Connect first" }
        val qty = quantity.toDoubleOrNull()
        val money = budget.toIntOrNull()
        val deadlineHours = hours.toLongOrNull()
        require(item.isNotBlank() && qty != null && qty.isFinite() && qty > 0 && money != null && money > 0 && deadlineHours != null && deadlineHours in 1..168) { "Enter an item, positive quantity, whole rupee budget, and deadline 1–168 hours ahead." }
        val request = Request(newId(), config.value.customerId, "kondapur", domain.trim().lowercase(), item.trim(), qty, "kg", money,
            System.currentTimeMillis() + deadlineHours * 3_600_000, constraints.split(',').map(::key).filter { it.isNotBlank() }.distinct())
        current.customer.send(request, System.currentTimeMillis())
    }
    fun retry() = action { checkNotNull(active.value).customer.retry(System.currentTimeMillis()) }
    fun selectOffer(offerId: String) = action { checkNotNull(active.value).customer.select(offerId, System.currentTimeMillis()) }
    fun cancelOrder() = action { checkNotNull(active.value).customer.cancel(System.currentTimeMillis()) }
    fun checkSeller() = action { checkNotNull(active.value).checkSeller() }
    fun reset() { connect(config.value.copy(sessionId = newSessionCode())) }
    private fun action(block: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            failure.value = null
            try { block() } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { failure.value = e.message ?: "Action failed" }
            finally { working.value = false }
        }
    }
    override fun onCleared() {
        val transport = active.value?.transport ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { try { transport.disconnect() } finally { cancel() } }
    }
}








