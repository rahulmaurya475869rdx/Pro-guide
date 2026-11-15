package com.example.proguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var isSatelliteMode = false
    private var currentStyle: Style? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    // UI Elements
    private lateinit var dotGps: View
    private lateinit var dotInternet: View
    private lateinit var rowGps: LinearLayout
    private lateinit var rowInternet: LinearLayout
    
    // Status Variables
    private var currentGpsStatus = "Searching..."
    private var currentInternetStatus = "Offline"
    private var isFirstLocationFix = true // Pehli baar map ko move karne ke liye

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        dotGps = findViewById(R.id.dotGps)
        dotInternet = findViewById(R.id.dotInternet)
        rowGps = findViewById(R.id.rowGps)
        rowInternet = findViewById(R.id.rowInternet)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapView.onCreate(savedInstanceState)

        // Click listeners for Status Popups
        rowGps.setOnClickListener {
            Toast.makeText(this, "GPS Status: $currentGpsStatus", Toast.LENGTH_SHORT).show()
        }
        rowInternet.setOnClickListener {
            Toast.makeText(this, "Internet: $currentInternetStatus", Toast.LENGTH_SHORT).show()
        }

        // Permission check
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            setupMap()
            startLocationUpdates()
            monitorInternetConnection()
        }

        // Buttons setup
        findViewById<FloatingActionButton>(R.id.btnMyLocation).setOnClickListener {
            mapLibreMap?.let { LocationHelper.moveToCurrentLocation(this@MainActivity, it) }
        }

        findViewById<FloatingActionButton>(R.id.btnLayers).setOnClickListener {
            mapLibreMap?.let { map ->
                if (isSatelliteMode) {
                    map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                        currentStyle = style
                        LocationHelper.enableLocation(this@MainActivity, map, style)
                    }
                    isSatelliteMode = false
                    Toast.makeText(this@MainActivity, "Default Mode", Toast.LENGTH_SHORT).show()
                } else {
                    map.setStyle(MapConfig.SATELLITE_STYLE) { style ->
                        currentStyle = style
                        LocationHelper.enableLocation(this@MainActivity, map, style)
                    }
                    isSatelliteMode = true
                    Toast.makeText(this@MainActivity, "Satellite Mode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<FloatingActionButton>(R.id.btnDownload).setOnClickListener {
            mapLibreMap?.let { map ->
                val styleToDownload = if (isSatelliteMode) MapConfig.SATELLITE_STYLE else MapConfig.DEFAULT_STYLE
                OfflineMapManager.downloadCurrentArea(this@MainActivity, map, styleToDownload)
            }
        }
    }

    private fun setupMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                currentStyle = style
                LocationHelper.enableLocation(this@MainActivity, map, style)
            }
        }
    }

    // --- GPS LIVE TRACKING & FILTERING ---
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val accuracy = location.accuracy
                    
                    // 1. UPDATE STATUS COLORS & TEXT
                    when {
                        accuracy < 10.0 -> {
                            currentGpsStatus = "Excellent (${accuracy.toInt()}m)"
                            dotGps.setBackgroundColor(Color.parseColor("#4CAF50")) // Green
                        }
                        accuracy < 20.0 -> {
                            currentGpsStatus = "Good (${accuracy.toInt()}m)"
                            dotGps.setBackgroundColor(Color.parseColor("#2196F3")) // Blue
                        }
                        accuracy < 50.0 -> {
                            currentGpsStatus = "Average (${accuracy.toInt()}m)"
                            dotGps.setBackgroundColor(Color.parseColor("#FFC107")) // Yellow
                        }
                        else -> {
                            currentGpsStatus = "Weak (${accuracy.toInt()}m)"
                            dotGps.setBackgroundColor(Color.parseColor("#F44336")) // Red
                        }
                    }

                    // 2. FILTER CACHED / TOWER LOCATION
                    // Agar accuracy 30m se kharab hai, toh map ko mat hilao
                    if (accuracy <= 30.0) {
                        val userLoc = LatLng(location.latitude, location.longitude)
                        
                        // Pehli baar map ko move karo, uske baad user khud pan karega
                        if (isFirstLocationFix && mapLibreMap != null) {
                            val pos = CameraPosition.Builder()
                                .target(userLoc)
                                .zoom(16.0)
                                .tilt(50.0)
                                .build()
                            mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
                            isFirstLocationFix = false
                        }
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    // --- INTERNET MONITORING ---
    private fun monitorInternetConnection() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    // Check type: WiFi or Mobile Data
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            currentInternetStatus = "WiFi"
                            dotInternet.setBackgroundColor(Color.parseColor("#4CAF50")) // Green
                        } else {
                            currentInternetStatus = "Mobile Data"
                            dotInternet.setBackgroundColor(Color.parseColor("#2196F3")) // Blue
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    currentInternetStatus = "Offline"
                    dotInternet.setBackgroundColor(Color.parseColor("#F44336")) // Red
                }
            }
        })
        
        // Initial Check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            dotInternet.setBackgroundColor(Color.parseColor("#F44336"))
            currentInternetStatus = "Offline"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupMap()
            startLocationUpdates()
            monitorInternetConnection()
        } else {
            Toast.makeText(this, "GPS permission zaroori hai!", Toast.LENGTH_LONG).show()
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
