package `in`.nukkad.product

import android.content.Context
import `in`.nukkad.model.*
import `in`.nukkad.debug.Seeds

class ProductStore(context: Context) {
    private val prefs = context.getSharedPreferences("product", Context.MODE_PRIVATE)
    fun read(key: String, default: String = "") = prefs.getString(key, default) ?: default
    fun write(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun profile(id: String): SellerProfile = runCatching {
        Protocol.json.decodeFromString(SellerProfile.serializer(), read("profile"))
    }.getOrElse { Seeds.seller(id) }.let { it.copy(sellerId = id, domains = setOf(CommerceDomain.from(it.category))) }
    fun saveProfile(profile: SellerProfile) { write("profile", Protocol.json.encodeToString(SellerProfile.serializer(), profile)) }
}
