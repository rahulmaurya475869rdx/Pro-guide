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

    // Map Styles (Default and Satellite)
    private val defaultMapStyle = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    private val satelliteMapStyle = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // MapLibre Initialize
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // Location Client Initialize
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        // Map Load Hone ke baad ka logic
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(defaultMapStyle) { style ->
                currentStyle = style
                enableLocationComponent(style)
                moveCameraToCurrentLocation()
            }
        }

        // "My Location" (Zoom) Button Logic
        val btnMyLocation = findViewById<FloatingActionButton>(R.id.btnMyLocation)
        btnMyLocation.setOnClickListener {
            moveCameraToCurrentLocation()
        }

        // "Layers" (Switch Mode) Button Logic
        val btnLayers = findViewById<FloatingActionButton>(R.id.btnLayers)
        btnLayers.setOnClickListener {
            if (mapLibreMap != null) {
                if (isSatelliteMode) {
                    // Switch to Default
                    mapLibreMap?.setStyle(defaultMapStyle) { style ->
                        currentStyle = style
                        enableLocationComponent(style)
                    }
                    isSatelliteMode = false
                    Toast.makeText(this, "Default Mode ON", Toast.LENGTH_SHORT).show()
                } else {
                    // Switch to Satellite
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

    // Green Dot aur Compass enable karne ka function
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val locationComponent = mapLibreMap?.locationComponent
        
        // Custom Location Options (Green Color)
        val locationComponentOptions = LocationComponentOptions.builder(this)
            .accuracyColor(Color.parseColor("#4CAF50")) // Green accuracy circle
            .build()

        val activationOptions = LocationComponentActivationOptions.builder(this, loadedMapStyle)
            .useDefaultLocationEngine(true)
            .locationComponentOptions(locationComponentOptions) // Custom options yahan set hain
            .build()

        locationComponent?.activateLocationComponent(activationOptions)
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.renderMode = RenderMode.COMPASS // Direction (Compass) dikhane ke liye
    }

    // Camera ko current location par 3D tilt ke saath le jaane ka function
    @SuppressLint("MissingPermission")
    private fun moveCameraToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && mapLibreMap != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    
                    val position = CameraPosition.Builder()
                        .target(userLocation)
                        .zoom(16.0) // Zoom level
                        .tilt(50.0) // 3D View
                        .build()

                    mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000)
                }
            }
        }
    }

    // Permission Result Handle karna
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            currentStyle?.let { enableLocationComponent(it) }
            moveCameraToCurrentLocation()
        }
    }

    // MapView Lifecycle Methods (Inhe aise hi rehne dena hai)
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
