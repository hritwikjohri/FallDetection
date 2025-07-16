package com.hritwik.falldetection.mdoel

data class FallDetectionUiState(
    val errorMessage: String? = null,
    val lastThresholdUpdate: Long = 0L,
    val lastReset: Long = 0L,
    val fallAlertDismissed: Boolean = false
)