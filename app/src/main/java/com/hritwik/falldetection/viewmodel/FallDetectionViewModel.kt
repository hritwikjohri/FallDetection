package com.hritwik.falldetection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hritwik.falldetection.mdoel.FallDetectionUiState
import com.hritwik.falldetection.sensors.FallDetectionSensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FallDetectionViewModel(
    private val sensorManager: FallDetectionSensorManager
) : ViewModel() {

    // Expose sensor manager flows
    val isMonitoring: StateFlow<Boolean> = sensorManager.isMonitoring
    val fallDetected: StateFlow<Boolean> = sensorManager.fallDetected
    val currentSensorReading = sensorManager.currentSensorReading
    val currentPhase = sensorManager.currentPhase
    val fallEvents = sensorManager.fallEvents
    val debugInfo = sensorManager.debugInfo
    val sensorInfo = sensorManager.sensorInfo

    // UI state
    private val _uiState = MutableStateFlow(FallDetectionUiState())
    val uiState: StateFlow<FallDetectionUiState> = _uiState.asStateFlow()

    fun startMonitoring() {
        viewModelScope.launch {
            val success = sensorManager.startMonitoring()
            if (!success) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to start monitoring. Check if sensors are available."
                )
            }
        }
    }

    fun stopMonitoring() {
        sensorManager.stopMonitoring()
    }

    fun updateThresholds(freeFall: Float, impact: Float, rotation: Float) {
        sensorManager.updateThresholds(freeFall, impact, rotation)
        _uiState.value = _uiState.value.copy(
            lastThresholdUpdate = System.currentTimeMillis()
        )
    }

    fun resetDetector() {
        sensorManager.resetDetector()
        _uiState.value = _uiState.value.copy(
            lastReset = System.currentTimeMillis()
        )
    }

    fun resetFallAlert() {
        // The sensor manager handles the fall detected state
        // This is mainly for UI feedback
        _uiState.value = _uiState.value.copy(
            fallAlertDismissed = true
        )
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.cleanup()
    }
}