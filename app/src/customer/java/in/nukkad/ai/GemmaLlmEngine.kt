package `in`.nukkad.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import `in`.nukkad.model.CommerceDomain
import java.io.File

/** Local-only request extraction. The model never authorizes, prices, or chooses a merchant. */
@OptIn(ExperimentalApi::class)
class GemmaLlmEngine(context: Context, modelFile: File) : LlmEngine, AutoCloseable {
    private val appContext = context.applicationContext
    private val modelPath = modelFile.absolutePath
    private val lock = Mutex()
    private var engine: Engine? = null
    override val name = "Gemma 3 1B · on-device"

    override suspend fun extract(transcript: String, nowEpoch: Long, languageTag: String): IntentDraft = lock.withLock {
        require(transcript.isNotBlank()) { "Request text is empty" }
        val activeEngine = withContext(Dispatchers.IO) {
            engine ?: Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = appContext.cacheDir.absolutePath))
                .also { it.initialize(); engine = it }
        }
        val prompt = """
            Extract a local-commerce request from the transcript below. Return one JSON object only, no markdown.
            Allowed domain values: MEDICINES, BAKERY, CARPENTRY, OTHER.
            Keys: domain, item, quantity, unit, budgetMax, deadlineText, constraints, confidence.
            Infer the category from the item name (for example Dolo 650 is MEDICINES, cake is BAKERY,
            cupboard hinge repair is CARPENTRY). A medicine strength such as 650 in Dolo 650 is part of the item,
            never the requested quantity. Quantity means count/amount explicitly requested (one strip, 2 tablets, 1 kg).
            Return the exact mentioned item name and fill every fact stated. Use null for any missing quantity, unit, budgetMax, or deadlineText. Do not guess, calculate, prescribe,
            set prices, select a shop, or infer symptoms as a medicine order. Preserve only explicitly stated details.
            Keep item and constraints in the transcript's language. confidence must be 0..1.
            Language tag: $languageTag
            Transcript: ${transcript.take(1200)}
        """.trimIndent()
        val response = withContext(Dispatchers.IO) {
            val conversation = activeEngine.createConversation()
            try {
                val message = conversation.sendMessage(prompt)
                // The response is already rendered text. renderMessageIntoString applies the
                // model's input chat template again and fails for this model's output message.
                message.contents.toString()
            } finally {
                conversation.close()
            }
        }
        parseResponse(response, transcript)
    }

    private fun parseResponse(response: String, transcript: String): IntentDraft {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        require(start >= 0 && end > start) { "Gemma returned no JSON object" }
        val root = Json { isLenient = true; ignoreUnknownKeys = true }.parseToJsonElement(response.substring(start, end + 1)).jsonObject
        fun string(key: String) = root[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        val lowerDomain = string("domain")?.uppercase().orEmpty()
        val transcriptDomain = inferDomain(transcript)
        val modelDomain = when {
            lowerDomain in setOf("MEDICINES", "MEDICINE", "PHARMACY") -> CommerceDomain.PHARMACY
            lowerDomain in setOf("BAKERY", "BAKE") -> CommerceDomain.BAKERY
            lowerDomain in setOf("CARPENTRY", "CARPENTER", "WOODWORK") -> CommerceDomain.CARPENTRY
            else -> CommerceDomain.OTHER
        }
        // Strong, explicit product/service evidence wins over an inconsistent model label.
        val domain = transcriptDomain.takeIf { it != CommerceDomain.OTHER } ?: modelDomain
        val item = string("item").orEmpty().ifBlank { inferItem(transcript, domain) }
        val quantityValue = root.numberValue("quantity")
        val budgetValue = root.numberValue("budgetMax")?.toInt()
        val deadline = string("deadlineText")
        // Never trust model-generated numbers unless those digits appeared in the user's own transcript.
        val quantity = quantityValue?.takeIf { it > 0 && containsExplicitQuantity(transcript, it) }
        val budget = budgetValue?.takeIf { it > 0 && containsNumericEvidence(transcript, it.toDouble()) }
        val supportedDeadline = deadline?.takeIf { value ->
            value.lowercase().split(Regex("\\W+")).any { token -> token.length >= 3 && transcript.contains(token, ignoreCase = true) }
        }
        val constraints = runCatching { root["constraints"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList() }.getOrDefault(emptyList())
            .filter { candidate -> candidate.isNotBlank() && transcript.contains(candidate, ignoreCase = true) }
        val confidence = root["confidence"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 1.0)
        val warnings = buildList {
            if (domain == CommerceDomain.OTHER) add("Confirm the category")
            if (item.isBlank()) add("Confirm the item or service")
            if (quantity == null) add("Confirm quantity and unit")
            if (budgetValue != null && budget == null) add("Budget was not grounded in the transcript; confirm it")
            if (confidence != null && confidence < 0.55) add("Gemma is unsure; review every field")
        }
        return IntentDraft(domain = domain, item = item, quantity = quantity, unit = string("unit")?.takeIf { quantity != null },
            budgetMax = budget, constraints = constraints, originalTranscript = transcript, normalizedText = transcript,
            needsConfirmation = true, warnings = warnings, confidence = confidence, deadlineText = supportedDeadline, interpreter = name)
    }

    private fun JsonObject.numberValue(key: String): Double? = runCatching {
        this[key]?.jsonPrimitive?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    }.getOrNull()

    private fun inferDomain(transcript: String): CommerceDomain {
        val text = transcript.lowercase()
        return when {
            listOf("dolo", "medicine", "medicines", "tablet", "pharmacy", "दवा", "दवाई", "మందు", "మందులు").any(text::contains) -> CommerceDomain.PHARMACY
            listOf("cake", "bakery", "bread", "pastry", "केक", "రొట్టె", "కేక్").any(text::contains) -> CommerceDomain.BAKERY
            listOf("carpenter", "carpentry", "cupboard", "hinge", "woodwork", "बढ़ई", "अलमारी", "కార్పెంటర్", "అల్మారా").any(text::contains) -> CommerceDomain.CARPENTRY
            else -> CommerceDomain.OTHER
        }
    }

    private fun inferItem(transcript: String, domain: CommerceDomain): String {
        val text = transcript.lowercase()
        return when (domain) {
            CommerceDomain.PHARMACY -> when {
                Regex("\\bdolo\\s*[- ]?650\\b", RegexOption.IGNORE_CASE).containsMatchIn(transcript) -> "Dolo 650"
                Regex("\\bdolo\\b", RegexOption.IGNORE_CASE).containsMatchIn(transcript) -> "Dolo"
                else -> ""
            }
            CommerceDomain.BAKERY -> when {
                "chocolate cake" in text -> "chocolate cake"
                "cake" in text || "కేక్" in text || "केक" in text -> "cake"
                "bread" in text || "రొట్టె" in text -> "bread"
                else -> ""
            }
            CommerceDomain.CARPENTRY -> when {
                "hinge" in text -> "cupboard hinge repair"
                "cupboard" in text || "अलमारी" in text || "అల్మారా" in text -> "cupboard repair"
                else -> ""
            }
            else -> ""
        }
    }

    private fun containsExplicitQuantity(transcript: String, value: Double): Boolean {
        val words = when (value.toInt()) {
            1 -> listOf("one", "a", "an", "ek", "एक", "ఒక", "ఒక్క")
            2 -> listOf("two", "do", "दो", "రెండు")
            3 -> listOf("three", "teen", "तीन", "మూడు")
            4 -> listOf("four", "चार", "నాలుగు")
            5 -> listOf("five", "paanch", "पांच", "ఐదు")
            6 -> listOf("six", "छह", "ఆరు")
            7 -> listOf("seven", "सात", "ఏడు")
            8 -> listOf("eight", "आठ", "ఎనిమిది")
            9 -> listOf("nine", "नौ", "తొమ్మిది")
            10 -> listOf("ten", "दस", "పది")
            else -> emptyList()
        }
        val numberTokens = listOf(value.toString().removeSuffix(".0")) + words
        val unit = "(?:kg|kilo(?:gram)?s?|g|gram(?:s)?|strip(?:s)?|tablet(?:s)?|tab(?:s)?|capsule(?:s)?|piece(?:s)?|pcs|packet(?:s)?|box(?:es)?|bottle(?:s)?|dozen|service|hours?|दिन|दिनों|गोली|गोलियां|स्ट्रिप|पत्ता|పట్టీ|మాత్రలు|గంటలు|కిలోలు?)"
        return numberTokens.any { token ->
            val n = Regex.escape(token)
            Regex("(?<![\\p{L}\\d])$n\\s*$unit(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(transcript) ||
                Regex("$unit\\s*$n(?![\\p{L}\\d])", RegexOption.IGNORE_CASE).containsMatchIn(transcript)
        }
    }

    private fun containsNumericEvidence(transcript: String, value: Double): Boolean {
        val plain = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        val decimal = value.toString()
        val normalized = transcript.replace(',', '.')
        return Regex("(?<![\\p{L}\\d])${Regex.escape(plain)}(?![\\p{L}\\d])").containsMatchIn(normalized) ||
            Regex("(?<![\\p{L}\\d])${Regex.escape(decimal)}(?![\\p{L}\\d])").containsMatchIn(normalized)
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
