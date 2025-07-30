package com.hritwik.falldetection.model

data class TransmissionStats(
    val heartRateMessagesSent: Int = 0,
    val oxygenMessagesSent: Int = 0,
    val locationMessagesSent: Int = 0,
    val sosMessagesSent: Int = 0,
    val fallDetectionMessagesSent: Int = 0,
    val watchHealthMessagesSent: Int = 0,
    val deviceStatusMessagesSent: Int = 0,
    val geofenceMessagesSent: Int = 0,
    val errorCount: Int = 0,
    val lastHeartRateTransmission: Long = 0L,
    val lastOxygenTransmission: Long = 0L,
    val lastLocationTransmission: Long = 0L,
    val lastSOSTransmission: Long = 0L,
    val lastFallDetectionTransmission: Long = 0L,
    val lastWatchHealthTransmission: Long = 0L,
    val lastDeviceStatusTransmission: Long = 0L,
    val lastGeofenceTransmission: Long = 0L
) {
    fun getTotalMessages(): Int {
        return heartRateMessagesSent + oxygenMessagesSent + locationMessagesSent +
                sosMessagesSent + fallDetectionMessagesSent + watchHealthMessagesSent +
                deviceStatusMessagesSent + geofenceMessagesSent
    }
}
