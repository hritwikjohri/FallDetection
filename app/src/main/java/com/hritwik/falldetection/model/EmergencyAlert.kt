package com.hritwik.falldetection.model

import android.location.Location

data class EmergencyAlert(
    val timestamp: Long,
    val location: Location?,
    val alertType: AlertType,
    val isActive: Boolean = true
)