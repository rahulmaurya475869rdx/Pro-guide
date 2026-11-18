// ... (Imports same rahenge, bas inko add karna:)
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
// ...

    // Variables me add karo
    private val ARROW_LAYER = "route-arrows-layer"
    private var arrowAnimator: ValueAnimator? = null

// ... (onCreate function me tts init ke baad ye add karo)

    // setupRouteLayers function update karo
    private fun setupRouteLayers(style: Style) {
        if (style.getSource(ROUTE_PURPLE_SOURCE) != null) style.removeSource(ROUTE_PURPLE_SOURCE)
        if (style.getLayer(ROUTE_PURPLE_LAYER) != null) style.removeLayer(ROUTE_PURPLE_LAYER)

        // 1. Purple Line Layer
        style.addSource(GeoJsonSource(ROUTE_PURPLE_SOURCE, FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(LineLayer(ROUTE_PURPLE_LAYER, ROUTE_PURPLE_SOURCE).withProperties(
            PropertyFactory.lineColor("#D500F9"), PropertyFactory.lineWidth(5f), PropertyFactory.lineJoin("round")
        ))

        // 2. NAYA: Arrow Image Load karo (Vector to Bitmap)
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

        // 3. Arrow Layer (Jo road ke upar arrows draw karega)
        if (style.getLayer(ARROW_LAYER) != null) style.removeLayer(ARROW_LAYER)
        
        val arrowLayer = SymbolLayer(ARROW_LAYER, ROUTE_PURPLE_SOURCE).withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE), // Line ke sath place karega
            PropertyFactory.iconImage("route-arrow-icon"),
            PropertyFactory.iconSize(1.5f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.symbolSpacing(30f) // Har 30 pixel par arrow aayega
        )
        style.addLayer(arrowLayer)
        
        // 4. Glow Animation start karo
        startArrowGlowAnimation(style)
    }

    // NAYA: Arrow ko Glow (Pulse) karne ke liye Animation
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
                PropertyFactory.iconColor("#2196F3") // Blue color glow
            )
        }
        arrowAnimator?.start()
    }

    // Return mode start hone par Arrow dikhao
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
        
        // Arrow layer ko visible karo
        currentStyle?.getLayer(ARROW_LAYER)?.setProperties(PropertyFactory.visibility(Property.VISIBILITY_VISIBLE))
    }

    // Return mode stop hone par Arrow hatao
    private fun stopReturning() {
        isReturning = false
        btnReturn.setImageResource(android.R.drawable.ic_menu_revert)
        speak("Return mode stopped")
        Toast.makeText(this@MainActivity, "Return Mode OFF", Toast.LENGTH_SHORT).show()
        
        // Arrow layer ko hide karo
        currentStyle?.getLayer(ARROW_LAYER)?.setProperties(PropertyFactory.visibility(Property.VISIBILITY_NONE))
    }

    // Purana triggerTurnNavigation (Jisme emoji arrows the) usko hata do, 
    // Ab voice guidance ke liye checkNavigation function use karo
    
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

            // Sirf Voice ke liye logic (Arrow ab MapLibre khud handle kar raha hai)
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

    // onDestroy me animator cancel karo
    override fun onDestroy() {
        arrowAnimator?.cancel()
        if (this::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy(); mapView.onDestroy()
    }
