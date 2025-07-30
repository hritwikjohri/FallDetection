package com.hritwik.falldetection.model

data class OxygenData(
    val timestamp: Long = System.currentTimeMillis(),
    val oxygenSaturation: Int, // SpO2 percentage
    val isNormal: Boolean = oxygenSaturation >= 95
)