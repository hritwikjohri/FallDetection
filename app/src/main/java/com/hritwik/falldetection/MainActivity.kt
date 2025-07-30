package com.hritwik.falldetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hritwik.falldetection.health.HealthDataManager
import com.hritwik.falldetection.location.LocationManager
import com.hritwik.falldetection.mqtt.MqttManager
import com.hritwik.falldetection.sensors.FallDetectionSensorManager
import com.hritwik.falldetection.service.DataTransmissionService
import com.hritwik.falldetection.ui.theme.FallDetectionTheme

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: FallDetectionSensorManager
    private lateinit var emergencySystem: EmergencySystem
    private lateinit var healthDataManager: HealthDataManager
    private lateinit var locationManager: LocationManager
    private lateinit var mqttManager: MqttManager
    private lateinit var dataTransmissionService: DataTransmissionService

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationPermissionGranted) {
            // Initialize location manager after permission granted
            locationManager.startLocationUpdates()
        }

        // Log permission results
        permissions.entries.forEach { (permission, granted) ->
            android.util.Log.d("MainActivity", "Permission $permission: $granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers
        sensorManager = FallDetectionSensorManager(this)
        emergencySystem = EmergencySystem(this)
        healthDataManager = HealthDataManager(this)
        locationManager = LocationManager(this, lifecycleScope)
        mqttManager = MqttManager(this, lifecycleScope)

        dataTransmissionService = DataTransmissionService(
            context = this,
            healthDataManager = healthDataManager,
            locationManager = locationManager,
            mqttManager = mqttManager
        )

        // Request permissions
        requestNecessaryPermissions()

        enableEdgeToEdge()
        setContent {
            FallDetectionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FallDetectionApp(
                        sensorManager = sensorManager,
                        emergencySystem = emergencySystem,
                        healthDataManager = healthDataManager,
                        locationManager = locationManager,
                        mqttManager = mqttManager,
                        dataTransmissionService = dataTransmissionService
                    )
                }
            }
        }
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Call permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE)
        }

        // SMS permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup all managers
        dataTransmissionService.cleanup()
        sensorManager.cleanup()
        emergencySystem.cleanup()
        healthDataManager.cleanup()
        locationManager.cleanup()
        mqttManager.cleanup()
    }

    override fun onPause() {
        super.onPause()
        // Keep services running in background for fall detection
    }

    override fun onResume() {
        super.onResume()
        // Reconnect MQTT if needed
        if (!mqttManager.isConnected()) {
            mqttManager.connect()
        }
    }
}