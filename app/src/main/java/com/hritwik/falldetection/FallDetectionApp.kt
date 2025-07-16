package com.hritwik.falldetection

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hritwik.falldetection.mdoel.AlertType
import com.hritwik.falldetection.mdoel.EmergencyAlert
import com.hritwik.falldetection.mdoel.FallEvent
import com.hritwik.falldetection.mdoel.FallPhase
import com.hritwik.falldetection.mdoel.SensorReading
import com.hritwik.falldetection.sensors.FallDetectionSensorManager
import com.hritwik.falldetection.ui.components.FallDetectionMetrics
import com.hritwik.falldetection.ui.components.FallRiskIndicator
import com.hritwik.falldetection.ui.components.SensorVisualizationCard
import com.hritwik.falldetection.viewmodel.FallDetectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallDetectionApp(
    sensorManager: FallDetectionSensorManager,
    emergencySystem: EmergencySystem,
    viewModel: FallDetectionViewModel = viewModel { FallDetectionViewModel(sensorManager) }
) {
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val fallDetected by viewModel.fallDetected.collectAsState()
    val currentPhase by viewModel.currentPhase.collectAsState()
    val sensorReading by viewModel.currentSensorReading.collectAsState()
    val fallEvents by viewModel.fallEvents.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val isEmergencyMode by emergencySystem.isEmergencyMode.collectAsState()

    // Trigger emergency system when fall is detected
    LaunchedEffect(fallDetected) {
        if (fallDetected) {
            emergencySystem.triggerEmergencyAlert(AlertType.FALL_DETECTION)
        }
    }

    // Show fall alert
    if (isEmergencyMode) {
        EmergencyAlertDialog(
            onEmergencyCall = {
                val contacts = emergencySystem.emergencyContacts.value
                val primaryContact = contacts.find { it.isPrimary }
                primaryContact?.let { contact ->
                    emergencySystem.makeEmergencyCall(contact.phoneNumber,
                        EmergencyAlert(
                            timestamp = System.currentTimeMillis(),
                            location = null,
                            alertType = AlertType.FALL_DETECTION
                        )
                    )
                }
            },
            onDismiss = {
                emergencySystem.cancelEmergencyAlert()
                viewModel.resetFallAlert()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fall Detection") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = when (currentPhase) {
                        FallPhase.NORMAL -> MaterialTheme.colorScheme.primary
                        FallPhase.FREE_FALL -> Color(0xFFFF9800) // Orange
                        FallPhase.IMPACT -> Color(0xFFF44336) // Red
                        FallPhase.POST_IMPACT -> Color(0xFF9C27B0) // Purple
                        FallPhase.FALL_DETECTED -> Color(0xFFD32F2F) // Dark Red
                    }
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Monitoring Control
            item {
                MonitoringControlCard(
                    isMonitoring = isMonitoring,
                    currentPhase = currentPhase,
                    onStartMonitoring = viewModel::startMonitoring,
                    onStopMonitoring = viewModel::stopMonitoring
                )
            }

            // Live Sensor Data
            if (isMonitoring) {
                item {
                    LiveSensorDataCard(
                        sensorReading = sensorReading,
                        sensorManager = sensorManager
                    )
                }

                // Sensor Visualization
                item {
                    SensorVisualizationCard(
                        sensorManager = sensorManager,
                        isMonitoring = true
                    )
                }

                // Fall Risk Indicator
                sensorReading?.let { reading ->
                    item {
                        FallRiskIndicator(
                            currentAcceleration = sensorManager.calculateMagnitude(reading.accelerometer),
                            currentRotation = sensorManager.calculateMagnitude(reading.gyroscope),
                            freeFallThreshold = 3.0f, // These should come from viewModel
                            impactThreshold = 20.0f,
                            rotationThreshold = 3.0f
                        )
                    }
                }

                // Detection Metrics
                item {
                    FallDetectionMetrics(
                        sensorManager = sensorManager,
                        isMonitoring = true
                    )
                }
            }

            // Detection Configuration
            item {
                DetectionConfigCard(
                    onUpdateThresholds = viewModel::updateThresholds,
                    onResetDetector = viewModel::resetDetector
                )
            }

            // Debug Information
            if (isMonitoring) {
                item {
                    DebugInfoCard(debugInfo = debugInfo)
                }
            }

            // Fall Events History
            if (fallEvents.isNotEmpty()) {
                item {
                    FallEventsCard(events = fallEvents)
                }
            }

            // Sensor Information
            item {
                SensorInfoCard(sensorManager = sensorManager)
            }
        }
    }
}

