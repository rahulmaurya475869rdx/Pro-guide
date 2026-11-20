package com.example.proguide

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RoutePoint(val lat: Double, val lng: Double, val color: String)

data class SavedRoute(
    val fileName: String,
    val name: String,
    val description: String,
    val timestamp: Long,
    val points: List<RoutePoint>
)

object RouteFileManager {

    private var currentPoints = mutableListOf<RoutePoint>()

    fun startNewRoute() {
        currentPoints.clear()
    }

    fun appendPoint(lat: Double, lng: Double, color: String) {
        currentPoints.add(RoutePoint(lat, lng, color))
    }

    fun saveCurrentRoute(context: Context, name: String, description: String) {
        if (currentPoints.isEmpty()) return

        val dir = StorageHelper.getRoutesDir(context) ?: return
        val timestamp = System.currentTimeMillis()
        val fileName = "route_$timestamp.json"
        
        val file = dir.createFile("application/json", fileName) ?: return

        try {
            val jsonObject = JSONObject()
            jsonObject.put("name", name)
            jsonObject.put("description", description)
            jsonObject.put("timestamp", timestamp)

            val pointsArray = JSONArray()
            for (p in currentPoints) {
                val pObj = JSONObject()
                pObj.put("lat", p.lat)
                pObj.put("lng", p.lng)
                pObj.put("color", p.color)
                pointsArray.put(pObj)
            }
            jsonObject.put("points", pointsArray)

            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                outputStream.write(jsonObject.toString().toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentPoints.clear()
    }

    fun getAllSavedRoutes(context: Context): List<SavedRoute> {
        val dir = StorageHelper.getRoutesDir(context) ?: return emptyList()
        val routes = mutableListOf<SavedRoute>()
        
        val files = dir.listFiles().filter { it.name?.endsWith(".json") == true }
        for (file in files) {
            try {
                val text = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    val json = JSONObject(text)
                    val name = json.optString("name", "Unnamed Route")
                    val desc = json.optString("description", "")
                    val time = json.optLong("timestamp", 0L)
                    
                    val pointsArray = json.getJSONArray("points")
                    val points = mutableListOf<RoutePoint>()
                    for (i in 0 until pointsArray.length()) {
                        val pObj = pointsArray.getJSONObject(i)
                        points.add(RoutePoint(pObj.getDouble("lat"), pObj.getDouble("lng"), pObj.getString("color")))
                    }
                    
                    routes.add(SavedRoute(file.name ?: "", name, desc, time, points))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return routes.sortedByDescending { it.timestamp }
    }

    fun deleteRoute(context: Context, fileName: String) {
        val dir = StorageHelper.getRoutesDir(context) ?: return
        val file = dir.findFile(fileName)
        file?.delete()
    }
}
