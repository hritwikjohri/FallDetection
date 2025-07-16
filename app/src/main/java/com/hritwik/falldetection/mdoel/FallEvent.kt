package com.hritwik.falldetection.mdoel


data class FallEvent(
    val timestamp: Long,
    val confidence: Float,
    val phase: FallPhase,
    val details: String
)