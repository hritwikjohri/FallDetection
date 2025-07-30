package com.hritwik.falldetection.model

data class SOSData(
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val heartRate: Int? = null,
    val oxygenSaturation: Int? = null,
    val message: String = "MANUAL SOS TRIGGERED"
)