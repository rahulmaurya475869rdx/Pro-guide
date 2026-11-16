package com.example.proguide

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

object RouteFileManager {

    private var currentCsvFile: File? = null

    // Naya route record karna shuru karein
    fun startNewRoute(context: Context, useSdCard: Boolean) {
        val dir = StorageHelper.getRoutesDir(context, useSdCard)
        val timestamp = System.currentTimeMillis()
        currentCsvFile = File(dir, "route_$timestamp.csv")
        currentCsvFile?.writeText("") // Khali file banayein
    }

    // Naya point file mein append karein (Instant Save)
    fun appendPoint(lat: Double, lng: Double, color: String) {
        currentCsvFile?.appendText("$lat,$lng,$color\n")
    }

    // Recording khatam hone par file ko Compress (GZIP) karein
    fun compressCurrentRoute() {
        val csvFile = currentCsvFile ?: return
        if (!csvFile.exists()) return

        val gzFile = File(csvFile.parentFile, csvFile.name.replace(".csv", ".gz"))

        try {
            val fis = FileInputStream(csvFile)
            val fos = FileOutputStream(gzFile)
            val gzos = GZIPOutputStream(fos)

            val buffer = ByteArray(1024)
            var len: Int
            while (fis.read(buffer).also { len = it } > 0) {
                gzos.write(buffer, 0, len)
            }

            gzos.close()
            fos.close()
            fis.close()

            // Compression ke baad CSV file delete kar dein
            csvFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentCsvFile = null
    }
}
