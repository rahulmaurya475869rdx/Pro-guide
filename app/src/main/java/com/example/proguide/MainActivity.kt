package com.example.proguide

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
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
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
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
    private lateinit var tvSpeed: TextView
    private lateinit var tvNavDistance: TextView
    private lateinit var dotGps: View
    private lateinit var pulseGps: View
    private lateinit var dotInternet: View
    private lateinit var pulseInternet: View
    
    private var isFirstLocationFix = true
    private lateinit var prefs: SharedPreferences
    private var isCompassModeEnabled = false
    private var isArrowsEnabled = true
    
    private val REQUEST_CODE_SAF = 42
    private val REQUEST_CODE_ROUTES = 100

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    @Volatile private var smoothedBearing: Float = 0f

    private var isUserMovingMap = false
    private val handler = Handler(Looper.getMainLooper())

    private var isRecording = false
    private var isReturning = false
    private var isForwardNavigation = true 
    private var selectedVehicle = "Car"
    private val recordedPoints = mutableListOf<LatLng>() 
    
    private var isVoiceEnabled = true
    
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var currentTargetBearing: Float = 0f
    private var currentRoutePointsForNav = mutableListOf<LatLng>()

    private lateinit var navArrowView: NavigationArrowView

    private val ROUTE_PURPLE_SOURCE = "route-purple-source"
    private val ROUTE_PURPLE_LAYER = "route-purple-layer"
    private val ARROW_LAYER = "route-arrows-layer"
    private val CONNECTING_SOURCE = "connecting-source"
    private val CONNECTING_LAYER = "connecting-layer"
    private var arrowAnimator: ValueAnimator? = null

    // Background Service Receiver
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationTrackingService.BROADCAST_LOCATION) {
                val lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(LocationTrackingService.EXTRA_LNG, 0.0)
                currentTargetBearing = intent.getFloatExtra(LocationTrackingService.EXTRA_TARGET_BEARING, 0f)
                val showArrows = intent.getBooleanExtra(LocationTrackingService.EXTRA_SHOW_ARROWS, false)
                val isRec = intent.getBooleanExtra(LocationTrackingService.EXTRA_IS_RECORDING, false)
                val distToTurn = intent.getFloatExtra(LocationTrackingService.EXTRA_DIST_TO_TURN, 0f)
                val speed = intent.getFloatExtra(LocationTrackingService.EXTRA_SPEED, 0f)

                val userLoc = LatLng(lat, lng)

                if (isFirstLocationFix && mapLibreMap != null) {
                    val pos = CameraPosition.Builder().target(userLoc).zoom(18.0).tilt(50.0).build()
                    mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
                    isFirstLocationFix = false
                }

                if (isRec) {
                    if (recordedPoints.isEmpty() || calculateDistance(recordedPoints.last().latitude, recordedPoints.last().longitude, lat, lng) > 5f) {
                        recordedPoints.add(userLoc)
                        updateAllMapLines()
                    }
                }

                if (isReturning) {
                    if (showArrows && isArrowsEnabled) {
                        navArrowView.visibility = View.VISIBLE
                        tvNavDistance.visibility = View.VISIBLE
                        tvNavDistance.text = "${distToTurn.toInt()} m"
                        updateHudArrow(userLoc)
                    } else {
                        navArrowView.visibility = View.GONE
                        tvNavDistance.visibility = View.GONE
                    }
                } else {
                    tvNavDistance.visibility = View.GONE
                }
            }
        }
    }

    // UI Location Callback (Updates GPS Status and Speedometer)
    private val uiLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                val accuracy: Float = location.accuracy
                
                when {
                    accuracy < 10f -> { tvGpsStatus.text = "Exact (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#4CAF50") }
                    accuracy < 20f -> { tvGpsStatus.text = "Good (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#2196F3") }
                    accuracy < 50f -> { tvGpsStatus.text = "Average (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#FFC107") }
                    else -> { tvGpsStatus.text = "Weak (${accuracy.toInt()}m)"; updateStatusUI(dotGps, pulseGps, "#F44336") }
                }

                // FIX: Speedometer update every time location changes
                if (location.hasSpeed()) {
                    val currentSpeedKmh = location.speed * 3.6f
                    tvSpeed.text = "${currentSpeedKmh.toInt()} km/h"
                } else {
                    tvSpeed.text = "0 km/h"
                }

                if (accuracy <= 30f) {
                    val userLoc = LatLng(location.latitude, location.longitude)
                    if (isFirstLocationFix && mapLibreMap != null) {
                        val pos = CameraPosition.Builder().target(userLoc).zoom(18.0).tilt(50.0).build()
                        mapLibreMap?.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2000)
                        isFirstLocationFix = false
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this@MainActivity)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        if (savedInstanceState != null) {
            val latArray = savedInstanceState.getDoubleArray("saved_lats")
            val lngArray = savedInstanceState.getDoubleArray("saved_lngs")
            if (latArray != null && lngArray != null) {
                for (i in latArray.indices) {
                    recordedPoints.add(LatLng(latArray[i], lngArray[i]))
                }
            }
        }

        prefs = getSharedPreferences(StorageHelper.PREFS_NAME, Context.MODE_PRIVATE)
        isCompassModeEnabled = prefs.getBoolean("compass_mode", false)
        isArrowsEnabled = prefs.getBoolean("show_arrows", true)
        isVoiceEnabled = prefs.getBoolean("is_voice_enabled", true)

        mapView = findViewById(R.id.mapView)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvNetStatus = findViewById(R.id.tvNetStatus)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvNavDistance = findViewById(R.id.tvNavDistance)
        dotGps = findViewById(R.id.dotGps)
        pulseGps = findViewById(R.id.pulseGps)
        dotInternet = findViewById(R.id.dotInternet)
        pulseInternet = findViewById(R.id.pulseInternet)
        
        btnRecord = findViewById(R.id.btnRecord)
        btnReturn = findViewById(R.id.btnReturn)
        btnVoice = findViewById(R.id.btnVoice)
        btnSatellite = findViewById(R.id.btnSatellite)
        tvSatelliteIcon = findViewById(R.id.tvSatelliteIcon)
        navArrowView = findViewById(R.id.navArrowView)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
        mapView.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        updateStatusUI(dotGps, pulseGps, "#F44336")
        updateStatusUI(dotInternet, pulseInternet, "#F44336")

        if (isVoiceEnabled) {
            btnVoice.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            btnVoice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676"))
        } else {
            btnVoice.setImageResource(android.R.drawable.ic_lock_silent_mode)
            btnVoice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }

        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        } else {
            setupMap()
            startUiLocationUpdates()
            monitorInternetConnection()
            startPulseAnimations() 
        }

        findViewById<FloatingActionButton>(R.id.btnMyLocation).setOnClickListener {
            isUserMovingMap = true 
            mapLibreMap?.let { LocationHelper.moveToCurrentLocation(this@MainActivity, it) }
            handler.postDelayed({ isUserMovingMap = false }, 2500) 
        }

        btnVoice.setOnClickListener {
            isVoiceEnabled = !isVoiceEnabled
            prefs.edit().putBoolean("is_voice_enabled", isVoiceEnabled).apply()
            
            if (isVoiceEnabled) {
                btnVoice.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                btnVoice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676"))
                if (ttsReady) {
                    speakLocal("Voice guidance on")
                } else {
                    Toast.makeText(this, "Initializing voice...", Toast.LENGTH_SHORT).show()
                }
            } else {
                btnVoice.setImageResource(android.R.drawable.ic_lock_silent_mode)
                btnVoice.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                if (::tts.isInitialized) {
                    tts.stop()
                }
            }
        }

        btnSatellite.setOnClickListener {
            mapLibreMap?.let { map ->
                arrowAnimator?.cancel()
                arrowAnimator = null

                val newStyle = if (isSatelliteMode) MapConfig.DEFAULT_STYLE else MapConfig.SATELLITE_STYLE
                
                map.setStyle(newStyle) { style ->
                    currentStyle = style
                    setupRouteLayers(style)
                    LocationHelper.enableLocation(this@MainActivity, map, style)
                    updateAllMapLines()
                    
                    isSatelliteMode = !isSatelliteMode
                    if (isSatelliteMode) {
                        tvSatelliteIcon.text = "🗺️"
                        btnSatellite.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
                    } else {
                        tvSatelliteIcon.text = "🛰"
                        btnSatellite.setCardBackgroundColor(Color.WHITE)
                    }
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

        if (StorageHelper.getFolderUri(this) == null) {
            showFolderSelectionDialog()
        }

        UpdateManager.checkForUpdates(this, isManual = false)
    }

    private fun startUiLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, uiLocationCallback, Looper.getMainLooper())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.US
            }
            tts.setSpeechRate(0.85f) // SLOWER SPEECH
            ttsReady = true
        }
    }

    private fun speakLocal(text: String) {
        if (ttsReady && isVoiceEnabled) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_id")
        }
    }

    private fun updateHudArrow(userLoc: LatLng) {
        var relativeAngle = currentTargetBearing - smoothedBearing
        if (relativeAngle > 180f) relativeAngle -= 360f
        if (relativeAngle < -180f) relativeAngle += 360f
        navArrowView.setDirection(relativeAngle)
    }

    private fun showFolderSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Select Storage Folder")
            .setMessage("Please select a folder to save your routes and maps. Your data will be safe even if you uninstall the app.")
            .setCancelable(false)
            .setPositiveButton("Select Folder") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                startActivityForResult(intent, REQUEST_CODE_SAF)
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SAF && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                StorageHelper.saveFolderUri(this, uri)
                Toast.makeText(this, "Folder Selected Successfully!", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_CODE_ROUTES && resultCode == RESULT_OK) {
            val fileName = data?.getStringExtra("load_route_file")
            if (fileName != null) {
                val routes = RouteFileManager.getAllSavedRoutes(this)
                val selectedRoute = routes.find { it.fileName == fileName }
                
                if (selectedRoute != null) {
                    recordedPoints.clear()
                    for (p in selectedRoute.points) {
                        recordedPoints.add(LatLng(p.lat, p.lng))
                    }
                    updateAllMapLines()
                    Toast.makeText(this, "Route Loaded: ${selectedRoute.name}", Toast.LENGTH_SHORT).show()
                    startNavigation(true)
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
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
            
            if (isCompassModeEnabled && !isUserMovingMap) {
                mapLibreMap?.let { map ->
                    val pos = CameraPosition.Builder(map.cameraPosition).bearing(smoothedBearing.toDouble()).build()
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 250)
                }
            }

            if (isReturning && isArrowsEnabled && navArrowView.visibility == View.VISIBLE) {
                var relativeAngle = currentTargetBearing - smoothedBearing
                if (relativeAngle > 180f) relativeAngle -= 360f
                if (relativeAngle < -180f) relativeAngle += 360f
                navArrowView.setDirection(relativeAngle)
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
        arrowAnimator?.cancel()

        if (style.getLayer(ROUTE_PURPLE_LAYER) != null) style.removeLayer(ROUTE_PURPLE_LAYER)
        if (style.getLayer(ARROW_LAYER) != null) style.removeLayer(ARROW_LAYER)
        if (style.getSource(ROUTE_PURPLE_SOURCE) != null) style.removeSource(ROUTE_PURPLE_SOURCE)
        
        if (style.getLayer(CONNECTING_LAYER) != null) style.removeLayer(CONNECTING_LAYER)
        if (style.getSource(CONNECTING_SOURCE) != null) style.removeSource(CONNECTING_SOURCE)

        val emptyLine = LineString.fromLngLats(emptyList<Point>())
        style.addSource(GeoJsonSource(ROUTE_PURPLE_SOURCE, Feature.fromGeometry(emptyLine)))
        style.addSource(GeoJsonSource(CONNECTING_SOURCE, Feature.fromGeometry(emptyLine)))

        style.addLayer(LineLayer(ROUTE_PURPLE_LAYER, ROUTE_PURPLE_SOURCE).withProperties(
            PropertyFactory.lineColor("#D500F9"), PropertyFactory.lineWidth(5f), PropertyFactory.lineJoin("round")
        ))

        style.addLayer(LineLayer(CONNECTING_LAYER, CONNECTING_SOURCE).withProperties(
            PropertyFactory.lineColor("#FFC107"), 
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            PropertyFactory.lineJoin("round")
        ))

        var imageLoaded = false
        try {
            val drawable = ContextCompat.getDrawable(this, R.drawable.route_arrow)
            if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                style.addImage("route-arrow-icon", bitmap, true)
                imageLoaded = true
            }
        } catch (e: Exception) { e.printStackTrace() }

        if (imageLoaded) {
            val arrowLayer = SymbolLayer(ARROW_LAYER, ROUTE_PURPLE_SOURCE).withProperties(
                PropertyFactory.symbolPlacement("line"),
                PropertyFactory.iconImage("route-arrow-icon"),
                PropertyFactory.iconSize(1.5f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.symbolSpacing(30f),
                PropertyFactory.iconRotationAlignment("map")
            )
            style.addLayer(arrowLayer)
            startArrowGlowAnimation(style)
        }
    }

    private fun startArrowGlowAnimation(style: Style) {
        arrowAnimator?.cancel()
        arrowAnimator = ValueAnimator.ofFloat(0.3f, 1.0f)
        arrowAnimator?.duration = 800
        arrowAnimator?.repeatCount = ValueAnimator.INFINITE
        arrowAnimator?.repeatMode = ValueAnimator.REVERSE
        arrowAnimator?.addUpdateListener { animation ->
            val alpha = animation.animatedValue as Float
            if (currentStyle === style) {
                style.getLayerAs<SymbolLayer>(ARROW_LAYER)?.setProperties(
                    PropertyFactory.iconOpacity(alpha),
                    PropertyFactory.iconColor("#2196F3")
                )
            }
        }
        arrowAnimator?.start()
    }

    private fun startRecording() {
        isRecording = true; isReturning = false
        isForwardNavigation = true
        recordedPoints.clear()
        currentRoutePointsForNav.clear()
        RouteFileManager.startNewRoute()
        btnRecord.setImageResource(android.R.drawable.ic_media_pause)
        btnReturn.visibility = View.GONE
        navArrowView.visibility = View.GONE
        Toast.makeText(this@MainActivity, "Recording Started", Toast.LENGTH_SHORT).show()
        
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        serviceIntent.action = LocationTrackingService.ACTION_START_RECORDING
        serviceIntent.putExtra("vehicle", selectedVehicle)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopRecording() {
        isRecording = false
        btnRecord.setImageResource(android.R.drawable.ic_media_play)
        btnReturn.visibility = View.VISIBLE 
        
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        serviceIntent.action = LocationTrackingService.ACTION_STOP_SERVICE
        startService(serviceIntent)
        
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
                RouteFileManager.saveCurrentRoute(this@MainActivity, name, etDesc.text.toString())
                Toast.makeText(this@MainActivity, "Route Saved!", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Discard") { _, _ -> }
            builder.show()
        }
    }

    private fun startReturning() {
        if (recordedPoints.isEmpty()) {
            Toast.makeText(this@MainActivity, "Please record a route first!", Toast.LENGTH_SHORT).show()
            return
        }
        startNavigation(false)
    }

    private fun startNavigation(isForward: Boolean) {
        isReturning = true
        isRecording = false
        isForwardNavigation = isForward
        
        currentRoutePointsForNav = if (isForward) recordedPoints.toMutableList() else recordedPoints.reversed().toMutableList()
        
        btnReturn.setImageResource(android.R.drawable.ic_media_pause)
        btnReturn.visibility = View.VISIBLE
        Toast.makeText(this, "Navigation ON", Toast.LENGTH_SHORT).show()
        
        updateAllMapLines()
        currentStyle?.getLayer(ARROW_LAYER)?.setProperties(PropertyFactory.visibility("visible"))
        
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        serviceIntent.action = LocationTrackingService.ACTION_START_NAVIGATION
        serviceIntent.putParcelableArrayListExtra("route_points", ArrayList(currentRoutePointsForNav))
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopReturning() {
        isReturning = false
        navArrowView.visibility = View.GONE
        tvNavDistance.visibility = View.GONE
        btnReturn.setImageResource(android.R.drawable.ic_menu_revert)
        Toast.makeText(this@MainActivity, "Navigation OFF", Toast.LENGTH_SHORT).show()
        
        currentStyle?.getLayer(ARROW_LAYER)?.setProperties(PropertyFactory.visibility("none"))
        currentStyle?.getSourceAs<GeoJsonSource>(CONNECTING_SOURCE)?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(emptyList<Point>())))
        
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        serviceIntent.action = LocationTrackingService.ACTION_STOP_SERVICE
        startService(serviceIntent)
        
        isForwardNavigation = true
        updateAllMapLines()
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
            val topMarginPx = (100 * density).toInt() 
            
            map.uiSettings.setCompassMargins(marginPx, topMarginPx, marginPx, marginPx)
            
            map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                currentStyle = style
                setupRouteLayers(style)
                LocationHelper.enableLocation(this@MainActivity, map, style)
                updateAllMapLines()
            }
        }
    }

    private fun updateAllMapLines() {
        runOnUiThread {
            if (currentStyle == null) return@runOnUiThread
            if (recordedPoints.size > 1) {
                val pointsToDraw = if (isForwardNavigation) recordedPoints else recordedPoints.reversed()
                val coords = pointsToDraw.map { Point.fromLngLat(it.longitude, it.latitude) }
                val line = LineString.fromLngLats(coords)
                currentStyle?.getSourceAs<GeoJsonSource>(ROUTE_PURPLE_SOURCE)?.setGeoJson(Feature.fromGeometry(line))
            }
        }
    }

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
        if (requestCode == 1) {
            setupMap()
            startUiLocationUpdates()
            monitorInternetConnection()
            startPulseAnimations()
        }
    }

    override fun onResume() {
        super.onResume(); mapView.onResume()
        val filter = IntentFilter(LocationTrackingService.BROADCAST_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationReceiver, filter)
        }
        rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() { 
        super.onPause(); mapView.onPause(); sensorManager.unregisterListener(this) 
        unregisterReceiver(locationReceiver)
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    
    override fun onDestroy() {
        arrowAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy(); mapView.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) { 
        super.onSaveInstanceState(outState); 
        val lats = DoubleArray(recordedPoints.size) { recordedPoints[it].latitude }
        val lngs = DoubleArray(recordedPoints.size) { recordedPoints[it].longitude }
        outState.putDoubleArray("saved_lats", lats)
        outState.putDoubleArray("saved_lngs", lngs)
        mapView.onSaveInstanceState(outState) 
    }

    private fun showBottomSheetMenu() {
        val dialog = BottomSheetDialog(this@MainActivity)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet, null) as View
        dialog.setContentView(view)

        val switchCompass = view.findViewById<Switch>(R.id.switchCompass)
        val switchArrows = view.findViewById<Switch>(R.id.switchArrows)
        val tvAppVersion = view.findViewById<TextView>(R.id.tvAppVersion)
        val tvFolderPath = view.findViewById<TextView>(R.id.tvFolderPath)
        val tvStorageInfo = view.findViewById<TextView>(R.id.tvStorageInfo)
        val btnCheckUpdate = view.findViewById<LinearLayout>(R.id.btnCheckUpdate)
        val btnDownload = view.findViewById<LinearLayout>(R.id.btnDownloadMap)
        val btnDeleteData = view.findViewById<LinearLayout>(R.id.btnDeleteData)
        val btnViewRoutes = view.findViewById<LinearLayout>(R.id.btnViewRoutes)
        val btnChangeFolder = view.findViewById<LinearLayout>(R.id.btnChangeFolder)

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "Version ${pInfo.versionName}"
        } catch (e: Exception) {
            tvAppVersion.text = "Version 1.0"
        }

        switchCompass.isChecked = isCompassModeEnabled
        switchCompass.setOnCheckedChangeListener { _, isChecked ->
            isCompassModeEnabled = isChecked
            prefs.edit().putBoolean("compass_mode", isChecked).apply()
            if (isChecked) {
                mapLibreMap?.locationComponent?.cameraMode = CameraMode.NONE
                Toast.makeText(this@MainActivity, "Compass Mode ON", Toast.LENGTH_SHORT).show()
            } else {
                mapLibreMap?.locationComponent?.cameraMode = CameraMode.TRACKING
                mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
                Toast.makeText(this@MainActivity, "Compass Mode OFF", Toast.LENGTH_SHORT).show()
            }
        }

        switchArrows.isChecked = isArrowsEnabled
        switchArrows.setOnCheckedChangeListener { _, isChecked ->
            isArrowsEnabled = isChecked
            prefs.edit().putBoolean("show_arrows", isChecked).apply()
            if (!isChecked) {
                navArrowView.visibility = View.GONE
                tvNavDistance.visibility = View.GONE
            }
        }

        val uri = StorageHelper.getFolderUri(this)
        tvFolderPath.text = uri?.lastPathSegment ?: "Folder not selected"

        val rSize = StorageHelper.getFolderSizeInMB(StorageHelper.getRoutesDir(this))
        val mSize = StorageHelper.getFolderSizeInMB(StorageHelper.getMapsDir(this))
        tvStorageInfo.text = "Routes: $rSize | Maps: $mSize"

        btnCheckUpdate.setOnClickListener {
            UpdateManager.checkForUpdates(this, isManual = true)
        }

        btnChangeFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivityForResult(intent, REQUEST_CODE_SAF)
            dialog.dismiss()
        }

        btnDownload.setOnClickListener {
            mapLibreMap?.let { map ->
                val styleToDownload = if (isSatelliteMode) MapConfig.SATELLITE_STYLE else MapConfig.DEFAULT_STYLE
                OfflineMapManager.downloadCurrentArea(this@MainActivity, map, styleToDownload, false)
                dialog.dismiss()
            }
        }

        btnViewRoutes.setOnClickListener {
            val intent = Intent(this@MainActivity, SavedRoutesActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_ROUTES)
            dialog.dismiss()
        }

        btnDeleteData.setOnClickListener {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Delete Data?")
            builder.setMessage("Are you sure you want to delete all Routes and Maps?")
            builder.setPositiveButton("Yes, Delete") { _, _ ->
                StorageHelper.deleteFolderContents(StorageHelper.getRoutesDir(this@MainActivity))
                StorageHelper.deleteFolderContents(StorageHelper.getMapsDir(this@MainActivity))
                Toast.makeText(this@MainActivity, "Storage Cleared Successfully!", Toast.LENGTH_SHORT).show()
                val newRSize = StorageHelper.getFolderSizeInMB(StorageHelper.getRoutesDir(this@MainActivity))
                val newMSize = StorageHelper.getFolderSizeInMB(StorageHelper.getMapsDir(this@MainActivity))
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
