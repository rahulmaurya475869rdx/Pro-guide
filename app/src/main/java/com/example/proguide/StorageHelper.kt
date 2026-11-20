package com.example.proguide

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object StorageHelper {
    
    // SharedPreferences me folder ka URI save karenge
    const val PREFS_NAME = "ProGuidePrefs"
    const val KEY_FOLDER_URI = "saf_folder_uri"

    fun getFolderUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_FOLDER_URI, null)
        return if (uriString != null) Uri.parse(uriString) else null
    }

    fun saveFolderUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun getRootDocumentFile(context: Context): DocumentFile? {
        val uri = getFolderUri(context) ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun getRoutesDir(context: Context): DocumentFile? {
        val root = getRootDocumentFile(context) ?: return null
        var routesDir = root.findFile("Routes")
        if (routesDir == null || !routesDir.isDirectory) {
            routesDir = root.createDirectory("Routes")
        }
        return routesDir
    }

    fun getMapsDir(context: Context): DocumentFile? {
        val root = getRootDocumentFile(context) ?: return null
        var mapsDir = root.findFile("Maps")
        if (mapsDir == null || !mapsDir.isDirectory) {
            mapsDir = root.createDirectory("Maps")
        }
        return mapsDir
    }

    fun getFolderSizeInMB(dir: DocumentFile?): String {
        if (dir == null || !dir.exists()) return "0.00 MB"
        var size: Long = 0
        val files = dir.listFiles()
        for (file in files) {
            size += if (file.isDirectory) {
                getFolderSizeRecursive(file)
            } else {
                file.length()
            }
        }
        val sizeInMB = size.toDouble() / (1024 * 1024)
        return String.format("%.2f MB", sizeInMB)
    }

    private fun getFolderSizeRecursive(dir: DocumentFile): Long {
        var size: Long = 0
        val files = dir.listFiles()
        for (file in files) {
            size += if (file.isDirectory) getFolderSizeRecursive(file) else file.length()
        }
        return size
    }

    fun deleteFolderContents(dir: DocumentFile?): Boolean {
        if (dir == null) return false
        var deleted = true
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                if (!deleteFolderContents(file)) deleted = false
            } else {
                if (!file.delete()) deleted = false
            }
        }
        return deleted
    }
}
