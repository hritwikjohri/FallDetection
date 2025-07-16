package com.hritwik.falldetection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hritwik.falldetection.sensors.FallDetectionSensorManager
import com.hritwik.falldetection.ui.theme.FallDetectionTheme

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: FallDetectionSensorManager
    private lateinit var emergencySystem: EmergencySystem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = FallDetectionSensorManager(this)
        emergencySystem = EmergencySystem(this)

        enableEdgeToEdge()
        setContent {
            FallDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FallDetectionApp(
                        sensorManager = sensorManager,
                        emergencySystem = emergencySystem
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.cleanup()
        emergencySystem.cleanup()
    }
}