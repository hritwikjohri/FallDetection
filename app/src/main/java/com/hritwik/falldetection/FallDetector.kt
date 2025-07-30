package com.hritwik.falldetection

import android.annotation.SuppressLint
import com.hritwik.falldetection.model.FallEvent
import com.hritwik.falldetection.model.FallPhase
import com.hritwik.falldetection.model.SensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.sqrt

class FallDetector {

    companion object {
        private const val WINDOW_SIZE = 50
        private const val MIN_FALL_DURATION = 200L // milliseconds
        private const val MAX_FALL_DURATION = 2000L // milliseconds

        // Default thresholds based on research
        private const val DEFAULT_FREE_FALL_THRESHOLD = 3.0f // Below this = potential free fall
        private const val DEFAULT_IMPACT_THRESHOLD = 20.0f   // Above this = potential impact
        private const val DEFAULT_ROTATION_THRESHOLD = 3.0f  // Gyroscope threshold for rotation
    }

    private val sensorBuffer = mutableListOf<SensorReading>()
    private val _currentPhase = MutableStateFlow(FallPhase.NORMAL)
    val currentPhase: StateFlow<FallPhase> = _currentPhase.asStateFlow()

    private val _fallEvents = MutableStateFlow<List<FallEvent>>(emptyList())
    val fallEvents: StateFlow<List<FallEvent>> = _fallEvents.asStateFlow()

    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo.asStateFlow()

    // Configuration
    var freeFallThreshold = DEFAULT_FREE_FALL_THRESHOLD
    var impactThreshold = DEFAULT_IMPACT_THRESHOLD
    var rotationThreshold = DEFAULT_ROTATION_THRESHOLD

    // State tracking
    private var freeFallStartTime: Long? = null
    private var impactDetectedTime: Long? = null
    private var lastFallDetectionTime = 0L
    private val fallCooldownPeriod = 5000L // 5 seconds between detections

    fun processSensorReading(reading: SensorReading): Boolean {
        addToBuffer(reading)

        if (sensorBuffer.size < 10) return false // Need minimum data

        val accelMagnitude = calculateMagnitude(reading.accelerometer)
        val gyroMagnitude = calculateMagnitude(reading.gyroscope)

        updateDebugInfo(reading, accelMagnitude, gyroMagnitude)

        return when (_currentPhase.value) {
            FallPhase.NORMAL -> checkForFreeFall(reading, accelMagnitude)
            FallPhase.FREE_FALL -> checkForImpact(reading, accelMagnitude)
            FallPhase.IMPACT -> checkPostImpact(reading)
            FallPhase.POST_IMPACT -> checkFallCompletion(reading)
            FallPhase.FALL_DETECTED -> handleFallDetected(reading)
        }
    }

    private fun addToBuffer(reading: SensorReading) {
        sensorBuffer.add(reading)
        if (sensorBuffer.size > WINDOW_SIZE) {
            sensorBuffer.removeAt(0)
        }
    }

    private fun checkForFreeFall(reading: SensorReading, accelMagnitude: Float): Boolean {
        // Phase 1: Monitor for free fall condition (low acceleration)
        if (accelMagnitude < freeFallThreshold) {
            if (freeFallStartTime == null) {
                freeFallStartTime = reading.timestamp
                _currentPhase.value = FallPhase.FREE_FALL
                addEvent(reading.timestamp, 0.3f, FallPhase.FREE_FALL, "Free fall detected")
            }
        } else {
            // Reset if acceleration returns to normal
            freeFallStartTime = null
            _currentPhase.value = FallPhase.NORMAL
        }
        return false
    }

    private fun checkForImpact(reading: SensorReading, accelMagnitude: Float): Boolean {
        val freeFallStart = freeFallStartTime ?: return resetToNormal()
        val freeFallDuration = reading.timestamp - freeFallStart

        // Check if free fall lasted too long (probably not a fall)
        if (freeFallDuration > MAX_FALL_DURATION) {
            return resetToNormal()
        }

        // Phase 2: Check for impact spike within time window
        if (accelMagnitude > impactThreshold && freeFallDuration > MIN_FALL_DURATION) {
            impactDetectedTime = reading.timestamp
            _currentPhase.value = FallPhase.IMPACT
            addEvent(reading.timestamp, 0.6f, FallPhase.IMPACT,
                "Impact detected after ${freeFallDuration}ms free fall")
            return false
        }

        // Continue monitoring if still in reasonable timeframe
        return false
    }

    private fun checkPostImpact(reading: SensorReading): Boolean {
        val impactTime = impactDetectedTime ?: return resetToNormal()

        // Phase 3: Analyze gyroscope for rotation patterns
        val rotationDetected = analyzeRotationPattern()
        val postImpactStability = analyzePostImpactMovement()

        if (rotationDetected && postImpactStability) {
            _currentPhase.value = FallPhase.POST_IMPACT
            return false
        }

        // If enough time has passed, make final determination
        if (reading.timestamp - impactTime > 1000) { // 1 second post-impact analysis
            return determineFallResult(reading)
        }

        return false
    }

    private fun checkFallCompletion(reading: SensorReading): Boolean {
        // Phase 4: Verify post-impact movement patterns
        val confidence = calculateFallConfidence()

        return if (confidence > 0.7f) {
            detectFall(reading, confidence)
        } else {
            resetToNormal()
        }
    }

