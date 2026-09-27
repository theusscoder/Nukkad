package `in`.nukkad.ai

import android.content.Context
import java.io.File

/** The merchant flavor does not package or use the customer-side language model. */
class GemmaLlmEngine(context: Context, modelFile: File) : LlmEngine, AutoCloseable {
    override val name = "Gemma unavailable in merchant app"
    override suspend fun extract(transcript: String, nowEpoch: Long, languageTag: String): IntentDraft =
        error("Gemma request interpretation is only available in the customer app")
    override fun close() = Unit
}
