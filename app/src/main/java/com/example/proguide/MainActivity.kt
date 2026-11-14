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

    private val defaultMapStyle = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    private val satelliteMapStyle = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        mapView.getMapAsync { map ->
            mapLibreMap = map
            // Loading Default Map
            map.setStyle(defaultMapStyle) { style ->
                currentStyle = style
                enableLocationComponent(style)
                moveCameraToCurrentLocation()
            }
        }

        // My Location Button Logic
        val btnMyLocation = findViewById<FloatingActionButton>(R.id.btnMyLocation)
        btnMyLocation.setOnClickListener {
            moveCameraToCurrentLocation()
        }

        // Layers Button Logic
        val btnLayers = findViewById<FloatingActionButton>(R.id.btnLayers)
        btnLayers.setOnClickListener {
            if (mapLibreMap != null) {
                if (isSatelliteMode) {
                    mapLibreMap?.setStyle(defaultMapStyle) { style ->
                        currentStyle = style
                        enableLocationComponent(style)
                    }
                    isSatelliteMode = false
                    Toast.makeText(this, "Default Mode", Toast.LENGTH_SHORT).show()
                } else {
                    mapLibreMap?.setStyle(satelliteMapStyle) { style ->
                        currentStyle = style
                        enableLocationComponent(style)
                    }
                    isSatelliteMode = true
                    Toast.makeText(this, "Satellite Mode", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Green Dot (Location Component) enable karne ka function
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val locationComponent = mapLibreMap?.locationComponent
        val locationComponentOptions = LocationComponentActivationOptions.builder(this, loadedMapStyle)
            .useDefaultLocationEngine(true)
            .build()

        locationComponent?.activateLocationComponent(locationComponentOptions)
        locationComponent?.isLocationComponentEnabled = true
        locationComponent?.renderMode = RenderMode.COMPASS // Direction dikhane ke liye

        // Custom Green Dot
        locationComponent?.locationComponentOptions = locationComponent?.locationComponentOptions?.toBuilder()
            ?.accuracyColor(Color.parseColor("#4CAF50")) // Accuracy circle green
            ?.backgroundDrawableStale(null)
            ?.build()
    }

    @SuppressLint("MissingPermission")
    private fun moveCameraToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && mapLibreMap != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    val position = CameraPosition.Builder()
                        .target(userLocation)
                        .zoom(16.0)
                        .tilt(50.0)
                        .build()

                    mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            currentStyle?.let { enableLocationComponent(it) }
            moveCameraToCurrentLocation()
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
