package com.example.proguide

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    // TUJHE TERA ASLI LINK YAHAN MIL GAYA HAI
    private const val VERSION_URL = "https://raw.githubusercontent.com/rahulmaurya475869rdx/Pro-guide/main/version.json"

    fun checkForUpdates(activity: Activity, isManual: Boolean = false) {
        if (isManual) {
            Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show()
        }

        Thread {
            try {
                val url = URL(VERSION_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val newVersionCode = json.getInt("versionCode")
                    val newVersionName = json.getString("versionName")
                    val apkUrl = json.getString("apkUrl")
                    val updateMsg = json.optString("updateMessage", "A new version is available!")
                    val whatsNew = json.optString("whatsNew", "Performance improvements and bug fixes.")

                    val currentVersionCode = getAppVersionCode(activity)

                    if (newVersionCode > currentVersionCode) {
                        activity.runOnUiThread {
                            showUpdateDialog(activity, newVersionName, apkUrl, updateMsg, whatsNew)
                        }
                    } else {
                        if (isManual) {
                            activity.runOnUiThread {
                                AlertDialog.Builder(activity)
                                    .setTitle("No Updates")
                                    .setMessage("You are already using the latest version of ProGuide.")
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isManual) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Failed to check for updates. Check your internet.", Toast.LENGTH_SHORT).show()
                    }
                }
                e.printStackTrace()
            }
        }.start()
    }

    private fun getAppVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    private fun showUpdateDialog(activity: Activity, versionName: String, apkUrl: String, message: String, whatsNew: String) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available (v$versionName)")
            .setMessage("$message\n\nWhat's New:\n$whatsNew")
            .setCancelable(false)
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallApk(activity, apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstallApk(activity: Activity, apkUrl: String) {
        try {
            val destination = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ProGuide_Update.apk")
            if (destination.exists()) destination.delete()

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("ProGuide Update")
                .setDescription("Downloading the latest version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destination))
                .setAllowedOverMetered(true)

            val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            Toast.makeText(activity, "Download started...", Toast.LENGTH_SHORT).show()

            val onDownloadComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk(activity, destination)
                        activity.unregisterReceiver(this)
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                activity.registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }

        } catch (e: Exception) {
            Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(activity: Activity, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open installer. Please find the APK in your Downloads folder and install it manually.", Toast.LENGTH_LONG).show()
        }
    }
}
