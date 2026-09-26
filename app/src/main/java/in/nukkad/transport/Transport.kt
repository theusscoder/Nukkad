package `in`.nukkad.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class Packet(val topic: String, val payload: String)
sealed interface ConnectionState {
    data object Offline : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}
interface Transport {
    val packets: Flow<Packet>
    val connection: StateFlow<ConnectionState>
    suspend fun connect()
    suspend fun subscribe(topic: String)
    suspend fun publish(topic: String, payload: String)
    suspend fun disconnect()
}
