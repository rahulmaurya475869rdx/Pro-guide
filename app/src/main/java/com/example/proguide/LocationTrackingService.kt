package com.example.proguide

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.maplibre.android.geometry.LatLng
import java.util.*
import kotlin.math.abs

class LocationTrackingService : Service(), TextToSpeech.OnInitListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var isVoiceEnabled = true

    private var isRecording = false
    private var isReturning = false
    private var selectedVehicle = "Car"
    private val announcedTurns = mutableSetOf<Int>()
    private var currentRoutePointsForNav = mutableListOf<LatLng>()
    private var lastRecordedLatLng: LatLng? = null
    private var kalmanFilter = KalmanLatLong()
    private var isOffRouteAlerted = false

    companion object {
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_START_NAVIGATION = "ACTION_START_NAVIGATION"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        
        const val BROADCAST_LOCATION = "BROADCAST_LOCATION"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_TARGET_BEARING = "target_bearing"
        const val EXTRA_SHOW_ARROWS = "show_arrows"
        const val EXTRA_IS_RECORDING = "is_recording"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                isRecording = true
                isReturning = false
                selectedVehicle = intent.getStringExtra("vehicle") ?: "Car"
                startForegroundService("Recording Route...", "ProGuide is recording your route.")
                startLocationUpdates()
            }
            ACTION_START_NAVIGATION -> {
                isRecording = false
                isReturning = true
                val points = intent.getParcelableArrayListExtra<LatLng>("route_points")
                if (points != null) {
                    currentRoutePointsForNav = points.toMutableList()
                }
                startForegroundService("Navigating...", "ProGuide is guiding you.")
                startLocationUpdates()
            }
            ACTION_STOP_SERVICE -> {
                stopLocationUpdates()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService(title: String, message: String) {
        val channelId = "ProGuideTracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ProGuide Tracking", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
            
        startForeground(1, notification)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                val accuracy = location.accuracy
                if (accuracy <= 30f) {
                    kalmanFilter.process(location.latitude, location.longitude, accuracy, System.currentTimeMillis())
                    val userLoc = LatLng(kalmanFilter.getLat(), kalmanFilter.getLng())

                    if (isRecording) {
                        var shouldRecord = false
                        if (lastRecordedLatLng == null) shouldRecord = true
                        else {
                            val dist = calculateDistance(lastRecordedLatLng!!.latitude, lastRecordedLatLng!!.longitude, userLoc.latitude, userLoc.longitude)
                            if (dist > getMinDistance()) shouldRecord = true
                        }
                        if (shouldRecord) {
                            RouteFileManager.appendPoint(userLoc.latitude, userLoc.longitude, "#D500F9")
                            lastRecordedLatLng = userLoc
                        }
                    }

                    var targetBearing = 0f
                    var showArrows = false

                    if (isReturning && currentRoutePointsForNav.size >= 2) {
                        val navResult = checkNavigation(userLoc)
                        targetBearing = navResult.first
                        showArrows = navResult.second
                    }

                    // Broadcast to MainActivity
                    val intent = Intent(BROADCAST_LOCATION)
                    intent.setPackage(packageName)
                    intent.putExtra(EXTRA_LAT, userLoc.latitude)
                    intent.putExtra(EXTRA_LNG, userLoc.longitude)
                    intent.putExtra(EXTRA_TARGET_BEARING, targetBearing)
                    intent.putExtra(EXTRA_SHOW_ARROWS, showArrows)
                    intent.putExtra(EXTRA_IS_RECORDING, isRecording)
                    sendBroadcast(intent)
                }
            }
        }
    }

    private fun checkNavigation(userLoc: LatLng): Pair<Float, Boolean> {
        var closestIndex = -1
        var minDistance = Float.MAX_VALUE
        
        for (i in 0 until currentRoutePointsForNav.size - 1) {
            val dist = calculateDistance(userLoc.latitude, userLoc.longitude, currentRoutePointsForNav[i].latitude, currentRoutePointsForNav[i].longitude)
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = i
            }
        }

        if (closestIndex == -1) return Pair(0f, false)

        // OFF-ROUTE
        if (minDistance > 50f) {
            if (!isOffRouteAlerted) {
                speak("You are off route. Please return to the path.")
                isOffRouteAlerted = true
            }
            val targetPoint = currentRoutePointsForNav[closestIndex]
            return Pair(calculateBearing(userLoc, targetPoint), true)
        } else {
            isOffRouteAlerted = false
        }

        if (closestIndex >= currentRoutePointsForNav.size - 2) return Pair(0f, false)

        var nextTurnIndex = closestIndex
        var turnAngle = 0f
        var distToTurn = 0f

        for (i in closestIndex until currentRoutePointsForNav.size - 2) {
            val p1 = currentRoutePointsForNav[i]
            val p2 = currentRoutePointsForNav[i + 1]
            val p3 = currentRoutePointsForNav[i + 2]

            val bearing1 = calculateBearing(p1, p2)
            val bearing2 = calculateBearing(p2, p3)
            val angle = calculateTurnAngle(bearing1, bearing2)

            if (abs(angle) > 15f) { 
                nextTurnIndex = i + 1
                turnAngle = angle
                distToTurn = calculateDistance(userLoc.latitude, userLoc.longitude, p2.latitude, p2.longitude)
                break
            }
        }

        if (nextTurnIndex == closestIndex) { 
            val lastPoint = currentRoutePointsForNav.last()
            distToTurn = calculateDistance(userLoc.latitude, userLoc.longitude, lastPoint.latitude, lastPoint.longitude)
            turnAngle = 0f
        }

        if (distToTurn in 15f..40f && !announcedTurns.contains(nextTurnIndex) && turnAngle != 0f) {
            announcedTurns.add(nextTurnIndex)
            val voiceText = when {
                abs(turnAngle) < 20f -> "Continue straight"
                abs(turnAngle) in 20f..45f -> if (turnAngle > 0) "Slight left" else "Slight right"
                abs(turnAngle) in 45f..120f -> if (turnAngle > 0) "Turn left" else "Turn right"
                else -> if (turnAngle > 0) "Sharp left" else "Sharp right"
            }
            speak("In ${distToTurn.toInt()} meters, $voiceText")
        }

        val targetPoint = currentRoutePointsForNav[nextTurnIndex]
        return Pair(calculateBearing(userLoc, targetPoint), true)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            ttsReady = true
        }
    }

    private fun speak(text: String) {
        if (isVoiceEnabled && ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
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
        return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    private fun calculateTurnAngle(bearing1: Float, bearing2: Float): Float {
        var diff = bearing2 - bearing1
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff 
    }

    private fun getMinDistance(): Float = when(selectedVehicle) { "Walk" -> 10f; "Cycle" -> 15f; "Bike" -> 20f; "Car" -> 30f; else -> 15f }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val startLoc = Location("start").apply { latitude = lat1; longitude = lon1 }
        val endLoc = Location("end").apply { latitude = lat2; longitude = lon2 }
        return startLoc.distanceTo(endLoc)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
