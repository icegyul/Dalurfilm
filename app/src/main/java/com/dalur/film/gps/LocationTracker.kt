package com.dalur.film.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTracker(private val context: Context) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locations(intervalMs: Long = 3000): Flow<Location> = callbackFlow {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                for (l in r.locations) trySend(l)
            }
        }
        try {
            client.requestLocationUpdates(req, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(cb) }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastFix(): Location? = try {
        var done: Location? = null
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { done = it; if (cont.isActive) cont.resume(true) {} }
                .addOnFailureListener { if (cont.isActive) cont.resume(false) {} }
        }
        done
    } catch (_: SecurityException) { null }
}
