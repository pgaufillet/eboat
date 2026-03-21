package com.eboat.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.eboat.domain.model.BoatState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1_000L
    ).setMinUpdateDistanceMeters(1f).build()

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<BoatState> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    BoatState(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speedOverGround = location.speed * 1.94384f, // m/s to knots
                        courseOverGround = location.bearing,
                        accuracy = location.accuracy,
                        hasPosition = true
                    )
                )
            }
        }
        fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
