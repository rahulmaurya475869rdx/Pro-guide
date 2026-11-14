package com.example.proguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isSatelliteMode = false
    private var currentStyle: Style? = null

    // Aapki MapTiler API Key
    private val apiKey = "O3IKvQesO3ZZuzn5FfXP"

    // Google Maps jaisa Colorful Default Map
    private val defaultMapStyle = "https://api.maptiler.com/maps/streets-v2/style.json?key=$apiKey"
    
    // Clear Satellite Map (Labels ke saath)
    private val satelliteMapStyle = "https://api.maptiler.com/maps/hybrid/style.json?key=$apiKey"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(defaultMapStyle) { style ->
                currentStyle = style
                enableLocationComponent(style)
            }
        }

        // My Location Button
        val btnMyLocation = findViewById<FloatingActionButton>(R.id.btnMyLocation)
        btnMyLocation.setOnClickListener {
            moveCameraToCurrentLocation()
        }

        // Layers Button
        val btnLayers = findViewById<FloatingActionButton>(R.id.btnLayers)
        btnLayers.setOnClickListener {
            if (mapLibreMap != null) {
                if (isSatelliteMode) {
                    mapLibreMap?.setStyle(defaultMapStyle) { style ->
                        currentStyle = style
                        enableLocationComponent(style)
                    }
                    isSatelliteMode = false
                    Toast.makeText(this, "Default Mode ON", Toast.LENGTH_SHORT).show()
                } else {
                    mapLibreMap?.setStyle(satelliteMapStyle) { style ->
                        currentStyle = style
                        enableLocationComponent(style)
                    }
                    isSatelliteMode = true
                    Toast.makeText(this, "Satellite Mode ON", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val locationComponent = mapLibreMap?.locationComponent
        
        val locationComponentOptions = LocationComponentOptions.builder(this)
            .accuracyColor(Color.parseColor("#4CAF50")) // Green accuracy circle
            .build()

        val activationOptions = LocationComponentActivationOptions.builder(this, loadedMapStyle)
            .useDefaultLocationEngine(true) // Default Engine (Error free)
            .locationComponentOptions(locationComponentOptions)
            .build()

        locationComponent?.activateLocationComponent(activationOptions)
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.renderMode = RenderMode.COMPASS
        
        moveCameraToCurrentLocation()
    }

    @SuppressLint("MissingPermission")
    private fun moveCameraToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Yahan hum FusedLocationClient use kar rahe hain jo build.gradle mein pehle se hai
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && mapLibreMap != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    val position = CameraPosition.Builder()
                        .target(userLocation)
                        .zoom(16.0)
                        .tilt(50.0) // 3D view
                        .build()

                    mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000)
                } else {
                    Toast.makeText(this, "Location nahi mil rahi, thoda wait karein.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            currentStyle?.let { enableLocationComponent(it) }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
