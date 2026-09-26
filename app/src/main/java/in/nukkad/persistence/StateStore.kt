package `in`.nukkad.persistence

interface StateStore<T> { fun load(): T; fun save(value: T) }
class MemoryStore<T>(private var value: T) : StateStore<T> {
    override fun load(): T = value
    override fun save(value: T) { this.value = value }
}
