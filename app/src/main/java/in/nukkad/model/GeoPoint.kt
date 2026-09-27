package `in`.nukkad.model

import kotlinx.serialization.Serializable

/** Coordinates are kept locally for shops; offer coordinates are deliberately coarsened. */
@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null) {
    fun isValid(): Boolean = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun publicApproximation(): GeoPoint = copy(latitude = kotlin.math.round(latitude * 1000.0) / 1000.0,
        longitude = kotlin.math.round(longitude * 1000.0) / 1000.0, accuracyMeters = maxOf(accuracyMeters ?: 0f, 120f))
}
