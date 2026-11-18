package com.example.proguide

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
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
    private lateinit var btnSatellite: MaterialCardView
    private lateinit var tvSatelliteIcon: TextView
    
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvNetStatus: TextView
    private lateinit var dotGps: View
    private lateinit var pulseGps: View
    private lateinit var dotInternet: View
    private lateinit var pulseInternet: View
    
    private var isFirstLocationFix = true
    
    private lateinit var prefs: SharedPreferences
    private var isCompassModeEnabled = false
    private var useSdCard = false

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var smoothedBearing: Float = 0f

    // Recording & Return Variables
    private var isRecording = false
    private var isReturning = false
    private var selectedVehicle = "Car"
    
    // Route Points
    private val recordedPoints = mutableListOf<LatLng>() // Master saved points
    private val greenPoints = mutableListOf<LatLng>() // Covered correctly
    private val redPoints = mutableListOf<LatLng>()   // Off route
    private val yellowPoints = mutableListOf<LatLng>() // Recovered points
    
    private var lastRecordedLatLng: LatLng? = null
    private var lastRecordedBearing: Float = 0f
    private var lastOffRouteTime: Long = 0
    private var wasOffRoute = false
    
    // Kalman Filter
    private var kalmanFilter = KalmanLatLong()

    // Layer IDs
    private val ROUTE_PURPLE_SOURCE = "route-purple-source"
    private val ROUTE_PURPLE_LAYER = "route-purple-layer"
    private val RETURN_GREEN_SOURCE = "return-green-source"
    private val RETURN_GREEN_LAYER = "return-green-layer"
    private val RETURN_RED_SOURCE = "return-red-source"
    private val RETURN_RED_LAYER = "return-red-layer"
    private val RETURN_YELLOW_SOURCE = "return-yellow-source"
    private val RETURN_YELLOW_LAYER = "return-yellow-layer"

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
        btnSatellite = findViewById(R.id.btnSatellite)
        tvSatelliteIcon = findViewById(R.id.tvSatelliteIcon)
        
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

        btnSatellite.setOnClickListener {
            mapLibreMap?.let { map ->
                if (isSatelliteMode) {
                    map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                        currentStyle = style
                        setupRouteLayers(style)
                        LocationHelper.enableLocation(this@MainActivity, map, style)
                    }
                    isSatelliteMode = false
                    tvSatelliteIcon.text = "🛰"
                    btnSatellite.setCardBackgroundColor(Color.WHITE)
                } else {
                    map.setStyle(MapConfig.SATELLITE_STYLE) { style ->
                        currentStyle = style
                        setupRouteLayers(style)
                        LocationHelper.enableLocation(this@MainActivity, map, style)
                    }
                    isSatelliteMode = true
                    tvSatelliteIcon.text = "🗺️"
                    btnSatellite.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
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

    // ... (Sensor, Pulse, UpdateStatus same as before) ...
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
            
            smoothedBearing += (0.08f * delta)
            if (smoothedBearing < 0f) smoothedBearing += 360f
            if (smoothedBearing >= 360f) smoothedBearing -= 360f

            mapLibreMap?.let { map ->
                val pos = CameraPosition.Builder(map.cameraPosition)
                    .bearing(smoothedBearing.toDouble())
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 250)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPulseAnimations() { /* Same as before */ 
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

    private fun updateStatusUI(dotView: View, pulseView: View, colorHex: String) { /* Same as before */
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
        val btnDeleteData = view.findViewById<LinearLayout>(R.id.btnDeleteData)
        val btnViewRoutes = view.findViewById<LinearLayout>(R.id.btnViewRoutes)

        switchCompass.isChecked = isCompassModeEnabled
        switchCompass.setOnCheckedChangeListener { _, isChecked ->
            isCompassModeEnabled = isChecked
            prefs.edit().putBoolean("compass_mode", isChecked).apply()
            if (isChecked) {
                mapLibreMap?.locationComponent?.cameraMode = CameraMode.NONE
                rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                Toast.makeText(this@MainActivity, "Compass Mode ON", Toast.LENGTH_SHORT).show()
            } else {
                sensorManager.unregisterListener(this)
                mapLibreMap?.locationComponent?.cameraMode = CameraMode.TRACKING
                mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
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

        btnViewRoutes.setOnClickListener {
            val intent = Intent(this@MainActivity, SavedRoutesActivity::class.java)
            intent.putExtra("use_sd_card", useSdCard)
            startActivityForResult(intent, 100)
            dialog.dismiss()
        }

        btnDeleteData.setOnClickListener {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Delete Data?")
            builder.setMessage("Kya aap sach me saare Routes aur Maps delete karna chahte hain?")
            builder.setPositiveButton("Yes, Delete") { _, _ ->
                StorageHelper.deleteFolderContents(StorageHelper.getRoutesDir(this@MainActivity, useSdCard))
                StorageHelper.deleteFolderContents(StorageHelper.getMapsDir(this@MainActivity, useSdCard))
                Toast.makeText(this@MainActivity, "Storage Cleared Successfully!", Toast.LENGTH_SHORT).show()
                val newRSize = StorageHelper.getFolderSizeInMB(StorageHelper.getRoutesDir(this@MainActivity, useSdCard))
                val newMSize = StorageHelper.getFolderSizeInMB(StorageHelper.getMapsDir(this@MainActivity, useSdCard))
                tvStorageInfo.text = "Routes: $newRSize | Maps: $newMSize"
            }
            builder.setNegativeButton("Cancel", null)
            builder.show()
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

    // --- NAYA: 4 Alag LineLayers Setup ---
    private fun setupRouteLayers(style: Style) {
        // Clear old sources/layers if exist
        listOf(ROUTE_PURPLE_SOURCE, RETURN_GREEN_SOURCE, RETURN_RED_SOURCE, RETURN_YELLOW_SOURCE).forEach {
            if (style.getSource(it) != null) style.removeSource(it)
        }
        listOf(ROUTE_PURPLE_LAYER, RETURN_GREEN_LAYER, RETURN_RED_LAYER, RETURN_YELLOW_LAYER).forEach {
            if (style.getLayer(it) != null) style.removeLayer(it)
        }

        // 1. Purple Line (Recording)
        style.addSource(GeoJsonSource(ROUTE_PURPLE_SOURCE, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer(ROUTE_PURPLE_LAYER, ROUTE_PURPLE_SOURCE).withProperties(
            PropertyFactory.lineColor("#D500F9"), PropertyFactory.lineWidth(5f), PropertyFactory.lineJoin("round")
        ))

        // 2. Green Line (Return Safe)
        style.addSource(GeoJsonSource(RETURN_GREEN_SOURCE, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer(RETURN_GREEN_LAYER, RETURN_GREEN_SOURCE).withProperties(
            PropertyFactory.lineColor("#00E676"), PropertyFactory.lineWidth(5f), PropertyFactory.lineJoin("round")
        ))

        // 3. Red Line (Off Route)
        style.addSource(GeoJsonSource(RETURN_RED_SOURCE, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer(RETURN_RED_LAYER, RETURN_RED_SOURCE).withProperties(
            PropertyFactory.lineColor("#FF1744"), PropertyFactory.lineWidth(5f), PropertyFactory.lineJoin("round")
        ))

        // 4. Yellow Line (Recovery)
        style.addSource(GeoJsonSource(RETURN_YELLOW_SOURCE, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer(RETURN_YELLOW_LAYER, RETURN_YELLOW_SOURCE).withProperties(
            PropertyFactory.lineColor("#FFEA00"), PropertyFactory.lineWidth(5f), PropertyFactory.lineJoin("round")
        ))
    }

    private fun startRecording() {
        isRecording = true
        isReturning = false
        recordedPoints.clear()
        greenPoints.clear()
        redPoints.clear()
        yellowPoints.clear()
        lastRecordedLatLng = null
        lastRecordedBearing = 0f
        wasOffRoute = false
        kalmanFilter = KalmanLatLong() // Reset filter
        
        RouteFileManager.startNewRoute()
        
        btnRecord.setImageResource(android.R.drawable.ic_media_pause)
        btnReturn.visibility = View.GONE
        Toast.makeText(this@MainActivity, "Recording Started ($selectedVehicle)", Toast.LENGTH_SHORT).show()
        
        if (!isCompassModeEnabled) mapLibreMap?.locationComponent?.cameraMode = CameraMode.TRACKING
    }

    private fun stopRecording() {
        isRecording = false
        btnRecord.setImageResource(android.R.drawable.ic_media_play)
        btnReturn.visibility = View.VISIBLE 
        
        if (recordedPoints.isNotEmpty()) {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Save Route")
            val layout = LinearLayout(this@MainActivity)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(50, 40, 50, 10)
            val etName = EditText(this@MainActivity)
            etName.hint = "Route Name (e.g., Ghar se Dukaan)"
            layout.addView(etName)
            val etDesc = EditText(this@MainActivity)
            etDesc.hint = "Description (Optional)"
            layout.addView(etDesc)
            builder.setView(layout)
            
            builder.setPositiveButton("Save") { _, _ ->
                val name = if (etName.text.toString().isBlank()) "Unnamed Route" else etName.text.toString()
                RouteFileManager.saveCurrentRoute(this@MainActivity, useSdCard, name, etDesc.text.toString())
                Toast.makeText(this@MainActivity, "Route Saved to ${if(useSdCard) "SD Card" else "Phone"}.", Toast.LENGTH_LONG).show()
            }
            builder.setNegativeButton("Discard") { _, _ ->
                Toast.makeText(this@MainActivity, "Route Discarded.", Toast.LENGTH_SHORT).show()
            }
            builder.setCancelable(false)
            builder.show()
        } else {
            Toast.makeText(this@MainActivity, "No points recorded.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startReturning() {
        if (recordedPoints.isEmpty()) {
            Toast.makeText(this@MainActivity, "Pehle koi route record karein!", Toast.LENGTH_SHORT).show()
            return
        }

        isReturning = true
        isRecording = false
        greenPoints.clear()
        redPoints.clear()
        yellowPoints.clear()
        wasOffRoute = false
        kalmanFilter = KalmanLatLong() // Reset filter for return

        btnReturn.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this@MainActivity, "Return Mode: Follow the purple line!", Toast.LENGTH_SHORT).show()
        updateAllMapLines()
    }

    private fun stopReturning() {
        isReturning = false
        btnReturn.setImageResource(android.R.drawable.ic_menu_revert)
        Toast.makeText(this@MainActivity, "Return Mode Stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun getMinDistance(): Float {
        return when(selectedVehicle) {
            "Walk" -> 10f; "Cycle" -> 15f; "Bike" -> 20f; "Car" -> 30f; else -> 15f
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val startLoc = Location("start").apply { latitude = lat1; longitude = lon1 }
        val endLoc = Location("end").apply { latitude = lat2; longitude = lon2 }
        return startLoc.distanceTo(endLoc)
    }

    private fun setupMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.setCompassGravity(Gravity.TOP or Gravity.END)
            val density = resources.displayMetrics.density
            val marginPx = (16 * density).toInt()
            val topMarginPx = (80 * density).toInt()
            map.uiSettings.setCompassMargins(marginPx, topMarginPx, marginPx, marginPx)
            
            map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                currentStyle = style
                setupRouteLayers(style) // Setup new layers
                LocationHelper.enableLocation(this@MainActivity, map, style)
                if (isCompassModeEnabled) {
                    map.locationComponent.cameraMode = CameraMode.NONE
                    rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
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
                        accuracy < 10f -> { tvGpsStatus.text = "Exact (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#4CAF50") }
                        accuracy < 20f -> { tvGpsStatus.text = "Good (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#2196F3") }
                        accuracy < 50f -> { tvGpsStatus.text = "Avg (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#FFC107") }
                        else -> { tvGpsStatus.text = "Weak (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#F44336") }
                    }

                    if (accuracy <= 30f) { 
                        // NAYA: Kalman Filter lagaya
                        kalmanFilter.process(location.latitude, location.longitude, accuracy, System.currentTimeMillis())
                        val userLoc = LatLng(kalmanFilter.getLat(), kalmanFilter.getLng())
                        
                        if (isFirstLocationFix && mapLibreMap != null) {
                            val pos = CameraPosition.Builder().target(userLoc).zoom(16.0).tilt(50.0).build()
                            mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
                            isFirstLocationFix = false
                        }

                        // --- RECORDING LOGIC ---
                        if (isRecording) {
                            var shouldRecord = false
                            if (lastRecordedLatLng == null) {
                                shouldRecord = true
                            } else {
                                val dist = calculateDistance(lastRecordedLatLng!!.latitude, lastRecordedLatLng!!.longitude, userLoc.latitude, userLoc.longitude)
                                if (dist > getMinDistance()) shouldRecord = true
                                if (dist > 2f && abs(location.bearing - lastRecordedBearing) > 30f) shouldRecord = true
                            }

                            if (shouldRecord) {
                                recordedPoints.add(userLoc)
                                lastRecordedLatLng = userLoc
                                lastRecordedBearing = location.bearing
                                RouteFileManager.appendPoint(userLoc.latitude, userLoc.longitude, "#D500F9")
                                updateAllMapLines()
                            }
                        } 
                        
                        // --- RETURN MODE LOGIC ---
                        if (isReturning) {
                            var isOffRoute = true
                            // Check if user is near any saved point (within 20 meters)
                            for (routePoint in recordedPoints) {
                                val dist = calculateDistance(routePoint.latitude, routePoint.longitude, userLoc.latitude, userLoc.longitude)
                                if (dist < 20f) { // Threshold for being "On Route"
                                    isOffRoute = false
                                    break
                                }
                            }

                            if (isOffRoute) {
                                if (!wasOffRoute) {
                                    wasOffRoute = true
                                    redPoints.add(userLoc)
                                } else {
                                    val lastRed = redPoints.last()
                                    if (calculateDistance(lastRed.latitude, lastRed.longitude, userLoc.latitude, userLoc.longitude) > 5f) {
                                        redPoints.add(userLoc)
                                    }
                                }
                            } else {
                                if (wasOffRoute) {
                                    wasOffRoute = false
                                    yellowPoints.add(userLoc) // First point of recovery
                                } else {
                                    val lastGreen = greenPoints.lastOrNull()
                                    if (lastGreen == null) {
                                        greenPoints.add(userLoc)
                                    } else {
                                        if (calculateDistance(lastGreen.latitude, lastGreen.longitude, userLoc.latitude, userLoc.longitude) > 5f) {
                                            greenPoints.add(userLoc)
                                        }
                                    }
                                }
                            }
                            updateAllMapLines()
                        }
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    // --- NAYA: Function to Update all 4 Lines on Map ---
    private fun updateAllMapLines() {
        runOnUiThread {
            if (currentStyle == null) return@runOnUiThread
            
            // 1. Update Purple Line (Saved Route)
            updateLineSource(ROUTE_PURPLE_SOURCE, recordedPoints)

            // 2. Update Green Line (Covered)
            updateLineSource(RETURN_GREEN_SOURCE, greenPoints)

            // 3. Update Red Line (Off route)
            updateLineSource(RETURN_RED_SOURCE, redPoints)
            
            // 4. Update Yellow Line (Recovery)
            updateLineSource(RETURN_YELLOW_SOURCE, yellowPoints)
        }
    }

    private fun updateLineSource(sourceId: String, points: List<LatLng>) {
        val source = currentStyle?.getSourceAs<GeoJsonSource>(sourceId)
        if (points.size > 1) {
            val coordinates = points.map { Point.fromLngLat(it.longitude, it.latitude) }
            val lineString = LineString.fromLngLats(coordinates)
            source?.setGeoJson(Feature.fromGeometry(lineString))
        } else {
            source?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        }
    }

    private fun monitorInternetConnection() { /* Same as before */ 
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val fileName = data?.getStringExtra("load_route_file")
            if (fileName != null) {
                val allRoutes = RouteFileManager.getAllSavedRoutes(this@MainActivity, useSdCard)
                val selectedRoute = allRoutes.find { it.fileName == fileName }
                
                if (selectedRoute != null) {
                    recordedPoints.clear()
                    // Load only coordinates
                    for (p in selectedRoute.points) {
                        recordedPoints.add(LatLng(p.lat, p.lng))
                    }
                    greenPoints.clear()
                    redPoints.clear()
                    yellowPoints.clear()
                    
                    updateAllMapLines()
                    isReturning = false
                    Toast.makeText(this@MainActivity, "'${selectedRoute.name}' Loaded!", Toast.LENGTH_SHORT).show()
                    
                    if (recordedPoints.isNotEmpty()) {
                        val firstPoint = recordedPoints.first()
                        val pos = CameraPosition.Builder().target(firstPoint).zoom(16.0).build()
                        mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 1000)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (isCompassModeEnabled) {
            mapLibreMap?.locationComponent?.cameraMode = CameraMode.NONE
            rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
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
