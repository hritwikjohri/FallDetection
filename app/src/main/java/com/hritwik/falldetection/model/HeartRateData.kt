package com.hritwik.falldetection.model

data class HeartRateData(
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Int, // BPM
    val isNormal: Boolean = heartRate in 60..100
)