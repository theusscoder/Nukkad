package `in`.nukkad.persistence

import android.util.AtomicFile
import `in`.nukkad.model.Protocol
import kotlinx.serialization.KSerializer
import java.io.File

/** Crash-safe local JSON. Corrupt state fails closed instead of silently forgetting reservations. */
class JsonStateStore<T>(file: File, private val serializer: KSerializer<T>, private val empty: () -> T) : StateStore<T> {
    private val atomic = AtomicFile(file)
    override fun load(): T = if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) empty()
        else atomic.openRead().use { Protocol.json.decodeFromString(serializer, it.readBytes().toString(Charsets.UTF_8)) }
    override fun save(value: T) {
        val bytes = Protocol.json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
}
