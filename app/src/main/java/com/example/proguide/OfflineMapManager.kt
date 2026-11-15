package com.example.proguide

import android.content.Context
import android.widget.Toast
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

object OfflineMapManager {

    fun downloadCurrentArea(context: Context, map: MapLibreMap, styleUrl: String) {
        val visibleRegion = map.projection.visibleRegion ?: return
        val latLngBounds = visibleRegion.latLngBounds

        // Zoom level 10 se 16 tak download karega (Gaon/Street level)
        val minZoom = 10.0
        val maxZoom = 16.0

        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            latLngBounds,
            minZoom,
            maxZoom,
            context.resources.displayMetrics.density
        )

        val metadata = "My Downloaded Area".toByteArray()

        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    Toast.makeText(context, "Download Shuru Ho Gaya! (Wait karein)", Toast.LENGTH_LONG).show()

                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                Toast.makeText(context, "Area Download Complete! Ab Offline Chalega.", Toast.LENGTH_LONG).show()
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            }
                        }
                        override fun onError(error: OfflineRegionError) {
                            Toast.makeText(context, "Download Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                        override fun mapboxTileLimitExceeded() {}
                    })
                }

                override fun onError(error: String) {
                    Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
