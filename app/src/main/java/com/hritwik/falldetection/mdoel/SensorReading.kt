package com.hritwik.falldetection.mdoel

data class SensorReading(
    val accelerometer: Triple<Float, Float, Float>,
    val gyroscope: Triple<Float, Float, Float>,
    val timestamp: Long
)