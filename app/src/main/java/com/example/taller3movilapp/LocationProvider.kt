package com.example.taller3movilapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast

class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun getLocation(callback: (Location?) -> Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                callback(loc)
                locationManager.removeUpdates(this)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                Toast.makeText(context, "Activa la ubicación (GPS)", Toast.LENGTH_SHORT).show()
            }
        }

        // Pedimos actualizaciones de ubicación
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)

        // Si hay una última ubicación, la usamos
        val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        callback(lastKnown)
    }
}
