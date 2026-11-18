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
import android.graphics.Bitmap
import android.graphics.Canvas
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
import android.speech.tts.TextToSpeech
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
import androidx.core.content.ContextCompat
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity(), SensorEventListener, TextToSpeech.OnInitListener {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var isSatelliteMode = false
    private var currentStyle: Style? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnRecord: FloatingActionButton
    private lateinit var btnReturn: FloatingActionButton
    private lateinit var btnVoice: FloatingActionButton
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

    private var isRecording = false
    private var isReturning = false
    private var selectedVehicle = "Car"
    private val recordedPoints = mutableListOf<LatLng>() 
    
    private var isVoiceEnabled = true
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private val announcedTurns = mutableSetOf<Int>()
    private var currentRoutePointsForNav = mutableListOf<LatLng>()

    private var lastRecordedLatLng: LatLng? = null
    private var kalmanFilter = KalmanLatLong()

    private val ROUTE_PURPLE_SOURCE = "route-purple-source"
    private val ROUTE_PURPLE_LAYER = "route-purple-layer"
    private val ARROW_LAYER = "route-arrows-layer"
    private var arrowAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this@MainActivity)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

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
        btnVoice = findViewById(R.id.btnVoice)
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

        btnVoice.setOnClickListener {
            isVoiceEnabled = !isVoiceEnabled
            if (isVoiceEnabled) {
                btnVoice.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                btnVoice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676"))
                speak("Voice guidance on")
            } else {
                btnVoice.setImageResource(android.R.drawable.ic_lock_silent_mode)
                btnVoice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            }
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.US
            }
            ttsReady = true
        }
    }

    private fun speak(text: String) {
        if (isVoiceEnabled && ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

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
                val pos = CameraPosition.Builder(map.cameraPosition).bearing(smoothedBearing.toDouble()).build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 250)
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
        gpsAnimator.duration = 1500L; gpsAnimator.repeatCount = ValueAnimator.INFINITE; gpsAnimator.interpolator = AccelerateDecelerateInterpolator(); gpsAnimator.start()
        val internetAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseInternet, scaleUpX, scaleUpY, fadeOut)
        internetAnimator.duration = 1500L; internetAnimator.repeatCount = ValueAnimator.INFINITE; internetAnimator.interpolator = AccelerateDecelerateInterpolator(); internetAnimator.start()
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

    private fun setupRouteLayers(style: Style) {
        if (style.getSource(ROUTE_PURPLE_SOURCE) != null) style.removeSource(ROUTE_PURPLE_SOURCE)
        if (style.getLayer(ROUTE_PURPLE_LAYER) != null) style.removeLayer(ROUTE_PURPLE_LAYER)

        style.addSource(GeoJsonSource(ROUTE_PURPLE_SOURCE, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer(ROUTE_PURPLE_LAYER, ROUTE_PURPLE_SOURCE).withProperties(
            PropertyFactory.lineColor("#D500F9"), PropertyFactory.lineWidth(5f), PropertyFactory.lineJoin("round")
        ))

        try {
            val drawable = ContextCompat.getDrawable(this, R.drawable.route_arrow)
            if (drawable != null) {
                val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                style.addImage("route-arrow-icon", bitmap)
            }
        } catch (e: Exception) { e.printStackTrace() }

        if (style.getLayer(ARROW_LAYER) != null) style.removeLayer(ARROW_LAYER)
        
        val arrowLayer = SymbolLayer(ARROW_LAYER, ROUTE_PURPLE_SOURCE).withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            PropertyFactory.iconImage("route-arrow-icon"),
            PropertyFactory.iconSize(1.5f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.symbolSpacing(30f)
        )
        style.addLayer(arrowLayer)
        
        startArrowGlowAnimation(style)
    }

    private fun startArrowGlowAnimation(style: Style) {
        arrowAnimator?.cancel()
        arrowAnimator = ValueAnimator.ofFloat(0.3f, 1.0f)
        arrowAnimator?.duration = 800
        arrowAnimator?.repeatCount = ValueAnimator.INFINITE
        arrowAnimator?.repeatMode = ValueAnimator.REVERSE
        arrowAnimator?.addUpdateListener { animation ->
            val alpha = animation.animatedValue as Float
            style.getLayerAs<SymbolLayer>(ARROW_LAYER)?.setProperties(
                PropertyFactory.iconOpacity(alpha),
                PropertyFactory.iconColor("#2196F3")
            )
        }
        arrowAnimator?.start()
    }

    private fun startRecording() {
        isRecording = true; isReturning = false
        recordedPoints.clear(); announcedTurns.clear()
        currentRoutePointsForNav.clear()
        lastRecordedLatLng = null
        kalmanFilter = KalmanLatLong()
        RouteFileManager.startNewRoute()
        btnRecord.setImageResource(android.R.drawable.ic_media_pause)
        btnReturn.visibility = View.GONE
        speak("Recording started")
        Toast.makeText(this@MainActivity, "Recording Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        btnRecord.setImageResource(android.R.drawable.ic_media_play)
        btnReturn.visibility = View.VISIBLE 
        speak("Recording stopped")
        
        if (recordedPoints.isNotEmpty()) {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Save Route")
            val layout = LinearLayout(this@MainActivity)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(50, 40, 50, 10)
            val etName = EditText(this@MainActivity)
            etName.hint = "Route Name"
            layout.addView(etName)
            val etDesc = EditText(this@MainActivity)
            etDesc.hint = "Description (Optional)"
            layout.addView(etDesc)
            builder.setView(layout)
            
            builder.setPositiveButton("Save") { _, _ ->
                val name = if (etName.text.toString().isBlank()) "Unnamed Route" else etName.text.toString()
                RouteFileManager.saveCurrentRoute(this@MainActivity, useSdCard, name, etDesc.text.toString())
                Toast.makeText(this@MainActivity, "Route Saved!", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Discard") { _, _ -> }
            builder.show()
        }
    }

    private fun startReturning() {
        if (recordedPoints.isEmpty()) {
            Toast.makeText(this@MainActivity, "Pehle koi route record karein!", Toast.LENGTH_SHORT).show()
            return
        }
        isReturning = true
        isRecording = false
        announcedTurns.clear()
        
        currentRoutePointsForNav = recordedPoints.reversed().toMutableList()
        
        btnReturn.setImageResource(android.R.drawable.ic_media_pause)
        speak("Return mode started. Follow the blue arrows.")
        Toast.makeText(this@MainActivity, "Return Mode ON", Toast.LENGTH_SHORT).show()
        updateAllMapLines()
        
        currentStyle?.getLayer(ARROW_LAYER)?.setProperties(PropertyFactory.visibility(Property.VISIBILITY_VISIBLE))
    }

    private fun stopReturning() {
        isReturning = false
        btnReturn.setImageResource(android.R.drawable.ic_menu_revert)
        speak("Return mode stopped")
        Toast.makeText(this@MainActivity, "Return Mode OFF", Toast.LENGTH_SHORT).show()
        
        currentStyle?.getLayer(ARROW_LAYER)?.setProperties(PropertyFactory.visibility(Property.VISIBILITY_NONE))
    }

    private fun setupMap() {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.setCompassGravity(Gravity.TOP or Gravity.END)
            
            map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                currentStyle = style
                setupRouteLayers(style)
                LocationHelper.enableLocation(this@MainActivity, map, style)
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
                        kalmanFilter.process(location.latitude, location.longitude, accuracy, System.currentTimeMillis())
                        val userLoc = LatLng(kalmanFilter.getLat(), kalmanFilter.getLng())
                        
                        if (isFirstLocationFix && mapLibreMap != null) {
                            val pos = CameraPosition.Builder().target(userLoc).zoom(18.0).tilt(50.0).build()
                            mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
                            isFirstLocationFix = false
                        }

                        if (isRecording) {
                            var shouldRecord = false
                            if (lastRecordedLatLng == null) shouldRecord = true
                            else {
                                val dist = calculateDistance(lastRecordedLatLng!!.latitude, lastRecordedLatLng!!.longitude, userLoc.latitude, userLoc.longitude)
                                if (dist > getMinDistance()) shouldRecord = true
                            }
                            if (shouldRecord) {
                                recordedPoints.add(userLoc)
                                lastRecordedLatLng = userLoc
                                updateAllMapLines()
                            }
                        } 
                        
                        if (isReturning) {
                            checkNavigation(userLoc, location.bearing)
                        }
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    private fun checkNavigation(userLoc: LatLng, currentBearing: Float) {
        if (currentRoutePointsForNav.size < 2) return

        var closestIndex = -1
        var minDistance = Double.MAX_VALUE
        
        for (i in 0 until currentRoutePointsForNav.size - 1) {
            val point = currentRoutePointsForNav[i]
            val dist = calculateDistance(userLoc.latitude, userLoc.longitude, point.latitude, point.longitude)
            if (dist < minDistance) {
                minDistance = dist.toDouble()
                closestIndex = i
            }
        }

        if (minDistance > 50.0) return

        if (closestIndex != -1 && closestIndex < currentRoutePointsForNav.size - 2) {
            val nextPoint = currentRoutePointsForNav[closestIndex + 1]
            val pointAfterNext = currentRoutePointsForNav[closestIndex + 2]

            val bearing1 = calculateBearing(currentRoutePointsForNav[closestIndex], nextPoint)
            val bearing2 = calculateBearing(nextPoint, pointAfterNext)
            
            val turnAngle = calculateTurnAngle(bearing1, bearing2)
            val distToTurn = calculateDistance(userLoc.latitude, userLoc.longitude, nextPoint.latitude, nextPoint.longitude)

            if (distToTurn in 15f..25f && !announcedTurns.contains(closestIndex)) {
                announcedTurns.add(closestIndex)
                
                val absAngle = abs(turnAngle)
                var voiceText = ""
                when {
                    absAngle < 20f -> voiceText = "Continue straight"
                    absAngle in 20f..45f -> voiceText = if (turnAngle > 0) "Slight left ahead" else "Slight right ahead"
                    absAngle in 45f..120f -> voiceText = if (turnAngle > 0) "Turn left" else "Turn right"
                    else -> voiceText = if (turnAngle > 0) "Sharp left" else "Sharp right"
                }
                speak("In ${distToTurn.toInt()} meters, $voiceText")
            }
        }
    }

    private fun updateAllMapLines() {
        runOnUiThread {
            if (currentStyle == null) return@runOnUiThread
            if (recordedPoints.size > 1) {
                val coords = recordedPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                val line = LineString.fromLngLats(coords)
                currentStyle?.getSourceAs<GeoJsonSource>(ROUTE_PURPLE_SOURCE)?.setGeoJson(Feature.fromGeometry(line))
            }
        }
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)
        val dLon = lon2 - lon1
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    private fun calculateTurnAngle(bearing1: Float, bearing2: Float): Float {
        var diff = bearing2 - bearing1
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        return diff 
    }

    private fun getMinDistance(): Float = when(selectedVehicle) { "Walk" -> 10f; "Cycle" -> 15f; "Bike" -> 20f; "Car" -> 30f; else -> 15f }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val startLoc = Location("start").apply { latitude = lat1; longitude = lon1 }
        val endLoc = Location("end").apply { latitude = lat2; longitude = lon2 }
        return startLoc.distanceTo(endLoc)
    }

    private fun monitorInternetConnection() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { tvNetStatus.text = "WiFi"; updateStatusUI(dotInternet, pulseInternet, "#4CAF50") }
                        else { tvNetStatus.text = "Data"; updateStatusUI(dotInternet, pulseInternet, "#2196F3") }
                    }
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread { tvNetStatus.text = "Offline"; updateStatusUI(dotInternet, pulseInternet, "#F44336") }
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupMap(); startLocationUpdates(); monitorInternetConnection(); startPulseAnimations()
        }
    }

    override fun onResume() {
        super.onResume(); mapView.onResume()
        if (isCompassModeEnabled) { rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    }

    override fun onPause() { super.onPause(); mapView.onPause(); sensorManager.unregisterListener(this) }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    
    override fun onDestroy() {
        arrowAnimator?.cancel()
        if (this::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy(); mapView.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

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
        
        radioInternal.setOnClickListener { useSdCard = false; prefs.edit().putBoolean("use_sd_card", false).apply() }
        radioSdCard.setOnClickListener { useSdCard = true; prefs.edit().putBoolean("use_sd_card", true).apply() }

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
        builder.setTitle("Select Vehicle")
        builder.setSingleChoiceItems(vehicles, 1) { _, which -> selectedVehicle = vehicles[which] }
        builder.setPositiveButton("Start") { _, _ -> startRecording() }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}
