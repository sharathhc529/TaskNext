package com.example.taskreminder.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Turns the phone's approximate (city-level) location into "City, Country". Never tracks in the background. */
object ApproxLocation {
    private const val TIMEOUT_MS = 10_000L

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns e.g. "Bengaluru, India", or null if permission is missing or nothing could be found. */
    suspend fun detect(context: Context): String? {
        if (!hasPermission(context)) return null
        val location = withTimeoutOrNull(TIMEOUT_MS) { currentLocation(context) } ?: return null
        return withContext(Dispatchers.IO) { describe(context, location) }
    }

    @SuppressLint("MissingPermission") // checked in detect()
    private suspend fun currentLocation(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        // A recent cached fix is plenty for city level
        providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            ?.let { return it }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }

    private suspend fun describe(context: Context, location: Location): String {
        val fallback = String.format(Locale.US, "Near %.2f, %.2f", location.latitude, location.longitude)
        if (!Geocoder.isPresent()) return fallback
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses.firstOrNull())
                            override fun onError(errorMessage: String?) = continuation.resume(null)
                        })
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            }
        } catch (e: Exception) {
            null
        }
        val place = listOfNotNull(
            address?.locality ?: address?.subAdminArea ?: address?.adminArea,
            address?.countryName
        ).distinct()
        return if (place.isEmpty()) fallback else place.joinToString(", ")
    }
}
