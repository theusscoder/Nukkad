package `in`.nukkad.product

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import `in`.nukkad.model.GeoPoint
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceLocation(context: Context) {
    private val app = context.applicationContext
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun current(): GeoPoint? {
        check(hasPermission()) { "Allow location to show nearby shops." }
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }
            LocationServices.getFusedLocationProviderClient(app)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location?.let {
                        GeoPoint(it.latitude, it.longitude, it.accuracy).takeIf(GeoPoint::isValid)
                    })
                }
                .addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWithException(failure) }
        }
    }
}

fun straightLineDistanceMeters(from: GeoPoint, to: GeoPoint): Float? {
    if (!from.isValid() || !to.isValid()) return null
    val result = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, result)
    return result.firstOrNull()?.takeIf(Float::isFinite)
}

fun approximateDistanceLabel(meters: Float, accuracy: Float = 0f): String {
    val value = meters.coerceAtLeast(0f)
    val uncertainty = accuracy.coerceAtLeast(0f)
    return if (value < 1000f) {
        val step = if (uncertainty > 150f) 100 else if (uncertainty > 50f) 50 else 10
        "~${(value / step).toInt() * step} m away"
    } else {
        val rounded = if (uncertainty > 250f) kotlin.math.floor(value / 1000f * 2f) / 2f
            else kotlin.math.round(value / 100f) / 10f
        "~${"%.1f".format(java.util.Locale.US, rounded)} km away"
    }
}