    private fun handleFallDetected(reading: SensorReading): Boolean {
        // Maintain fall detected state briefly, then reset
        if (reading.timestamp - lastFallDetectionTime > fallCooldownPeriod) {
            return resetToNormal()
        }
        return true
    }

    private fun analyzeRotationPattern(): Boolean {
        if (sensorBuffer.size < 20) return false

        val recentGyroReadings = sensorBuffer.takeLast(20)
            .map { calculateMagnitude(it.gyroscope) }

        val maxRotation = recentGyroReadings.maxOrNull() ?: 0f
        val avgRotation = recentGyroReadings.average().toFloat()

        // High rotation indicates tumbling during fall
        return maxRotation > rotationThreshold || avgRotation > (rotationThreshold * 0.5f)
    }

    private fun analyzePostImpactMovement(): Boolean {
        if (sensorBuffer.size < 15) return false

        val recentAccelReadings = sensorBuffer.takeLast(15)
            .map { calculateMagnitude(it.accelerometer) }

        val variance = calculateVariance(recentAccelReadings)

        // After impact, expect some movement but then stabilization
        return variance < 10.0f // Relatively stable after impact
    }

    private fun determineFallResult(reading: SensorReading): Boolean {
        val confidence = calculateFallConfidence()

        return if (confidence > 0.6f) {
            detectFall(reading, confidence)
        } else {
            resetToNormal()
        }
    }

    private fun calculateFallConfidence(): Float {
        val freeFallStart = freeFallStartTime ?: return 0f
        val impactTime = impactDetectedTime ?: return 0f

        var confidence = 0f

        // Factor 1: Free fall duration (optimal range: 200-800ms)
        val freeFallDuration = impactTime - freeFallStart
        val durationScore = when (freeFallDuration) {
            in 200..800 -> 0.3f
            in 100..1200 -> 0.2f
            else -> 0.1f
        }
        confidence += durationScore

        // Factor 2: Impact magnitude
        val maxImpact =
            sensorBuffer.takeLast(10).maxOfOrNull { calculateMagnitude(it.accelerometer) } ?: 0f
        val impactScore = when {
            maxImpact > 25f -> 0.3f
            maxImpact > 15f -> 0.2f
            else -> 0.1f
        }
        confidence += impactScore

        // Factor 3: Rotation during fall
        val rotationScore = if (analyzeRotationPattern()) 0.2f else 0f
        confidence += rotationScore

        // Factor 4: Post-impact stability
        val stabilityScore = if (analyzePostImpactMovement()) 0.2f else 0f
        confidence += stabilityScore

        return confidence.coerceIn(0f, 1f)
    }

    private fun detectFall(reading: SensorReading, confidence: Float): Boolean {
        if (reading.timestamp - lastFallDetectionTime < fallCooldownPeriod) {
            return false // Too soon after last detection
        }

        _currentPhase.value = FallPhase.FALL_DETECTED
        lastFallDetectionTime = reading.timestamp

        val freeFallDuration = (impactDetectedTime ?: 0) - (freeFallStartTime ?: 0)
        addEvent(
            reading.timestamp,
            confidence,
            FallPhase.FALL_DETECTED,
            "Fall confirmed! Free fall: ${freeFallDuration}ms, Confidence: ${(confidence * 100).toInt()}%"
        )

        return true
    }

    private fun resetToNormal(): Boolean {
        _currentPhase.value = FallPhase.NORMAL
        freeFallStartTime = null
        impactDetectedTime = null
        return false
    }

    private fun calculateMagnitude(vector: Triple<Float, Float, Float>): Float {
        return sqrt(vector.first * vector.first +
                vector.second * vector.second +
                vector.third * vector.third)
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        return values.map { (it - mean).pow(2) }.average().toFloat()
    }

    private fun addEvent(timestamp: Long, confidence: Float, phase: FallPhase, details: String) {
        val event = FallEvent(timestamp, confidence, phase, details)
        _fallEvents.value = _fallEvents.value + event

        // Keep only recent events
        if (_fallEvents.value.size > 10) {
            _fallEvents.value = _fallEvents.value.takeLast(10)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateDebugInfo(reading: SensorReading, accelMag: Float, gyroMag: Float) {
        val freeFallDuration = freeFallStartTime?.let { reading.timestamp - it } ?: 0

        _debugInfo.value = """
            Phase: ${_currentPhase.value}
            Accel: ${String.format("%.2f", accelMag)} m/s²
            Gyro: ${String.format("%.2f", gyroMag)} rad/s
            Free Fall Duration: ${freeFallDuration}ms
            Buffer Size: ${sensorBuffer.size}
            Thresholds: Fall=${freeFallThreshold}, Impact=${impactThreshold}
        """.trimIndent()
    }

    fun updateThresholds(freeFall: Float, impact: Float, rotation: Float) {
        freeFallThreshold = freeFall
        impactThreshold = impact
        rotationThreshold = rotation
    }

    fun reset() {
        sensorBuffer.clear()
        _currentPhase.value = FallPhase.NORMAL
        freeFallStartTime = null
        impactDetectedTime = null
        _fallEvents.value = emptyList()
    }

    fun getRecentAccelerations(): List<Float> {
        return sensorBuffer.takeLast(20).map { calculateMagnitude(it.accelerometer) }
    }

    fun getRecentRotations(): List<Float> {
        return sensorBuffer.takeLast(20).map { calculateMagnitude(it.gyroscope) }
    }
}