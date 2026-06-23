package com.superencrypter.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GeoPoint(val latitude: Double, val longitude: Double)

class LocationService(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    suspend fun currentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { continuation.resume(null) }
            } catch (_: SecurityException) {
                continuation.resume(null)
            }
        }
    }

    fun distanceMeters(from: GeoPoint, to: GeoPoint): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            result
        )
        return result[0]
    }
}
