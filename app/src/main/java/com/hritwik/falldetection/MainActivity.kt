package com.hritwik.falldetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.hritwik.falldetection.health.HealthDataManager
import com.hritwik.falldetection.location.LocationManager
import com.hritwik.falldetection.mqtt.MqttManager
import com.hritwik.falldetection.sensors.FallDetectionSensorManager
import com.hritwik.falldetection.service.DataTransmissionService
import com.hritwik.falldetection.ui.theme.FallDetectionTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: FallDetectionSensorManager
    private lateinit var emergencySystem: EmergencySystem
    private lateinit var healthDataManager: HealthDataManager
    private lateinit var locationManager: LocationManager
    private lateinit var mqttManager: MqttManager
    private lateinit var dataTransmissionService: DataTransmissionService

    companion object {
        private const val TAG = "MainActivity"
    }

    // Permission launcher with better handling
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationPermissionGranted) {
            // Initialize location manager after permission granted
            lifecycleScope.launch {
                try {
                    locationManager.startLocationUpdates()
                    Log.d(TAG, "Location updates started")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start location updates", e)
                }
            }
        }

        // Log permission results
        permissions.entries.forEach { (permission, granted) ->
            Log.d(TAG, "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate: Initializing Fall Detection App")

        try {
            // Initialize managers with error handling
            initializeManagers()

            // Request permissions
            requestNecessaryPermissions()

            enableEdgeToEdge()
            setContent {
                FallDetectionTheme {
                    val snackbarHostState = remember { SnackbarHostState() }
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        modifier = Modifier.fillMaxSize()
                    ) { paddingValues ->
                        Box(modifier = Modifier.padding(paddingValues)) {
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate", e)
            // Could show an error dialog here
        }
    }

    private fun initializeManagers() {
        try {
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

            Log.d(TAG, "All managers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing managers", e)
            throw e
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

        // Background location permission for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                // Request background location separately after foreground location is granted
                Log.d(TAG, "Background location permission will be requested separately")
            }
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

        // Notification permissions for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All permissions already granted")
            // Start location updates if permissions already granted
            lifecycleScope.launch {
                try {
                    locationManager.startLocationUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start location updates", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Cleaning up resources")

        try {
            // Cleanup all managers
            dataTransmissionService.cleanup()
            sensorManager.cleanup()
            emergencySystem.cleanup()
            healthDataManager.cleanup()
            locationManager.cleanup()
            mqttManager.cleanup()
            Log.d(TAG, "All managers cleaned up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: App going to background")
        // Keep services running in background for fall detection
        // You might want to start a foreground service here for continued monitoring
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: App coming to foreground")

        // Reconnect MQTT if needed
        lifecycleScope.launch {
            try {
                if (!mqttManager.isConnected()) {
                    Log.d(TAG, "Reconnecting to MQTT broker")
                    mqttManager.connect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reconnect MQTT", e)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: App stopping")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: App starting")
    }
}