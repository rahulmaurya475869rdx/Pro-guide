package com.example.proguide

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location // IMPORTANT: Ye import add kiya hai
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Switch
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
import com.google.android.material.bottomsheet.BottomSheetDialog
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

class MainActivity : AppCompatActivity(), SensorEventListener {

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
    private lateinit var pulseGps: View
    private lateinit var dotInternet: View
    private lateinit var pulseInternet: View
    
    private var isFirstLocationFix = true
    
    // Preferences
    private lateinit var prefs: SharedPreferences
    private var isCompassModeEnabled = false
    private var useSdCard = false

    // Smooth Compass Sensor
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var smoothedBearing: Float = 0f

    // Recording Variables
    private var isRecording = false
    private var isReturning = false
    private var selectedVehicle = "Car"
    private val recordedPoints = mutableListOf<Feature>()
    private var lastRecordedLatLng: LatLng? = null
    private var lastRecordedBearing: Float = 0f
    private var lastOffRouteTime: Long = 0
    
    private val ROUTE_SOURCE_ID = "route-source"
    private val ROUTE_LAYER_ID = "route-layer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this@MainActivity)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("ProGuidePrefs", Context.MODE_PRIVATE)
        isCompassModeEnabled = prefs.getBoolean("compass_mode", false)
        useSdCard = prefs.getBoolean("use_sd_card", false)

        mapView = findViewById(R.id.mapView)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvNetStatus = findViewById(R.id.tvNetStatus)
        dotGps = findViewById(R.id.dotGps)
        pulseGps = findViewById(R.id.pulseGps)
        dotInternet = findViewById(R.id.dotInternet)
        pulseInternet = findViewById(R.id.pulseInternet)
        
        btnRecord = findViewById(R.id.btnRecord)
        btnReturn = findViewById(R.id.btnReturn)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
        mapView.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        updateStatusUI(dotGps, pulseGps, "#F44336")
        updateStatusUI(dotInternet, pulseInternet, "#F44336")

        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            setupMap()
            startLocationUpdates()
            monitorInternetConnection()
            startPulseAnimations() 
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

