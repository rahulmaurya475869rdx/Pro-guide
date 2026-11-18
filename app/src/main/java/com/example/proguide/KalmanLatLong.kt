package com.example.proguide

import kotlin.math.sqrt

class KalmanLatLong(private val qMetresPerSecond: Float = 3f) {
    private var variance = -1.0
    private var latitude = 0.0
    private var longitude = 0.0
    private var timeStamp = 0L
    private val minAccuracy = 1.0

    fun getLat(): Double = latitude
    fun getLng(): Double = longitude

    fun getAccuracy(): Float = sqrt(variance).toFloat()

    fun process(lat: Double, lng: Double, accuracy: Float, timeMilliseconds: Long) {
        if (accuracy < minAccuracy) {
            variance = minAccuracy * minAccuracy
        } else {
            variance = (accuracy * accuracy).toDouble()
        }

        if (variance < 0.0) {
            timeStamp = timeMilliseconds
            latitude = lat
            longitude = lng
        } else {
            val timeInc = timeMilliseconds - timeStamp
            if (timeInc > 0) {
                variance += timeInc * qMetresPerSecond * qMetresPerSecond / 1000.0
                timeStamp = timeMilliseconds
            }

            val k = variance / (variance + (accuracy * accuracy))
            latitude += k * (lat - latitude)
            longitude += k * (lng - longitude)
            variance *= (1.0 - k)
        }
    }
}
