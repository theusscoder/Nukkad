package `in`.nukkad.model

@kotlinx.serialization.Serializable
data class Check(val name: String, val passed: Boolean, val detail: String)

