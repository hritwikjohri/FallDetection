// app/src/main/java/com/hritwik/falldetection/service/DataTransmissionService.kt
package com.hritwik.falldetection.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hritwik.falldetection.health.HealthDataManager
import com.hritwik.falldetection.location.LocationManager
import com.hritwik.falldetection.model.FallEvent
import com.hritwik.falldetection.model.SOSData
import com.hritwik.falldetection.model.TransmissionStats
import com.hritwik.falldetection.mqtt.MqttManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DataTransmissionService(
    private val context: Context,
    private val healthDataManager: HealthDataManager,
    private val locationManager: LocationManager,
    private val mqttManager: MqttManager
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Handlers for periodic tasks
    private val heartRateHandler = Handler(Looper.getMainLooper())
    private val oxygenHandler = Handler(Looper.getMainLooper())
    private val locationHandler = Handler(Looper.getMainLooper())
    private val watchHealthHandler = Handler(Looper.getMainLooper())
    private val deviceStatusHandler = Handler(Looper.getMainLooper())

    // Runnables for periodic data transmission
    private val heartRateRunnable = object : Runnable {
        override fun run() {
            if (_isTransmitting.value) {
                transmitHeartRate()
                heartRateHandler.postDelayed(this, HEART_RATE_INTERVAL)
            }
        }
    }

    private val oxygenRunnable = object : Runnable {
        override fun run() {
            if (_isTransmitting.value) {
                transmitOxygen()
                oxygenHandler.postDelayed(this, OXYGEN_INTERVAL)
            }
        }
    }

    private val locationRunnable = object : Runnable {
        override fun run() {
            if (_isTransmitting.value) {
                transmitLocation()
                locationHandler.postDelayed(this, LOCATION_INTERVAL)
            }
        }
    }

    private val watchHealthRunnable = object : Runnable {
        override fun run() {
            if (_isTransmitting.value) {
                transmitWatchHealthData()
                watchHealthHandler.postDelayed(this, WATCH_HEALTH_INTERVAL)
            }
        }
    }

    private val deviceStatusRunnable = object : Runnable {
        override fun run() {
            if (_isTransmitting.value) {
                transmitDeviceStatus()
                deviceStatusHandler.postDelayed(this, DEVICE_STATUS_INTERVAL)
            }
        }
    }

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _transmissionStats = MutableStateFlow(TransmissionStats())
    val transmissionStats: StateFlow<TransmissionStats> = _transmissionStats.asStateFlow()

    private val _topicStats = MutableStateFlow<Map<String, TopicStats>>(emptyMap())
    val topicStats: StateFlow<Map<String, TopicStats>> = _topicStats.asStateFlow()

    companion object {
        private const val TAG = "DataTransmissionService"
        private const val HEART_RATE_INTERVAL = 30_000L // 30 seconds
        private const val OXYGEN_INTERVAL = 30_000L // 30 seconds
        private const val LOCATION_INTERVAL = 60_000L // 1 minute
        private const val WATCH_HEALTH_INTERVAL = 60_000L // 1 minute (combined health data)
        private const val DEVICE_STATUS_INTERVAL = 300_000L // 5 minutes (heartbeat)
    }

    fun startTransmission(): Boolean {
        if (_isTransmitting.value) {
            Log.w(TAG, "Transmission already running")
            return true
        }

        // Connect to MQTT first
        if (!mqttManager.isConnected()) {
            mqttManager.connect()
            // Wait a bit for connection
            serviceScope.launch {
                kotlinx.coroutines.delay(2000)
                if (mqttManager.isConnected()) {
                    startPeriodicTransmissions()
                } else {
                    Log.e(TAG, "Failed to connect to MQTT broker")
                }
            }
        } else {
            startPeriodicTransmissions()
        }

        return true
    }

    private fun startPeriodicTransmissions() {
        _isTransmitting.value = true

        // Start location tracking
        locationManager.startLocationUpdates()

        // Start periodic data transmission with staggered start times
        heartRateHandler.post(heartRateRunnable)

        // Start oxygen transmission 10 seconds after heart rate
        oxygenHandler.postDelayed(oxygenRunnable, 10_000L)

        // Start location transmission 20 seconds after heart rate
        locationHandler.postDelayed(locationRunnable, 20_000L)

        // Start watch health transmission 30 seconds after heart rate
        watchHealthHandler.postDelayed(watchHealthRunnable, 30_000L)

        // Start device status transmission 45 seconds after heart rate
        deviceStatusHandler.postDelayed(deviceStatusRunnable, 45_000L)

        // Reset stats
        _transmissionStats.value = TransmissionStats()

        Log.i(TAG, "Data transmission started with topic-based publishing")
    }

    fun stopTransmission() {
        _isTransmitting.value = false

        // Stop all handlers
        heartRateHandler.removeCallbacks(heartRateRunnable)
        oxygenHandler.removeCallbacks(oxygenRunnable)
        locationHandler.removeCallbacks(locationRunnable)
        watchHealthHandler.removeCallbacks(watchHealthRunnable)
        deviceStatusHandler.removeCallbacks(deviceStatusRunnable)

        // Stop location tracking
        locationManager.stopLocationUpdates()

        // Send offline status
        mqttManager.publishDeviceStatus("OFFLINE")

        Log.i(TAG, "Data transmission stopped")
    }

    // Individual transmission methods
    private fun transmitHeartRate() {
        serviceScope.launch {
            try {
                val heartRateData = healthDataManager.generateHeartRateData()
                mqttManager.publishHeartRate(heartRateData)

                updateStats("heartRate", true)
                updateTopicStats("health/heartrate", true)

                Log.d(TAG, "Heart rate transmitted: ${heartRateData.heartRate} BPM")
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting heart rate", e)
                updateStats("heartRate", false)
                updateTopicStats("health/heartrate", false)
            }
        }
    }

    private fun transmitOxygen() {
        serviceScope.launch {
            try {
                val oxygenData = healthDataManager.generateOxygenData()
                mqttManager.publishOxygen(oxygenData)

                updateStats("oxygen", true)
                updateTopicStats("health/spo2", true)

                Log.d(TAG, "Oxygen transmitted: ${oxygenData.oxygenSaturation}%")
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting oxygen", e)
                updateStats("oxygen", false)
                updateTopicStats("health/spo2", false)
            }
        }
    }

    private fun transmitLocation() {
        serviceScope.launch {
            try {
                val locationData = locationManager.currentLocation.value
                if (locationData != null) {
                    mqttManager.publishLocation(locationData)

                    updateStats("location", true)
                    updateTopicStats("location/gps", true)

                    Log.d(TAG, "Location transmitted: ${locationData.latitude}, ${locationData.longitude}")
                } else {
                    Log.w(TAG, "No location data available for transmission")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting location", e)
                updateStats("location", false)
                updateTopicStats("location/gps", false)
            }
        }
    }

    private fun transmitWatchHealthData() {
        serviceScope.launch {
            try {
                val heartRateData = healthDataManager.currentHeartRate.value
                val oxygenData = healthDataManager.currentOxygen.value
                val activityLevel = determineActivityLevel(heartRateData?.heartRate ?: 70)

                mqttManager.publishWatchHealthData(heartRateData, oxygenData, activityLevel)

                updateStats("watchHealth", true)
                updateTopicStats("health/watch", true)

                Log.d(TAG, "Watch health data transmitted (combined)")
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting watch health data", e)
                updateStats("watchHealth", false)
                updateTopicStats("health/watch", false)
            }
        }
    }

    private fun transmitDeviceStatus() {
        serviceScope.launch {
            try {
                val batteryLevel = getBatteryLevel()
                val signalStrength = getSignalStrength()

                mqttManager.publishDeviceStatus("ONLINE", batteryLevel, signalStrength)

                updateStats("deviceStatus", true)
                updateTopicStats("device/status", true)

                Log.d(TAG, "Device status transmitted (heartbeat)")
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting device status", e)
                updateStats("deviceStatus", false)
                updateTopicStats("device/status", false)
            }
        }
    }

    // Emergency transmissions (immediate)
    fun triggerManualSOS() {
        serviceScope.launch {
            try {
                // Get current data
                val currentLocation = locationManager.getCurrentLocationForSOS()
                val currentHeartRate = healthDataManager.currentHeartRate.value
                val currentOxygen = healthDataManager.currentOxygen.value

                val sosData = SOSData(
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude,
                    heartRate = currentHeartRate?.heartRate,
                    oxygenSaturation = currentOxygen?.oxygenSaturation,
                    message = "MANUAL SOS TRIGGERED BY USER"
                )

                // Send SOS message immediately with highest priority
                mqttManager.publishSOS(sosData)

                updateStats("sos", true)
                updateTopicStats("emergency/sos", true)

                Log.w(TAG, "Manual SOS triggered and transmitted")

            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting manual SOS", e)
                updateStats("sos", false)
                updateTopicStats("emergency/sos", false)
            }
        }
    }

    fun triggerFallDetectedSOS(fallEvent: FallEvent) {
        serviceScope.launch {
            try {
                // Get current data
                val currentLocation = locationManager.getCurrentLocationForSOS()
                val currentHeartRate = healthDataManager.currentHeartRate.value
                val currentOxygen = healthDataManager.currentOxygen.value

                // Simulate emergency state in health data
                healthDataManager.simulateEmergencyState()

                // Send fall detection message with highest priority
                mqttManager.publishFallDetected(
                    fallEvent = fallEvent,
                    locationData = currentLocation,
                    heartRate = currentHeartRate?.heartRate,
                    oxygen = currentOxygen?.oxygenSaturation
                )

                updateStats("fallDetection", true)
                updateTopicStats("emergency/fall", true)

                Log.w(TAG, "Fall detection SOS transmitted")

            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting fall detection SOS", e)
                updateStats("fallDetection", false)
                updateTopicStats("emergency/fall", false)
            }
        }
    }

    fun triggerGeofenceEvent(event: String, zoneName: String) {
        serviceScope.launch {
            try {
                val currentLocation = locationManager.currentLocation.value
                if (currentLocation != null) {
                    mqttManager.publishGeofenceEvent(currentLocation, event, zoneName)

                    updateStats("geofence", true)
                    updateTopicStats("location/geofence", true)

                    Log.i(TAG, "Geofence event transmitted: $event at $zoneName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting geofence event", e)
                updateStats("geofence", false)
                updateTopicStats("location/geofence", false)
            }
        }
    }

    // Helper methods
    private fun updateStats(dataType: String, success: Boolean) {
        val currentStats = _transmissionStats.value
        val newStats = when (dataType) {
            "heartRate" -> currentStats.copy(
                heartRateMessagesSent = if (success) currentStats.heartRateMessagesSent + 1 else currentStats.heartRateMessagesSent,
                lastHeartRateTransmission = if (success) System.currentTimeMillis() else currentStats.lastHeartRateTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            "oxygen" -> currentStats.copy(
                oxygenMessagesSent = if (success) currentStats.oxygenMessagesSent + 1 else currentStats.oxygenMessagesSent,
                lastOxygenTransmission = if (success) System.currentTimeMillis() else currentStats.lastOxygenTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            "location" -> currentStats.copy(
                locationMessagesSent = if (success) currentStats.locationMessagesSent + 1 else currentStats.locationMessagesSent,
                lastLocationTransmission = if (success) System.currentTimeMillis() else currentStats.lastLocationTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            "sos" -> currentStats.copy(
                sosMessagesSent = if (success) currentStats.sosMessagesSent + 1 else currentStats.sosMessagesSent,
                lastSOSTransmission = if (success) System.currentTimeMillis() else currentStats.lastSOSTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            "fallDetection" -> currentStats.copy(
                fallDetectionMessagesSent = if (success) currentStats.fallDetectionMessagesSent + 1 else currentStats.fallDetectionMessagesSent,
                lastFallDetectionTransmission = if (success) System.currentTimeMillis() else currentStats.lastFallDetectionTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            "watchHealth" -> currentStats.copy(
                watchHealthMessagesSent = if (success) currentStats.watchHealthMessagesSent + 1 else currentStats.watchHealthMessagesSent,
                lastWatchHealthTransmission = if (success) System.currentTimeMillis() else currentStats.lastWatchHealthTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            "deviceStatus" -> currentStats.copy(
                deviceStatusMessagesSent = if (success) currentStats.deviceStatusMessagesSent + 1 else currentStats.deviceStatusMessagesSent,
                lastDeviceStatusTransmission = if (success) System.currentTimeMillis() else currentStats.lastDeviceStatusTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            "geofence" -> currentStats.copy(
                geofenceMessagesSent = if (success) currentStats.geofenceMessagesSent + 1 else currentStats.geofenceMessagesSent,
                lastGeofenceTransmission = if (success) System.currentTimeMillis() else currentStats.lastGeofenceTransmission,
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
            else -> currentStats.copy(
                errorCount = if (!success) currentStats.errorCount + 1 else currentStats.errorCount
            )
        }
        _transmissionStats.value = newStats
    }

    private fun updateTopicStats(topic: String, success: Boolean) {
        val currentTopicStats = _topicStats.value.toMutableMap()
        val existingStats = currentTopicStats[topic] ?: TopicStats()

        currentTopicStats[topic] = existingStats.copy(
            messagesSent = if (success) existingStats.messagesSent + 1 else existingStats.messagesSent,
            errors = if (!success) existingStats.errors + 1 else existingStats.errors,
            lastTransmission = if (success) System.currentTimeMillis() else existingStats.lastTransmission
        )

        _topicStats.value = currentTopicStats
    }

    private fun determineActivityLevel(heartRate: Int): String {
        return when {
            heartRate < 60 -> "resting"
            heartRate < 90 -> "light"
            heartRate < 120 -> "moderate"
            heartRate < 150 -> "vigorous"
            else -> "maximum"
        }
    }

    private fun getBatteryLevel(): Int {
        // Simulated battery level - can be replaced with actual battery API
        return (75..95).random()
    }

    private fun getSignalStrength(): Int {
        // Simulated signal strength in dBm - can be replaced with actual signal strength API
        return (-50..-30).random()
    }

    fun getTopicStatistics(): Map<String, TopicStats> = _topicStats.value

    fun getTransmissionSummary(): TransmissionSummary {
        val stats = _transmissionStats.value
        val topicStats = _topicStats.value

        return TransmissionSummary(
            totalMessagesSent = stats.getTotalMessages(),
            totalErrors = stats.errorCount,
            successRate = if (stats.getTotalMessages() > 0) {
                ((stats.getTotalMessages() - stats.errorCount).toFloat() / stats.getTotalMessages() * 100)
            } else 0f,
            topicBreakdown = topicStats,
            isTransmitting = _isTransmitting.value,
            mqttConnected = mqttManager.isConnected()
        )
    }

    fun cleanup() {
        stopTransmission()
        mqttManager.cleanup()
        locationManager.cleanup()
        healthDataManager.cleanup()
        Log.d(TAG, "Data transmission service cleaned up")
    }
}

data class TopicStats(
    val messagesSent: Int = 0,
    val errors: Int = 0,
    val lastTransmission: Long = 0L,
    val avgTransmissionTime: Long = 0L
) {
    fun getSuccessRate(): Float {
        val total = messagesSent + errors
        return if (total > 0) (messagesSent.toFloat() / total * 100) else 0f
    }
}

data class TransmissionSummary(
    val totalMessagesSent: Int,
    val totalErrors: Int,
    val successRate: Float,
    val topicBreakdown: Map<String, TopicStats>,
    val isTransmitting: Boolean,
    val mqttConnected: Boolean
)