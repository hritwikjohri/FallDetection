package com.hritwik.falldetection.model


data class FallEvent(
    val timestamp: Long,
    val confidence: Float,
    val phase: FallPhase,
    val details: String
)