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

    fun downloadCurrentArea(context: Context, map: MapLibreMap, styleUrl: String, useSdCard: Boolean) {
        val currentZoom = map.cameraPosition.zoom
        if (currentZoom < 5.0) {
            Toast.makeText(context, "Pehle apne gaon/saher par zoom karein!", Toast.LENGTH_LONG).show()
            return
        }

        val visibleRegion = map.projection.visibleRegion ?: return
        val latLngBounds = visibleRegion.latLngBounds

        OfflineManager.getInstance(context).setOfflineMapboxTileCountLimit(5000)

        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            latLngBounds,
            10.0,
            16.0,
            context.resources.displayMetrics.density
        )

        val metadata = "My Downloaded Area".toByteArray()
        val mainHandler = Handler(Looper.getMainLooper())

        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    mainHandler.post { Toast.makeText(context, "Download Shuru (${if(useSdCard) "SD" else "Phone"})!", Toast.LENGTH_LONG).show() }

                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                mainHandler.post { Toast.makeText(context, "Area Download Complete!", Toast.LENGTH_LONG).show() }
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                
                                // Dummy file banate hain taaki StorageHelper size check kar sake
                                val mapDir = StorageHelper.getMapsDir(context, useSdCard)
                                val dummyFile = java.io.File(mapDir, "map_downloaded_${System.currentTimeMillis()}.tmp")
                                try {
                                    dummyFile.createNewFile()
                                    // Ek choti si size de dete hain (e.g., 5MB) taaki pata chale map download hua hai
                                    // Note: Asal database file Android/Data me hi hoti hai, isliye direct size copy nahi kar sakte
                                    val writer = java.io.FileWriter(dummyFile)
                                    writer.write("Map downloaded at ${System.currentTimeMillis()}")
                                    writer.close()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                        override fun onError(error: OfflineRegionError) {
                            mainHandler.post { Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show() }
                        }
                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            mainHandler.post { Toast.makeText(context, "Limit Exceeded! Limit: $limit", Toast.LENGTH_LONG).show() }
                        }
                    })
                }

                override fun onError(error: String) {
                    mainHandler.post { Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show() }
                }
            }
        )
    }
}
