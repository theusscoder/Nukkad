package `in`.nukkad.product

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import `in`.nukkad.MainActivity
import `in`.nukkad.R
import `in`.nukkad.model.Offer
import `in`.nukkad.model.Request
import `in`.nukkad.model.DiscoveryMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.speech.tts.TextToSpeech

object ProductNavigation {
    private val focus = kotlinx.coroutines.flow.MutableStateFlow(0)
    val incomingFocus = focus
    fun focusIncomingRequest() { focus.value += 1 }
}

object MerchantNotifications {
    private const val channelId = "incoming_requests"
    const val EXTRA_OPEN_REQUEST = "in.nukkad.OPEN_INCOMING_REQUEST"

    fun show(context: Context, shopName: String, request: Request) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Customer requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a request reaches your shop"
                enableVibration(true)
            })
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_REQUEST, true)
        }
        val pending = PendingIntent.getActivity(context, 7103, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val requestText = buildString {
            append(request.item)
            request.quantity?.let { append(" · $it ${request.unit.orEmpty()}") }
            if (request.constraints.isNotEmpty()) append(" · ${request.constraints.joinToString()}")
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New request for $shopName")
            .setContentText(requestText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${request.domain.name.lowercase().replaceFirstChar(Char::uppercase)} · $requestText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(request.requestId.hashCode(), notification)
    }
}

class OfferSpeaker(context: Context) : AutoCloseable {
    private var pending: Pair<Offer, String>? = null
    private var ready = false
    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) pending?.let { (offer, language) -> pending = null; speak(offer, language) }
    }

    fun speak(offer: Offer, languageTag: String) {
        if (!ready) { pending = offer to languageTag; return }
        val locale = Locale.forLanguageTag(languageTag)
        val localized = when (languageTag) {
            "hi-IN" -> "${offer.sellerName} की कीमत ${offer.amount} रुपये है। तैयार होने का समय ${time(offer.readyByEpoch)}।"
            "te-IN" -> "${offer.sellerName} ధర ${offer.amount} రూపాయలు. సిద్ధమయ్యే సమయం ${time(offer.readyByEpoch)}."
            else -> "${offer.sellerName} offers this for ${offer.amount} rupees. Ready by ${time(offer.readyByEpoch)}."
        }
        val result = engine.setLanguage(locale)
        if (result >= TextToSpeech.LANG_AVAILABLE) engine.speak(localized, TextToSpeech.QUEUE_FLUSH, null, "offer-${offer.offerId}")
    }

    private fun time(epoch: Long): String = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .withZone(ZoneId.of("Asia/Kolkata")).format(Instant.ofEpochMilli(epoch))

    override fun close() { pending = null; engine.stop(); engine.shutdown() }
}

class MerchantSpeaker(context: Context) : AutoCloseable {
    private var ready = false
    private var pending: Pair<String, String>? = null
    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) pending?.let { (text, tag) -> pending = null; speak(text, tag) }
    }

    fun announceMode(mode: DiscoveryMode, languageTag: String) {
        val phrase = when (languageTag) {
            "hi-IN" -> when (mode) {
                DiscoveryMode.EXACT -> "मैं केवल वही काम लूंगा जो मेरी दुकान में है।"
                DiscoveryMode.FLEX -> "मैं मिलते-जुलते काम भी देखूंगा।"
                DiscoveryMode.OPEN -> "मैं इस श्रेणी के सभी काम देखूंगा। कीमत मैं खुद तय करूंगा।"
            }
            "te-IN" -> when (mode) {
                DiscoveryMode.EXACT -> "నా దుకాణంలో ఉన్న పనులనే చూస్తాను."
                DiscoveryMode.FLEX -> "సంబంధిత పనులను కూడా చూస్తాను."
                DiscoveryMode.OPEN -> "ఈ విభాగంలోని పనులను చూస్తాను. ధరను నేనే నిర్ధారిస్తాను."
            }
            else -> when (mode) {
                DiscoveryMode.EXACT -> "Only items in your shop."
                DiscoveryMode.FLEX -> "Similar work too."
                DiscoveryMode.OPEN -> "All requests in your category. You set the price."
            }
        }
        speak(phrase, languageTag)
    }

    fun speakRequest(request: Request, languageTag: String) {
        val quantity = request.quantity?.let { "$it ${request.unit.orEmpty()}" }.orEmpty()
        val options = request.constraints.joinToString()
        val budget = request.budgetMax?.let { "$it" }.orEmpty()
        val phrase = when (languageTag) {
            "hi-IN" -> listOf(quantity, options, request.item, budget.takeIf { it.isNotBlank() }?.let { "बजट $it रुपये" })
                .filterNotNull().filter(String::isNotBlank).joinToString("। ")
            "te-IN" -> listOf(request.item, quantity, options, budget.takeIf { it.isNotBlank() }?.let { "బడ్జెట్ $it రూపాయలు" })
                .filterNotNull().filter(String::isNotBlank).joinToString(". ")
            else -> listOf(quantity, options, request.item, budget.takeIf { it.isNotBlank() }?.let { "budget $it rupees" })
                .filterNotNull().filter(String::isNotBlank).joinToString(". ")
        }
        if (phrase.isNotBlank()) speak(phrase, languageTag)
    }

    private fun speak(text: String, languageTag: String) {
        if (!ready) { pending = text to languageTag; return }
        if (engine.setLanguage(Locale.forLanguageTag(languageTag)) >= TextToSpeech.LANG_AVAILABLE) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "merchant-guidance")
        }
    }

    override fun close() { pending = null; engine.stop(); engine.shutdown() }
}
