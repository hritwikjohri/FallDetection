package com.hritwik.falldetection.health

import android.content.Context
import android.util.Log
import com.hritwik.falldetection.model.HeartRateData
import com.hritwik.falldetection.model.OxygenData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin
import kotlin.random.Random

class HealthDataManager(private val context: Context) {

    private val _currentHeartRate = MutableStateFlow<HeartRateData?>(null)
    val currentHeartRate: StateFlow<HeartRateData?> = _currentHeartRate.asStateFlow()

    private val _currentOxygen = MutableStateFlow<OxygenData?>(null)
    val currentOxygen: StateFlow<OxygenData?> = _currentOxygen.asStateFlow()

    private val _heartRateHistory = MutableStateFlow<List<HeartRateData>>(emptyList())
    val heartRateHistory: StateFlow<List<HeartRateData>> = _heartRateHistory.asStateFlow()

    private val _oxygenHistory = MutableStateFlow<List<OxygenData>>(emptyList())
    val oxygenHistory: StateFlow<List<OxygenData>> = _oxygenHistory.asStateFlow()

    // Base values for realistic simulation
    private var baseHeartRate = 75
    private var baseOxygen = 98
    private var stressLevel = 0f // 0 = relaxed, 1 = stressed
    private var activityLevel = 0f // 0 = resting, 1 = active
    private var startTime = System.currentTimeMillis()

    companion object {
        private const val TAG = "HealthDataManager"
        private const val HISTORY_LIMIT = 100
    }

    fun generateHeartRateData(): HeartRateData {
        // Simulate realistic heart rate variations
        val timeElapsed = (System.currentTimeMillis() - startTime) / 1000.0

        // Base variation due to breathing and natural variability
        val breathingVariation = sin(timeElapsed * 0.2) * 3

        // Random variation
        val randomVariation = Random.nextFloat() * 10 - 5

        // Stress and activity modifiers
        val stressModifier = stressLevel * 20
        val activityModifier = activityLevel * 30

        // Calculate final heart rate
        var heartRate = (baseHeartRate + breathingVariation + randomVariation +
                stressModifier + activityModifier).toInt()

        // Ensure realistic bounds
        heartRate = heartRate.coerceIn(45, 150)

        val data = HeartRateData(heartRate = heartRate)

        // Update current and history
        _currentHeartRate.value = data
        updateHeartRateHistory(data)

        // Occasionally change base parameters to simulate different states
        if (Random.nextFloat() < 0.1f) { // 10% chance every reading
            simulateStateChange()
        }

        Log.d(TAG, "Generated heart rate: ${data.heartRate} BPM")
        return data
    }

    fun generateOxygenData(): OxygenData {
        // Simulate realistic oxygen saturation
        val timeElapsed = (System.currentTimeMillis() - startTime) / 1000.0

        // Small natural variation
        val naturalVariation = sin(timeElapsed * 0.1) * 1
        val randomVariation = Random.nextFloat() * 2 - 1

        // Activity can slightly reduce oxygen levels
        val activityModifier = activityLevel * -2

        var oxygenLevel = (baseOxygen + naturalVariation + randomVariation + activityModifier).toInt()

        // Ensure realistic bounds (normal range 95-100%)
        oxygenLevel = oxygenLevel.coerceIn(92, 100)

        val data = OxygenData(oxygenSaturation = oxygenLevel)

        // Update current and history
        _currentOxygen.value = data
        updateOxygenHistory(data)

        Log.d(TAG, "Generated oxygen level: ${data.oxygenSaturation}%")
        return data
    }

    private fun simulateStateChange() {
        val scenarios = listOf(
            "resting" to { baseHeartRate = Random.nextInt(65, 80); stressLevel = 0f; activityLevel = 0f },
            "walking" to { baseHeartRate = Random.nextInt(80, 100); stressLevel = 0.2f; activityLevel = 0.4f },
            "stressed" to { baseHeartRate = Random.nextInt(85, 110); stressLevel = 0.7f; activityLevel = 0.2f },
            "exercising" to { baseHeartRate = Random.nextInt(120, 140); stressLevel = 0.3f; activityLevel = 0.8f },
            "sleeping" to { baseHeartRate = Random.nextInt(55, 70); stressLevel = 0f; activityLevel = 0f }
        )

        val (scenario, action) = scenarios.random()
        action()

        // Adjust oxygen based on activity
        baseOxygen = when {
            activityLevel > 0.6f -> Random.nextInt(96, 99) // Slightly lower during exercise
            activityLevel < 0.2f -> Random.nextInt(98, 100) // Higher when resting
            else -> Random.nextInt(97, 99)
        }

        Log.d(TAG, "State changed to: $scenario (HR: $baseHeartRate, Stress: $stressLevel, Activity: $activityLevel)")
    }

    private fun updateHeartRateHistory(data: HeartRateData) {
        val currentHistory = _heartRateHistory.value.toMutableList()
        currentHistory.add(data)

        // Keep only recent data
        if (currentHistory.size > HISTORY_LIMIT) {
            currentHistory.removeAt(0)
        }

        _heartRateHistory.value = currentHistory
    }

    private fun updateOxygenHistory(data: OxygenData) {
        val currentHistory = _oxygenHistory.value.toMutableList()
        currentHistory.add(data)

        // Keep only recent data
        if (currentHistory.size > HISTORY_LIMIT) {
            currentHistory.removeAt(0)
        }

        _oxygenHistory.value = currentHistory
    }

    // Simulate emergency/fall scenario
    fun simulateEmergencyState() {
        baseHeartRate = Random.nextInt(110, 140) // Elevated due to stress/pain
        baseOxygen = Random.nextInt(90, 95) // Potentially lower due to breathing issues
        stressLevel = 1f
        activityLevel = 0.8f
        Log.w(TAG, "Emergency state simulated - elevated vitals")
    }

    // Reset to normal state
    fun resetToNormalState() {
        baseHeartRate = Random.nextInt(70, 80)
        baseOxygen = Random.nextInt(97, 99)
        stressLevel = 0.1f
        activityLevel = 0.2f
        Log.i(TAG, "Reset to normal state")
    }

    fun getAverageHeartRate(minutes: Int = 5): Float {
        val cutoffTime = System.currentTimeMillis() - (minutes * 60 * 1000)
        val recentData = _heartRateHistory.value.filter { it.timestamp > cutoffTime }
        return if (recentData.isNotEmpty()) {
            recentData.map { it.heartRate }.average().toFloat()
        } else 0f
    }

    fun getAverageOxygen(minutes: Int = 5): Float {
        val cutoffTime = System.currentTimeMillis() - (minutes * 60 * 1000)
        val recentData = _oxygenHistory.value.filter { it.timestamp > cutoffTime }
        return if (recentData.isNotEmpty()) {
            recentData.map { it.oxygenSaturation }.average().toFloat()
        } else 0f
    }

    fun cleanup() {
        Log.d(TAG, "Health data manager cleaned up")
    }
}