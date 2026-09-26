package `in`.nukkad.model

import kotlinx.serialization.Serializable

/** All epoch values in this protocol are milliseconds. Money is whole INR. */
@Serializable
data class Request(
    val requestId: String,
    val customerId: String,
    val area: String,
    val category: String,
    val item: String,
    val quantity: Double?,
    val unit: String?,
    val budgetMax: Int?,
    val deadlineEpoch: Long?,
    val constraints: List<String>,
    val domain: CommerceDomain = CommerceDomain.from(category),
    val originalTranscript: String? = null,
    val normalizedText: String? = null
)


