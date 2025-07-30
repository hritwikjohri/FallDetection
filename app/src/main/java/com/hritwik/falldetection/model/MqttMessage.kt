package com.hritwik.falldetection.model

data class MqttMessage(
    val deviceId: String,
    val messageType: String, // "HEART_RATE", "OXYGEN", "LOCATION", "SOS", "FALL_DETECTED"
    val timestamp: Long,
    val data: Any
)