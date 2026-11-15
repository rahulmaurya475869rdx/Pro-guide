package com.example.proguide

import android.content.Context
import android.os.Handler
import android.os.Looper
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

        // Background thread se UI update karne ke liye Handler
        val mainHandler = Handler(Looper.getMainLooper())

        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    mainHandler.post {
                        Toast.makeText(context, "Download Shuru Ho Gaya! (Wait karein)", Toast.LENGTH_LONG).show()
                    }

                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                mainHandler.post {
                                    Toast.makeText(context, "Area Download Complete! Ab Offline Chalega.", Toast.LENGTH_LONG).show()
                                }
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            mainHandler.post {
                                Toast.makeText(context, "Download Error: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            mainHandler.post {
                                Toast.makeText(context, "Limit Exceeded! Limit: $limit", Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                }

                override fun onError(error: String) {
                    mainHandler.post {
                        Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
