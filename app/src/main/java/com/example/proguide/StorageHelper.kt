package com.example.proguide

import android.content.Context
import java.io.File

object StorageHelper {

    // Phone ya SD Card ka base path nikalne ke liye
    fun getBaseDir(context: Context, useSdCard: Boolean): File {
        val dirs = context.getExternalFilesDirs(null)
        // Index 0 = Internal, Index 1 = SD Card (agar laga hua hai)
        val base = if (useSdCard && dirs.size > 1 && dirs[1] != null) dirs[1] else dirs[0]
        return base ?: context.filesDir
    }

    // Routes ka folder
    fun getRoutesDir(context: Context, useSdCard: Boolean): File {
        val dir = File(getBaseDir(context, useSdCard), "ProGuide/Routes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // Maps ka folder
    fun getMapsDir(context: Context, useSdCard: Boolean): File {
        val dir = File(getBaseDir(context, useSdCard), "ProGuide/Maps")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // Folder ka Size (MB me) nikalne ke liye
    fun getFolderSizeInMB(dir: File): String {
        var size: Long = 0
        if (dir.exists()) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    size += file.length()
                }
            }
        }
        val sizeInMB = size.toDouble() / (1024 * 1024)
        return String.format("%.2f MB", sizeInMB)
    }
}
