package com.example.proguide

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
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
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var isSatelliteMode = false
    private var currentStyle: Style? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnRecord: FloatingActionButton
    private lateinit var btnReturn: FloatingActionButton
    
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvNetStatus: TextView
    private lateinit var dotGps: View
    private lateinit var dotInternet: View
    
    private var isFirstLocationFix = true

    // --- RECORDING VARIABLES ---
    private var isRecording = false
    private var isReturning = false
    private var selectedVehicle = "Car"
    private var useSdCard = false // Storage option
    private val recordedPoints = mutableListOf<Feature>()
    
    private var lastRecordedLatLng: LatLng? = null
    private var lastRecordedBearing: Float = 0f // Direction track karne ke liye
    
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvNetStatus = findViewById(R.id.tvNetStatus)
        dotGps = findViewById(R.id.dotGps)
        dotInternet = findViewById(R.id.dotInternet)
        btnRecord = findViewById(R.id.btnRecord)
        btnReturn = findViewById(R.id.btnReturn)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapView.onCreate(savedInstanceState)

        setupCircleDot(dotGps, "#F44336")
        setupCircleDot(dotInternet, "#F44336")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            setupMap()
            startLocationUpdates()
            monitorInternetConnection()
        }

        findViewById<FloatingActionButton>(R.id.btnMyLocation).setOnClickListener {
            mapLibreMap?.let { LocationHelper.moveToCurrentLocation(this@MainActivity, it) }
        }

        findViewById<FloatingActionButton>(R.id.btnLayers).setOnClickListener {
            mapLibreMap?.let { map ->
                if (isSatelliteMode) {
                    map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                        currentStyle = style
                        setupRouteLayer(style)
                        LocationHelper.enableLocation(this@MainActivity, map, style)
                    }
                    isSatelliteMode = false
                } else {
                    map.setStyle(MapConfig.SATELLITE_STYLE) { style ->
                        currentStyle = style
                        setupRouteLayer(style)
                        LocationHelper.enableLocation(this@MainActivity, map, style)
                    }
                    isSatelliteMode = true
                }
            }
        }

        // Menu Button with Storage Options
        findViewById<FloatingActionButton>(R.id.btnMenu).setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Storage: Internal (Phone)")
            popup.menu.add(0, 2, 0, "Storage: SD Card")
            popup.menu.add(0, 3, 0, "Check Storage Usage")
            popup.menu.add(0, 4, 0, "Download Offline Map")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { useSdCard = false; Toast.makeText(this, "Storage set to Phone", Toast.LENGTH_SHORT).show() }
                    2 -> { useSdCard = true; Toast.makeText(this, "Storage set to SD Card", Toast.LENGTH_SHORT).show() }
                    3 -> {
                        val rSize = StorageHelper.getFolderSizeInMB(StorageHelper.getRoutesDir(this, useSdCard))
                        val mSize = StorageHelper.getFolderSizeInMB(StorageHelper.getMapsDir(this, useSdCard))
                        Toast.makeText(this, "Routes: $rSize\nMaps: $mSize", Toast.LENGTH_LONG).show()
                    }
                    4 -> {
                        mapLibreMap?.let { map ->
                            val styleToDownload = if (isSatelliteMode) MapConfig.SATELLITE_STYLE else MapConfig.DEFAULT_STYLE
                            OfflineMapManager.downloadCurrentArea(this, map, styleToDownload, useSdCard)
                        }
                    }
                }
                true
            }
            popup.show()
        }

        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else showVehicleSelectionDialog()
        }

        btnReturn.setOnClickListener {
            if (isReturning) stopReturning() else startReturning()
        }
    }

    private fun showVehicleSelectionDialog() {
        val vehicles = arrayOf("Bike", "Car", "Walk", "Cycle")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Vehicle for Recording")
        builder.setSingleChoiceItems(vehicles, 1) { _, which -> selectedVehicle = vehicles[which] }
        builder.setPositiveButton("Start") { _, _ -> startRecording() }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun setupRouteLayer(style: Style) {
        if (style.getSource(ROUTE_SOURCE_ID) != null) {
            style.removeLayer(ROUTE_LAYER_ID)
            style.removeSource(ROUTE_SOURCE_ID)
        }
        val geoJsonSource = GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray()))
        style.addSource(geoJsonSource)

        val circleLayer = CircleLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.get("color")),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor("#FFFFFF")
        )
        style.addLayer(circleLayer)
    }

    private fun startRecording() {
        isRecording = true
        isReturning = false
        recordedPoints.clear()
        lastRecordedLatLng = null
        lastRecordedBearing = 0f
        
        // File Save Shuru Karein
        RouteFileManager.startNewRoute(this, useSdCard)
        
        btnRecord.setImageResource(android.R.drawable.ic_media_pause)
        btnReturn.visibility = View.GONE
        Toast.makeText(this, "Recording Started ($selectedVehicle)", Toast.LENGTH_SHORT).show()
        mapLibreMap?.locationComponent?.cameraMode = CameraMode.TRACKING
    }

    private fun stopRecording() {
        isRecording = false
        btnRecord.setImageResource(android.R.drawable.ic_media_play)
        btnReturn.visibility = View.VISIBLE 
        
        // File ko Compress Karein (ZIP)
        RouteFileManager.compressCurrentRoute()
        
        Toast.makeText(this, "Saved & Compressed to ${if(useSdCard) "SD Card" else "Phone"}.", Toast.LENGTH_LONG).show()
    }

    private fun startReturning() {
        isReturning = true
        isRecording = false
        btnReturn.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this, "Return Mode: Follow the dots!", Toast.LENGTH_SHORT).show()
        
        val updatedFeatures = recordedPoints.map {
            val point = it.geometry() as Point
            val newFeature = Feature.fromGeometry(point)
            newFeature.addStringProperty("color", "#00E676") // Green
            newFeature
        }
        recordedPoints.clear()
        recordedPoints.addAll(updatedFeatures)
        updateRouteOnMap()
    }

    private fun stopReturning() {
        isReturning = false
        btnReturn.setImageResource(android.R.drawable.ic_menu_revert)
        Toast.makeText(this, "Return Mode Stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun updateRouteOnMap() {
        runOnUiThread {
            if (currentStyle == null) return@runOnUiThread
            val featureCollection = FeatureCollection.fromFeatures(recordedPoints.toTypedArray())
            val source = currentStyle?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
            source?.setGeoJson(featureCollection)
        }
    }

    // Vehicle wise minimum distance
    private fun getMinDistance(): Float {
        return when(selectedVehicle) {
            "Walk" -> 10f
            "Cycle" -> 15f
            "Bike" -> 20f
            "Car" -> 30f
            else -> 15f
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371000.0
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        val c = 2 * Math.atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }

    private fun setupCircleDot(view: View, colorHex: String) {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(Color.parseColor(colorHex))
        view.background = drawable
    }

    private fun setupMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                currentStyle = style
                setupRouteLayer(style)
                LocationHelper.enableLocation(this@MainActivity, map, style)
            }
        }
    }

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
                    
                    when {
                        accuracy < 10.0 -> { tvGpsStatus.text = "Exact (${accuracy.toInt()}m)"; setupCircleDot(dotGps, "#4CAF50") }
                        accuracy < 20.0 -> { tvGpsStatus.text = "Good (${accuracy.toInt()}m)"; setupCircleDot(dotGps, "#2196F3") }
                        accuracy < 50.0 -> { tvGpsStatus.text = "Avg (${accuracy.toInt()}m)"; setupCircleDot(dotGps, "#FFC107") }
                        else -> { tvGpsStatus.text = "Weak (${accuracy.toInt()}m)"; setupCircleDot(dotGps, "#F44336") }
                    }

                    if (accuracy <= 30.0) {
                        val userLoc = LatLng(location.latitude, location.longitude)
                        
                        if (isFirstLocationFix && mapLibreMap != null) {
                            val pos = CameraPosition.Builder().target(userLoc).zoom(16.0).tilt(50.0).build()
                            mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
                            isFirstLocationFix = false
                        }

                        // SMART RECORDING LOGIC (15m + Turn)
                        if (isRecording) {
                            var shouldRecord = false
                            
                            if (lastRecordedLatLng == null) {
                                shouldRecord = true
                            } else {
                                val dist = calculateDistance(
                                    lastRecordedLatLng!!.latitude, lastRecordedLatLng!!.longitude,
                                    userLoc.latitude, userLoc.longitude
                                )
                                
                                // 1. Distance Logic (15 meter)
                                if (dist > getMinDistance()) {
                                    shouldRecord = true
                                }
                                
                                // 2. Turn Logic (2 meter chalte hi 30 degree turn)
                                if (dist > 2f) {
                                    val bearingChange = abs(location.bearing - lastRecordedBearing)
                                    if (bearingChange > 30f) { // Direction change
                                        shouldRecord = true
                                    }
                                }
                            }

                            if (shouldRecord) {
                                addPointToRoute(userLoc, "#D500F9") // Purple
                                lastRecordedLatLng = userLoc
                                lastRecordedBearing = location.bearing
                                
                                // Direct file me save karein (RAM bachane ke liye)
                                RouteFileManager.appendPoint(userLoc.latitude, userLoc.longitude, "#D500F9")
                            }
                        } 
                        
                        // Return Logic
                        if (isReturning) {
                            var isOffRoute = true
                            for (feature in recordedPoints) {
                                val p = feature.geometry() as Point
                                val dist = calculateDistance(p.latitude(), p.longitude(), userLoc.latitude, userLoc.longitude)
                                if (dist < 40f) {
                                    isOffRoute = false
                                    break
                                }
                            }
                            if (isOffRoute) {
                                addPointToRoute(userLoc, "#FF1744") // Red
                            }
                        }
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    private fun addPointToRoute(latLng: LatLng, colorHex: String) {
        val point = Point.fromLngLat(latLng.longitude, latLng.latitude)
        val feature = Feature.fromGeometry(point)
        feature.addStringProperty("color", colorHex)
        recordedPoints.add(feature)
        updateRouteOnMap()
    }

    private fun monitorInternetConnection() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            tvNetStatus.text = "WiFi"
                            setupCircleDot(dotInternet, "#4CAF50")
                        } else {
                            tvNetStatus.text = "Data"
                            setupCircleDot(dotInternet, "#2196F3")
                        }
                    }
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread {
                    tvNetStatus.text = "Offline"
                    setupCircleDot(dotInternet, "#F44336")
                }
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupMap()
            startLocationUpdates()
            monitorInternetConnection()
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
