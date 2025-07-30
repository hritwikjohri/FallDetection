package com.hritwik.falldetection.model

data class LocationData(
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val address: String? = null
)