package com.example

import android.hardware.GeomagneticField
import kotlin.math.*

object QiblaMath {
    // Exact Kaaba (Mecca) coordinates
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262

    /**
     * Calculates the Qibla bearing (clockwise angle from True North) in degrees.
     * Formula: Q = atan2(sin(Δλ), cos(φA) * tan(φB) - sin(φA) * cos(Δλ))
     */
    fun calculateQiblaBearing(userLat: Double, userLon: Double): Double {
        val phiA = Math.toRadians(userLat)
        val phiB = Math.toRadians(KAABA_LAT)
        val deltaLon = Math.toRadians(KAABA_LON - userLon)

        val y = sin(deltaLon)
        val x = cos(phiA) * tan(phiB) - sin(phiA) * cos(deltaLon)

        val qiblaRad = atan2(y, x)
        val qiblaDeg = Math.toDegrees(qiblaRad)

        // Ensure bearing is between [0, 360) degrees
        return (qiblaDeg + 360.0) % 360.0
    }

    /**
     * Estimates distance from the user's location to the Kaaba in kilometers.
     */
    fun calculateDistanceToKaaba(userLat: Double, userLon: Double): Double {
        val r = 6371.0 // Radius of earth in km
        val dLat = Math.toRadians(KAABA_LAT - userLat)
        val dLon = Math.toRadians(KAABA_LON - userLon)
        
        val a = (sin(dLat / 2).pow(2) + 
                cos(Math.toRadians(userLat)) * cos(Math.toRadians(KAABA_LAT)) * 
                sin(dLon / 2).pow(2)).coerceIn(0.0, 1.0)
                
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    /**
     * Returns the magnetic declination correction factor in degrees using Android's WMM utility.
     * Positive means East, negative means West.
     */
    fun getMagneticDeclination(userLat: Double, userLon: Double, userAlt: Double = 0.0): Float {
        val timeMillis = System.currentTimeMillis()
        return try {
            val safeLat = userLat.coerceIn(-90.0, 90.0).toFloat()
            val safeLon = userLon.coerceIn(-180.0, 180.0).toFloat()
            val safeAlt = userAlt.toFloat()
            val geomagneticField = GeomagneticField(
                safeLat,
                safeLon,
                safeAlt,
                timeMillis
            )
            geomagneticField.declination
        } catch (e: Exception) {
            0f
        }
    }
}
