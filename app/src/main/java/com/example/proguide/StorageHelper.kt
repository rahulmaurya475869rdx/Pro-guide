package com.example.proguide

import android.content.Context
import java.io.File

object StorageHelper {

    fun getBaseDir(context: Context, useSdCard: Boolean): File {
        val dirs = context.getExternalFilesDirs(null)
        val base = if (useSdCard && dirs.size > 1 && dirs[1] != null) dirs[1] else dirs[0]
        return base ?: context.filesDir
    }

    fun getRoutesDir(context: Context, useSdCard: Boolean): File {
        val dir = File(getBaseDir(context, useSdCard), "ProGuide/Routes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMapsDir(context: Context, useSdCard: Boolean): File {
        val dir = File(getBaseDir(context, useSdCard), "ProGuide/Maps")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getFolderSizeInMB(dir: File): String {
        var size: Long = 0
        if (dir.exists()) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    size += if (file.isDirectory) getFolderSizeRecursive(file) else file.length()
                }
            }
        }
        val sizeInMB = size.toDouble() / (1024 * 1024)
        return String.format("%.2f MB", sizeInMB)
    }

    private fun getFolderSizeRecursive(dir: File): Long {
        var size: Long = 0
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isDirectory) getFolderSizeRecursive(file) else file.length()
            }
        }
        return size
    }

    fun deleteFolderContents(dir: File): Boolean {
        var deleted = true
        if (dir.exists()) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        if (!deleteFolderContents(file)) deleted = false
                    } else {
                        if (!file.delete()) deleted = false
                    }
                }
            }
        }
        return deleted
    }
}
