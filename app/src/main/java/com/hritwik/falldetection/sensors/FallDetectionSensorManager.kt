package com.hritwik.falldetection.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hritwik.falldetection.FallDetector
import com.hritwik.falldetection.model.SensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class FallDetectionSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val fallDetector = FallDetector()

    // Latest sensor values
    private var latestAccel = Triple(0f, 0f, 0f)
    private var latestGyro = Triple(0f, 0f, 0f)
    private var hasAccelData = false
    private var hasGyroData = false

    // State flows
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _fallDetected = MutableStateFlow(false)
    val fallDetected: StateFlow<Boolean> = _fallDetected.asStateFlow()

    private val _currentSensorReading = MutableStateFlow<SensorReading?>(null)
    val currentSensorReading: StateFlow<SensorReading?> = _currentSensorReading.asStateFlow()

    private val _sensorInfo = MutableStateFlow("")
    val sensorInfo: StateFlow<String> = _sensorInfo.asStateFlow()

    // Expose fall detector flows
    val currentPhase = fallDetector.currentPhase
    val fallEvents = fallDetector.fallEvents
    val debugInfo = fallDetector.debugInfo

    // Sensor data processing
    private var lastProcessingTime = 0L
    private val processingInterval = 50L // Process every 50ms (20Hz)

    companion object {
        private const val TAG = "FallSensorManager"
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
    }

    init {
        updateSensorInfo()
    }

    fun startMonitoring(): Boolean {
        if (accelerometer == null || gyroscope == null) {
            Log.e(TAG, "Required sensors not available")
            return false
        }

        val accelSuccess = sensorManager.registerListener(this, accelerometer, SENSOR_DELAY)
        val gyroSuccess = sensorManager.registerListener(this, gyroscope, SENSOR_DELAY)

        val success = accelSuccess && gyroSuccess
        _isMonitoring.value = success

        if (success) {
            Log.d(TAG, "Sensor monitoring started")
            fallDetector.reset()
        } else {
            Log.e(TAG, "Failed to start sensor monitoring")
        }

        return success
    }

    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
        _isMonitoring.value = false
        _fallDetected.value = false
        hasAccelData = false
        hasGyroData = false
        Log.d(TAG, "Sensor monitoring stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAccel = Triple(event.values[0], event.values[1], event.values[2])
                hasAccelData = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestGyro = Triple(event.values[0], event.values[1], event.values[2])
                hasGyroData = true
            }
        }

        // Process sensor data when we have both accelerometer and gyroscope data
        if (hasAccelData && hasGyroData) {
            val currentTime = System.currentTimeMillis()

            // Throttle processing to avoid overwhelming the algorithm
            if (currentTime - lastProcessingTime >= processingInterval) {
                processSensorData(currentTime)
                lastProcessingTime = currentTime
            }
        }
    }

    private fun processSensorData(timestamp: Long) {
        val reading = SensorReading(
            accelerometer = latestAccel,
            gyroscope = latestGyro,
            timestamp = timestamp
        )

        _currentSensorReading.value = reading

        try {
            val fallDetected = fallDetector.processSensorReading(reading)

            if (fallDetected && !_fallDetected.value) {
                _fallDetected.value = true
                Log.w(TAG, "FALL DETECTED!")

                // Auto-reset fall detection after 10 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    _fallDetected.value = false
                }, 10000)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing sensor data", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name}, accuracy: $accuracy")
        updateSensorInfo()
    }

    private fun updateSensorInfo() {
        val info = buildString {
            accelerometer?.let { sensor ->
                appendLine("Accelerometer: ${sensor.name}")
                appendLine("Vendor: ${sensor.vendor}")
                appendLine("Range: ±${sensor.maximumRange} m/s²")
                appendLine("Resolution: ${sensor.resolution} m/s²")
                appendLine("Power: ${sensor.power} mA")
                appendLine()
            }

            gyroscope?.let { sensor ->
                appendLine("Gyroscope: ${sensor.name}")
                appendLine("Vendor: ${sensor.vendor}")
                appendLine("Range: ±${sensor.maximumRange} rad/s")
                appendLine("Resolution: ${sensor.resolution} rad/s")
                appendLine("Power: ${sensor.power} mA")
            }
        }

        _sensorInfo.value = info
    }

    // Fall detector configuration
    fun updateThresholds(freeFall: Float, impact: Float, rotation: Float) {
        fallDetector.updateThresholds(freeFall, impact, rotation)
        Log.d(TAG, "Thresholds updated: freeFall=$freeFall, impact=$impact, rotation=$rotation")
    }

    fun resetDetector() {
        fallDetector.reset()
        _fallDetected.value = false
        Log.d(TAG, "Fall detector reset")
    }

    // Utility functions
    fun calculateMagnitude(vector: Triple<Float, Float, Float>): Float {
        return sqrt(
            vector.first * vector.first +
                    vector.second * vector.second +
                    vector.third * vector.third
        )
    }

    fun getRecentAccelerations(): List<Float> {
        return fallDetector.getRecentAccelerations()
    }

    fun getRecentRotations(): List<Float> {
        return fallDetector.getRecentRotations()
    }

    fun isSensorAvailable(): Boolean {
        return accelerometer != null && gyroscope != null
    }

    fun cleanup() {
        stopMonitoring()
        Log.d(TAG, "Sensor manager cleaned up")
    }
}