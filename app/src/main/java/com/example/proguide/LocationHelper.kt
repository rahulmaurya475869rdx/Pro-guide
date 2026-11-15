package com.example.proguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

object LocationHelper {

    fun enableLocation(context: Context, map: MapLibreMap, style: Style) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val locationComponent = map.locationComponent
        
        val options = LocationComponentOptions.builder(context)
            .accuracyColor(Color.parseColor("#4CAF50"))
            .build()

        val activationOptions = LocationComponentActivationOptions.builder(context, style)
            .useDefaultLocationEngine(true)
            .locationComponentOptions(options)
            .build()

        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.renderMode = RenderMode.COMPASS
    }

    @SuppressLint("MissingPermission")
    fun moveToCurrentLocation(context: Context, map: MapLibreMap) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    moveCamera(map, location.latitude, location.longitude)
                } else {
                    // Agar lastLocation null hai, toh naya location fetch karein
                    fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { currentLoc ->
                            if (currentLoc != null) {
                                moveCamera(map, currentLoc.latitude, currentLoc.longitude)
                            } else {
                                Toast.makeText(context, "GPS signal dhoond rahe hain, thoda wait karein...", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }
        }
    }

    private fun moveCamera(map: MapLibreMap, lat: Double, lng: Double) {
        val userLoc = LatLng(lat, lng)
        val pos = CameraPosition.Builder()
            .target(userLoc)
            .zoom(16.0) // Zoom level
            .tilt(50.0) // 3D view
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
    }
}