@Composable
fun MonitoringControlCard(
    isMonitoring: Boolean,
    currentPhase: FallPhase,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = if (isMonitoring) Color.Green else Color.Gray
                )
                Text(
                    text = "Status: ${if (isMonitoring) "MONITORING" else "STOPPED"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isMonitoring) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (currentPhase) {
                            FallPhase.NORMAL -> Icons.Default.Check
                            FallPhase.FREE_FALL -> Icons.Default.Warning
                            FallPhase.IMPACT -> Icons.Default.Error
                            FallPhase.POST_IMPACT -> Icons.Default.Info
                            FallPhase.FALL_DETECTED -> Icons.Default.Emergency
                        },
                        contentDescription = null,
                        tint = when (currentPhase) {
                            FallPhase.NORMAL -> Color.Green
                            FallPhase.FREE_FALL -> Color(0xFFFF9800)
                            FallPhase.IMPACT -> Color.Red
                            FallPhase.POST_IMPACT -> Color(0xFF9C27B0)
                            FallPhase.FALL_DETECTED -> Color.Red
                        }
                    )
                    Text(
                        text = "Phase: ${currentPhase.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartMonitoring,
                    enabled = !isMonitoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }

                Button(
                    onClick = onStopMonitoring,
                    enabled = isMonitoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
fun LiveSensorDataCard(
    sensorReading: SensorReading?,
    sensorManager: FallDetectionSensorManager
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Sensors, contentDescription = null)
                Text(
                    text = "Live Sensor Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            sensorReading?.let { reading ->
                // Accelerometer Data
                SensorDataRow(
                    label = "Accelerometer",
                    values = reading.accelerometer,
                    unit = "m/s²",
                    magnitude = sensorManager.calculateMagnitude(reading.accelerometer)
                )

                // Gyroscope Data
                SensorDataRow(
                    label = "Gyroscope",
                    values = reading.gyroscope,
                    unit = "rad/s",
                    magnitude = sensorManager.calculateMagnitude(reading.gyroscope)
                )

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                Text(
                    text = "Last Update: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(reading.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SensorDataRow(
    label: String,
    values: Triple<Float, Float, Float>,
    unit: String,
    magnitude: Float
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "X: ${String.format("%.2f", values.first)} $unit",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Y: ${String.format("%.2f", values.second)} $unit",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Z: ${String.format("%.2f", values.third)} $unit",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Magnitude: ${String.format("%.2f", magnitude)} $unit",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun DetectionConfigCard(
    onUpdateThresholds: (Float, Float, Float) -> Unit,
    onResetDetector: () -> Unit
) {
    var freeFallThreshold by remember { mutableFloatStateOf(3.0f) }
    var impactThreshold by remember { mutableFloatStateOf(20.0f) }
    var rotationThreshold by remember { mutableFloatStateOf(3.0f) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Text(
                    text = "Detection Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Free Fall Threshold
            Column {
                Text("Free Fall Threshold: ${String.format("%.1f", freeFallThreshold)} m/s²")
                Text(
                    text = "Lower values = more sensitive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = freeFallThreshold,
                    onValueChange = { freeFallThreshold = it },
                    valueRange = 1.0f..8.0f,
                    steps = 28
                )
            }

            // Impact Threshold
            Column {
                Text("Impact Threshold: ${String.format("%.1f", impactThreshold)} m/s²")
                Text(
                    text = "Higher values = less sensitive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = impactThreshold,
                    onValueChange = { impactThreshold = it },
                    valueRange = 10.0f..40.0f,
                    steps = 30
                )
            }

            // Rotation Threshold
            Column {
                Text("Rotation Threshold: ${String.format("%.1f", rotationThreshold)} rad/s")
                Text(
                    text = "Rotation sensitivity during fall",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = rotationThreshold,
                    onValueChange = { rotationThreshold = it },
                    valueRange = 1.0f..10.0f,
                    steps = 18
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onUpdateThresholds(freeFallThreshold, impactThreshold, rotationThreshold)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Update, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update")
                }

                OutlinedButton(
                    onClick = onResetDetector,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
fun DebugInfoCard(debugInfo: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Text(
                    text = "Debug Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = debugInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun FallEventsCard(events: List<FallEvent>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Text(
                    text = "Fall Events (${events.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            events.takeLast(5).forEach { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = event.phase.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(event.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = event.details,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SensorInfoCard(sensorManager: FallDetectionSensorManager) {
    val sensorInfo by sensorManager.sensorInfo.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text(
                    text = "Sensor Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (sensorManager.isSensorAvailable()) "✅ All required sensors available" else "❌ Required sensors missing",
                style = MaterialTheme.typography.bodyMedium,
                color = if (sensorManager.isSensorAvailable()) Color.Green else Color.Red
            )

            if (sensorInfo.isNotEmpty()) {
                Text(
                    text = sensorInfo,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun EmergencyAlertDialog(
    onEmergencyCall: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(30) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
        if (countdown == 0) {
            onEmergencyCall()
        }
    }

    AlertDialog(
        onDismissRequest = { /* Don't allow dismissing by clicking outside */ },
        icon = {
            Icon(
                Icons.Default.Emergency,
                contentDescription = null,
                tint = Color.Red
            )
        },
        title = {
            Text(
                text = "🚨 FALL DETECTED!",
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("A fall has been detected!")
                Text("Emergency services will be called automatically in:")
                Text(
                    text = "$countdown seconds",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                Text("Are you okay?")
            }
        },
        confirmButton = {
            Button(
                onClick = onEmergencyCall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Icon(Icons.Default.Phone, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Call Now")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("I'm OK - Cancel")
            }
        }
    )
}