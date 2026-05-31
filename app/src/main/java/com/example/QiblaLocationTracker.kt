package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QiblaLocationTracker(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow

    private val _isGpsEnabled = MutableStateFlow(true)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled

    private var nativeLocationListener: LocationListener? = null

    init {
        checkGpsStatus()
    }

    fun checkGpsStatus() {
        try {
            _isGpsEnabled.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            _isGpsEnabled.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(forceRefresh: Boolean = false, onLocationReceived: ((Location) -> Unit)? = null) {
        stopLocationUpdates() // Cleanly tear down any active callback to avoid duplication
        checkGpsStatus()
        
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        // 1. Get last known location from all native providers instantly
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null) {
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (e: SecurityException) {
                // Ignore missing permissions or sandbox security restrictions
            } catch (e: Exception) {
                // Ignore other exceptions
            }
        }

        if (bestLocation != null) {
            _locationFlow.value = bestLocation
            onLocationReceived?.invoke(bestLocation)
            // Stop-on-Fix: if we aren't forcing a refresh and the last known location is precise, don't spin up hardware
            if (!forceRefresh && bestLocation.accuracy > 0 && bestLocation.accuracy <= 30.0f) {
                return
            }
        }

        // 2. Setup standard listener for real-time location tracking
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val currentBest = _locationFlow.value
                if (currentBest == null || location.time >= currentBest.time || location.accuracy < currentBest.accuracy) {
                    _locationFlow.value = location
                    onLocationReceived?.invoke(location)
                }
                // Stop-on-Fix: if accuracy is good, shut down active GPS hardware updates to save battery
                if (location.accuracy > 0 && location.accuracy <= 30.0f) {
                    stopLocationUpdates()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        nativeLocationListener = listener

        // 3. Request updates from GPS and Network providers safely
        for (provider in providers) {
            if (provider == LocationManager.PASSIVE_PROVIDER) continue
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        4000L, // Receive updates every 4 seconds
                        1.0f,  // Or if they move 1 meter
                        listener,
                        context.mainLooper
                    )
                }
            } catch (e: SecurityException) {
                // Ignore security constraint in test sandbox
            } catch (e: Exception) {
                // Ignore other registration exceptions
            }
        }
    }

    fun stopLocationUpdates() {
        try {
            nativeLocationListener?.let {
                locationManager.removeUpdates(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        nativeLocationListener = null
    }
}
