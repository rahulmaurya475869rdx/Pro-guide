package com.example.proguide

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var isSatelliteMode = false
    private var currentStyle: Style? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        mapView.getMapAsync { map ->
            mapLibreMap = map
            // MapConfig se style utha kar lagana
            map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                currentStyle = style
                LocationHelper.enableLocation(this, map, style)
                LocationHelper.moveToCurrentLocation(this, map)
            }
        }

        // My Location Button
        findViewById<FloatingActionButton>(R.id.btnMyLocation).setOnClickListener {
            mapLibreMap?.let { LocationHelper.moveToCurrentLocation(this, it) }
        }

        // Layers Button
        findViewById<FloatingActionButton>(R.id.btnLayers).setOnClickListener {
            mapLibreMap?.let { map ->
                if (isSatelliteMode) {
                    map.setStyle(MapConfig.DEFAULT_STYLE) { style ->
                        currentStyle = style
                        LocationHelper.enableLocation(this, map, style)
                    }
                    isSatelliteMode = false
                    Toast.makeText(this, "Default Mode", Toast.LENGTH_SHORT).show()
                } else {
                    map.setStyle(MapConfig.SATELLITE_STYLE) { style ->
                        currentStyle = style
                        LocationHelper.enableLocation(this, map, style)
                    }
                    isSatelliteMode = true
                    Toast.makeText(this, "Satellite Mode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Download Button (Abhi ke liye sirf message dikhayega)
        findViewById<FloatingActionButton>(R.id.btnDownload).setOnClickListener {
            Toast.makeText(this, "Offline Download Feature Coming Soon!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mapLibreMap?.let { map ->
                currentStyle?.let { style -> LocationHelper.enableLocation(this, map, style) }
                LocationHelper.moveToCurrentLocation(this, map)
            }
        }
    }

    // MapView Lifecycle
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
