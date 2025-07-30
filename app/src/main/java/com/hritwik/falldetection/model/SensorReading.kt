package com.hritwik.falldetection.model

data class SensorReading(
    val accelerometer: Triple<Float, Float, Float>,
    val gyroscope: Triple<Float, Float, Float>,
    val timestamp: Long
)