        findViewById<FloatingActionButton>(R.id.btnMenu).setOnClickListener {
            showBottomSheetMenu()
        }

        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else showVehicleSelectionDialog()
        }

        btnReturn.setOnClickListener {
            if (isReturning) stopReturning() else startReturning()
        }
    }

    // SENSOR LOGIC: Smooth Compass
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR && isCompassModeEnabled) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            var azimuth: Float = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0f) azimuth += 360f

            var delta: Float = azimuth - smoothedBearing
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            
            smoothedBearing += (0.15f * delta)
            if (smoothedBearing < 0f) smoothedBearing += 360f
            if (smoothedBearing >= 360f) smoothedBearing -= 360f

            mapLibreMap?.let { map ->
                val pos = CameraPosition.Builder(map.cameraPosition)
                    .bearing(smoothedBearing.toDouble())
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 100)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPulseAnimations() {
        pulseGps.visibility = View.VISIBLE
        pulseInternet.visibility = View.VISIBLE
        
        val scaleUpX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 3f)
        val scaleUpY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 3f)
        val fadeOut = PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 0f)

        val gpsAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseGps, scaleUpX, scaleUpY, fadeOut)
        gpsAnimator.duration = 1500L
        gpsAnimator.repeatCount = ValueAnimator.INFINITE
        gpsAnimator.interpolator = AccelerateDecelerateInterpolator()
        gpsAnimator.start()

        val internetAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseInternet, scaleUpX, scaleUpY, fadeOut)
        internetAnimator.duration = 1500L
        internetAnimator.repeatCount = ValueAnimator.INFINITE
        internetAnimator.interpolator = AccelerateDecelerateInterpolator()
        internetAnimator.start()
    }

    private fun updateStatusUI(dotView: View, pulseView: View, colorHex: String) {
        val dotDrawable = GradientDrawable()
        dotDrawable.shape = GradientDrawable.OVAL
        dotDrawable.setColor(Color.parseColor(colorHex))
        dotView.background = dotDrawable

        val pulseDrawable = GradientDrawable()
        pulseDrawable.shape = GradientDrawable.OVAL
        pulseDrawable.setColor(Color.parseColor(colorHex))
        pulseDrawable.alpha = 150 
        pulseView.background = pulseDrawable
    }

    private fun showBottomSheetMenu() {
        val dialog = BottomSheetDialog(this@MainActivity)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet, null) as View
        dialog.setContentView(view)

        val switchCompass = view.findViewById<Switch>(R.id.switchCompass)
        val radioInternal = view.findViewById<RadioButton>(R.id.radioInternal)
        val radioSdCard = view.findViewById<RadioButton>(R.id.radioSdCard)
        val tvStorageInfo = view.findViewById<TextView>(R.id.tvStorageInfo)
        val btnDownload = view.findViewById<LinearLayout>(R.id.btnDownloadMap)

        switchCompass.isChecked = isCompassModeEnabled
        switchCompass.setOnCheckedChangeListener { _, isChecked ->
            isCompassModeEnabled = isChecked
            prefs.edit().putBoolean("compass_mode", isChecked).apply()
            
            if (isChecked) {
                rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                Toast.makeText(this@MainActivity, "Compass Mode ON", Toast.LENGTH_SHORT).show()
            } else {
                sensorManager.unregisterListener(this)
                mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0f))
                Toast.makeText(this@MainActivity, "Compass Mode OFF", Toast.LENGTH_SHORT).show()
            }
        }

        if (useSdCard) radioSdCard.isChecked = true else radioInternal.isChecked = true
        
        radioInternal.setOnClickListener { 
            useSdCard = false
            prefs.edit().putBoolean("use_sd_card", false).apply()
            Toast.makeText(this@MainActivity, "Internal Selected", Toast.LENGTH_SHORT).show() 
        }
        radioSdCard.setOnClickListener { 
            useSdCard = true
            prefs.edit().putBoolean("use_sd_card", true).apply()
            Toast.makeText(this@MainActivity, "SD Card Selected", Toast.LENGTH_SHORT).show() 
        }

        val rSize = StorageHelper.getFolderSizeInMB(StorageHelper.getRoutesDir(this@MainActivity, useSdCard))
        val mSize = StorageHelper.getFolderSizeInMB(StorageHelper.getMapsDir(this@MainActivity, useSdCard))
        tvStorageInfo.text = "Routes: $rSize | Maps: $mSize"

        btnDownload.setOnClickListener {
            mapLibreMap?.let { map ->
                val styleToDownload = if (isSatelliteMode) MapConfig.SATELLITE_STYLE else MapConfig.DEFAULT_STYLE
                OfflineMapManager.downloadCurrentArea(this@MainActivity, map, styleToDownload, useSdCard)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showVehicleSelectionDialog() {
        val vehicles = arrayOf("Bike", "Car", "Walk", "Cycle")
        val builder = AlertDialog.Builder(this@MainActivity)
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
        
        RouteFileManager.startNewRoute(this@MainActivity, useSdCard)
        
        btnRecord.setImageResource(android.R.drawable.ic_media_pause)
        btnReturn.visibility = View.GONE
        Toast.makeText(this@MainActivity, "Recording Started ($selectedVehicle)", Toast.LENGTH_SHORT).show()
        mapLibreMap?.locationComponent?.cameraMode = CameraMode.TRACKING
    }

    private fun stopRecording() {
        isRecording = false
        btnRecord.setImageResource(android.R.drawable.ic_media_play)
        btnReturn.visibility = View.VISIBLE 
        RouteFileManager.compressCurrentRoute()
        Toast.makeText(this@MainActivity, "Saved & Compressed to ${if(useSdCard) "SD Card" else "Phone"}.", Toast.LENGTH_LONG).show()
    }

    private fun startReturning() {
        isReturning = true
        isRecording = false
        btnReturn.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this@MainActivity, "Return Mode: Follow the dots!", Toast.LENGTH_SHORT).show()
        
        val updatedFeatures = recordedPoints.map {
            val point = it.geometry() as Point
            val newFeature = Feature.fromGeometry(point)
            newFeature.addStringProperty("color", "#00E676")
            newFeature
        }
        recordedPoints.clear()
        recordedPoints.addAll(updatedFeatures)
        updateRouteOnMap()
    }

    private fun stopReturning() {
        isReturning = false
        btnReturn.setImageResource(android.R.drawable.ic_menu_revert)
        Toast.makeText(this@MainActivity, "Return Mode Stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun updateRouteOnMap() {
        runOnUiThread {
            if (currentStyle == null) return@runOnUiThread
            val featureCollection = FeatureCollection.fromFeatures(recordedPoints.toTypedArray())
            val source = currentStyle?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
            source?.setGeoJson(featureCollection)
        }
    }

    private fun getMinDistance(): Float {
        return when(selectedVehicle) {
            "Walk" -> 10f
            "Cycle" -> 15f
            "Bike" -> 20f
            "Car" -> 30f
            else -> 15f
        }
    }

    // FIXED: Android ka built-in Location system use kar rahe hain (No Math.sin/cos)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val startLoc = Location("start").apply { latitude = lat1; longitude = lon1 }
        val endLoc = Location("end").apply { latitude = lat2; longitude = lon2 }
        return startLoc.distanceTo(endLoc)
    }

    private fun setupMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                currentStyle = style
                setupRouteLayer(style)
                LocationHelper.enableLocation(this@MainActivity, map, style)
                
                if (isCompassModeEnabled) {
                    rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val accuracy: Float = location.accuracy
                    
                    when {
                        accuracy < 10f -> { 
                            tvGpsStatus.text = "Exact (${accuracy.toInt()}m)"
                            updateStatusUI(dotGps, pulseGps, "#4CAF50") 
                        }
                        accuracy < 20f -> { 
                            tvGpsStatus.text = "Good (${accuracy.toInt()}m)"
                            updateStatusUI(dotGps, pulseGps, "#2196F3") 
                        }
                        accuracy < 50f -> { 
                            tvGpsStatus.text = "Avg (${accuracy.toInt()}m)"
                            updateStatusUI(dotGps, pulseGps, "#FFC107") 
                        }
                        else -> { 
                            tvGpsStatus.text = "Weak (${accuracy.toInt()}m)"
                            updateStatusUI(dotGps, pulseGps, "#F44336") 
                        }
                    }

                    if (accuracy <= 30f) { 
                        val userLoc = LatLng(location.latitude, location.longitude)
                        
                        if (isFirstLocationFix && mapLibreMap != null) {
                            val pos = CameraPosition.Builder().target(userLoc).zoom(16.0).tilt(50.0).build()
                            mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
                            isFirstLocationFix = false
                        }

                        if (isRecording) {
                            var shouldRecord = false
                            if (lastRecordedLatLng == null) {
                                shouldRecord = true
                            } else {
                                val dist: Float = calculateDistance(
                                    lastRecordedLatLng!!.latitude, lastRecordedLatLng!!.longitude,
                                    userLoc.latitude, userLoc.longitude
                                )
                                
                                if (dist > getMinDistance()) shouldRecord = true
                                
                                if (dist > 2f) {
                                    val bearingChange: Float = abs(location.bearing - lastRecordedBearing)
                                    if (bearingChange > 30f) shouldRecord = true
                                }
                            }

                            if (shouldRecord) {
                                addPointToRoute(userLoc, "#D500F9")
                                lastRecordedLatLng = userLoc
                                lastRecordedBearing = location.bearing
                                RouteFileManager.appendPoint(userLoc.latitude, userLoc.longitude, "#D500F9")
                            }
                        } 
                        
                        if (isReturning) {
                            var isOffRoute = true
                            for (feature in recordedPoints) {
                                val p = feature.geometry() as Point
                                val dist: Float = calculateDistance(p.latitude(), p.longitude(), userLoc.latitude, userLoc.longitude)
                                if (dist < 40f) { 
                                    isOffRoute = false
                                    break
                                }
                            }
                            if (isOffRoute) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastOffRouteTime > 10000L) {
                                    addPointToRoute(userLoc, "#FF1744")
                                    lastOffRouteTime = currentTime
                                }
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
                            updateStatusUI(dotInternet, pulseInternet, "#4CAF50")
                        } else {
                            tvNetStatus.text = "Data"
                            updateStatusUI(dotInternet, pulseInternet, "#2196F3")
                        }
                    }
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread {
                    tvNetStatus.text = "Offline"
                    updateStatusUI(dotInternet, pulseInternet, "#F44336")
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
            startPulseAnimations()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (isCompassModeEnabled) {
            rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
